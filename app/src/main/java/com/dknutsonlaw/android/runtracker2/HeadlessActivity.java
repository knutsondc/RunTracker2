package com.dknutsonlaw.android.runtracker2;

import android.app.Activity;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.api.GoogleApiClient;

public class HeadlessActivity extends AppCompatActivity {
    private static final String TAG = "HeadlessActivity";
    //private static final GoogleApiClient sGoogleApiClient = RunTracker2.getGoogleApiClient();
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data){
        Log.i(TAG, "Reached onActivityResult() in headlessActivity in RunTracker2");
        Log.i(TAG, "requestCode is " + requestCode);
        Log.i(TAG, "resultCode is " +resultCode);
        if (requestCode == Constants.LOCATION_SETTINGS_CHECK){
            switch (resultCode) {
                case Activity.RESULT_OK:
                    RunTracker2.setLocationSettingsState(true);
                    Toast.makeText(this,
                            "All Location Settings requirements now met.",
                            Toast.LENGTH_LONG)
                            .show();
                    break;
                case Activity.RESULT_CANCELED:
                    RunTracker2.setLocationSettingsState(false);
                    Toast.makeText(this, "You declined to enable Location Services.\n" +
                            "Stopping tracking of this run.", Toast.LENGTH_LONG).show();

                    Run run = RunManager.getRun(RunManager.getCurrentRunId());
                    if (run.getDuration() == 0) {
                        Toast.makeText(this,
                                "Never got any locations for this Run. Deleting Run.",
                                Toast.LENGTH_LONG)
                                .show();
                        TrackingLocationIntentService.startActionDeleteRun(this, RunManager.getCurrentRunId());
                    }
            }
        } else if (requestCode == Constants.MESSAGE_PLAY_SERVICES_RESOLUTION_REQUEST){
            switch (resultCode){
                case Activity.RESULT_OK:
                    //sGoogleApiClient.connect();
                    break;
                case Activity.RESULT_CANCELED:
                    Toast.makeText(this, "You canceled recovery of Google Play Services. " +
                            "       Stopping Tracking.", Toast.LENGTH_LONG).show();
                    Run run = RunManager.getRun(RunManager.getCurrentRunId());
                    if (run.getDuration() == 0) {
                        Toast.makeText(this,
                                "Never got any locations for this Run. Deleting Run.",
                                Toast.LENGTH_LONG)
                                .show();
                        TrackingLocationIntentService.startActionDeleteRun(this, RunManager.getCurrentRunId());
                    }
                    break;
            }
        }
    }



}


