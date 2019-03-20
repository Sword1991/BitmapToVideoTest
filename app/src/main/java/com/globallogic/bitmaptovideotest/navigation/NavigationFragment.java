package com.globallogic.bitmaptovideotest.navigation;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.opengl.GLException;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.globallogic.bitmaptovideotest.BitmapToVideoEncoder;
import com.globallogic.bitmaptovideotest.Config;
import com.globallogic.bitmaptovideotest.R;
import com.globallogic.bitmaptovideotest.SocketHelper;
import com.mapbox.api.directions.v5.models.DirectionsResponse;
import com.mapbox.api.directions.v5.models.DirectionsRoute;
import com.mapbox.geojson.Point;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.services.android.navigation.ui.v5.NavigationView;
import com.mapbox.services.android.navigation.ui.v5.NavigationViewOptions;
import com.mapbox.services.android.navigation.ui.v5.OnNavigationReadyCallback;
import com.mapbox.services.android.navigation.ui.v5.listeners.NavigationListener;
import com.mapbox.services.android.navigation.v5.milestone.Milestone;
import com.mapbox.services.android.navigation.v5.milestone.MilestoneEventListener;
import com.mapbox.services.android.navigation.v5.navigation.NavigationRoute;
import com.mapbox.services.android.navigation.v5.routeprogress.ProgressChangeListener;
import com.mapbox.services.android.navigation.v5.routeprogress.RouteProgress;
import com.mapbox.services.android.navigation.v5.routeprogress.RouteProgressState;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.IntBuffer;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.opengles.GL10;

import retrofit2.Call;
import retrofit2.Response;

public class NavigationFragment extends Fragment implements OnNavigationReadyCallback, NavigationListener,
        ProgressChangeListener, MilestoneEventListener {

    private MapView mapView;
    private boolean bitmapReady=true;
    private GLSurfaceView glSurfaceView;

    private interface BitmapReadyCallbacks {

        void onBitmapReady(int[] argb);
    }

    private static final String TAG = "NavigationFragment";

    private static final double ORIGIN_LONGITUDE = -3.714873;
    private static final double ORIGIN_LATITUDE = 40.397389;
    private static final double DESTINATION_LONGITUDE = -3.166243;
    private static final double DESTINATION_LATITUDE = 40.650514;
    private static final String BROADCAST_IP = "255.255.255.255";

    private static final int BROADCAST_PORT = 50008;

    private NavigationView navigationView;
    private DirectionsRoute directionsRoute;

    private Handler mHandler = new Handler();
    private SocketHelper socketHelper;
    private BitmapToVideoEncoder bitmapToVideoEncoder;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_navigation, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        navigationView = view.findViewById(R.id.navigation_view_fragment);
        navigationView.onCreate(savedInstanceState);
        navigationView.initialize(this);
        socketHelper=new SocketHelper();

        bitmapToVideoEncoder = new BitmapToVideoEncoder(new BitmapToVideoEncoder.IBitmapToVideoEncoderCallback() {
            @Override
            public void onEncodingComplete() {

                Log.w(TAG, "onEncodingComplete: " );
            }
        }, mHandler);
    }

    @Override
    public void onStart() {
        super.onStart();
        navigationView.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
        navigationView.onResume();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        navigationView.onSaveInstanceState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        if (savedInstanceState != null) {
            navigationView.onRestoreInstanceState(savedInstanceState);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        navigationView.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
        navigationView.onStop();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        navigationView.onLowMemory();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        navigationView.onDestroy();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        stopEncoding();
    }

    @Override
    public void onNavigationReady(boolean isRunning) {
        Point origin = Point.fromLngLat(ORIGIN_LONGITUDE, ORIGIN_LATITUDE);
        Point destination = Point.fromLngLat(DESTINATION_LONGITUDE, DESTINATION_LATITUDE);
        fetchRoute(origin, destination);

        View feedbackFab = navigationView.findViewById(R.id.feedbackFab);
        if(feedbackFab!=null && feedbackFab.getVisibility()!=View.GONE) {
            feedbackFab.setVisibility(View.GONE);
        }
        navigationView.retrieveSoundButton().addOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.w(TAG, "soundButton clicked" );
            }
        });
        mapView = navigationView.findViewById(com.mapbox.services.android.navigation.ui.v5.R.id.navigationMapView);
        glSurfaceView = (GLSurfaceView) mapView.getChildAt(0);
        startEncoding();
        mapView.addOnCameraDidChangeListener(new MapView.OnCameraDidChangeListener() {
            @Override
            public void onCameraDidChange(boolean animated) {
                startSnapShot();
            }
        });


    }

    private void startEncoding(){
        if (!socketHelper.createSocket()) {
            Log.e(TAG, "Failed to create socket to receiver, ip: " + SocketHelper.mReceiverIp);
        }
        bitmapToVideoEncoder.startEncoding(Config.SELECTED_WIDTH, Config.SELECTED_HEIGHT, socketHelper.mSocketOutputStream);
    }

    private void stopEncoding(){
        bitmapToVideoEncoder.stopEncoding();
        socketHelper.closeSocket();
    }

    @Override
    public void onCancelNavigation() {
        navigationView.stopNavigation();
        stopNavigation();
        startNavigation();
    }

    @Override
    public void onNavigationFinished() {
        // no-op
    }

    @Override
    public void onNavigationRunning() {
        // no-op
    }

    @Override
    public void onProgressChange(Location location, RouteProgress routeProgress) {

        if(routeProgress.currentState()== RouteProgressState.ROUTE_ARRIVED){
            stopNavigation();
            startNavigation();
        }/*
        if(AppCompatDelegate.getDefaultNightMode()!=AppCompatDelegate.MODE_NIGHT_YES){
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            getActivity().recreate();
        }*/
        //startSnapShot();
    }

    private void fetchRoute(Point origin, Point destination) {
        NavigationRoute.builder(getContext())
                .accessToken(Mapbox.getAccessToken())
                .origin(origin)
                .destination(destination)
                .build()
                .getRoute(new SimplifiedCallback() {
                    @Override
                    public void onResponse(Call<DirectionsResponse> call, Response<DirectionsResponse> response) {
                        directionsRoute = response.body().routes().get(0);
                        startNavigation();
                    }
                });
    }

    private void startNavigation() {
        if (directionsRoute == null) {
            return;
        }
        NavigationViewOptions options = NavigationViewOptions.builder()

                .directionsRoute(directionsRoute)
                .shouldSimulateRoute(true)
                .navigationListener(NavigationFragment.this)
                .progressChangeListener(this)
                .milestoneEventListener(this)
                .build();
        navigationView.startNavigation(options);
    }

    private void stopNavigation() {
        updateWasNavigationStopped(true);
    }


    private boolean wasNavigationStopped() {
        Context context = getActivity();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        return preferences.getBoolean(getString(R.string.was_navigation_stopped), false);
    }

    public void updateWasNavigationStopped(boolean wasNavigationStopped) {
        Context context = getActivity();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(getString(R.string.was_navigation_stopped), wasNavigationStopped);
        editor.apply();
    }

    @Override
    public void onMilestoneEvent(RouteProgress routeProgress, String instruction, Milestone milestone) {
        /*if(instruction!=null && instruction.length()>0) {
            sendTestMessage(instruction);
        }*/

    }


    private void startSnapShot() {
        if(bitmapToVideoEncoder==null || !bitmapToVideoEncoder.isEncodingStarted()){
            Log.w(TAG, "startSnapShot: NOT READY YET" );
            return;
        }

        if(bitmapReady) {
            bitmapReady=false;
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    captureBitmap(glSurfaceView, new BitmapReadyCallbacks() {
                        @Override
                        public void onBitmapReady(int[] argb) {
                            //Log.w(TAG, "onBitmapReady: ");
                            bitmapToVideoEncoder.queueFrame(argb);
                            bitmapReady=true;
                        }
                    });
                }
            });

        }//else Log.w(TAG, "startSnapShot: BITMAP NOT READY" );
    }


    private void captureBitmap(final GLSurfaceView glSurfaceView, final BitmapReadyCallbacks bitmapReadyCallbacks) {
        glSurfaceView.queueEvent(new Runnable() {
            @Override
            public void run() {
                EGL10 egl = (EGL10) EGLContext.getEGL();
                GL10 gl = (GL10) egl.eglGetCurrentContext().getGL();
                //Log.w(TAG, "run: glSurfaceView.getWidth()="+glSurfaceView.getWidth()+" glSurfaceView.getHeight()="+glSurfaceView.getHeight() );
                bitmapReadyCallbacks.onBitmapReady(
                        createBitmapFromGLSurface(0, 0, glSurfaceView.getWidth(), glSurfaceView.getHeight(), gl)
                );

            }
        });
    }

    private int[] createBitmapFromGLSurface(int x, int y, int w, int h, GL10 gl) {

        int bitmapBuffer[] = new int[w * h];
        int bitmapSource[] = new int[w * h];
        IntBuffer intBuffer = IntBuffer.wrap(bitmapBuffer);
        intBuffer.position(0);

        try {
            gl.glReadPixels(x, y, w, h, GL10.GL_RGBA, GL10.GL_UNSIGNED_BYTE, intBuffer);
            int offset1, offset2;
            for (int i = 0; i < h; i++) {
                offset1 = i * w;
                offset2 = (h - i - 1) * w;
                for (int j = 0; j < w; j++) {
                    int texturePixel = bitmapBuffer[offset1 + j];
                    int blue = (texturePixel >> 16) & 0xff;
                    int red = (texturePixel << 16) & 0x00ff0000;
                    int pixel = (texturePixel & 0xff00ff00) | red | blue;
                    bitmapSource[offset2 + j] = pixel;
                }
            }
        } catch (GLException e) {
            Log.e(TAG, "createBitmapFromGLSurface: " + e.getMessage(), e);
            return null;
        }

        return bitmapSource;
    }

    private void sendTestMessage(final String messageStr){
        new Thread(new Runnable() {
            @Override
            public void run() {
                try{
                    DatagramSocket s = new DatagramSocket();
                    InetAddress local = InetAddress.getByName(BROADCAST_IP);//my broadcast ip
                    int msg_length=messageStr.length();
                    byte[] message = messageStr.getBytes();
                    DatagramPacket p = new DatagramPacket(message, msg_length,local,BROADCAST_PORT);
                    s.send(p);
                    Log.w(TAG,"message send: "+messageStr);
                }catch(Exception e){
                    Log.e(TAG,"error  " + e.toString());
                }

            }
        }).start();
    }
}

