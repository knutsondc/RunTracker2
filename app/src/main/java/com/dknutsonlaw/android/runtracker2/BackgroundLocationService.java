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
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
//import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.net.Uri;
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
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

//import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;
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
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("Convert2Lambda")
public class BackgroundLocationService extends Service {

   private static final String TAG = "location.service";

   private final IBinder mBinder = new LocationBinder();
   private FusedLocationProviderClient mFusedLocationClient;
   private LocationRequest mLocationRequest;
   private Handler mServiceHandler;
   private PendingIntent mPi;
   private static ScheduledThreadPoolExecutor sStpe = null;
   private static ScheduledFuture<?> sScheduledFuture = null;
   private boolean mBound  = false;

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
        /*This Service is started only when we're tracking a Run and the CombinedRun fragment
         *for that Run unbinds and goes into the background, so to keep the Service going at
         *full speed, it needs to be started and run as a foreground service. The calls to
         *startService() and startForeground() are in onUnBind(); if the CombinedFragment has
         *already been rebound to the Service, then we don't need to be a foreground service*/
        if (mBound) {
            stopForeground(true);
        }
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
            mBound = true;
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
        try {
            stopForeground(true);
            mBound = true;
        } catch (NullPointerException npe){
            Log.e(TAG, "Caught a null pointer trying to stopForeground");
        }
        super.onRebind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent){
        Log.i(TAG, "In onUnbind");
        //When the last CombinedFragment unbinds, the Service needs to be started.
        //The Service must then run as a foreground service, so we call startForeground()
        if (RunManager.isTrackingRun()) {
            Log.i(TAG, "In onUnBind(), starting the service.");
            //We don't use startForegroundService() because the CombinedFragment might rebind before we
            //get to call startForeground(), which will call cause a crash.
            /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
                startForegroundService(new Intent(this, BackgroundLocationService.class));
            } else {*/
                startService(new Intent(this, BackgroundLocationService.class));
            //}
            mBound = false;
            startForeground(Constants.NOTIFICATION_ID, createNotification(this));
        } else {
            Log.i(TAG, "Service is unbound, but not starting service - not tracking any Run");
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
            NotificationManager manager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
            try {
                //noinspection ConstantConditions
                manager.createNotificationChannel(channel);
            } catch (NullPointerException npe){
                Log.i(TAG, "Caught an NPE trying to create notification channel");
            }
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

    void startLocationUpdates(long runId) {
        //Location updates are given to the PendingIntent and broadcast with the
        //IntentFilter ACTION_LOCATION. TrackingLocationReceiver, which is statically
        //declared in AndroidManifest, receives those broadcasts and calls RunManager's
        //insertLocation() method.
        Log.i(TAG, "Reached startLocationUpdates() for Run " + runId);
        Run mRun = RunManager.getRun(runId);
        //Calling the getPendingIntent() method from RunManager each time we start or stop location
        //updates instead of assigning the PendingIntent to an instance variable seems to be required
        //to make sure that we use the same PendingIntent and thereby successfully stop location
        //updates.
        try {
            Log.i(TAG, "mPi null for Run " + runId +"? " + (mPi == null));
                    mFusedLocationClient.requestLocationUpdates(
                    mLocationRequest,
                    RunManager.getLocationPendingIntent(this, true)
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
                    Log.e(TAG, "requestLocationUpdates() for Run " + runId + " failed: " + failureMessage);
                    RunTracker2.setIsTrackingRun(false);
                }
            })
            .addOnCompleteListener(new OnCompleteListener<Void>() {
                @Override
                public void onComplete(@NonNull Task<Void> task) {
                    Log.i(TAG, task.toString());
                    if (task.isSuccessful()){
                        Log.i(TAG, "Completed request for Location updates for Run " + runId);
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
                    //Now that the request for location updates has succeeded, start calling task to update Ending
                    //Address
                    RunTracker2.setIsTrackingRun(true);
                    Log.i(TAG, "In OnSuccessListener after requesting location updates for Run " + runId);
                    try {
                        sStpe = new ScheduledThreadPoolExecutor(3);
                        Log.i(TAG, "Created new ScheduledThreadPoolExecutor" + sStpe + " for Run " + runId);
                        Log.i(TAG, "sStpe has " + sStpe.getActiveCount() + " active tasks");
                        sScheduledFuture = sStpe.scheduleAtFixedRate(new updateEndAddressTask(
                                BackgroundLocationService.this,
                                       mRun),
                                30,
                                10,
                                 TimeUnit.SECONDS);
                        Log.i(TAG, "Created ScheduledFuture " + sScheduledFuture +  " for Run " +runId);
                                /*+ RunManager.getRun(RunTracker2.getPrefs().getLong(Constants.PREF_CURRENT_RUN_ID, -1)));*/
                    } catch (RejectedExecutionException rJee){
                        Log.i(TAG, "Caught rejected execution exception");
                        Log.i(TAG, "Cause: " + rJee.getCause());
                        Log.i(TAG, "Message: " + rJee.getMessage());
                    }
                }
            });
        //We should never catch an exception here because we've just gotten through checking permissions.
        } catch (SecurityException e){
            Log.i(TAG, "SecurityException thrown when trying to start location updates:" + e.toString());
            RunTracker2.setIsTrackingRun(false);
        }
    }

    void stopLocationUpdates() {

        Log.i(TAG, "Removing Location updates.");

        try{
            //Calling the getPendingIntent() method from RunManager each time we start or stop location
            //updates instead of assigning the PendingIntent to an instance variable seems to be required
            //to make sure that we use the same PendingIntent and thereby successfully stop location
            //updates.
            mFusedLocationClient.removeLocationUpdates(RunManager.getLocationPendingIntent(this, true))
                    .addOnSuccessListener(new OnSuccessListener<Void>() {
                        @Override
                        public void onSuccess(Void aVoid) {
                            Log.i(TAG, "Successfully stopped location updates.");
                            //Cancel the PendingIntent because we no longer need it to broadcast
                            //location updates and calling RunManager.getPendingIntent(context, false)
                            //will return null to indicate that no Runs are being tracked.
                            RunTracker2.setIsTrackingRun(false);
                            if (RunManager.getLocationPendingIntent(BackgroundLocationService.this, false) != null){
                                RunManager.getLocationPendingIntent(BackgroundLocationService.this, false).cancel();
                            }
                            Log.d(TAG, "We canceled the PendingIntent. Is it null? " + (RunManager.getLocationPendingIntent(BackgroundLocationService.this, false) == null));
                            stopForeground(true);
                            NotificationManagerCompat.from(BackgroundLocationService.this).cancel(Constants.NOTIFICATION_ID);
                            RunTracker2.getPrefs().edit().remove(Constants.PREF_CURRENT_RUN_ID).apply();
                            //Stop the recurring task that does updates of the Ending Address.
                            Log.i(TAG, "mScheduledFuture null? " + (sScheduledFuture == null));
                            if (sScheduledFuture != null) {
                                sScheduledFuture.cancel(true);
                                Log.i(TAG, "Called .cancel(true) on ScheduledFuture " + sScheduledFuture);
                            }
                            stopSelf();
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Log.d(TAG, "Couldn't stop location updates!");
                            Toast.makeText(BackgroundLocationService.this,
                                "Couldn't stop location updates!!", Toast.LENGTH_LONG).show();
                            stopSelf();
                        }
                    });
        } catch (SecurityException se) {
            Log.e(TAG, "Apparently lost location permission. Can't remove updates.");
            RunTracker2.setIsTrackingRun(true);
        }

    }

    @Override
    public void onDestroy(){
        mServiceHandler.removeCallbacksAndMessages(null);
        Log.i(TAG, "mStpe null? " + (sStpe == null));
        if (sStpe != null) {
            List<Runnable> shutdownList = sStpe.shutdownNow();
            Log.i(TAG, "There were " + shutdownList.size() + " tasks queued when BackgroundLocationService was destroyed.");
            Log.i(TAG, "Called .shutdownNow() on ScheduledThreadPoolExecutor " + sStpe);
        }
        Log.i(TAG, DateFormat.getDateTimeInstance().format(new Date()) + ": Stopped");
        super.onDestroy();
    }

    private static class updateEndAddressTask implements Runnable {

        private final Context mContext;
        private final Run mRun;

        updateEndAddressTask(Context context, Run run) {
            mContext = context;
            mRun = run;
        }
        @Override
        public void run() {
            Log.i(TAG, "In run() function for updateEndAddressTask in RunManager for Run " + mRun.getId());

            //Get address for last location received from geocoder for this Run.
            try {
                Location location = RunManager.getLastLocationForRun(mRun.getId());
                LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
                String endAddress = RunManager.getAddress(mContext, latLng);
                Log.i(TAG, "End Address in updateEndAddressTask for Run " + mRun.getId() + " is: " + endAddress);
                //Update the current run object with the address we get
                mRun.setEndAddress(endAddress);
                ContentValues cv = new ContentValues();
                cv.put(Constants.COLUMN_RUN_END_ADDRESS, endAddress);
                //update the database with the new ending address
                int i = mContext.getContentResolver().update(
                        Uri.withAppendedPath(Constants.URI_TABLE_RUN, String.valueOf(mRun.getId())),
                        cv,
                        Constants.COLUMN_RUN_ID + " = ?",
                        new String[]{String.valueOf(mRun.getId())}
                );
                //This operation should update only one row of the Run table, so i should be 1. If
                //not, something went wrong, so report the error back to the UI fragments
                if (i != 1) {
                    //Send the results of the update operation to the UI using a local broadcast
                    LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(mContext);
                    Intent resultIntent = new Intent(Constants.SEND_RESULT_ACTION)
                            .putExtra(Constants.ACTION_ATTEMPTED,
                                    Constants.ACTION_UPDATE_END_ADDRESS)
                            .putExtra(Constants.EXTENDED_RESULTS_DATA, i)
                            .putExtra(Constants.UPDATED_ADDRESS_RESULT, endAddress);
                    boolean receiver = localBroadcastManager.sendBroadcast(resultIntent);
                    Log.i(TAG, "updateEndAddressTask failed!");
                    if (!receiver)
                        Log.i(TAG, "No receiver for EndAddressUpdate resultIntent!");
                } else {
                    Log.i(TAG, "Successfully updated End Address for Run " + mRun.getId());
                }
            } catch (NullPointerException npe){
                Log.i(TAG, "No Last Location available - updateEndAddressTask skipped");
            } catch (RuntimeException re){
                Log.i(TAG, "In run(), Runtime Exception!!!");
                re.printStackTrace();
            } catch (Exception e){
                Log.i(TAG, " in run(), Exception!!!");
                e.getCause();
                e.printStackTrace();
            }
        }
    }
}
