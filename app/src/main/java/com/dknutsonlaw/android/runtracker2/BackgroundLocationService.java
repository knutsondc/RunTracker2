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
import android.content.IntentFilter;
import android.graphics.Color;
import android.location.Location;
import android.os.Binder;
import android.os.Build;
//import android.os.Bundle;
//import android.os.Handler;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
//import android.os.Message;
//import android.os.Messenger;
//import android.os.RemoteException;
import android.support.annotation.NonNull;
//import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;

//import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
//import com.google.android.gms.location.LocationSettingsRequest;
//import com.google.android.gms.location.LocationSettingsResult;
//import com.google.android.gms.location.LocationSettingsStatusCodes;

//import java.lang.ref.WeakReference;
import java.text.DateFormat;
import java.util.Date;

@SuppressWarnings("Convert2Lambda")
public class BackgroundLocationService extends Service {

   private static final String TAG = "location.service";

   private final IBinder mBinder = new LocationBinder();
   private FusedLocationProviderClient mFusedLocationClient;
   private LocationRequest mLocationRequest;
   private Handler mServiceHandler;
   private PendingIntent mPi;

   public class LocationBinder extends Binder {
       BackgroundLocationService getService(){
           return BackgroundLocationService.this;
       }
   }
   public BackgroundLocationService() {

   }

    @Override
    public void onCreate() {
        Log.i(TAG, "Reached onCreate()");
        super.onCreate();
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        //we need a LocationRequest to ask Location Services to start location updates.
        mLocationRequest = buildLocationRequest();
        HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        mServiceHandler = new Handler(handlerThread.getLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        Log.i(TAG, "Reached onStartCommand");
        //This Service is started only when we're tracking a Run and the CombinedRun fragment
        //for that Run unbinds and goes into the background, so to keep the Service going at
        //full speed, it needs to run as a foreground service and we need too ensure that location
        //updates continue.
        startForeground(Constants.NOTIFICATION_ID, createNotification(this));
        startLocationUpdates();
        return START_NOT_STICKY;
    }

    private LocationRequest buildLocationRequest(){
        Log.i(TAG, "Now creating LocationRequest.");
        //Construct the Location Request
        LocationRequest locationRequest= new LocationRequest();
        //Use high accuracy
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(1000);
        return locationRequest;
    }

    /*public boolean serviceIsRunningInForeground(Context context){
        ActivityManager manager = (ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(
                Integer.MAX_VALUE)){
            if (getClass().getName().equals(service.service.getClassName())){
                if (service.foreground){
                    return true;
                }
            }
        }
        return false;
    }*/

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "In onBind()");
        try {
            //When bound to a CombinedRun Fragment, the Service need no longer run as
            //a foreground service
            stopForeground(true);
        } catch (NullPointerException npe){
            Log.e(TAG, " Caught a null pointer trying to stopForeground");
        }
        return mBinder;
    }

    @Override
    public void onRebind(Intent intent){
        Log.i(TAG, "In onRebind()");
        //When bound to a CombinedRun Fragment, the Service need no longer run as
        //a foreground service
        stopForeground(true);
        super.onRebind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent){
        Log.i(TAG, "In onUnbind");
        if (RunManager.isTrackingRun()){
            Log.i(TAG, "Starting Foreground Service");
            //When the last CombinedFragment unbinds, the Service needs to be started.
            //The Service must then run as a foreground service, so startForegroun() gets
            //called in onStartCommand().
            if (RunManager.isTrackingRun()){
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
                    startForegroundService(new Intent(this, BackgroundLocationService.class));
                } else {
                    startService(new Intent(this, BackgroundLocationService.class));
                }
            }

        }
        return true;
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
                    .setContentText(context.getString(R.string.notification_text))
                    .setContentIntent(resultPendingIntent)
                    .setOngoing(true)
                    .setWhen(System.currentTimeMillis());
            return builder.build();
        } else {
            NotificationCompat.Builder builder =
                    new NotificationCompat.Builder(context)

                            .setSmallIcon(android.R.drawable.ic_menu_report_image)
                            .setContentTitle(context.getString(R.string.notification_title))
                            .setContentText(context.getString(R.string.notification_text))
                            .setContentIntent(resultPendingIntent)
                            .setOngoing(true)
                            .setWhen(System.currentTimeMillis());
            return builder.build();
        }
    }

    void startLocationUpdates() {
        //Location updates are given to the PendingIntent and broadcast with the
        //IntentFilter ACTION_LOCATION. TrackkingLocationReceiver, which is statically
        //declared in AndroidManifest, receives those broadcasts and calls RunManager's
        //insertLocation() method.
        Log.i(TAG, "Reached startLocationUpdates()");
        mPi = RunManager.getLocationPendingIntent(this, true);
        try {
            Log.i(TAG, "mPi null? " + (mPi == null));
                    mFusedLocationClient.requestLocationUpdates(
                    mLocationRequest,
                    mPi
            )
            .addOnFailureListener(new OnFailureListener() {
                String failureMessage;
                @Override
                public void onFailure(@NonNull Exception e) {

                    if (e instanceof ApiException){
                        failureMessage = ((ApiException) e).getStatusMessage();
                    } else {
                        failureMessage = e.getMessage();
                    }
                    Log.e(TAG, "requestLocationUpdates() failed: " + failureMessage);
                }
            })
            .addOnCompleteListener(new OnCompleteListener<Void>() {
                @Override
                public void onComplete(@NonNull Task<Void> task) {
                    Log.i(TAG, task.toString());
                    if (task.isSuccessful()){
                        Log.i(TAG, "Successfully started Location updates.");
                    } else {
                        Exception e = task.getException();
                        if (e != null){
                            Log.e(TAG, "Exception is: " + e.getMessage());
                        } else {
                            Log.e(TAG, "Exception was null!");
                        }
                    }
                }
            })
            .addOnSuccessListener(new OnSuccessListener<Void>() {
                @Override
                public void onSuccess(Void aVoid) {
                    Log.d(TAG, "In OnSuccessListener after attempting to request location updates.");

                }
            });
        //We should never catch an exception here because we've just gotten through checking permissions.
        } catch (SecurityException e){
            Log.i(TAG, "SecurityException thrown when trying to start location updates:" + e.toString());
        }
    }

    void stopLocationUpdates() {

        Log.i(TAG, "Removing Location updates.");
        try{
            mFusedLocationClient.removeLocationUpdates(mPi);
            //Cancel the PendingIntent because we no longer need it to broadcast
            //location updates and calling RunManager.getPendingIntent(context, false)
            //will return null to indicate that no Runs are being tracked.
            if (mPi != null){
                mPi.cancel();
            }
            stopSelf();
        } catch (SecurityException se) {
            Log.e(TAG, "Apparently lost location permission. Can't remove updates.");
        }
    }

    @Override
    public void onDestroy(){
        mServiceHandler.removeCallbacksAndMessages(null);
        stopLocationUpdates();
        Log.i(TAG, DateFormat.getDateTimeInstance().format(new Date()) + ": Stopped");
        super.onDestroy();
    }
}
