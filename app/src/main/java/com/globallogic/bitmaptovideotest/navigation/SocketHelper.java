package com.globallogic.bitmaptovideotest.navigation;

import android.annotation.SuppressLint;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

public class SocketHelper {

    private static final String TAG = "SocketHelper";
    private static final String  H264_ENCODER_NAME = "h264";
    public ServerSocket mServerSocket;
    public Socket mSocket;
    public DataOutputStream mSocketOutputStream;


    private static final String HTTP_MESSAGE_TEMPLATE = "POST /api/v1/h264 HTTP/1.1\r\n" +
            "Connection: close\r\n" +
            "X-WIDTH: %1$d\r\n" +
            "X-HEIGHT: %2$d\r\n" +
            "FPS: %3$d\r\n" +
            "BITRATE: %4$d\r\n" +
            "ENCODER: %5$s\r\n" +
            "\r\n";

    public String mReceiverIp;
    private static final String DEFAULT_RECEIVER_IP = "192.168.0.107";

    public static final int VIEWER_PORT = 53515;


    public boolean createSocket() {
        Log.w(TAG, "createSocket" );
        mReceiverIp=getReceiverIpString();

        Thread th = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    InetAddress serverAddr = InetAddress.getByName(mReceiverIp);
                    mSocket = new Socket(serverAddr, VIEWER_PORT);
                    mSocketOutputStream = new DataOutputStream( mSocket.getOutputStream());
                    OutputStreamWriter osw = new OutputStreamWriter(mSocketOutputStream);
                    @SuppressLint("DefaultLocale")
                    String format =String.format(HTTP_MESSAGE_TEMPLATE,
                            NavigationController.PRESENTATION_WIDTH,
                            NavigationController.PRESENTATION_HEIGHT,
                            NavigationController.PRESENTATION_FRAMERATE,
                            NavigationController.PRESENTATION_BITRATE,
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

    public void closeSocket() {
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


    private String getReceiverIpString(){
        String result = DEFAULT_RECEIVER_IP;
        File sdcard = Environment.getExternalStorageDirectory();
        File file = new File(sdcard,"receiverip.txt");
        StringBuilder text = new StringBuilder();
        try {
            BufferedReader br = new BufferedReader(new FileReader(file));
            String line;

            while ((line = br.readLine()) != null) {
                text.append(line);
                text.append('\n');
            }
            br.close();
        }
        catch (IOException e) {
            //You'll need to add proper error handling here
            Log.e(TAG, "getReceiverIpString: error" );
            e.printStackTrace();
        }
        if(text.toString().length()>0){
            result=text.toString().replace("\n","");
        }else {
            Log.w(TAG, "getReceiverIpString: ERROR using default receiver ip");
        }

        Log.w(TAG, "getReceiverIpString: ["+result+"]" );

        return result;
    }

}
