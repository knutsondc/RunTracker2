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
public class RunTracker2 extends Application /*implements GoogleApiClient.ConnectionCallbacks,
                                                GoogleApiClient.OnConnectionFailedListener*/{

    private static final String TAG = RunTracker2.class.getSimpleName();
    private static RunTracker2 sInstance;
    private static GoogleApiClient sGoogleApiClient;
    private static boolean sLocationSettingsEnabled = false;
    private static SharedPreferences sPrefs = null;
    private static RunManager sRunManager;

    @Override
    public void onCreate(){
        super.onCreate();
        Log.i(TAG, "In onCreate() of RunTracker2");
        sInstance = this;
        sPrefs = getSharedPreferences(Constants.PREFS_FILE, Context.MODE_PRIVATE);
        //This instance of RunManager is never used, but we need to create it so that the static
        //methods in its RunDataBaseHelper member are immediately accessible to create the opening
        //RunRecyclerListFragment
        sRunManager = RunManager.get(this);
    }

    public static synchronized RunTracker2 getInstance(){
        return sInstance;
    }

    public static SharedPreferences getPrefs(){
        return sPrefs;
    }

    public static boolean getLocationSettingsState(){
        return sLocationSettingsEnabled;
    }

    public static void setLocationSettingsState(boolean isEnabled){
        sLocationSettingsEnabled = isEnabled;
    }
}