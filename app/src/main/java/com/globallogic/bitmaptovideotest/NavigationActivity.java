package com.globallogic.bitmaptovideotest;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.os.StrictMode;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.mapbox.mapboxsdk.Mapbox;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;

public class NavigationActivity extends AppCompatActivity {

    private static final String  H264_ENCODER_NAME = "h264";
    public BitmapToVideoEncoder bitmapToVideoEncoder;
    private static final String TAG = "NavigationActivity";



    private Handler mHandler = new Handler(new ServiceHandlerCallback());
    private ArrayList<Messenger> mClients = new ArrayList<Messenger>();
    private ServerSocket mServerSocket;
    private Socket mSocket;
    private DataOutputStream mSocketOutputStream;


    private static final String HTTP_MESSAGE_TEMPLATE = "POST /api/v1/h264 HTTP/1.1\r\n" +
            "Connection: close\r\n" +
            "X-WIDTH: %1$d\r\n" +
            "X-HEIGHT: %2$d\r\n" +
            "FPS: %3$d\r\n" +
            "BITRATE: %4$d\r\n" +
            "ENCODER: %5$s\r\n" +
            "\r\n";

    private String mReceiverIp;
    private static final int SELECTED_WIDTH =644;
    private  static final int SELECTED_HEIGHT =500;

    private static final String RECEIVER_IP = "192.168.0.107";

    private class ServiceHandlerCallback implements Handler.Callback {
        @Override
        public boolean handleMessage(Message msg) {
            Log.d(TAG, "Handler got event, what: " + msg.what);
            switch (msg.what) {
                case Config.MSG_REGISTER_CLIENT: {
                    mClients.add(msg.replyTo);
                    break;
                }
                case Config.MSG_UNREGISTER_CLIENT: {
                    mClients.remove(msg.replyTo);
                    break;
                }
                case Config.MSG_STOP_CAST: {
                    //stopScreenCapture();
                    closeSocket(true);
                    //stopSelf();
                }
            }
            return false;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
        Mapbox.getInstance(this, getString(R.string.mapbox_access_token));
        setContentView(R.layout.activity_navigation);

        Button btn = (Button) findViewById(R.id.btn);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(!bitmapToVideoEncoder.isEncodingStarted()){
                    startEncoding();
                }else{
                    bitmapToVideoEncoder.stopEncoding();
                }
            }
        });
        bitmapToVideoEncoder = new BitmapToVideoEncoder(new BitmapToVideoEncoder.IBitmapToVideoEncoderCallback() {
            @Override
            public void onEncodingComplete() {

                Log.w(TAG, "onEncodingComplete: " );
            }
        }, mHandler);
    }


    private void startEncoding(){
        mReceiverIp=RECEIVER_IP;
        if (!createSocket()) {
            Log.e(TAG, "Failed to create socket to receiver, ip: " + mReceiverIp);
        }
        bitmapToVideoEncoder.startEncoding(SELECTED_WIDTH, SELECTED_HEIGHT, mSocketOutputStream);
    }

    private void stopEncoding(){
        bitmapToVideoEncoder.stopEncoding();
        closeSocket();
    }

    private boolean createSocket() {
        Log.w(TAG, "createSocket" );
        Thread th = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    InetAddress serverAddr = InetAddress.getByName(mReceiverIp);
                    mSocket = new Socket(serverAddr, Config.VIEWER_PORT);
                    mSocketOutputStream = new DataOutputStream( mSocket.getOutputStream());
                    OutputStreamWriter osw = new OutputStreamWriter(mSocketOutputStream);
                    @SuppressLint("DefaultLocale")
                    String format =String.format(HTTP_MESSAGE_TEMPLATE,
                            SELECTED_WIDTH,
                            SELECTED_HEIGHT,
                            BitmapToVideoEncoder.FRAME_RATE,
                            BitmapToVideoEncoder.BIT_RATE,
                            H264_ENCODER_NAME
                    );
                    Log.w(TAG, "format="+format );
                    osw.write(format);
                    osw.flush();
                    mSocketOutputStream.flush();
                    return;
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                mSocket = null;
                mSocketOutputStream = null;
            }
        });
        th.start();
        try {
            th.join();
            if (mSocket != null && mSocketOutputStream != null) {
                return true;
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return false;
    }

    private void closeSocket() {
        closeSocket(false);
    }

    private void closeSocket(boolean closeServerSocket) {
        if (mSocketOutputStream != null) {
            try {
                mSocketOutputStream.flush();
                mSocketOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (mSocket != null) {
            try {
                mSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (closeServerSocket) {
            if (mServerSocket != null) {
                try {
                    mServerSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            mServerSocket = null;
        }
        mSocket = null;
        mSocketOutputStream = null;
    }

}
