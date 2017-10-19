package com.dknutsonlaw.android.runtracker2;

/*
  Created by dck on 9/6/15 for RunTracker2.
  added by dck 1/15/2015 to original RunTracker program.
  A subclass of Broadcast Receiver to receive Location updates for use in updating the database,
  with separate instantiations to provide "live" updates directly to the UIs in RunFragment and
  RunMapFragment.

  2/12/2015
  No longer used for "live" UI updates after implementation of MyLocationListCursorLoader that
  supplies "live" updates from the database, so the only instance left is TrackingLocationReceiver
  that supplies Location updates to the database.
 */
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.util.Log;

import com.google.android.gms.location.LocationResult;

import java.util.List;

public class LocationReceiver extends BroadcastReceiver {
    private static final String TAG = "LocationReceiver";

    public LocationReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        /*This method is called when the BroadcastReceiver is receiving
         *an Intent broadcast. Extract the results and use them.
         */
        if (LocationResult.hasResult(intent)) {
            LocationResult locationResult = LocationResult.extractResult(intent);
            List<Location> locationList = locationResult.getLocations();
            for (int i = 0; i < locationList.size(); i++) {
                onLocationReceived(context, locationList.get(i));
            }

        }
    }
    //The next method should be overridden in any actual implementation.
    void onLocationReceived(Context context, Location loc) {
        Log.d(TAG, this + " Got location from " + loc.getProvider() + ": "
                + loc.getLatitude() + ", " + loc.getLongitude());
    }
}
