package com.globallogic.bitmaptovideotest.navigation;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.globallogic.bitmaptovideotest.R;


public class NavigationFragment extends Fragment {
  private static final String TAG = "NavigationFragment";

  private NavigationController mNavigationController;

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
    FrameLayout navigationContainer = view.findViewById(R.id.navigation_view_container);
    navigationContainer.addView(mNavigationController.createNavigationView(navigationContainer));
    mNavigationController.initialize();
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mNavigationController = new NavigationController(getActivity());
    mNavigationController.onCreate(savedInstanceState);
  }

  @Override
  public void onStart() {
    super.onStart();
    mNavigationController.onStart();
  }

  @Override
  public void onResume() {
    super.onResume();
    mNavigationController.onResume();
  }

  @Override
  public void onSaveInstanceState(@NonNull Bundle outState) {
    mNavigationController.onSaveInstanceState(outState);
    super.onSaveInstanceState(outState);
  }

  @Override
  public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
    super.onViewStateRestored(savedInstanceState);
    if (savedInstanceState != null) {
      mNavigationController.onRestoreInstanceState(savedInstanceState);
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    mNavigationController.onPause();
  }

  @Override
  public void onStop() {
    super.onStop();
    mNavigationController.onStop();
  }

  @Override
  public void onLowMemory() {
    super.onLowMemory();
    mNavigationController.onLowMemory();
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    mNavigationController.onDestroy();
  }
}
