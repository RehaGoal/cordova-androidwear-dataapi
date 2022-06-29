package us.m4rc.cordova.androidwear.dataapi;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.Result;
import com.google.android.gms.common.api.ResultTransform;
import com.google.android.gms.common.api.ResultCallbacks;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaDialogsHelper;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;

import static com.google.android.gms.wearable.DataApi.FILTER_LITERAL;

public class WearDataApiPlugin extends CordovaPlugin
    implements DataApi.DataListener, GoogleApiClient.ConnectionCallbacks  {

  static WearDataApiPlugin WearDataApiPluginSingleton = null;
  private GoogleApiClient mGoogleApiClient;


  private final String TAG = WearDataApiPlugin.class.getSimpleName();
  private final String[] ACTIONS = {
    "putDataItem",
    "getDataItems",
    "deleteDataItems",
    "addListener"
  };

  // keep the callbacks for all registered receivers
  private Queue<CallbackContext> registeredListeners = new LinkedList<CallbackContext>();

  // for queueing actions while api is starting up
  private Queue<ExecuteAction> queuedActions = new LinkedList<ExecuteAction>();

  @Override
  public void onConnected(@Nullable Bundle bundle) {
      Log.d(TAG, "Google API Client connected");
      onServiceStarted();
  }

  @Override
  public void onConnectionSuspended(int i) {

  }


  private class ExecuteAction {
    public String action;
    public JSONArray args;
    public CallbackContext callbackContext;

    ExecuteAction(String action,JSONArray args,CallbackContext callbackContext) {
      this.action = action;
      this.args = args;
      this.callbackContext = callbackContext;
    }
  }

  private void onServiceStarted() {
    // run all queued actions
    while (queuedActions.size() > 0) {
      ExecuteAction ea = queuedActions.remove();
      try {
        _execute(ea);
      }
      catch (Exception ex) {
        ea.callbackContext.error(ex.getMessage());
      }
    }
  }

  @Override
  public void initialize(CordovaInterface cordova, CordovaWebView webView) {
    super.initialize(cordova, webView);
    Log.d(TAG, "WearDataApiPlugin initialized.");

    Activity context = cordova.getActivity();
    WearDataApiPluginSingleton = this;
    context.startService(new Intent(context, WearDataApiService.class));

    // set up the Google API client
    if (mGoogleApiClient==null) {
      mGoogleApiClient = new GoogleApiClient.Builder(cordova.getActivity())
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
    super.onDestroy();
    WearDataApiPluginSingleton = null; // if we are called gracefully
  }


   @Override
   protected void finalize() throws Throwable {
     super.finalize();
     WearDataApiPluginSingleton = null; // when we are garbage collected
   }

  @Override
  public boolean execute(String action, JSONArray args, CallbackContext callbackContext) {
    // verify action is valid
    if (!Arrays.asList(ACTIONS).contains(action)) {
      return false;
    }
    ExecuteAction ea = new ExecuteAction(action,args,callbackContext);
    if (mGoogleApiClient.isConnected()) {
      try {
        _execute(ea); // run immediately if already connected
      }
      catch (Exception ex) {
        ea.callbackContext.error(ex.getMessage());
      }
    }
    else {
      queuedActions.add(ea); // otherwise queue
    }
    return true;
  }

  /**
   * maps Cordova exec commands to plugin functions
   */
  private void _execute(ExecuteAction ea) throws Exception {
    if (ea.action.equals("putDataItem")) {
      putDataItem(ea.args, ea.callbackContext);
    }
    if (ea.action.equals("getDataItems")) {
      cmdDataItems("get", ea.args, ea.callbackContext);
    }
    if (ea.action.equals("deleteDataItems")) {
      cmdDataItems("delete", ea.args, ea.callbackContext);
    }
    if (ea.action.equals("addListener")) {
      addListener(ea.args, ea.callbackContext);
    }
  }

  @Override
  public void onReset() {
    super.onReset();
    // window reloaded, clear listeners to make sure we don't leak them
    registeredListeners.clear();
  }

  private void putDataItem(JSONArray args, final CallbackContext callbackContext) throws JSONException {
    if (args.length() != 2) {
      throw new JSONException("putDataItem error: invalid arguments");
    }
    PutDataMapRequest putDataMapReq = PutDataMapRequest.create(args.getString(0));
    // Original: convert object with JsonConverter
    //      JsonConverter.jsonToDataMap((JSONObject)args.get(1), putDataMapReq.getDataMap());

    // Changed: put complete json in data property
    DataMap response = putDataMapReq.getDataMap();
    long id = new Random(System.currentTimeMillis()).nextLong();
    response.putLong("msgID", id);
    response.putString("data", (String) args.get(1));

    // Changed: added setUrgent flag
    this.putDataRequest(putDataMapReq.asPutDataRequest().setUrgent())
      .then(new ResultTransform<DataApi.DataItemResult, Result>() {
        @Override
        public PendingResult<Result> onSuccess(@NonNull DataApi.DataItemResult dataItemResult) {
          callbackContext.success();
          return null;
        }
      })
      .andFinally(new ResultCallbacks<Result>() {
        //TODO: remove andFinally after successful testing
        @Override
        public void onSuccess(@NonNull Result r) {
          // nothing to do.
        }
        @Override
        public void onFailure(@NonNull Status status) {
          // An API call failed.
          Log.d(TAG, "putDataRequest failed! with status code: " + status.getStatusCode() + "and msg: " + status.getStatusMessage());
        }

      });
  }

  private void cmdDataItems(String cmd, JSONArray args, final CallbackContext callbackContext) throws Exception {
    if (args.length() < 1) {
      throw new JSONException(cmd + "DataItems error: invalid arguments");
    }
    int filterType = FILTER_LITERAL;
    Uri uri = Uri.parse(args.getString(0));
    if (args.length()==2) {
      filterType = args.getInt(1);
    }
    if (cmd.equals("get")) {
      this.getDataItems(uri, filterType).then(new ResultTransform<DataItemBuffer, Result>() {
        @Override
        public PendingResult<Result> onSuccess(@NonNull DataItemBuffer dataItems) {
          callbackContext.success(JsonConverter.dataItemBufferToJson(dataItems));
          return null;
        }
      });
    } else if (cmd.equals("delete")) {
      this.deleteDataItems(uri, filterType).then(new ResultTransform<DataApi.DeleteDataItemsResult, Result>() {
        @Override
        public PendingResult<Result> onSuccess(@NonNull DataApi.DeleteDataItemsResult deleteResult) {
          JSONObject json = new JSONObject();
          try {
            json.put("NumDeleted", deleteResult.getNumDeleted());
          }
          catch (Exception ignored) {}
          callbackContext.success(json);
          return null;
        }
      });
    } else {
      callbackContext.error("Invalid verb: "+ cmd);
    }
  }

  private void addListener(JSONArray args, CallbackContext callbackContext) throws JSONException {
    if (args.length() != 0) {
      throw new JSONException("addListener error: invalid arguments");
    }
    registeredListeners.add(callbackContext);
  }

  @Override
  public void onDataChanged(DataEventBuffer dataEventBuffer) {
    JSONArray results = JsonConverter.dataEventBufferToJson(dataEventBuffer);
    for (CallbackContext registeredDataReceiver: registeredListeners) {
      // don't just call success() because that will dispose the callback
      // so we have to return a PluginResult instead
      PluginResult result = new PluginResult(PluginResult.Status.OK, results);
      result.setKeepCallback(true);
      registeredDataReceiver.sendPluginResult(result);
    }
  }

  // methods below form a thin wrapper around the DataApi for use in the Plugin
  private PendingResult<DataApi.DataItemResult> putDataRequest(PutDataRequest data) {
    return Wearable.DataApi.putDataItem(mGoogleApiClient, data);
  }

  private PendingResult<DataApi.DeleteDataItemsResult> deleteDataItems(Uri uri, int filterType) {
    return Wearable.DataApi.deleteDataItems(mGoogleApiClient, uri, filterType);
  }

  private PendingResult<DataItemBuffer> getDataItems(Uri uri, int filterType) {
    return Wearable.DataApi.getDataItems(mGoogleApiClient, uri, filterType);
  }
}
