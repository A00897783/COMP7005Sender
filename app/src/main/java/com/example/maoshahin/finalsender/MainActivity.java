package com.example.maoshahin.finalsender;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.DhcpInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.format.Formatter;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;

import android.os.Handler;
import android.widget.Toast;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ipaulpro.afilechooser.utils.FileUtils;

import org.json.JSONException;
import org.json.JSONObject;


public class MainActivity extends Activity {


    private DatagramPacket dp;
    private TextView tv;
    private String st = "";
    private UDPSender udpsender;
    private TextView TV_filePath;
    private ArrayList<Frame> myWindow;


    private static final int SERVER_PORT = 7005;
    private static final String SERVER_IP = "192.168.168.113";
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
        ((TextView) findViewById(R.id.tv_ip)).setText(SERVER_IP);
        ((TextView) findViewById(R.id.tv_port)).setText(SERVER_PORT + "");
        ((TextView) findViewById(R.id.tv_port_my)).setText(PORT_MY + "");
        ((TextView) findViewById(R.id.tv_ip_my)).setText(getIpAddress());

        if(mUDPReceiver == null) {
            mUDPReceiver = new UDPReceiver(MainActivity.this);
        }
        mUDPReceiver.start();

        TV_filePath = (TextView) findViewById(R.id.tv_file);
        TV_filePath.setText("/storage/emulated/0/DCIM/COMP7005/goodbye2.txt");//"/storage/emulated/0/DCIM/COMP7005/goodbye.txt");


        tv = (TextView) findViewById(R.id.tv);
        mHandler = new Handler();
        udpsender = new UDPSender();
    }

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


    public void onClickChooseFile(View v) {
        Intent getContentIntent = FileUtils.createGetContentIntent();
        Intent intent = Intent.createChooser(getContentIntent, "Select a file");
        startActivityForResult(intent, REQUEST_CHOOSER);
    }

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

    public void onClickSend(View v) throws IOException {
        fis = new FileInputStream(TV_filePath.getText().toString());
        sequenceNo = START_SEQUENCE_NUM;
        fileCount = 0; // return from fis.read
        sendWindow();
        if(mUDPReceiver == null) {
            mUDPReceiver = new UDPReceiver(MainActivity.this);
            mUDPReceiver.start();
        }else{
            printOnPhoneScreen("ACK Receiver thread running");
        }


    }
    public void onClickStop(View v){
        mHandler.removeCallbacksAndMessages(null);
    }

    public void sendWindow() throws IOException {
        byte[] sendData = null;
        myWindow = new ArrayList<Frame>();


        while (myWindow.size() < WINDOW_SIZE && fileCount != -1) { //we have "free / usable" frames in ou
            byte[] data = new byte[DATA_SIZE];
            fileCount = fis.read(data);
            //fill all 6 frames in window ( this entire if chunk)
            Frame sendFrame = new Frame("DAT", sequenceNo, data);
            myWindow.add(sendFrame);
            sendData = sendFrame.toString().getBytes();
            udpsender.send(sendData);
            printOnPhoneScreen("sending data with seq# " + sendFrame.getSEQ());
            sequenceNo++;
        }
        mHandler.postDelayed(timer, TIMER_MS);
    }

    public void ackArrived(Frame arrivedFrame) throws JSONException, IOException {
        if ((arrivedFrame.getTYPE().equals("ACK"))) {
            int highestACK = arrivedFrame.getSEQ();
            printOnPhoneScreen("ack arrived for seq# " + highestACK);
            while (myWindow.size()!=0 && myWindow.get(0).getSEQ() <= highestACK ) {
                printOnPhoneScreen("deleting seq# " + myWindow.get(0).getSEQ());
                myWindow.remove(0);
            }
            if (fileCount == -1) {//after all the data is acked
                Frame sendFrame = new Frame("EOT", 0, null);
                byte[] sendData = sendFrame.toString().getBytes();
                udpsender.send(sendData);
                udpsender.send(sendData);
                udpsender.send(sendData);
            } else if (myWindow.size() == 0) {//if framePos
                mHandler.removeCallbacksAndMessages(null);
                sendWindow();
            }
        }else{
            Log.d("arrivedFrame", arrivedFrame.toString());
        }
    }
    public void onClickClear(View v) {
        st = "";
        tv.setText(st);
    }

    /*file chooser start*/
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
    /*file chooser end */



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


    @Override
    public void onDestroy() {
        if(mUDPReceiver != null) {
            mUDPReceiver.stop();
        }
        super.onDestroy();
    }

    public class UDPSender {
        private AsyncTask<Void, Void, Void> async_cient;
        private InetAddress host;
        private DatagramSocket ds;

        public UDPSender(){
            try {
                host = InetAddress.getByName(SERVER_IP);
                ds = new DatagramSocket();  //DatagramSocket
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
                        dp = new DatagramPacket(data, data.length, host, SERVER_PORT);  //DatagramPacket
                        ds.send(dp);
                    } catch (Exception e) {
                        e.printStackTrace();

                    }
                    return null;
                }
                protected void onPostExecute(Void result) {
                    super.onPostExecute(result);
                }
            };

            if (Build.VERSION.SDK_INT >= 11)
                async_cient.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            else async_cient.execute();
        }
    }

    class UDPReceiver implements Runnable {
        private static final String TAG = "UDPReceiverThread";
        DatagramSocket mDatagramRecvSocket = null;
        MainActivity mActivity = null;

        Thread udpreceiverthread;

        public UDPReceiver(MainActivity mainActivity) {
            super();
            mActivity = mainActivity;

        }

        public void start() {
            if( udpreceiverthread == null ) {
                udpreceiverthread = new Thread( this );
                udpreceiverthread.start();
            }
        }

        public void stop() {
            if( udpreceiverthread != null ) {
                udpreceiverthread.interrupt();
                mDatagramRecvSocket.disconnect();
                mDatagramRecvSocket.close();
            }
        }

        @Override
        public void run() {
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
                    mDatagramRecvSocket.receive(receivePacket);
                    String packetString = new String(receivePacket.getData(), 0, receivePacket.getLength());
                    Log.d(TAG, "In run(): packet received [" + packetString + "]");
                    Frame receivedFrame = Frame.createFrameFromString(packetString);
                    String packetType = receivedFrame.getTYPE();
                    if (packetType.equals("ACK")) {
                        mActivity.ackArrived(receivedFrame);
                    } else if (packetType.equals("EOT")) {
                        mActivity.printOnPhoneScreen("File Transfer is successfully finished");
                        mHandler.removeCallbacksAndMessages(null);
                        break;
                    }
                }
                mActivity.printOnPhoneScreen("ACK Receiver thread end");
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
