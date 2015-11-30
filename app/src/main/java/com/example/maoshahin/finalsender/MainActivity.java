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

import android.os.Handler;

import com.ipaulpro.afilechooser.utils.FileUtils;


public class MainActivity extends Activity {

    private DatagramPacket dp;
    private TextView tv;
    private String st;
    private UDPSender udpsender;
    private TextView TV_filePath;

    private static final int SERVER_PORT = 7005;
    private static final String SERVER_IP = "192.168.168.106";
    private static final int PORT_MY = 7005;
    private static final String IP_MY = "192.168.168.111";

    UDPReceiverThread mUDPReceiver= null;
    Handler mHandler = null;
    private static final int REQUEST_CHOOSER = 1234;


    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ((TextView) findViewById(R.id.tv_ip)).setText(SERVER_IP);
        ((TextView) findViewById(R.id.tv_port)).setText(SERVER_PORT + "");
        ((TextView) findViewById(R.id.tv_ip_my)).setText(IP_MY);
        ((TextView) findViewById(R.id.tv_port_my)).setText(PORT_MY + "");
        TV_filePath = (TextView)findViewById(R.id.tv_file);
        TV_filePath.setText("/storage/emulated/0/Download/hello.txt");

        tv = (TextView) findViewById(R.id.tv);
        mHandler = new Handler();
        udpsender = new UDPSender();
    }


    public void onClickChooseFile(View v){
        Intent getContentIntent = FileUtils.createGetContentIntent();
        Intent intent = Intent.createChooser(getContentIntent, "Select a file");
        startActivityForResult(intent, REQUEST_CHOOSER);
    }

    public void onClickSend(View v){
        udpsender.send();
    }
    public void onClickClear(View v){
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



    public void printOnPhoneScreen(String msg){
        st += msg;
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                tv.setText(st);
                return;
            }
        });
    }

    public class UDPSender {
        private AsyncTask<Void, Void, Void> async_cient;

        @SuppressLint("NewApi")
        public void send() {
            async_cient = new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    DatagramSocket ds = null;
                    byte[] sendData = new byte[1024];
                    FileInputStream fis = null;
                    InetAddress host = null;
                    int count = -1;

                    try {
                        fis = new FileInputStream(TV_filePath.getText().toString());
                        count= fis.read(sendData);
                        host = InetAddress.getByName(SERVER_IP);
                        ds = new DatagramSocket();  //DatagramSocket
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (UnknownHostException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    while(true) {
                        try {
                            if(count != -1) {
                            dp = new DatagramPacket(sendData, sendData.length, host, SERVER_PORT);  //DatagramPacket
                            ds.send(dp);
                            count = fis.read(sendData);
                            } else {
                                break;
                            }
                        } catch (Exception e) {
                        }
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

    @Override
    public void onDestroy() {
        mUDPReceiver.onStop();
        super.onDestroy();
    }

    class UDPReceiverThread extends Thread {
        private static final String TAG="UDPReceiverThread";
        private static final int comm_port = 7005;
        public static final String COMM_END_STRING="end";

        DatagramSocket mDatagramRecvSocket= null;
        MainActivity mActivity= null;
        boolean mIsArive= false;

        public UDPReceiverThread( MainActivity mainActivity ) {
            super();
            mActivity= mainActivity;
            try {
                mDatagramRecvSocket= new DatagramSocket(comm_port);
            }catch( Exception e ) {
                e.printStackTrace();
            }

        }
        @Override
        public void start() {
            mIsArive= true;
            super.start();
        }
        public void onStop() {
            mIsArive= false;
        }
        @Override
        public void run() {
            byte receiveBuffer[] = new byte[1024];
            DatagramPacket receivePacket =
                    new DatagramPacket(receiveBuffer, receiveBuffer.length);

            Log.d(TAG, "In run(): thread start.");
            try {
                while (mIsArive) {
                    mDatagramRecvSocket.receive(receivePacket);
                    String packetString=new String(receivePacket.getData(),0, receivePacket.getLength());
                    Log.d(TAG,"In run(): packet received ["+packetString+"]");
                    if( packetString.equals(COMM_END_STRING) ) {
                        mActivity.finish();
                        break;
                    }else{
                        Log.d(TAG,packetString.equals(COMM_END_STRING)+"");
                        Log.d(TAG,packetString+" "+COMM_END_STRING);

                    }
                }
            }catch( Exception e ) {
                e.printStackTrace();
            }
            Log.d(TAG,"In run(): thread end.");
            mDatagramRecvSocket.close();
            mDatagramRecvSocket= null;
            mActivity= null;
            receivePacket= null;
            receiveBuffer= null;
        }
    }
}
