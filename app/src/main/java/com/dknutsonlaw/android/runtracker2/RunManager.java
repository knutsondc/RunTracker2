package com.dknutsonlaw.android.runtracker2;

import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.util.LongSparseArray;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by dck on 9/6/15. Basic set of methods for creating, updating and deleting Runs and their
 * constituent fields.
 */
public class RunManager {

    private static final String TAG = "RunManager";

    private static RunManager sRunManager;
    //private Context mAppContext;
    private static final LongSparseArray<WeakReference<LatLngBounds>> sBoundsMap = new LongSparseArray<>();
    //private static final Map<Long, WeakReference<LatLngBounds>> sBoundsMap = new HashMap<>();
    private static final LongSparseArray<WeakReference<List<LatLng>>> sPointsMap = new LongSparseArray<>();
    //private static final Map<Long, WeakReference<List<LatLng>>> sPointsMap = new HashMap<>();
    private final NotificationManagerCompat mNotificationManager;
    //Handle for the recurring task of updating Ending Addresses; needed so task can be cancelled
    //when we're not tracking runs
    private ScheduledFuture<?> mScheduledFuture;
    //private final ThreadPoolExecutor.CallerRunsPolicy mCrp = new ThreadPoolExecutor.CallerRunsPolicy();
    //private final ScheduledThreadPoolExecutor mStpe = new ScheduledThreadPoolExecutor(3, mCrp);
    private ScheduledThreadPoolExecutor mStpe;
    //mHelper is public so that TrackingLocationIntentService can access it
    final RunDatabaseHelper mHelper;
    final SharedPreferences mPrefs;
    private long mCurrentRunId;

    //The private constructor forces users to use RunManager.get(Context)
    private RunManager (Context appContext) {
        //mAppContext = appContext;
        /*mNotificationManager = (NotificationManager)mAppContext
                .getSystemService(Context.NOTIFICATION_SERVICE);*/
        /*Now using NotificationManagerCompat so Wear functionality can be accessed */
        ///mNotificationManager = NotificationManagerCompat.from(mAppContext);
        mNotificationManager = NotificationManagerCompat.from(appContext);
        //mHelper = new RunDatabaseHelper(mAppContext);
        mHelper = new RunDatabaseHelper(appContext);
        //mPrefs = mAppContext.getSharedPreferences(Constants.PREFS_FILE, Context.MODE_PRIVATE);
        mPrefs = appContext.getSharedPreferences(Constants.PREFS_FILE, Context.MODE_PRIVATE);
        mCurrentRunId = mPrefs.getLong(Constants.PREF_CURRENT_RUN_ID, -1);
    }

    public static RunManager get(Context c) {
        if (sRunManager == null) {
            //Use the application context to avoid leaking activities
            sRunManager = new RunManager(c.getApplicationContext());
        }
        return sRunManager;
    }

    /*public void startNewRun() {
        //Ask the Intent Service to insert a new Run into the database
        Log.i(TAG, "Reached RunManager.startNewRun()");
        TrackingLocationIntentService.startActionInsertRun(mAppContext, new Run());
    }*/

    //public void startTrackingRun(Run run) {
    void startTrackingRun(Context context, long runId){
        //Location updates get started from the RunFragment by starting the BackgroundLocationService
        //and instructing it to start supplying the updates. This method handles the other
        //housekeeping associated with starting to track a run.
        //We get here from the RunFragment's Start Button.
        //First, keep the RunId in a member variable.
        Log.i(TAG, "Reached RunManager.startTrackingRun()");
        mCurrentRunId = runId;
        //mCurrentRunId = run.getId();
        //Store it in shared preferences
        mPrefs.edit().putLong(Constants.PREF_CURRENT_RUN_ID, mCurrentRunId).apply();
        //Give the user a notice that a run is being tracked that will be available even
        //if the UI isn't visible.
        createNotification(context);
        //Set up a scheduled thread pool executor and a task to schedule on it for updating Ending
        //Addresses every 5 seconds. Initial call comes in 30 seconds to make sure a new run gets
        //properly initialized in time so the first call won't fail and suppress ALL subsequent
        //calls for the scheduled task.
    }

    void startUpdatingEndAddress(Context context){
        try {
            mStpe = new ScheduledThreadPoolExecutor(3, /* mCrp */ new ThreadPoolExecutor.CallerRunsPolicy());
            Log.i(TAG, "Created new ScheduledThreadPoolExecutor" + mStpe);
            mScheduledFuture = mStpe.scheduleAtFixedRate(new updateEndAddressTask(context, getRun(mCurrentRunId)), 15, 5, TimeUnit.SECONDS);
            Log.i(TAG, "Created ScheduledFuture " + mScheduledFuture);
        } catch (RejectedExecutionException rJee){
            Log.i(TAG, "Caught rejected execution exception");
            Log.i(TAG, "Cause: " + rJee.getCause());
            Log.i(TAG, "Message: " + rJee.getMessage());
            //mCrp.rejectedExecution(new updateEndAddressTask(getRun(mCurrentRunId)), mStpe);
        }
    }

    //When the user starts tracking a run, create a Notification that will stay around until the
    //user dismisses it, stops tracking the run, or deletes the run, even if all the UI elements
    //are off screen (or even destroyed)
    private void createNotification(Context context) {
        /*Notification.Builder builder =
                new Notification.Builder(mAppContext)
                        .setSmallIcon(android.R.drawable.ic_menu_report_image)
                        .setContentTitle(mAppContext.getString(R.string.notification_title))
                        .setContentText(mAppContext.getString(R.string.notification_text));*/
        //Switched to NotificationCompat.Builder for Android Wear functionality.
        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context)

                    .setSmallIcon(android.R.drawable.ic_menu_report_image)
                    .setContentTitle(context.getString(R.string.notification_title))
                    .setContentText(context.getString(R.string.notification_text));


        //Create an explicit Intent for RunPagerActivity - we'll tie this
        //to the run that's being monitored
        Intent runPagerActivityIntent = RunPagerActivity.newIntent(context,
                Constants.KEEP_EXISTING_SORT, mCurrentRunId);

        //The stack builder object will contain an artificial back stack for
        //RunPagerActivity so that when navigating back from the RunFragment we're
        //viewing we'll go to the RunRecyclerListFragment instead of the Home screen
        TaskStackBuilder stackBuilder =
                TaskStackBuilder.create(context)

                        //The artificial stack consists of everything defined in the manifest as a parent or parent
                        //of a parent of the Activity to which the notification will return us
                        .addNextIntentWithParentStack(runPagerActivityIntent);
        PendingIntent resultPendingIntent =
                stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(resultPendingIntent);
        mNotificationManager.notify(0, builder.build());
    }
    //When starting location updates, call with shouldCreate true, so the PendingIntent will be returned;
    //When calling just to check if any Run is being tracked, call with shouldCreate false; if we've created
    //the PendingIntent to start location updates, this will return the existing PendingIntent, but if not,
    //this will not create the PendingIntent, but rather return null.
    PendingIntent getLocationPendingIntent(@NonNull Context context, boolean shouldCreate) {
        Intent broadcast = new Intent(Constants.ACTION_LOCATION);
        int flags = shouldCreate ? 0 : PendingIntent.FLAG_NO_CREATE;
        return PendingIntent.getBroadcast(context, 0, broadcast, flags);
    }

    //public void stopRun(Run run) {
    void stopRun(long runId){
        Log.i(TAG, "Entered stopRun()");
        //Location updates get stopped from the RunFragment by instructing the BackgroundLocationService
        //to stop supplying updates. This method handles the rest of the housekeeping following
        //shutdown of location updates. First, check to see if we have a good EndAddress value and
        //update it if we don't.
        //TrackingLocationIntentService.checkEndAddress(mAppContext, getRun(runId), getLastLocationForRun(runId));
        //TrackingLocationIntentService.checkEndAddress(mAppContext, run, getLastLocationForRun(run.getId()));
        //We only use a single notification, so we can assume its id is 0 when we stop
        //tracking a run and should cancel the notification.
        mNotificationManager.cancel(0);
        mCurrentRunId = -1;
        mPrefs.edit().remove(Constants.PREF_CURRENT_RUN_ID).apply();
       /* if (addressBad(getRun(runId).getEndAddress())) {
            mHelper.updateEndAddress(mAppContext, getRun(runId));
        }*/
        //Stop the recurring task that does updates of the Ending Address.
        Log.i(TAG, "mScheduledFuture null? " + (mScheduledFuture == null));
        if (mScheduledFuture != null) {
            mScheduledFuture.cancel(false);
            Log.i(TAG, "Called .cancel() on ScheduledFuture " + mScheduledFuture);
        }
        Log.i(TAG, "mStpe null? " + (mStpe == null));
        if (mStpe != null) {
            mStpe.shutdown();
            Log.i(TAG, "Called .shutdown() on ScheduledThreadPoolExecutor " + mStpe);
        }
    }
    //Invoke various methods to update the database by invoking the Intent service, thereby
    //taking those tasks off the main, UI thread.
    /*public void updateRunStartDate(Run run) {
        TrackingLocationIntentService.startActionUpdateStartDate(mAppContext, run);
    }*/

    /*public void updateRunStartAddress(Run run, Location location){
        TrackingLocationIntentService.startActionUpdateStartAddress(mAppContext, run, location);
    }*/

// --Commented out by Inspection START (7/3/16 9:06 PM):
//    public void checkStartAddress(Run run, Location location){
//        TrackingLocationIntentService.startActionCheckStartAddress(mAppContext, run, location);
//    }
// --Commented out by Inspection STOP (7/3/16 9:06 PM)

    /*public void updateRunEndAddress(Run run, Location location){
        TrackingLocationIntentService.startActionUpdateEndAddress(mAppContext, run, location);
    }*/

// --Commented out by Inspection START (7/3/16 9:06 PM):
//    public void checkEndAddress(Run run, Location location){
//        TrackingLocationIntentService.checkEndAddress(mAppContext, run, location);
//    }
// --Commented out by Inspection STOP (7/3/16 9:06 PM)
    //Methods to construct different cursors from the database.
    RunDatabaseHelper.RunCursor queryForNoRuns() {
        return mHelper.queryForNoRuns();
    }

    RunDatabaseHelper.RunCursor queryRunsDateAsc() {
        return mHelper.queryRunsDateAsc();
    }

    RunDatabaseHelper.RunCursor queryRunsDateDesc() {
        return mHelper.queryRunsDateDesc();
    }

    RunDatabaseHelper.RunCursor queryRunsDistanceAsc() {
        return mHelper.queryRunsDistanceAsc();
    }

    RunDatabaseHelper.RunCursor queryRunsDistanceDesc() {
        return mHelper.queryRunsDistanceDesc();
    }

    RunDatabaseHelper.RunCursor queryRunsDurationAsc() {
        return mHelper.queryRunsDurationAsc();
    }

    RunDatabaseHelper.RunCursor queryRunsDurationDesc() {
        return mHelper.queryRunsDurationDesc();
    }

    RunDatabaseHelper.LocationCursor queryLastLocationForRun(long runId){
        return mHelper.queryLastLocationForRun(runId);
    }

    RunDatabaseHelper.RunCursor queryRun(long runId){
        return mHelper.queryRun(runId);
    }
    //Get a Run from the database using its RunId
    Run getRun(long id) {
        Run run = null;
        RunDatabaseHelper.RunCursor cursor = mHelper.queryRun(id);
        cursor.moveToFirst();
        //If you got a row, get a run
        if (!cursor.isAfterLast())
            run = cursor.getRun();
        cursor.close();
        return run;
    }
    //Insert a new Location into the database relating to the CurrentRun using the Intent service
    //to take this task off the main, UI thread
    void insertLocation(Context context, Location loc) {
        if (mCurrentRunId != -1) {
            //Pass along the Application Context to the Intent Service so it can
            //pass it to the Database Helper method so that it,  in turn, can call
            //ContentResolver.notifyChanged on the Location table.
            TrackingLocationIntentService.startActionInsertLocation(context, mCurrentRunId, loc);
        } else {
            Log.e(TAG, "Location received with no tracking run; ignoring.");
        }
    }

   /* public void deleteRuns(ArrayList<Long> runIds) {
        //We need to pass along a Context here so that the Intent Service can create
        //an Intent
        TrackingLocationIntentService.startActionDeleteRuns(mAppContext, runIds);
    }*/

    /*public void deleteRun(Long runId){
        //We need to pass along a Context here so that the Intent Service can create
        //an Intent
        TrackingLocationIntentService.startActionDeleteRun(mAppContext, runId);
    }*/

    //Return the starting (i.e., first) location object recorded for the given Run
    Location getStartLocationForRun(long runId) {
        Location location = null;
        RunDatabaseHelper.LocationCursor cursor = mHelper.queryFirstLocationForRun(runId);
        cursor.moveToFirst();
        //If you got a row, get a location
        if (!cursor.isAfterLast())
            location = cursor.getLocation();
        cursor.close();
        return location;
    }
    //Return the latest location object recorded for the given Run.
    Location getLastLocationForRun(long runId) {
        Location location = null;
        RunDatabaseHelper.LocationCursor cursor = mHelper.queryLastLocationForRun(runId);
        cursor.moveToFirst();
        //If you got a row, get a location
        if (!cursor.isAfterLast())
            location = cursor.getLocation();
        cursor.close();
        return location;
    }

    void saveBounds(Long runId, LatLngBounds bounds){
        sBoundsMap.put(runId, new WeakReference<>(bounds));
    }

    LatLngBounds retrieveBounds(Long runId){
        WeakReference<LatLngBounds> latLngBoundsWeakReference = sBoundsMap.get(runId);
        return latLngBoundsWeakReference.get();
    }

    void savePoints(Long runId, List<LatLng> points){
        sPointsMap.put(runId, new WeakReference<>(points));
    }

    List<LatLng> retrievePoints(Long runId){
        WeakReference<List<LatLng>> listWeakReference = sPointsMap.get(runId);
        return listWeakReference.get();
    }

    /*Function to return the street address of the nearest building to the LatLng object
     *passed in as an argument - used in the RunFragment and RunMapFragment UIs
     */
    String getAddress(Context context, LatLng loc){
        String filterAddress = "";
        Geocoder geocoder = new Geocoder(context);
        if (loc == null) {
            Log.i(TAG, "Location is null in geocoding getString()");
            filterAddress = context.getString(R.string.lastlocation_null);
        } else if (Geocoder.isPresent()){
            //need to check whether the getFromLocation() method is available
            try {
                List<Address> addresses = geocoder.getFromLocation(
                        loc.latitude, loc.longitude, 1);
                if (addresses.size() > 0){
                    for (int i = 0; i < addresses.get(0).getMaxAddressLineIndex(); i++)
                        filterAddress += addresses.get(0).getAddressLine(i) + "\n";
                }
                filterAddress  = filterAddress.substring(0, filterAddress.lastIndexOf("\n"));
            } catch (IOException ioe){
                Log.i(TAG, "IO error in geocoder.");
                filterAddress = context.getString(R.string.geocoder_io_error);
                ioe.printStackTrace();
            } catch (IllegalArgumentException iae){
                Log.i(TAG, "Bad latitude or longitude argument");
                filterAddress = context.getString(R.string.geocoder_bad_argument_error);
            }

        } else {
            Log.i(TAG, "getFromLocation() functionality missing.");
            filterAddress = context.getString(R.string.get_address_function_unavailable);
        }
        return filterAddress;
    }

    boolean addressBad(Context context, String address){
        Resources r = context.getResources();
        /*if (address.compareToIgnoreCase(r.getString(R.string.geocoder_io_error)) == 0){
            Log.i(TAG, "Address was geocoder_io_error");
            return true;
        } else if (address.compareToIgnoreCase("") == 0){
            Log.i(TAG, "Address is empty");
            return true;
        } else if (address.compareToIgnoreCase(r.getString(R.string.lastlocation_null)) == 0){
            Log.i(TAG, "Address is lastlocation_null");
            return true;
        } else if (address.compareToIgnoreCase(r.getString(R.string.get_address_function_unavailable)) == 0){
            Log.i(TAG, "Address is get_address_function_unavailable");
            return true;
        } else {
            Log.i(TAG, "Address is good");
            return false;
        }*/
        return  address.compareToIgnoreCase("") == 0 ||
                address.compareToIgnoreCase(r.getString(R.string.geocoder_io_error)) == 0 ||
                address.compareToIgnoreCase(r.getString(R.string.geocoder_bad_argument_error)) == 0 ||
                address.compareToIgnoreCase(r.getString(R.string.lastlocation_null)) == 0 ||
                address.compareToIgnoreCase(r.getString(R.string.get_address_function_unavailable)) == 0;
    }
    //Ask for a cursor holding all the Location objects recorded for the given Run
    RunDatabaseHelper.LocationCursor queryLocationsForRun(long runId) {
        return mHelper.queryLocationsForRun(runId);
    }
    //Ask for number of locations recorded for a given Run
    long getRunLocationCount(long runId){
        return mHelper.getRunLocationCount(runId);
    }
    //Are we tracking ANY Run?
    //Note that getLocationPendingIntent(boolean) in this class is used by the BackgroundLocationService
    //to get the PendingIntent used to start and stop location updates. If the call to getLocationPendingIntent
    //returns null, we know that location updates have not been started and no Run is being tracked.
    boolean isTrackingRun(@NonNull Context context) {
        return getLocationPendingIntent(context, false) != null;
    }
    //Are we tracking the specified Run?
    boolean isTrackingRun(Run run) {
        return run != null && run.getId() == mCurrentRunId;
    }

    //Set up task to update End Address field that can be submitted to the ScheduledThreadPoolExecutor.
    //We need to use a named class for the Runnable so that we can pass in the Run as a parameter
    //to be used in the run() method.

    private class updateEndAddressTask implements Runnable {

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
                LatLng latLng = new LatLng(getLastLocationForRun(mRun.getId()).getLatitude(),
                        getLastLocationForRun(mRun.getId()).getLongitude());
                String endAddress = getAddress(mContext, latLng);
                //Update the current run object with the address we get
                mRun.setEndAddress(endAddress);
                //update the database with the new ending address
                int i = mHelper.updateEndAddress(mContext, mRun);
                //Send the results of the update operation to the UI using a local broadcast
                LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(mContext);
                Intent resultIntent = new Intent(Constants.SEND_RESULT_ACTION)
                        .putExtra(Constants.ACTION_ATTEMPTED,
                                Constants.ACTION_UPDATE_END_ADDRESS)
                        .putExtra(Constants.EXTENDED_RESULTS_DATA, i)
                        .putExtra(Constants.UPDATED_ADDRESS_RESULT, endAddress);
                boolean receiver = localBroadcastManager.sendBroadcast(resultIntent);
                Log.i(TAG, "Successfully completed updateEndAddressTask");
                if (!receiver)
                    Log.i(TAG, "No receiver for EndAddressUpdate resultIntent!");
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

