package com.example.maoshahin.finalsender;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;

import android.os.Handler;

import com.ipaulpro.afilechooser.utils.FileUtils;

import org.json.JSONException;

public class MainActivity extends Activity {


    private DatagramPacket dp;
    private TextView tv;
    private String st = "";
    private UDPSender udpsender;
    private TextView TV_filePath;
    private ArrayList<Frame> myWindow;


    private static final int SERVER_PORT = 7005;
    private static final String SERVER_IP = "192.168.1.38";
    private static final int PORT_MY = 7005;

    public static final int WINDOW_SIZE = 6;
    public static final int DATA_SIZE = 1024;
    private static final int START_SEQUENCE_NUM = 1;
    private static final int TIMER_MS = 5000;
    private static final int BUFFER_SIZE = DATA_SIZE*2;


    UDPReceiver mUDPReceiver = null;
    FileInputStream fis = null;
    int fileCount;
    int sequenceNo;

    Handler mHandler = null;
    private static final int REQUEST_CHOOSER = 1234;


    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // set fields
        ((TextView) findViewById(R.id.tv_ip)).setText(SERVER_IP);
        ((TextView) findViewById(R.id.tv_port)).setText(SERVER_PORT + "");
        ((TextView) findViewById(R.id.tv_port_my)).setText(PORT_MY + "");
        ((TextView) findViewById(R.id.tv_ip_my)).setText(getIpAddress());
        TV_filePath = (TextView) findViewById(R.id.tv_file);
        TV_filePath.setText("/storage/emulated/0/DCIM/COMP7005/20KB.txt");


        // create receiver thread for acks
        if(mUDPReceiver == null) {
            mUDPReceiver = new UDPReceiver(MainActivity.this);
        }
        mUDPReceiver.start();

        // get status field and create hundler to add logs to the field from different thread
        tv = (TextView) findViewById(R.id.tv);
        mHandler = new Handler();

        // create sender class
        udpsender = new UDPSender();
    }

    /**
     * get local ip address and return as a string
     * @return ip address
     */
    public String getIpAddress() {
        try {
            for (Enumeration en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = (NetworkInterface) en.nextElement();
                for (Enumeration enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = (InetAddress) enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress()&&inetAddress instanceof Inet4Address) {
                        String ipAddress=inetAddress.getHostAddress().toString();
                        Log.e("IP address",""+ipAddress);
                        return ipAddress;
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        return null;
    }


    /**
     * called when choose file is pressed, start activity to choose files
     * @param v
     */
    public void onClickChooseFile(View v) {
        Intent getContentIntent = FileUtils.createGetContentIntent();
        Intent intent = Intent.createChooser(getContentIntent, "Select a file");
        startActivityForResult(intent, REQUEST_CHOOSER);
    }

    /**
     * method needed to implement to choose file, set path of a file after choosing
     * @param requestCode
     * @param resultCode
     * @param data
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CHOOSER:
                if (resultCode == RESULT_OK) {
                    final Uri uri = data.getData();
                    String path = FileUtils.getPath(this, uri);
                    if (path != null && FileUtils.isLocal(path)) {
                        TV_filePath.setText(path);
                    }
                }
                break;
        }
    }

    /**
     * used to resend windows. called when it times out
     */
    private Runnable timer = new Runnable() {
        @Override
        public void run() {
            for (int i = 0; i < myWindow.size(); i++) {//send rest of frames which is not acked
                udpsender.send(myWindow.get(i).toString().getBytes());
                printOnPhoneScreen("resending packet with seq# "+ (myWindow.get(i)).getSEQ());
            }
            mHandler.postDelayed(timer, TIMER_MS);
        }
    };

    /**
     * called when send button is clicked. send first frames in window and start receiver thread
     * @param v
     * @throws IOException
     */
    public void onClickSend(View v) throws IOException {
        fis = new FileInputStream(TV_filePath.getText().toString());
        sequenceNo = START_SEQUENCE_NUM;
        fileCount = 0; // return from fis.read
        sendWindow();
        if(mUDPReceiver == null) {
            // start receiver thread if it is not initialized
            mUDPReceiver = new UDPReceiver(MainActivity.this);
            mUDPReceiver.start();
        }else{
            printOnPhoneScreen("ACK Receiver thread running");
        }


    }

    /**
     * called when stop button is clicked. Stop times
     * @param v
     */
    public void onClickStop(View v){
        mHandler.removeCallbacksAndMessages(null);
    }

    /**
     * method to send frames in a window for the first time
     * @throws IOException
     */
    public void sendWindow() throws IOException {
        byte[] sendData = null;
        myWindow = new ArrayList<Frame>();
        //till window fills up to WINDOW_SIZE or it gets to the end of file
        while (myWindow.size() < WINDOW_SIZE && fileCount != -1) {
            // create frame
            byte[] data = new byte[DATA_SIZE];
            fileCount = fis.read(data);
            Frame sendFrame = new Frame("DAT", sequenceNo, data);
            // put frame into window
            myWindow.add(sendFrame);
            // send data
            sendData = sendFrame.toString().getBytes();
            udpsender.send(sendData);
            printOnPhoneScreen("sending data with seq# " + sendFrame.getSEQ());
            sequenceNo++;
        }
        mHandler.postDelayed(timer, TIMER_MS);
    }

    /**
     * this method is called from receiver thread.
     * It does
     * - shrink the window size when ack is received
     * - prepare and send new window when all frames in a window is acked
     * - send EOT when all frames are acked
     * @param arrivedFrame
     * @throws JSONException
     * @throws IOException
     */
    public void ackArrived(Frame arrivedFrame) throws JSONException, IOException {
        if ((arrivedFrame.getTYPE().equals("ACK"))) {
            int highestACK = arrivedFrame.getSEQ();
            printOnPhoneScreen("ack arrived for seq# " + highestACK);
            //shrink the window depending on the sequence number of the ack
            while (myWindow.size()!=0 && myWindow.get(0).getSEQ() <= highestACK ) {
                printOnPhoneScreen("deleting a frame with seq# " + myWindow.get(0).getSEQ() + " from window");
                myWindow.remove(0);
            }
            if (myWindow.size() == 0) {//if all frames in window are acked
                mHandler.removeCallbacksAndMessages(null); // stop timers for window
                if (fileCount == -1) { //after all the data is acked, send EOT three times
                    Frame sendFrame = new Frame("EOT", 0, null);
                    printOnPhoneScreen("Sending EOT");
                    byte[] sendData = sendFrame.toString().getBytes();
                    udpsender.send(sendData);
                    udpsender.send(sendData);
                    udpsender.send(sendData);
                }else {
                    sendWindow();
                }
            }
        }
    }

    /**
     * method to clear status field
     * @param v
     */
    public void onClickClear(View v) {
        st = "";
        tv.setText(st);
    }

    /**
     * method to put logs in status field
     * @param msg
     */
    public void printOnPhoneScreen(String msg) {
        st += msg+"\n";
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                tv.setText(st);
                return;
            }
        });
    }

    /**
     * called when the app is closed. stop receiver thread
     */
    @Override
    public void onDestroy() {
        if(mUDPReceiver != null) {
            mUDPReceiver.stop();
        }
        super.onDestroy();
    }

    /**
     * class to send data to network emulator
     */
    public class UDPSender {
        private AsyncTask<Void, Void, Void> async_cient;
        private InetAddress host;
        private DatagramSocket ds;

        public UDPSender(){
            // set all variables needed for transfer
            try {
                host = InetAddress.getByName(SERVER_IP);
                ds = new DatagramSocket();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @SuppressLint("NewApi")
        public void send(final byte[] data) {
            async_cient = new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    try {
                        // send data
                        dp = new DatagramPacket(data, data.length, host, SERVER_PORT);  //DatagramPacket
                        ds.send(dp);
                    } catch (Exception e) {
                        e.printStackTrace();

                    }
                    return null;
                }
            };

            if (Build.VERSION.SDK_INT >= 11)
                async_cient.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            else async_cient.execute();
        }
    }

    /**
     * Receiver thread for acknowledgments
     */
    class UDPReceiver implements Runnable {
        DatagramSocket mDatagramRecvSocket = null;
        MainActivity mActivity = null;

        Thread udpreceiverthread;

        public UDPReceiver(MainActivity mainActivity) {
            super();
            mActivity = mainActivity;

        }

        /**
         * called to start thread
         */
        public void start() {
            if( udpreceiverthread == null ) {
                udpreceiverthread = new Thread( this );
                udpreceiverthread.start();
            }
        }

        /**
         * called to stop thread
         */
        public void stop() {
            if( udpreceiverthread != null ) {
                udpreceiverthread.interrupt();
                mDatagramRecvSocket.disconnect();
                mDatagramRecvSocket.close();
            }
        }

        @Override
        public void run() {
            // set variables needed for receiving packets
            byte receiveBuffer[] = new byte[BUFFER_SIZE];
            DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
            if( mDatagramRecvSocket == null) {
                try {
                    mDatagramRecvSocket = new DatagramSocket(mActivity.PORT_MY);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            try {
                while (!udpreceiverthread.interrupted()) {
                    // receive and convert it to Frame object
                    mDatagramRecvSocket.receive(receivePacket);
                    String packetString = new String(receivePacket.getData(), 0, receivePacket.getLength());
                    Frame receivedFrame = Frame.createFrameFromString(packetString);

                    String packetType = receivedFrame.getTYPE();
                    if (packetType.equals("ACK")) {
                        mActivity.ackArrived(receivedFrame);//notify to main thread
                    } else if (packetType.equals("EOT")) {
                        mActivity.printOnPhoneScreen("File Transfer is successfully finished");
                        mHandler.removeCallbacksAndMessages(null);// stop timers
                        break;
                    }
                }
                mActivity.printOnPhoneScreen("ACK Receiver thread end");
                // close sockets
                if(mDatagramRecvSocket!=null) {
                    mDatagramRecvSocket.disconnect();
                    mDatagramRecvSocket.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }finally {

                udpreceiverthread = null;
                mActivity.mUDPReceiver = null;
            }

        }
    }
}
