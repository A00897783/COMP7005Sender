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
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;

import android.os.Handler;
import android.widget.Toast;

import com.ipaulpro.afilechooser.utils.FileUtils;

import org.json.JSONException;
import org.json.JSONObject;


public class MainActivity extends Activity {


    private DatagramPacket dp;
    private TextView tv;
    private String st = "";
    private UDPSender udpsender;
    private TextView TV_filePath;
    private ArrayList<JSONObject> myWindow;// make window


    private static final int SERVER_PORT = 7005;
    private static final String SERVER_IP = "192.168.168.106";
    private static final int PORT_MY = 7005;
    private static final String IP_MY = "192.168.168.111";


    public static final String TYPE = "TYPE";
    public static final String SEQ = "SEQ";
    public static final String DATA = "DATA";

    public static final int WINDOW_SIZE = 6;
    public static final int DATA_SIZE = 1024;
    private static final int START_SEQUENCE_NUM = 1;
    private static final int TIMER_MS = 5000;

    private int framePosAcked;//how many frames in window have been acked

    UDPReceiverThread mUDPReceiver = null;
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
        ((TextView) findViewById(R.id.tv_ip_my)).setText(IP_MY);
        ((TextView) findViewById(R.id.tv_port_my)).setText(PORT_MY + "");
        TV_filePath = (TextView) findViewById(R.id.tv_file);
        TV_filePath.setText("/storage/emulated/0/DCIM/COMP7005/goodbye.txt");//"/storage/sdcard0/DCIM/COMP7005/goodbye.txt");

        tv = (TextView) findViewById(R.id.tv);
        mHandler = new Handler();
        udpsender = new UDPSender();
    }


    public void onClickChooseFile(View v) {
        Intent getContentIntent = FileUtils.createGetContentIntent();
        Intent intent = Intent.createChooser(getContentIntent, "Select a file");
        startActivityForResult(intent, REQUEST_CHOOSER);
    }

    private Runnable timer = new Runnable() {
        @Override
        public void run() {
            int i = framePosAcked + 1;
            while (i < myWindow.size()) {//send rest of frames which is not acked
                udpsender.send(myWindow.get(i).toString().getBytes());
                try {
                    printOnPhoneScreen("resending packet with seq# "+ (myWindow.get(i)).getInt(SEQ));
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                i++;
            }
            mHandler.postDelayed(timer, TIMER_MS);
        }
    };
    private Runnable timerForSOS = new Runnable() {
        @Override
        public void run() {
            int i = framePosAcked + 1;
            JSONObject sendJson = null;
            try {
                sendJson = createJson("SOS", 0, null);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            udpsender.send(sendJson.toString().getBytes());
            printOnPhoneScreen("resending SOS");
            mHandler.postDelayed(timerForSOS, TIMER_MS);
        }
    };


    public void onClickSend(View v) throws IOException, JSONException {
        fis = new FileInputStream(TV_filePath.getText().toString());
        sequenceNo = START_SEQUENCE_NUM;
        fileCount = 0; // return from fis.read
        JSONObject sendJson = createJson("SOS", 0, null);
        udpsender.send(sendJson.toString().getBytes());
        mUDPReceiver = new UDPReceiverThread(MainActivity.this);
        mUDPReceiver.start();
        printOnPhoneScreen("sending SOS ");
        mHandler.postDelayed(timerForSOS, TIMER_MS);
    }
    public void onClickStop(View v){
        mHandler.removeCallbacksAndMessages(null);
        mUDPReceiver.onStop();
    }

    public void sendWindow() throws IOException, JSONException {
        byte[] sendData = null;
        byte[] data = new byte[DATA_SIZE];
        myWindow = new ArrayList<JSONObject>();
        framePosAcked = -1;

        while (myWindow.size() < WINDOW_SIZE && fileCount != -1) { //we have "free / usable" frames in our window
            fileCount = fis.read(data);
            //fill all 6 frames in window ( this entire if chunk)
            JSONObject sendJson = createJson("DAT", sequenceNo, data);
            myWindow.add(sendJson);
            sendData = sendJson.toString().getBytes();
            udpsender.send(sendData);
            printOnPhoneScreen("sending data with seq# " + sequenceNo);
            sequenceNo++;
        }
        mHandler.postDelayed(timer, TIMER_MS);
    }

    public void ackArrived(JSONObject arrivedJson) throws JSONException, IOException {
        if ((arrivedJson.getString(TYPE).equals("SOS"))) {
            mHandler.removeCallbacksAndMessages(null);
            printOnPhoneScreen("ack arrived for SOS");
            sendWindow();
        } else {
            int seq = arrivedJson.getInt(SEQ);
            printOnPhoneScreen("ack arrived for seq# " + seq);
            for (int i = framePosAcked; i < myWindow.size(); i++) {
                if (myWindow.get(i).getInt(SEQ) == seq) {
                    framePosAcked = i;
                    break;
                }
                if (fileCount == -1) {//after all the data is acked
                    JSONObject sendJson = createJson("EOT", 0, null);
                    byte[] sendData = sendJson.toString().getBytes();
                    udpsender.send(sendData);
                    udpsender.send(sendData);
                    udpsender.send(sendData);
                } else if (framePosAcked == myWindow.size()) {
                    mHandler.removeCallbacksAndMessages(null);
                    sendWindow();
                }
            }
        }
    }

    private JSONObject createJson(String type, int sequenceNo, byte[] data) throws JSONException {
        JSONObject packet = new JSONObject();
        packet.put(TYPE, type);
        packet.put(SEQ, sequenceNo);
        packet.put(DATA, data);
        return packet;
    }

    public void onClickClear(View v) {
        st = "";
        tv.setText(st);
    }

    /*file chooser stuff*/
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
        mUDPReceiver.onStop();
        super.onDestroy();
    }

    public class UDPSender {
        private AsyncTask<Void, Void, Void> async_cient;

        @SuppressLint("NewApi")
        public void send(final byte[] data) {
            async_cient = new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    try {
                        DatagramSocket ds = new DatagramSocket();  //DatagramSocket
                        InetAddress host = InetAddress.getByName(SERVER_IP);
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

    class UDPReceiverThread extends Thread {
        private static final String TAG = "UDPReceiverThread";
        DatagramSocket mDatagramRecvSocket = null;
        MainActivity mActivity = null;
        boolean mIsArive = false;

        public UDPReceiverThread(MainActivity mainActivity) {
            super();
            mActivity = mainActivity;
            if (mDatagramRecvSocket == null) {
                try {
                    mDatagramRecvSocket = new DatagramSocket(mActivity.PORT_MY);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

        }

        @Override
        public void start() {
            mIsArive = true;
            super.start();
        }

        public void onStop() {
            mIsArive = false;
        }

        @Override
        public void run() {
            byte receiveBuffer[] = new byte[1024];
            DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
            mIsArive = true;
            Log.d(TAG, "In run(): thread start.");
            try {
                while (mIsArive) {
                    mDatagramRecvSocket.receive(receivePacket);
                    String packetString = new String(receivePacket.getData(), 0, receivePacket.getLength());
                    Log.d(TAG, "In run(): packet received [" + packetString + "]");
                    JSONObject receivedJson = new JSONObject(packetString);
                    String packetType = receivedJson.getString(mActivity.TYPE);
                    if (packetType.equals("ACK") || packetType.equals("SOS")) {
                        mActivity.ackArrived(receivedJson);
                    } else if (packetType.equals("EOT")) {
                        mActivity.finish();
                        Toast.makeText(mActivity, "File Transfer is successfully finished", Toast.LENGTH_LONG);
                        break;
                    } else if (packetType.equals("ERR")) {
                        mActivity.finish();
                        Toast.makeText(mActivity, "There was an error", Toast.LENGTH_LONG);
                        break;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            Log.d(TAG, "In run(): thread end.");
            mDatagramRecvSocket.close();
            mDatagramRecvSocket = null;
            mActivity = null;
            receivePacket = null;
            receiveBuffer = null;
        }
    }
}
