package com.dknutsonlaw.android.runtracker2;

/*
 * A Service intended to allow the user to start and stop location updates and guarantee that the
 * updates will continue without regard to the state of the UI elements of the program.
 */

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
//import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
//import android.os.Bundle;
//import android.os.Handler;
import android.os.IBinder;
//import android.os.Message;
//import android.os.Messenger;
//import android.os.RemoteException;
import android.support.annotation.NonNull;
//import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
//import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
//import com.google.android.gms.location.LocationSettingsRequest;
//import com.google.android.gms.location.LocationSettingsResult;
//import com.google.android.gms.location.LocationSettingsStatusCodes;

//import java.lang.ref.WeakReference;
import java.text.DateFormat;
import java.util.Date;

@SuppressWarnings("Convert2Lambda")
public class BackgroundLocationService extends Service {

    private static final String TAG = "location.service";

    private final GoogleApiAvailability mGoogleApiAvailability = GoogleApiAvailability.getInstance();
    private static LocationRequest sLocationRequest;
    private static PendingIntent sPi;
    private static boolean sServicesAvailable = false;

    public BackgroundLocationService() {

    }

    @Override
    public void onCreate() {
        Log.i(TAG, "Reached onCreate()");
        super.onCreate();
        sServicesAvailable = servicesConnected();
        //we need a LocationRequest to ask Google Services to start location updates.
        buildLocationRequest();
    }

    private boolean servicesConnected() {
        //Check that Google Play Services is available
        int resultCode = mGoogleApiAvailability.isGooglePlayServicesAvailable(this);
        return resultCode == ConnectionResult.SUCCESS;
    }
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        Log.i(TAG, "Reached onStartCommand");
        //Check that Google Services are available and our GoogleApiClient is connected before
        //requesting location updates
        if (sServicesAvailable && RunTracker2.isConnected()){
            startLocationUpdates();
        }
        startForeground(Constants.NOTIFICATION_ID, createNotification(this));
        return START_STICKY;
    }

    private void buildLocationRequest(){
        //Construct the Location Request
        sLocationRequest = LocationRequest.create();
        //Use high accuracy
        sLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        sLocationRequest.setInterval(1000);
    }

    @Override
    public IBinder onBind(Intent intent) {

        return null;
    }

    //When the user starts tracking this run, create a Notification that will stay around until the
    //user dismisses it, stops tracking the run, or deletes the run, even if all the UI elements
    //are off screen (or even destroyed)

    private Notification createNotification(Context context) {
        //Create an explicit Intent for RunPagerActivity - we'll tie this
        //to the run that's being monitored
        Intent runPagerActivityIntent = RunPagerActivity.newIntent(context,
                Constants.KEEP_EXISTING_SORT, RunManager.getCurrentRunId());

        //The stack builder object will contain an artificial back stack for
        //RunPagerActivity so that when navigating back from the CombinedFragment we're
        //viewing we'll go to the RunRecyclerListFragment instead of the Home screen
        TaskStackBuilder stackBuilder =
                TaskStackBuilder.create(context)
                        //The artificial stack consists of everything defined in the manifest as a parent or parent
                        //of a parent of the Activity to which the notification will return us
                        .addNextIntentWithParentStack(runPagerActivityIntent);
        PendingIntent resultPendingIntent =
                stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            NotificationChannel channel = new NotificationChannel(Constants.PRIMARY_CHANNEL,
                    context.getString(R.string.default_channel), NotificationManager.IMPORTANCE_DEFAULT);
            channel.setLightColor(Color.GREEN);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            //Switched to NotificationCompat.Builder for Android Wear functionality.
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, Constants.PRIMARY_CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_menu_report_image)
                    .setContentTitle(context.getString(R.string.notification_title))
                    .setContentText(context.getString(R.string.notification_text));
            builder.setContentIntent(resultPendingIntent);
            //mNotificationManager.notify(0, builder.build());
            return builder.build();
        } else {
            NotificationCompat.Builder builder =
                    new NotificationCompat.Builder(context)

                            .setSmallIcon(android.R.drawable.ic_menu_report_image)
                            .setContentTitle(context.getString(R.string.notification_title))
                            .setContentText(context.getString(R.string.notification_text));
            builder.setContentIntent(resultPendingIntent);
            return builder.build();
        }
    }

    private static void startLocationUpdates() {
        Log.i(TAG, "Reached startLocationUpdates()");
        Log.i(TAG, "sServicesAvailable is " + sServicesAvailable);
        Log.i(TAG, "GoogleApiClient null? " + (RunTracker2.getGoogleApiClient() == null));
        Log.i(TAG, "GoogleApiClient.isConnected() is " + RunTracker2.getGoogleApiClient().isConnected());
        if (sServicesAvailable && RunTracker2.getGoogleApiClient() != null && RunTracker2.getGoogleApiClient().isConnected()) {
            sPi = RunManager.getLocationPendingIntent(RunTracker2.getInstance(), true);
            try {
                PendingResult<Status> result =
                        LocationServices.FusedLocationApi.requestLocationUpdates(
                            RunTracker2.getGoogleApiClient(),
                            sLocationRequest,
                            sPi);
                //This anonymous ResultCallback CANNOT be converted to a lambda - Android Lint lies when
                //it says otherwise! A lambda will result in an NPE.
                result.setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(@NonNull Status status) {

                        Log.i(TAG, "Reached OnResultCallback<Status> for location updates");
                        if (status.isSuccess()) {
                            Log.i(TAG, "ResultCallback<Status> status is Success.");
                        } else {
                            Log.i(TAG, "Attempt to start location updates failed!");
                        }
                    }
                });

            //We should never catch an exception here because we've just gotten through checking permissions.
            } catch (SecurityException e){
                Log.i(TAG, "SecurityException thrown when trying to start location updates.");
            }
        }
    }

    private static void stopLocationUpdates(){
        if (sServicesAvailable && RunTracker2.getGoogleApiClient() != null && RunTracker2.getGoogleApiClient().isConnected()){
            PendingResult<Status> result = LocationServices.FusedLocationApi.removeLocationUpdates(RunTracker2.getGoogleApiClient(), sPi);
            //This anonymous ResultCallback CANNOT be converted to a lambda - Android Lint lies when
            //it says otherwise! A lambda will result in an NPE.
            result.setResultCallback(new ResultCallback<Status>() {
                @Override
                public void onResult(@NonNull Status status) {
                    Log.i(TAG, "Reached onResult of removeLocationUpdates");
                    if (status.isSuccess()){
                        Log.i(TAG, "Location Updates off.");
                        if (sPi != null) {
                            sPi.cancel();
                        }
                    } else  {
                        Log.i(TAG, "Failed to  remove Location Updates");
                    }
                }
            });
        }
    }

    @Override
    public void onDestroy(){
        stopLocationUpdates();
        Log.i(TAG, DateFormat.getDateTimeInstance().format(new Date()) + ": Stopped");
        super.onDestroy();
    }
}
