/* Created on 8/30/17 by DCK. This extension of the Application class simply creates a
 * GoogleApiClient that can be shared throughout the app. This class also manages the client's
 * connection to Google Services.
 */
package com.dknutsonlaw.android.runtracker2;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;

@SuppressLint("Registered")
public class RunTracker2 extends Application implements GoogleApiClient.ConnectionCallbacks,
                                                GoogleApiClient.OnConnectionFailedListener{

    private static final String TAG = RunTracker2.class.getSimpleName();
    private static RunTracker2 sInstance;
    static GoogleApiClient sGoogleApiClient;
    private static boolean sLocationSettingsEnabled = false;
    private static SharedPreferences sPrefs = null;
    private RunManager mRunManager;

    @Override
    public void onCreate(){
        super.onCreate();
        Log.i(TAG, "In onCreate() of RunTracker2");
        sInstance = this;
        sPrefs = getSharedPreferences(Constants.PREFS_FILE, Context.MODE_PRIVATE);
        //This instance of RunManager is never used, but we need to create it so that the static
        //methods in its RunDataBaseHelper member are immediately accessible to create the opening
        //RunRecyclerListFragment
        mRunManager = RunManager.get(this);
        buildGoogleApiClient();
        sGoogleApiClient.connect();
    }

    public static synchronized RunTracker2 getInstance(){
        return sInstance;
    }

    public void buildGoogleApiClient(){
        sGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }

    public static GoogleApiClient getGoogleApiClient(){
        return sGoogleApiClient;
    }

    public static SharedPreferences getPrefs(){
        return sPrefs;
    }

    public static boolean isConnected(){
        return sGoogleApiClient != null && sGoogleApiClient.isConnected();
    }

    public static boolean getLocationSettingsState(){
        return sLocationSettingsEnabled;
    }

    public static void setLocationSettingsState(boolean isEnabled){
        sLocationSettingsEnabled = isEnabled;
    }

    @Override
    public void onConnected(Bundle bundle){
        Log.i(TAG, "GoogleApiClient connected.");
    }

    @Override
    public void onConnectionSuspended(int cause){
        //Display connection status and report the cause to the user
        if (cause == CAUSE_NETWORK_LOST){
            Toast.makeText(this,R.string.connection_suspended_network_lost,
                            Toast.LENGTH_LONG).show();
            Log.i(TAG, "GoogleApiClient connection suspended - network connection lost!");
        }
        if (cause == CAUSE_SERVICE_DISCONNECTED){
            Log.i(TAG, "GoogleApiClient connection suspended - remote service killed!");
            Toast.makeText(this,R.string.connection_suspended_service_disconnected,
                            Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.i(TAG, "GoogleApi Connection failed.");
        if (connectionResult.hasResolution()) {

         /* Google Play services can resolve some errors it detects.
         * If the error has a resolution, try sending an Intent to
         * start an Activity that in turn will start a Google Play
         * services activity that can resolve the error.*/
            try {
                connectionResult.startResolutionForResult(new HeadlessActivity(),
                        Constants.MESSAGE_PLAY_SERVICES_RESOLUTION_REQUEST);
            } catch (IntentSender.SendIntentException e) {
                Log.i(TAG, "Caught IntentSender.SentIntentException while trying to " +
                        "invoke startResolutionForResult with request code " +
                        "MESSAGE_PLAY_SERVICES_RESOLUTION_REQUEST");
            }
        } else {
            //If the error cannot be resolved, display a dialog advising the user of that fact
            Intent errorDialogIntent  = new Intent(this, DialogActivity.class);
            errorDialogIntent.putExtra(Constants.EXTRA_ERROR_CODE, connectionResult.getErrorCode());
            startActivity(errorDialogIntent);
        }
    }

}