/* Created on 8/30/17 by DCK. This extension of the Application class simply creates a
 * GoogleApiClient that can be shared throughout the app. This class also manages the client's
 * connection to Google Services.
 */
package com.dknutsonlaw.android.runtracker2;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

@SuppressLint("Registered")
public class RunTracker2 extends Application {

    private static final String TAG = RunTracker2.class.getSimpleName();
    private static RunTracker2 sInstance;
    private static boolean sLocationSettingsEnabled = false;
    private static SharedPreferences sPrefs = null;

    @Override
    public void onCreate(){
        super.onCreate();
        Log.i(TAG, "In onCreate() of RunTracker2");
        sInstance = this;
        sPrefs = getSharedPreferences(Constants.PREFS_FILE, Context.MODE_PRIVATE);
        /*This instance of RunManager is never used, but we need to create it so that the static
         *methods in its RunDataBaseHelper member are immediately accessible to create the opening
         *RunRecyclerListFragment.
         */
        @SuppressWarnings("unused") RunManager sRunManager = RunManager.get(getApplicationContext());
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