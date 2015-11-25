package com.dknutsonlaw.android.runtracker2;

/**
 * Created by dck on 9/6/15.
 */
import android.content.Context;
import android.location.Location;
import android.util.Log;

/**
 * Created by dck on 1/14/15.
 *
 * This receiver is statically registered in AndroidManifest.xml, where it has an IntentFilter that
 * causes it to respond to Intents created with the ACTION_LOCATION identifier in RunManager.java
 * and used in the PendingIntent used to request location updates.This receiver should run even
 * if the UI elements of the program are in the background or even destroyed.
 *
 * 8/25/2015 - added accuracy check on Location updates - trying to get around the "jumpiness" of
 * the GPS when it first starts.
 *
 * 8/28/2015 - Limited accuracy checks on location updates to gps provider; all locations from the
 * test provider have an accuracy of 100.0.
 *
 * 11/5/15 - Made changes due to switch to Google's FusedLocationProvider: We reject all location
 * updates that don't have an Altitude value. As the Network Provider's reports don't include an
 * Altitude value, this limits the updates recorded to the database to those coming from GPS.
 *
 * 11/12/15 - Removed limit on accepting network location provider updates and loosened accuracy
 * requirement to 40 meters.
 */
@SuppressWarnings("ALL")
public class TrackingLocationReceiver extends LocationReceiver {
    private static final String TAG = "TrackingLocationReceiver";
    //Reject all location updates that have an accuracy of greater than
    //40 meters.
    @Override
    protected void onLocationReceived(Context c, Location loc) {
       if (loc.getAccuracy() == 0.0) {
            Log.i(TAG, "Location rejected - no accuracy value");
        } else if (loc.getAccuracy() > 40.0) {
            Log.i(TAG, "Location rejected - insufficiently accurate: " + loc.getAccuracy());
        } else {
            //From LocationServices to here to RunManager to Intent Service to RunDatabaseHelper.
            //Use of the Intent Service keeps the database work off the main, UI thread.
            RunManager.get(c).insertLocation(loc);
            Log.i(TAG, "Got a good location.");
        }
    }
}

