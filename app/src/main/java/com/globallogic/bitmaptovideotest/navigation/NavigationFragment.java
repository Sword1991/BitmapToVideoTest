package com.globallogic.bitmaptovideotest.navigation;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.globallogic.bitmaptovideotest.BitmapToVideoEncoder;
import com.globallogic.bitmaptovideotest.NavigationActivity;
import com.globallogic.bitmaptovideotest.R;
import com.mapbox.api.directions.v5.models.DirectionsResponse;
import com.mapbox.api.directions.v5.models.DirectionsRoute;
import com.mapbox.geojson.Point;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.snapshotter.MapSnapshot;
import com.mapbox.mapboxsdk.snapshotter.MapSnapshotter;
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

import retrofit2.Call;
import retrofit2.Response;

public class NavigationFragment extends Fragment implements OnNavigationReadyCallback, NavigationListener,
        ProgressChangeListener, MilestoneEventListener {
  private static final String TAG = "NavigationFragment";

  private static final double ORIGIN_LONGITUDE = -3.714873;
  private static final double ORIGIN_LATITUDE = 40.397389;
  private static final double DESTINATION_LONGITUDE = -3.166243;
  private static final double DESTINATION_LATITUDE = 40.650514;
  private static final String BROADCAST_IP = "255.255.255.255";

  private static final int BROADCAST_PORT = 50008;

  private NavigationView navigationView;
  private DirectionsRoute directionsRoute;
  private MapSnapshotter mapSnapshotter;
  private MapboxMap mapboxMap;
  private boolean isSnapshotReady=true;
  private int counter=0;

  public static NavigationFragment newInstance() {
    Bundle args = new Bundle();
    NavigationFragment fragment = new NavigationFragment();
    fragment.setArguments(args);
    return fragment;
  }
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
  public void onNavigationReady(boolean isRunning) {
    Point origin = Point.fromLngLat(ORIGIN_LONGITUDE, ORIGIN_LATITUDE);
    Point destination = Point.fromLngLat(DESTINATION_LONGITUDE, DESTINATION_LATITUDE);
    fetchRoute(origin, destination);

    View feedbackFab = navigationView.findViewById(R.id.feedbackFab);
    if(feedbackFab!=null && feedbackFab.getVisibility()!= View.GONE) {
      feedbackFab.setVisibility(View.GONE);
    }
    navigationView.retrieveSoundButton().addOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        Log.w(TAG, "soundButton clicked" );
      }
    });
    mapboxMap=navigationView.retrieveNavigationMapboxMap().retrieveMap();
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
    Log.w(TAG, "onNavigationRunning: " );
    // no-op
  }

  @Override
  public void onProgressChange(Location location, RouteProgress routeProgress) {

    Log.w(TAG, "onProgressChange: " );
    if(routeProgress.currentState()==RouteProgressState.ROUTE_ARRIVED){
      stopNavigation();
      startNavigation();
    }/*
    if(AppCompatDelegate.getDefaultNightMode()!= AppCompatDelegate.MODE_NIGHT_YES){
      AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
      getActivity().recreate();
    }*/
    startSnapShot();
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
  }


  private void startSnapShot() {
    if(isSnapshotReady) {
      if (mapSnapshotter == null) {
        MapSnapshotter.Options options =
                new MapSnapshotter.Options(navigationView.getWidth(), navigationView.getHeight())
                        .withStyle(mapboxMap.getStyle().getUrl())
                        .withLogo(false)
                .withCameraPosition(mapboxMap.getCameraPosition());


        mapSnapshotter = new MapSnapshotter(getActivity(), options);

      } else {
        mapSnapshotter.setSize(navigationView.getWidth(), navigationView.getHeight());
        mapSnapshotter.setCameraPosition(mapboxMap.getCameraPosition());

      }

      isSnapshotReady=false;

      mapSnapshotter.start(new MapSnapshotter.SnapshotReadyCallback() {
        @Override
        public void onSnapshotReady(MapSnapshot snapshot) {
          if(getBitmapToVideoEncoder()!=null && getBitmapToVideoEncoder().isEncodingStarted()){
            getBitmapToVideoEncoder().queueFrame(snapshot.getBitmap());
          }
          Log.w(TAG, "onSnapshotReady: " );
          isSnapshotReady=true;
        }
      });
    }else {
      Log.w(TAG, "startSnapShot: NOT ready" );
    }
  }

  private BitmapToVideoEncoder getBitmapToVideoEncoder(){
    return ((NavigationActivity)getActivity()).bitmapToVideoEncoder;
  }
}
