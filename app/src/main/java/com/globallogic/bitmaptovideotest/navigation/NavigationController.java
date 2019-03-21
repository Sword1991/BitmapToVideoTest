package com.globallogic.bitmaptovideotest.navigation;

import android.app.Presentation;
import android.content.SharedPreferences;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.location.Location;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Surface;
import android.view.View;

import com.globallogic.bitmaptovideotest.R;
import com.mapbox.api.directions.v5.models.DirectionsResponse;
import com.mapbox.api.directions.v5.models.DirectionsRoute;
import com.mapbox.geojson.Point;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.services.android.navigation.ui.v5.NavigationView;
import com.mapbox.services.android.navigation.ui.v5.NavigationViewOptions;
import com.mapbox.services.android.navigation.ui.v5.OnNavigationReadyCallback;
import com.mapbox.services.android.navigation.ui.v5.camera.DynamicCamera;
import com.mapbox.services.android.navigation.ui.v5.listeners.NavigationListener;
import com.mapbox.services.android.navigation.v5.location.replay.ReplayRouteLocationEngine;
import com.mapbox.services.android.navigation.v5.milestone.Milestone;
import com.mapbox.services.android.navigation.v5.milestone.MilestoneEventListener;
import com.mapbox.services.android.navigation.v5.navigation.NavigationRoute;
import com.mapbox.services.android.navigation.v5.routeprogress.ProgressChangeListener;
import com.mapbox.services.android.navigation.v5.routeprogress.RouteProgress;
import com.mapbox.services.android.navigation.v5.routeprogress.RouteProgressState;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Response;

import static android.content.Context.DISPLAY_SERVICE;
import static com.mapbox.mapboxsdk.Mapbox.getApplicationContext;

public class NavigationController implements OnNavigationReadyCallback, NavigationListener,
        ProgressChangeListener, MilestoneEventListener {


    private static final double ORIGIN_LONGITUDE = -3.714873;
    private static final double ORIGIN_LATITUDE = 40.397389;
    private static final double DESTINATION_LONGITUDE = -3.166243;
    private static final double DESTINATION_LATITUDE = 40.650514;

    public static final int PRESENTATION_WIDTH = 300;
    public static final int PRESENTATION_HEIGHT = 300;
    public static final int PRESENTATION_FRAMERATE = 30;
    public static final int PRESENTATION_BITRATE = 6000000;
    private static final int PRESENTATION_DENSITY = 120;



    private static final String TAG = NavigationController.class.getSimpleName();
    private List<NavigationView> mNavigationViewList;
    private FragmentActivity mContext;
    private Presentation mPresentation;
    private DirectionsRoute mDirectionsRoute;
    private boolean isRouteFetched = false;
    private int readyViewsCount = 0;


    private final SocketHelper socketHelper;
    private Surface inputSurface;
    private MediaCodec videoEncoder;
    private boolean socketStarted = false;
    VirtualDisplay display;

    private static final String VIDEO_MIME_TYPE = "video/avc";

    private MediaCodec.Callback encoderCallback;
    private Runnable mWriteDataRunnable;

    private Handler mHandler = null;

    private HandlerThread mHandlerThread = null;

    public void startHandlerThread(){
        mHandlerThread = new HandlerThread("HandlerThread");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
    }


    public NavigationController(FragmentActivity fragmentActivity) {
        this.mContext = fragmentActivity;
        mNavigationViewList = new ArrayList<>(2);
        encoderCallback = new EncoderCallback();
        startHandlerThread();
        socketHelper = new SocketHelper();
        createPresentation();
        startPresentation();

    }

    public NavigationView createNavigationView(View container) {


        NavigationView view = new NavigationView(mContext);
        mNavigationViewList.add(view);

        return view;
    }

    public void startPresentation() {
        mPresentation.show();
    }

    private void createPresentation() {

        DisplayManager dm = (DisplayManager) getApplicationContext().getSystemService(DISPLAY_SERVICE);

        prepareVideoEncoder(PRESENTATION_WIDTH, PRESENTATION_HEIGHT);
        display = dm.createVirtualDisplay("Recording Display", PRESENTATION_WIDTH,
                PRESENTATION_HEIGHT, PRESENTATION_DENSITY, null, DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY,
                null, null);

        if (mPresentation != null)
            throw new RuntimeException("Presentation already created");

        NavigationView navigationView = createNavigationView(null);

        mPresentation = new Presentation(mContext, display.getDisplay());
        mPresentation.setContentView(navigationView);
    }


    private synchronized void fetchRoute(Point origin, Point destination) {
        if (isRouteFetched)
            return;
        isRouteFetched = true;
        NavigationRoute.builder(mContext)
                .accessToken(Mapbox.getAccessToken())
                .origin(origin)
                .destination(destination)
                .build()
                .getRoute(new SimplifiedCallback() {
                    @Override
                    public void onResponse(Call<DirectionsResponse> call, Response<DirectionsResponse> response) {
                        Log.w(TAG, "onResponse: startNavigation" );
                        mDirectionsRoute = response.body().routes().get(0);
                        startNavigation();
                    }
                });
    }

    private void startNavigation() {
        Log.w(TAG, "startNavigation: " );
        if (mDirectionsRoute == null) {
            return;
        }
        NavigationViewOptions options = NavigationViewOptions.builder()

                .directionsRoute(mDirectionsRoute)
                .shouldSimulateRoute(true)
                .navigationListener(this)
                .progressChangeListener(this)
                .milestoneEventListener(this)
                .locationEngine(new ReplayRouteLocationEngine())
                .build();

        for (NavigationView nav : mNavigationViewList) {
            nav.startNavigation(options);
        }

        // DONT TOUCH! This is magic!
        mNavigationViewList.get(1).retrieveMapboxNavigation().setCameraEngine(new DynamicCamera(mNavigationViewList.get(0).retrieveNavigationMapboxMap().retrieveMap()));

        startRecording();
    }

    private void stopNavigation() {
        updateWasNavigationStopped(true);
        stopRecording();
    }

    public void updateWasNavigationStopped(boolean wasNavigationStopped) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(mContext.getString(R.string.was_navigation_stopped), wasNavigationStopped);
        editor.apply();
    }

    @Override
    public synchronized void onNavigationReady(boolean isRunning) {

        Log.e(TAG, "onNavigationReady "+isRunning);
        Point origin = Point.fromLngLat(ORIGIN_LONGITUDE, ORIGIN_LATITUDE);
        Point destination = Point.fromLngLat(DESTINATION_LONGITUDE, DESTINATION_LATITUDE);

        readyViewsCount++;

        // Fetch route after all views are ready
        if (readyViewsCount == mNavigationViewList.size()) {
            fetchRoute(origin, destination);
        }

    }

    @Override
    public void onCancelNavigation() {

        for (NavigationView nav : mNavigationViewList) {
            nav.stopNavigation();
        }
        stopNavigation();
        startNavigation();
    }

    @Override
    public void onNavigationFinished() {

    }

    @Override
    public void onNavigationRunning() {

    }

    @Override
    public void onMilestoneEvent(RouteProgress routeProgress, String instruction, Milestone milestone) {

    }

    @Override
    public void onProgressChange(Location location, RouteProgress routeProgress) {

        if (routeProgress.currentState() == RouteProgressState.ROUTE_ARRIVED) {
            stopNavigation();
            startNavigation();
        }
    }


    public void initialize() {
        for (NavigationView nav : mNavigationViewList) {
            nav.initialize(this);
        }
    }

    public void onCreate(@Nullable Bundle savedInstanceState) {

        for (NavigationView nav : mNavigationViewList) {
            nav.onCreate(savedInstanceState);
        }
    }

    public void onStart() {
        for (NavigationView nav : mNavigationViewList) {
            nav.onStart();
        }
    }

    public void onResume() {
        for (NavigationView nav : mNavigationViewList) {
            nav.onResume();
        }
    }

    public void onSaveInstanceState(@NonNull Bundle outState) {
        for (NavigationView nav : mNavigationViewList) {
            nav.onSaveInstanceState(outState);
        }
    }

    public void onRestoreInstanceState(@Nullable Bundle savedInstanceState) {
        for (NavigationView nav : mNavigationViewList) {
            nav.onRestoreInstanceState(savedInstanceState);
        }
    }

    public void onPause() {
        for (NavigationView nav : mNavigationViewList) {
            nav.onPause();
        }
        stopRecording();
    }

    public void onStop() {
        for (NavigationView nav : mNavigationViewList) {
            nav.onStop();
        }
    }

    public void onLowMemory() {
        for (NavigationView nav : mNavigationViewList) {
            nav.onLowMemory();
        }
    }

    public void onDestroy() {
        for (NavigationView nav : mNavigationViewList) {
            nav.onDestroy();
        }
    }

    public void startRecording() {
        Log.d(TAG, "Start recording");
        socketHelper.createSocket();
        socketStarted=true;
        display.setSurface(inputSurface);
        videoEncoder.start();
    }

    public void stopRecording() {
        Log.d(TAG, "stop recording");
        releaseEncoders();
    }

    private void prepareVideoEncoder(int width, int height) {
        MediaFormat format = MediaFormat.createVideoFormat(VIDEO_MIME_TYPE, width, height);
        // Set some required properties. The media codec may fail if these aren't defined.
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, PRESENTATION_BITRATE); // 6Mbps
        format.setInteger(MediaFormat.KEY_FRAME_RATE, PRESENTATION_FRAMERATE);
        format.setInteger(MediaFormat.KEY_CAPTURE_RATE, PRESENTATION_FRAMERATE);
        format.setInteger(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 1000000 / PRESENTATION_FRAMERATE);
        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1); // 1 seconds between I-frames

        // Create a MediaCodec encoder and configure it. Get a Surface we can use for recording into.
        try {
            videoEncoder = MediaCodec.createEncoderByType(VIDEO_MIME_TYPE);
            videoEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            inputSurface = videoEncoder.createInputSurface();
            videoEncoder.setCallback(encoderCallback);
        } catch (IOException e) {
            releaseEncoders();
        }
    }

    private void releaseEncoders() {
        if (socketHelper != null) {
            if (socketStarted) {
                socketHelper.closeSocket();
            }
            socketStarted = false;
        }
        if (videoEncoder != null) {
            videoEncoder.stop();
            videoEncoder.release();
            videoEncoder = null;
        }
        if (inputSurface != null) {
            inputSurface.release();
            inputSurface = null;
        }
    }

    public boolean isRecording() {
        return socketStarted;
    }

    private class EncoderCallback extends MediaCodec.Callback {
        @Override
        public void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {
            Log.w(TAG, "onInputBufferAvailable: " +index);
        }

        @Override
        public void onOutputBufferAvailable(@NonNull MediaCodec codec, int index, @NonNull MediaCodec.BufferInfo info) {

            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                Log.e(TAG, "onOutputBufferAvailable: EOS");
                return;
            }
            ByteBuffer encodedData = videoEncoder.getOutputBuffer(index);
            if (encodedData == null) {
                throw new RuntimeException("couldn't fetch buffer at index " + index);
            }
            if (info.size != 0) {
                if (socketStarted) {
                    encodedData.position(info.offset);
                    int size = encodedData.remaining();
                    final byte[] buffer = new byte[size];
                    encodedData.get(buffer);
                    writeSampleData(buffer, 0, size);
                }else Log.w(TAG, "onOutputBufferAvailable: socket error" );
            }else Log.w(TAG, "onOutputBufferAvailable: info.size=0" );
            videoEncoder.releaseOutputBuffer(index, false);

        }

        @Override
        public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e) {
            Log.e(TAG, "MediaCodec " + codec.getName() + " onError:", e);
        }

        @Override
        public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {
            Log.d(TAG, "Output Format changed");
        }
    }

    private void writeSampleData(final byte[] buffer, final int offset, final int size) {

        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                Log.w(TAG, "writeSampleData" );
                if (socketHelper!= null && socketHelper.mSocketOutputStream != null) {
                    try {
                        socketHelper.mSocketOutputStream.write(buffer, offset, size);
                    } catch (IOException e) {
                        Log.e(TAG, "Failed to write data to socket, stop casting",e);
                        e.printStackTrace();
                    }
                }else {
                    Log.e(TAG, "writeSampleData: socket null" );
                }
            }
        });
    }
}
