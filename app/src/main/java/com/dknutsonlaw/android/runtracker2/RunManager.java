package com.dknutsonlaw.android.runtracker2;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
//import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
//import android.content.SharedPreferences;
import android.content.res.Resources;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.support.annotation.NonNull;
//import android.support.v4.app.NotificationCompat;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.util.LongSparseArray;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by dck on 9/6/15. Basic set of methods for creating, updating and deleting Runs and their
 * constituent fields.
 */
public class RunManager {

    private static final String TAG = "RunManager";
    @SuppressLint("StaticFieldLeak")
    private static Context sAppContext;
    @SuppressLint("StaticFieldLeak")
    private static RunManager sRunManager;
    //LongSparseArray to associate location objects with Bounds for use in constructing RunMapFragment
    private static final LongSparseArray<WeakReference<LatLngBounds>> sBoundsMap = new LongSparseArray<>();
    //LongSparseArray to associate location objects with Points used to  construct Polyline in RunMapFragment.
    private static final LongSparseArray<WeakReference<List<LatLng>>> sPointsMap = new LongSparseArray<>();
    private static NotificationManagerCompat sNotificationManager = null;
    //Handle for the recurring task of updating Ending Addresses; needed so task can be cancelled
    //when we're not tracking runs
    private static ScheduledFuture<?> sScheduledFuture;
    private static ScheduledThreadPoolExecutor sStpe  = null;
    private static RunDatabaseHelper sHelper = null;
    private static long sCurrentRunId;

    //The private constructor forces users to use RunManager.get(Context)
    private RunManager (Context appContext) {
        sAppContext = appContext.getApplicationContext();
        sNotificationManager = NotificationManagerCompat.from(appContext);
        sHelper = new RunDatabaseHelper(appContext);
        sCurrentRunId = RunTracker2.getPrefs().getLong(Constants.PREF_CURRENT_RUN_ID, -1);
    }

    public static RunManager get(Context c) {
        if (sRunManager == null) {
            //Use the application context to avoid leaking activities
            sRunManager = new RunManager(c);
        }
        return sRunManager;
    }

    //public void startTrackingRun(Run run) {
    static void startTrackingRun(Context context, long runId){
        //Location updates get started from the RunFragment by starting the BackgroundLocationService
        //and instructing it to start supplying the updates. This method handles the other
        //housekeeping associated with starting to track a run.We get here from the RunFragment's Start Button.
        //First, keep the RunId in a member variable.
        Log.i(TAG, "Reached RunManager.startTrackingRun()");
        sCurrentRunId = runId;
        //Store it in shared preferences
        RunTracker2.getPrefs().edit().putLong(Constants.PREF_CURRENT_RUN_ID, sCurrentRunId).apply();
        //Give the user a notice that a run is being tracked that will be available even
        //if the UI isn't visible.
        //createNotification(context);
        startUpdatingEndAddress(context);
    }

    //Set up a scheduled thread pool executor and a task to schedule on it for updating Ending
    //Addresses every 5 seconds. Initial call comes in 15 seconds to make sure a new run gets
    //properly initialized in time so the first call won't fail and suppress ALL subsequent
    //calls for the scheduled task.
    private static void startUpdatingEndAddress(Context context){
        Log.i(TAG, "Reached startUpdatingEndAddress() for Run " + sCurrentRunId);
        if (!isTrackingRun(getRun(sCurrentRunId))) {
            Log.i(TAG, "In startUpdatingEndAddress, returned because not tracking current run");
            return;
        }
        try {
            //Sometimes this method gets called multiple times on a single press of the Start Button,
            //so we need to check if the ScheduledThreadPoolExecutor already exists so we don't
            //create more than one - only one gets stopped in stopRun(), so updateEndAddressTask()
            //would go on indefinitely even though the Run is no longer being tracked.
            if (sStpe != null){
                Log.i(TAG, "Already created ScheduledThreadPoolExecutor - won't create another!");
                return;
            }
            sStpe = new ScheduledThreadPoolExecutor(3);
            Log.i(TAG, "Created new ScheduledThreadPoolExecutor" + sStpe + " for Run " + sCurrentRunId);
            sScheduledFuture = sStpe.scheduleAtFixedRate(new updateEndAddressTask(context, getRun(sCurrentRunId)), 20, 10, TimeUnit.SECONDS);
            Log.i(TAG, "Created ScheduledFuture " + sScheduledFuture +  " for Run " + sCurrentRunId);
        } catch (RejectedExecutionException rJee){
            Log.i(TAG, "Caught rejected execution exception");
            Log.i(TAG, "Cause: " + rJee.getCause());
            Log.i(TAG, "Message: " + rJee.getMessage());
        }
    }

    //When starting location updates, call with shouldCreate true, so the PendingIntent will be returned;
    //When calling just to check if any Run is being tracked, call with shouldCreate false; if we've created
    //the PendingIntent to start location updates, this will return the existing PendingIntent, but if not,
    //this will not create the PendingIntent, but rather return null.
    static PendingIntent getLocationPendingIntent(@NonNull Context context, boolean shouldCreate) {
        Intent broadcast = new Intent(Constants.ACTION_LOCATION);
        int flags = shouldCreate ? 0 : PendingIntent.FLAG_NO_CREATE;
        return PendingIntent.getBroadcast(context, 0, broadcast, flags);
    }

    static void stopRun(){
        Log.i(TAG, "Entered stopRun()");
        //Location updates get stopped from the RunFragment by instructing the BackgroundLocationService
        //to stop supplying updates. This method handles the rest of the housekeeping following
        //shutdown of location updates.

        //We only use a single notification, so we can assume its id is 0 when we stop
        //tracking a run and should cancel the notification.
        sNotificationManager.cancel(0);
        sCurrentRunId = -1;
        RunTracker2.getPrefs().edit().remove(Constants.PREF_CURRENT_RUN_ID).apply();
        //Stop the recurring task that does updates of the Ending Address.
        Log.i(TAG, "mScheduledFuture null? " + (sScheduledFuture == null));
        if (sScheduledFuture != null) {
            sScheduledFuture.cancel(true);
            Log.i(TAG, "Called .cancel(true) on ScheduledFuture " + sScheduledFuture);
        }
        Log.i(TAG, "mStpe null? " + (sStpe == null));
        if (sStpe != null) {
            List<Runnable> shutdownList = sStpe.shutdownNow();
            Log.i(TAG, "There were " + shutdownList.size() + " tasks queued when Stop was pressed.");
            Log.i(TAG, "Called .shutdownNow() on ScheduledThreadPoolExecutor " + sStpe);
        }
    }

    //Methods to construct different cursors from the database.
    static RunDatabaseHelper.RunCursor queryForNoRuns() {
        return sHelper.queryForNoRuns();
    }

    static RunDatabaseHelper.RunCursor queryRunsDateAsc() {
        return sHelper.queryRunsDateAsc();
    }

    static RunDatabaseHelper.RunCursor queryRunsDateDesc() {
        return sHelper.queryRunsDateDesc();
    }

    static RunDatabaseHelper.RunCursor queryRunsDistanceAsc() {
        return sHelper.queryRunsDistanceAsc();
    }

    static RunDatabaseHelper.RunCursor queryRunsDistanceDesc() {
        return sHelper.queryRunsDistanceDesc();
    }

    static RunDatabaseHelper.RunCursor queryRunsDurationAsc() {
        return sHelper.queryRunsDurationAsc();
    }

    static RunDatabaseHelper.RunCursor queryRunsDurationDesc() {
        return sHelper.queryRunsDurationDesc();
    }

    static RunDatabaseHelper.LocationCursor queryLastLocationForRun(long runId){
        return sHelper.queryLastLocationForRun(runId);
    }
    //Ask for a cursor holding all the Location objects recorded for the given Run
    static RunDatabaseHelper.LocationCursor queryLocationsForRun(long runId) {
        return sHelper.queryLocationsForRun(runId);
    }
    //Return a cursor with the data concerning a single Run
    static RunDatabaseHelper.RunCursor queryRun(long runId){
        return sHelper.queryRun(runId);
    }
    //Get a Run from the database using its RunId
    static Run getRun(long id) {
        Run run = null;
        RunDatabaseHelper.RunCursor cursor = sHelper.queryRun(id);
        cursor.moveToFirst();
        //If you got a row, get a run
        if (!cursor.isAfterLast())
            run = cursor.getRun();
        cursor.close();
        return run;
    }

    static long getCurrentRunId (){
        return sCurrentRunId;
    }

    static RunDatabaseHelper getHelper() {return sHelper; }
    //Insert a new Location into the database relating to the CurrentRun using the Intent service
    //to take this task off the main, UI thread
    static void insertLocation(Context context, Location loc) {
        if (sCurrentRunId != -1) {
            //Pass along the Application Context to the Intent Service so it can
            //pass it to the Database Helper method so that it,  in turn, can call
            //ContentResolver.notifyChanged on the Location table.
            TrackingLocationIntentService.startActionInsertLocation(context, sCurrentRunId, loc);
        } else {
            Log.e(TAG, "Location received with no tracking run; ignoring.");
        }
    }

    //Return the starting (i.e., first) location object recorded for the given Run
    static Location getStartLocationForRun(long runId) {
        Location location = null;
        RunDatabaseHelper.LocationCursor cursor = sHelper.queryFirstLocationForRun(runId);
        cursor.moveToFirst();
        //If you got a row, get a location
        if (!cursor.isAfterLast())
            location = cursor.getLocation();
        cursor.close();
        return location;
    }
    //Return the latest location object recorded for the given Run.
    static Location getLastLocationForRun(long runId) {
        Location location = null;
        RunDatabaseHelper.LocationCursor cursor = sHelper.queryLastLocationForRun(runId);
        cursor.moveToFirst();
        //If you got a row, get a location
        if (!cursor.isAfterLast())
            location = cursor.getLocation();
        cursor.close();
        return location;
    }
    //Save the SparseArray associating a given Run with the Bounds needed to display it.
    static void saveBounds(Long runId, LatLngBounds bounds){
        sBoundsMap.put(runId, new WeakReference<>(bounds));
    }
    //Get the the Bounds for a particular Run
    static LatLngBounds retrieveBounds(Long runId){
        WeakReference<LatLngBounds> latLngBoundsWeakReference = sBoundsMap.get(runId);
        if (latLngBoundsWeakReference != null) {
            return latLngBoundsWeakReference.get();
        } else {
            return null;
        }
    }
    //Save the SparseArray associating a Run with the locations, expressed as LatLngs, for that Run
    static void savePoints(Long runId, List<LatLng> points){
        sPointsMap.put(runId, new WeakReference<>(points));
    }
    //Retrieve the list of locations, expressed as LatLngs, associated with a given Run
    @Nullable
    static List<LatLng> retrievePoints(Long runId){
        WeakReference<List<LatLng>> listWeakReference = sPointsMap.get(runId);
        if (listWeakReference != null) {
            return listWeakReference.get();
        } else {
            return null;
        }
    }

    /*Function to return the street address of the nearest building to the LatLng object
     *passed in as an argument - used in the CombinedFragment UI
     */
    static String getAddress(Context context, LatLng loc){
        Log.i(TAG, "Reached getAddress(Context, LatLng)");
        String filterAddress = "";
        Geocoder geocoder = new Geocoder(context);
        Log.i(TAG, "Geocoder is: " + geocoder);
        if (loc == null) {
            Log.i(TAG, "Location is null in geocoding getString()");
            filterAddress = context.getString(R.string.lastlocation_null);
        } else if (Geocoder.isPresent()){
            //need to check whether the getFromLocation() method is available
            Log.i(TAG, "Geocoder is present");
            try {
                List<Address> addresses = geocoder.getFromLocation(
                        loc.latitude, loc.longitude, 1);
                //Log.i(TAG, "addresses is: " + addresses);
                if (addresses.size() > 0){
                    Address address = addresses.get(0);
                    ArrayList<String> addressFragments = new ArrayList<>();
                    //Convert address to a single string with line separators to divide elements of
                    //the address
                    for (int i = 0; i <= address.getMaxAddressLineIndex(); i++){
                        addressFragments.add(address.getAddressLine(i));
                    }
                    filterAddress = TextUtils.join(System.getProperty("line separator"), addressFragments);
                } else {
                    Log.i(TAG, "Address is empty");
                }


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
    //Check to see if we got a useful value when trying to look up an address. If the address is
    //"bad" we can check again for the address for a given LatLng
    static boolean addressBad(Context context, String address){
        Resources r = context.getResources();

        return  address.compareToIgnoreCase("") == 0 ||
                address.compareToIgnoreCase(r.getString(R.string.geocoder_io_error)) == 0 ||
                address.compareToIgnoreCase(r.getString(R.string.geocoder_bad_argument_error)) == 0 ||
                address.compareToIgnoreCase(r.getString(R.string.lastlocation_null)) == 0 ||
                address.compareToIgnoreCase(r.getString(R.string.get_address_function_unavailable)) == 0;
    }
    //Are we tracking ANY Run? Note that getLocationPendingIntent(boolean) in this class is used by
    //the BackgroundLocationService to get the PendingIntent used to start and stop location updates.
    //If the call to getLocationPendingIntent returns null, we know that location updates have not
    //been started and no Run is being tracked.
    //boolean isTrackingRun(@NonNull Context context) {
    static boolean isTrackingRun(){
        return getLocationPendingIntent(sAppContext, false) != null;
    }
    //Are we tracking the specified Run?
    static boolean isTrackingRun(Run run) {
        return run != null && run.getId() == sCurrentRunId && isTrackingRun();
    }

    static String formatDistance(double meters){
        boolean system = RunTracker2.getPrefs().getBoolean(Constants.MEASUREMENT_SYSTEM, Constants.IMPERIAL);
        String result;
        if (system == Constants.METRIC){
            if (meters < 1000f){
                result = String.format(Locale.US, "%.0f", meters) + " meters";
            } else {
                result = String.format(Locale.US, "%.2f", (meters/1000)) + " kilometers";
            }
        } else {
            double feet = (meters * Constants.METERS_TO_FEET);
            if (feet < 5280.0f){
                result = String.format(Locale.US, "%.0f", feet) + " feet";
            } else {
                result = String.format(Locale.US, "%.2f", (meters * Constants.METERS_TO_MILES)) + " miles";
            }
        }
        return result;
    }

    static String formatAltitude(double meters){
        boolean system = RunTracker2.getPrefs().getBoolean(Constants.MEASUREMENT_SYSTEM, Constants.IMPERIAL);
        String result;
        if (system == Constants.METRIC){
            result = String.format(Locale.US, "%.0f", meters) + " meters";
        } else {
            result = String.format(Locale.US, "%.0f", meters * Constants.METERS_TO_FEET) + " feet";
        }
        return result;
    }

    //Set up task to update End Address field that can be submitted to the ScheduledThreadPoolExecutor.
    //We need to use a named class for the Runnable so that we can pass in the Run as a parameter
    //to be used in the run() method.

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
                LatLng latLng = new LatLng(getLastLocationForRun(mRun.getId()).getLatitude(),
                        getLastLocationForRun(mRun.getId()).getLongitude());
                String endAddress = getAddress(mContext, latLng);
                //Update the current run object with the address we get
                mRun.setEndAddress(endAddress);
                //update the database with the new ending address
                int i = sHelper.updateEndAddress(mContext, mRun);
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
                    Log.i(TAG, "Successfully completed updateEndAddressTask");
                    if (!receiver)
                        Log.i(TAG, "No receiver for EndAddressUpdate resultIntent!");
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

