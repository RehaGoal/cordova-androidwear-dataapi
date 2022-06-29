package us.m4rc.cordova.androidwear.dataapi;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

public class WearDataApiService extends WearableListenerService
    implements GoogleApiClient.ConnectionCallbacks {

  private final String TAG = WearDataApiService.class.getSimpleName();
  private GoogleApiClient mGoogleApiClient;

  @Override
  public void onConnected(@Nullable Bundle bundle) {
    Log.d(TAG, "Google API Client connected");
  }

  @Override
  public void onConnectionSuspended(int i) {}

  @Override
  public void onCreate() {
    super.onCreate();

    // set up the Google API client
    if (mGoogleApiClient==null) {
      mGoogleApiClient = new GoogleApiClient.Builder(this)
        .addApi(Wearable.API)
        .addConnectionCallbacks(this)
        .build();
    }
    if (!mGoogleApiClient.isConnected()) {
      mGoogleApiClient.connect();
    }
    Log.d(TAG, "WearDataApiService started.");
  }

  @Override
  public void onDestroy() {
    // clean up Google API client
    if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
      mGoogleApiClient.disconnect();
    }
    Log.d(TAG, "WearDataApiService shut down.");
    super.onDestroy();
  }

  /**
   * Forwards data changed to the plugin for callback to Cordova
   */
  @Override
  public void onDataChanged(DataEventBuffer dataEventBuffer) {
    if (WearDataApiPlugin.WearDataApiPluginSingleton != null) {
      WearDataApiPlugin.WearDataApiPluginSingleton.onDataChanged(dataEventBuffer);
    } else {
      Log.d(TAG, "onDataChanged but no DataListeners.");
    }
  }

}
