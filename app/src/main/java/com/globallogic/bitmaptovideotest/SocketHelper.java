package com.globallogic.bitmaptovideotest;

import android.annotation.SuppressLint;
import android.util.Log;

import java.io.DataOutputStream;
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

    public static final String mReceiverIp = "192.168.0.107";

    public boolean createSocket() {
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
                            Config.SELECTED_WIDTH,
                            Config.SELECTED_HEIGHT,
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

}
