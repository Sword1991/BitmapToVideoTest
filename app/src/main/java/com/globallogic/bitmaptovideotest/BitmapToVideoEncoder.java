package com.globallogic.bitmaptovideotest;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;

import io.reactivex.Completable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;


public class BitmapToVideoEncoder {
    private static final String TAG = BitmapToVideoEncoder.class.getSimpleName();
    private final Handler mHandler;

    private IBitmapToVideoEncoderCallback mCallback;
    private Queue<int[]> mEncodeQueue = new ConcurrentLinkedQueue();
    private MediaCodec mVideoEncoder;

    private Object mFrameSync = new Object();
    private CountDownLatch mNewFrameLatch;

    public static final String MIME_TYPE = "video/avc"; // H.264 Advanced Video Coding
    public static int mWidth;
    public static int mHeight;
    public static final int BIT_RATE = 4000000;
    public static final int FRAME_RATE = 10; // Frames per second

    public static final int I_FRAME_INTERVAL = 1;

    private int mGenerateIndex = 0;
    private boolean mNoMoreFrames = false;
    private boolean mAbort = false;
    private OutputStream mSocketOutputStream;
    private Runnable mWriteDataRunnable;


    public interface IBitmapToVideoEncoderCallback {
        void onEncodingComplete();
    }

    public BitmapToVideoEncoder(IBitmapToVideoEncoderCallback callback, Handler handler) {
        mCallback = callback;
        mHandler=handler;

    }

    public boolean isEncodingStarted() {
        return (mVideoEncoder != null) && (mSocketOutputStream != null) && !mNoMoreFrames && !mAbort;
    }

    public int getActiveBitmaps() {
        return mEncodeQueue.size();
    }

    public void startEncoding(int width, int height, OutputStream outputStream) {
        mWidth = width;
        mHeight = height;

        mSocketOutputStream = outputStream;


        MediaCodecInfo codecInfo = selectCodec(MIME_TYPE);
        if (codecInfo == null) {
            Log.e(TAG, "Unable to find an appropriate codec for " + MIME_TYPE);
            return;
        }
        Log.d(TAG, "found codec: " + codecInfo.getName());
        int colorFormat;
        try {
            colorFormat = selectColorFormat(codecInfo, MIME_TYPE);
        } catch (Exception e) {
            colorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar;
        }

        try {
            mVideoEncoder = MediaCodec.createByCodecName(codecInfo.getName());
        } catch (IOException e) {
            Log.e(TAG, "Unable to create MediaCodec " + e.getMessage());
            return;
        }

        MediaFormat mediaFormat = MediaFormat.createVideoFormat(MIME_TYPE, mWidth, mHeight);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL);
        mVideoEncoder.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mVideoEncoder.start();

        Log.d(TAG, "Initialization complete. Starting encoder...");

        Completable.fromAction(() -> encode())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe();
    }

    public void stopEncoding() {
        if (mVideoEncoder == null  || mSocketOutputStream == null ) {
            Log.d(TAG, "Failed to stop encoding since it never started");
            return;
        }
        Log.d(TAG, "Stopping encoding");

        mNoMoreFrames = true;

        synchronized (mFrameSync) {
            if ((mNewFrameLatch != null) && (mNewFrameLatch.getCount() > 0)) {
                mNewFrameLatch.countDown();
            }
        }
    }

    public void abortEncoding() {
        if (mVideoEncoder == null  || mSocketOutputStream == null) {
            Log.d(TAG, "Failed to abort encoding since it never started");
            return;
        }
        Log.d(TAG, "Aborting encoding");

        mNoMoreFrames = true;
        mAbort = true;
        mEncodeQueue = new ConcurrentLinkedQueue(); // Drop all frames

        synchronized (mFrameSync) {
            if ((mNewFrameLatch != null) && (mNewFrameLatch.getCount() > 0)) {
                mNewFrameLatch.countDown();
            }
        }
    }

    public void queueFrame(int[] argb) {
        if (mVideoEncoder == null  || mSocketOutputStream == null) {
            Log.d(TAG, "Failed to queue frame. Encoding not started");
            return;
        }

        Log.d(TAG, "Queueing frame");
        mEncodeQueue.add(argb);

        synchronized (mFrameSync) {
            if ((mNewFrameLatch != null) && (mNewFrameLatch.getCount() > 0)) {
                mNewFrameLatch.countDown();
            }
        }
    }

    private void encode() {

        Log.d(TAG, "Encoder started");

        while(true) {
            if (mNoMoreFrames && (mEncodeQueue.size() ==  0)) break;

            int[] argb = mEncodeQueue.poll();
            if (argb ==  null) {
                synchronized (mFrameSync) {
                    mNewFrameLatch = new CountDownLatch(1);
                }

                try {
                    mNewFrameLatch.await();
                } catch (InterruptedException e) {}

                argb = mEncodeQueue.poll();
            }

            if (argb == null) continue;
            byte[] byteConvertFrame = getNV21(mWidth, mHeight, argb);

            long TIMEOUT_USEC = 500000;
            int inputBufIndex = mVideoEncoder.dequeueInputBuffer(TIMEOUT_USEC);
            //Log.w(TAG, "encode: inputBufIndex="+inputBufIndex );
            long ptsUsec = computePresentationTime(mGenerateIndex, FRAME_RATE);
            if (inputBufIndex >= 0) {
                final ByteBuffer inputBuffer = mVideoEncoder.getInputBuffer(inputBufIndex);

                inputBuffer.clear();
                inputBuffer.put(byteConvertFrame);
                mVideoEncoder.queueInputBuffer(inputBufIndex, 0, byteConvertFrame.length, ptsUsec, 0);
                mGenerateIndex++;
            }
            MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
            int encoderStatus = mVideoEncoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // no output available yet
                Log.e(TAG, "No output from encoder available");
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {

                Log.e(TAG, "INFO_OUTPUT_FORMAT_CHANGED not expected for an encoder");
            } else if (encoderStatus < 0) {
                Log.e(TAG, "unexpected result from encoder.dequeueOutputBuffer: " + encoderStatus);
            } else if (mBufferInfo.size != 0) {
                ByteBuffer encodedData = mVideoEncoder.getOutputBuffer(encoderStatus);
                if (encodedData == null) {
                    Log.e(TAG, "encoderOutputBuffer " + encoderStatus + " was null");
                } else {
                    encodedData.position(mBufferInfo.offset);
                    encodedData.limit(mBufferInfo.offset + mBufferInfo.size);
                    int size = encodedData.remaining();
                    final byte[] buffer = new byte[size];
                    encodedData.get(buffer);
                    writeSampleData(buffer, 0, size);
                    mVideoEncoder.releaseOutputBuffer(encoderStatus, false);
                }
            }
        }

        release();

        if (mAbort) {
            Log.w(TAG, "encode: abort" );
        } else {
            mCallback.onEncodingComplete();
        }
    }

    private void writeSampleData(final byte[] buffer, final int offset, final int size) {
        mWriteDataRunnable=new Runnable() {
            @Override
            public void run() {
                if (mSocketOutputStream != null) {
                    try {
                        mSocketOutputStream.write(buffer, offset, size);
                    } catch (IOException e) {
                        Log.e(TAG, "Failed to write data to socket, stop casting",e);
                        e.printStackTrace();
                    }
                }else {
                    Log.e(TAG, "writeSampleData: socket null" );
                }
            }
        };
        mHandler.post(mWriteDataRunnable);
    }

    private void release() {
        if (mVideoEncoder != null) {
            mVideoEncoder.stop();
            mVideoEncoder.release();
            mVideoEncoder = null;
            Log.d(TAG,"RELEASE CODEC");
        }
    }

    private static MediaCodecInfo selectCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (!codecInfo.isEncoder()) {
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    return codecInfo;
                }
            }
        }
        return null;
    }

    private static int selectColorFormat(MediaCodecInfo codecInfo,
                                         String mimeType) {
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo
                .getCapabilitiesForType(mimeType);
        for (int i = 0; i < capabilities.colorFormats.length; i++) {
            int colorFormat = capabilities.colorFormats[i];
            if (isRecognizedFormat(colorFormat)) {
                return colorFormat;
            }
        }
        return 0; // not reached
    }

    private static boolean isRecognizedFormat(int colorFormat) {
        switch (colorFormat) {
            // these are the formats we know how to handle for
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
                return true;
            default:
                return false;
        }
    }

    private byte[] getNV21(int inputWidth, int inputHeight, int[] argb) {

        //int[] argb = new int[inputWidth * inputHeight];

        //scaled.getPixels(argb, 0, inputWidth, 0, 0, inputWidth, inputHeight);

        byte[] yuv = new byte[inputWidth * inputHeight * 3 / 2];
        encodeYUV420SP(yuv, argb, inputWidth, inputHeight);

        //scaled.recycle();

        return yuv;
    }

    private void encodeYUV420SP(byte[] yuv420sp, int[] argb, int width, int height) {
        final int frameSize = width * height;
        final int chromaSize = frameSize / 4;

        int yIndex = 0;
        int uIndex = frameSize;
        int vIndex = frameSize + chromaSize;

        int a, R, G, B, Y, U, V;
        int index = 0;
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {

                //a = (argb[index] & 0xff000000) >> 24; // a is not used obviously
                R = (argb[index] & 0xff0000) >> 16;
                G = (argb[index] & 0xff00) >> 8;
                B = (argb[index] & 0xff) >> 0;


                Y = ((66 * R + 129 * G + 25 * B + 128) >> 8) + 16;
                U = ((-38 * R - 74 * G + 112 * B + 128) >> 8) + 128;
                V = ((112 * R - 94 * G - 18 * B + 128) >> 8) + 128;


                yuv420sp[yIndex++] = (byte) ((Y < 0) ? 0 : ((Y > 255) ? 255 : Y));
                if (j % 2 == 0 && index % 2 == 0) {
                    yuv420sp[uIndex++] = (byte) ((U < 0) ? 0 : ((U > 255) ? 255 : U));
                    yuv420sp[vIndex++] = (byte) ((V < 0) ? 0 : ((V > 255) ? 255 : V));

                }

                index++;
            }
        }
    }

    private long computePresentationTime(long frameIndex, int framerate) {
        return 132 + frameIndex * 1000000 / framerate;
    }
}
