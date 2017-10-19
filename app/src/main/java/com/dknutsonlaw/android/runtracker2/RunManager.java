package com.dknutsonlaw.android.runtracker2;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
//import android.app.TaskStackBuilder;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
//import android.content.SharedPreferences;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.Cursor;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.support.annotation.NonNull;
//import android.support.v4.app.NotificationCompat;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.util.LongSparseArray;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
    //LongSparseArray to associate location objects with Bounds for use in displaying GoogleMap
    private static final LongSparseArray<WeakReference<LatLngBounds>> sBoundsMap =
                                                                    new LongSparseArray<>();
    //LongSparseArray to associate location objects with Points used to make Polyline on GoogleMap
    private static final LongSparseArray<WeakReference<List<LatLng>>> sPointsMap =
                                                                    new LongSparseArray<>();
    private static final LongSparseArray<Integer> sLocationCountMap = new LongSparseArray<>();
    //Executor for running database operations on background threads
    private static final ScheduledThreadPoolExecutor sExecutor = new ScheduledThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors() + 1);
    /*Handle for the recurring task of updating Ending Addresses; needed so task can be cancelled
     *when we're not tracking runs
     */
    private static ScheduledFuture<?> sScheduledFuture;
    private static RunDatabaseHelper sHelper = null;
    private static long sCurrentRunId;
    private ResultsReceiver mResultsReceiver;

    //The private constructor forces users to use RunManager.get(Context)
    private RunManager (Context appContext) {
        //Use the application context to avoid leaking activities
        sAppContext = appContext.getApplicationContext();
        sHelper = new RunDatabaseHelper(appContext);
    }

    public static RunManager get(Context c) {
        if (sRunManager == null) {
            sRunManager = new RunManager(c);
        }
        return sRunManager;
    }

    void startTrackingRun(long runId){
        /*Location updates get started from the CombinedFragment by binding to the
         *BackgroundLocationService and instructing it to start supplying the updates. This method
         *handles the other housekeeping associated with starting to track a run. We get here from
         *the CombinedFragment's Start Button.
         */
        //First, keep the RunId in a member variable.
        sCurrentRunId = runId;
        //Store it in shared preferences to make it available throughout the app.
        RunTracker2.getPrefs().edit().putLong(Constants.PREF_CURRENT_RUN_ID, runId).apply();
        Log.i(TAG, "Run " + runId + " saved in SharedPrefs as PREF_CURRENT_RUN_ID");
        /*Now that location updates have started, RunManager needs to listen for broadcasts directing
         *that End Address updates start or stop, so we register a BroadcastReceiver
         */
        mResultsReceiver = new ResultsReceiver();
        /*Filter to allow receipt of signals in broadcast receiver that End Address updates should
         *start or end.
         */
        IntentFilter intentFilter = new IntentFilter(Constants.ACTION_START_UPDATING_END_ADDRESS);
        intentFilter.addAction(Constants.ACTION_STOP_UPDATING_END_ADDRESS);
        LocalBroadcastManager.getInstance(sAppContext).registerReceiver(mResultsReceiver, intentFilter);
    }
    /*When starting location updates, call with shouldCreate true, so the PendingIntent will be
     *returned; when calling just to check if any Run is being tracked, call with shouldCreate
     *false; if we've created the PendingIntent to start location updates, this will return the
     *existing PendingIntent, but if not, this will not create the PendingIntent, but rather return
     *null, thus indicating that no Run is being tracked because there are no location updates.
     */
    static PendingIntent getLocationPendingIntent(@NonNull Context context, boolean shouldCreate) {
        Intent broadcast = new Intent(Constants.ACTION_LOCATION);
        broadcast.setClass(RunTracker2.getInstance(), TrackingLocationReceiver.class);
        int flags = shouldCreate ? 0 : PendingIntent.FLAG_NO_CREATE;
        return PendingIntent.getBroadcast(context, 0, broadcast, flags);
    }
    /*The logic in this method moved to onSuccessListener()of removeLocationUpdates()in
     *stopLocationUpdates method of BackgroundLocationService. If no Run is being tracked, no
     *broadcast intents intended for the RunManager will be sent, so we can unregister the
     *broadcast receiver.
     */
    private void stopTrackingRun(){
        Log.i(TAG, "Entered stopRun() in RunManager");
        LocalBroadcastManager.getInstance(sAppContext).unregisterReceiver(mResultsReceiver);
    }

    //Get a Run from the database using its RunId
    static Run getRun(long id) {
        Run run = null;
        Cursor cursor = sAppContext.getContentResolver().query(
                        Uri.withAppendedPath(Constants.URI_TABLE_RUN, String.valueOf(id)),
                        null,
                        Constants.COLUMN_RUN_ID + " = ?",
                        new String[]{String.valueOf(id)},
                        null);
        if (cursor != null) {
            cursor.moveToFirst();
            //If you got a row, get a run
            if (!cursor.isAfterLast())
                run = RunDatabaseHelper.getRun(cursor);
            cursor.close();
        }
        return run;
    }

    static long getCurrentRunId (){
        return sCurrentRunId;
    }

    /*Insert a new Location into the database relating to the CurrentRun using the an
     *insertLocationTask and Executor service to do so off the main, UI thread.
     */
    void insertLocation(Location loc) {
        Log.d(TAG, "In RunManager insertLocation(), sCurrentRunId is: " + sCurrentRunId);
        if (sCurrentRunId != -1) {
            sExecutor.execute(new insertLocationTask(sCurrentRunId, loc));
        } else {
            Log.e(TAG, "Location received with no tracking run; ignoring.");
        }
    }

    //Return the starting (i.e., first) location object recorded for the given Run
    static Location getStartLocationForRun(long runId) {
        Location location = null;
        Cursor cursor = sAppContext.getContentResolver().query(
                Uri.withAppendedPath(Constants.URI_TABLE_LOCATION, String.valueOf(runId)),
                null,
                Constants.COLUMN_LOCATION_RUN_ID + " = ?",
                new String[]{String.valueOf(runId)},
                Constants.COLUMN_LOCATION_TIMESTAMP + " asc"
        );
        if (cursor != null) {
            cursor.moveToFirst();
            //If you got a row, get a location
            if (!cursor.isAfterLast())
                location = RunDatabaseHelper.getLocation(cursor);
            cursor.close();
        }
        return location;
    }
    //Return the latest location object recorded for the given Run.
    static Location getLastLocationForRun(long runId) {
        Location location = null;
        Cursor cursor = sAppContext.getContentResolver().query(
                Uri.withAppendedPath(Constants.URI_TABLE_LOCATION, String.valueOf(runId)),
                null,
                Constants.COLUMN_LOCATION_RUN_ID + " = ?",
                new String[]{String.valueOf(runId)},
                Constants.COLUMN_LOCATION_TIMESTAMP + " desc"
        );
        if (cursor != null) {
            cursor.moveToFirst();
            //If you got a row, get a location
            if (!cursor.isAfterLast())
                location = RunDatabaseHelper.getLocation(cursor);
            cursor.close();
        }
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

    //Save the SparseArray associating a given Run with its LocationCount
    static void saveLocationCount(Long runId, int locationCount){
        sLocationCountMap.put(runId, locationCount);
    }

    //Retrieve the LocationCount for particular Run
    static int getLocationCount(Long runId){
        return sLocationCountMap.get(runId, 0);
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
        //Get a geocoder from Google Play Services and use its output to build an address string or
        //an error message, depending upon result.
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
                //The geocoder will return a list of addresses. We want only one, hence the final
                //argument to this method.
                List<Address> addresses = geocoder.getFromLocation(
                        loc.latitude, loc.longitude, 1);
                //Any address result will be a List element, even though we're getting only a single
                //result
                if (addresses != null && addresses.size() > 0){
                    Address address = addresses.get(0);
                    ArrayList<String> addressFragments = new ArrayList<>();
                    /*Convert address to a single string with line separators to divide elements of
                     *the address
                     */
                    for (int i = 0; i <= address.getMaxAddressLineIndex(); i++){
                        addressFragments.add(address.getAddressLine(i));
                    }
                    filterAddress = TextUtils.join(System.getProperty("line separator"),
                                                                    addressFragments);
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
    /*//Check to see if we got a useful value when trying to look up an address. If the address is
     *"bad" we can check again for the address for a given LatLng
     */
    static boolean addressBad(Context context, String address){
        Resources r = context.getResources();

        return  address.compareToIgnoreCase("") == 0 ||
                address.compareToIgnoreCase(r.getString(R.string.geocoder_io_error)) == 0 ||
                address.compareToIgnoreCase(r.getString(R.string.geocoder_bad_argument_error)) == 0 ||
                address.compareToIgnoreCase(r.getString(R.string.lastlocation_null)) == 0 ||
                address.compareToIgnoreCase(r.getString(R.string.get_address_function_unavailable)) == 0;
    }
    /*Are we tracking ANY Run? Note that getLocationPendingIntent(boolean) in this class is used by
     *the BackgroundLocationService to get the PendingIntent used to start and stop location updates.
     *If the call to getLocationPendingIntent returns null, we know that location updates have not
     *been started and no Run is being tracked.
     */
    static boolean isTrackingRun(){
        return getLocationPendingIntent(sAppContext, false) != null;
    }
    //Are we tracking the specified Run?
    static boolean isTrackingRun(Run run) {
        return (isTrackingRun() && run != null && run.getId() == sCurrentRunId);
    }
    //Format output of distance values depending upon whether we're using Metric or Imperial measures.
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
    //Format output of altitude values depending upon whether we're using Metric or Imperial measures.
    static String formatAltitude(double meters){
        boolean system = RunTracker2.getPrefs()
                .getBoolean(Constants.MEASUREMENT_SYSTEM, Constants.IMPERIAL);
        String result;
        if (system == Constants.METRIC){
            result = String.format(Locale.US, "%.0f", meters) + " meters";
        } else {
            result = String.format(Locale.US, "%.0f",
                    meters * Constants.METERS_TO_FEET) + " feet";
        }
        return result;
    }
    /*This method converts the +- decimal degrees formatted locations produced by Location Services
     *to N/S/E/W degrees/minutes/seconds format with the conventional symbols for degrees, minutes
     *and seconds.
     */
    static String convertLocation(double latitude, double longitude){
        StringBuilder builder = new StringBuilder();
        //Unicode for the little circle degree symbol
        char degree = 0x00B0;
        /*First format the latitude value. Android assigns positive values to northern hemisphere
         *latitudes and negative values to the southern hemisphere, so assign letters accordingly.
         */
        if (latitude < 0){
            builder.append("S ");
        } else {
            builder.append("N ");
        }
        /*First strip the negative/positive designation and then get degrees/minutes/seconds colon-
         *separated format Android makes available in its Location API..
         */
        String latitudeDegrees = Location.convert(Math.abs(latitude), Location.FORMAT_SECONDS);
        //Separate the degrees, minutes and seconds values and add the appropriate symbols.
        String[] latitudeSplit = latitudeDegrees.split(":");
        builder.append(latitudeSplit[0]);
        builder.append(degree);
        builder.append(latitudeSplit[1]);
        builder.append("'");
        builder.append(latitudeSplit[2]);
        builder.append("\"");
        //Separate the latitude and longitude values
        builder.append(" ");
        /*Now convert the longitude value. Android assigns positive values to locations east of 0
         *degrees longitude and negative values to locations west of there, so assign symbols accordingly.
         */
        if(longitude < 0){
            builder.append("W ");
        } else {
            builder.append("E ");
        }
        //Repeat same process as used for the latitude figure.
        String longitudeDegrees = Location.convert(Math.abs(longitude), Location.FORMAT_SECONDS);
        String[] longitudeSplit = longitudeDegrees.split(":");

        builder.append(longitudeSplit[0]);
        builder.append(degree);
        builder.append(longitudeSplit[1]);
        builder.append("'");
        builder.append(longitudeSplit[2]);
        builder.append("\"");

        return builder.toString();
    }

    private class ResultsReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent){

            String result = intent.getAction();

            switch(result){
                case Constants.ACTION_START_UPDATING_END_ADDRESS:
                    //Start recurring task of updating Ending Address
                    sScheduledFuture = sExecutor.scheduleAtFixedRate(new updateEndAddressTask(
                                                                        getRun(sCurrentRunId)),
                            20,
                            10,
                            TimeUnit.SECONDS);
                    break;
                case Constants.ACTION_STOP_UPDATING_END_ADDRESS:
                    //Cancel the recurring updates when no longer tracking a Run.
                    if (sScheduledFuture != null){
                        sScheduledFuture.cancel(true);
                    }
                    stopTrackingRun();
            }
        }
    }
    /*Series of static methods to invoke Runnable tasks to be executed by
     *the  Executor service on different threads.
     */
    static void insertRun(){
        sExecutor.execute(new insertNewRunTask(new Run()));
    }

    static void updateStartDate(Run run){
        sExecutor.execute(new updateStartDateTask(run));
    }

    static void updateStartAddress(Run run){
        sExecutor.execute(new updateStartAddressTask(run));
    }

    static void updateEndAddress(Run run){
        sExecutor.execute(new updateEndAddressTask(run));
    }

    static void deleteRuns(ArrayList<Long> runIds){
        sExecutor.execute(new deleteRunsTask(runIds));
    }

    static void deleteRun(Long runId){
        sExecutor.execute(new deleteRunTask(runId));
    }
    /*The following private classes are Runnable tasks responsible for
     *doing all database accesses. They are executed by the Executor
     *service, so those tasks are all done on separate threads, not
     *UI thread.
     */
    private static class insertNewRunTask implements Runnable{

        private final Run mRun;

        insertNewRunTask(Run run){
            mRun = run;
        }
        @Override
        public void run(){
            /*Put the new Run's initial values into ContentValues and submit
             *them to the ContentResolver's insert method for the Run table..
             */
            ContentValues cv = new ContentValues();
            cv.put(Constants.COLUMN_RUN_START_DATE, mRun.getStartDate().getTime());
            cv.put(Constants.COLUMN_RUN_START_ADDRESS, mRun.getStartAddress());
            cv.put(Constants.COLUMN_RUN_END_ADDRESS, mRun.getEndAddress());
            cv.put(Constants.COLUMN_RUN_DISTANCE, mRun.getDistance());
            cv.put(Constants.COLUMN_RUN_DURATION, mRun.getDuration());
            /*The ContentResolver's insertion of the Run into the Run table
             *returns the row number into which it was inserted. That number
             *becomes the Run's ID number, which is placed into the last path
             *segment of the URI returned by the ContentProvider.
             */
            Uri runResultUri = RunTracker2.getInstance().getContentResolver()
                                        .insert(Constants.URI_TABLE_RUN, cv);
            //Construct a String describing the results of the operation.
            String stringRunId = "";
            try {
                stringRunId = runResultUri != null ? runResultUri.getLastPathSegment() : null;
            } catch (NullPointerException npe){
                Log.e(TAG, "Caught an NPE while extracting a path segment from a Uri");
            }
            /*Assign the Run its ID if the operation was successful and notify the ContentResolver
             *that the Run table has been changed.
             */
            if (!stringRunId.equals("")) {
                long runId = Long.valueOf(runResultUri != null ?
                                                        runResultUri.getLastPathSegment() : null);
                mRun.setId(runId);
                RunTracker2.getInstance().getContentResolver()
                        .notifyChange(Constants.URI_TABLE_RUN, null);
            }

            /*Create an Intent with Extras to report the results of the operation. If the new Run
             *was created from the the RunRecyclerListFragment, the intent will return the Run to
             *the RunRecyclerListFragment, which will start the RunPagerActivity with the new Run's
             *RunId as an argument to set the current item for the ViewPager. If the new Run is
             *created from the RunPagerActivity, the intent will be returned to the RunPagerActivity
             *and the RunId will again be used to set the current item for the ViewPager. The
             *ViewPager will load the CombinedFragment for the new Run where the user can hit the
             *Start button to begin tracking the Run, which will start the loaders for the run and
             *set a Notification. The cursor loaders for the RunPagerActivity and the
             *RunRecyclerListFragment automatically update when the new Run is added to the Run
             *table in the database.
             */
            LocalBroadcastManager localBroadcastManager = LocalBroadcastManager
                                                            .getInstance(RunTracker2.getInstance());
            Intent responseIntent = new Intent(Constants.SEND_RESULT_ACTION)
                    .putExtra(Constants.ACTION_ATTEMPTED, Constants.ACTION_INSERT_RUN)
                    .putExtra(Constants.EXTENDED_RESULTS_DATA, mRun);
            boolean receiver = localBroadcastManager.sendBroadcast(responseIntent);
            if (!receiver)
                Log.i(TAG, "No receiver for Insert Run responseIntent!");
        }
    }

    private static class insertLocationTask implements Runnable{

        private final long mRunId;
        private final Location mLocation;

        insertLocationTask(long runId, Location location){
            mRunId = runId;
            mLocation = location;
        }
        @Override
        public void run(){

            if (mRunId == -1){
                //Don't insert a Location unless there's valid RunId to go with it.
                Log.d(TAG, "RunId is -1 in attempt to insert location");
                return;
            }
            Resources r = RunTracker2.getInstance().getResources();
            double distance;
            long duration;
            ContentValues cv = new ContentValues();
            Location oldLocation = null;
            //Construct a String to report the results of operation upon any failure.
            String resultString = "";
            StringBuilder builder = new StringBuilder(resultString);
            Run run = null;
            //Retrieve the Run specified in the method argument to make sure it's valid
            Cursor cursor = RunTracker2.getInstance().getContentResolver().query(Uri.withAppendedPath(Constants.URI_TABLE_RUN, String.valueOf(mRunId)),
                    null,
                    Constants.COLUMN_RUN_ID + " = ?",
                    new String[]{String.valueOf(mRunId)},
                    null);
            if (cursor != null) {
                cursor.moveToFirst();
                if (!cursor.isAfterLast()) {
                    run = RunDatabaseHelper.getRun(cursor);
                    if (run == null){
                        //If there's no Run with the ID supplied, don't insert the Location.
                        return;
                    }
                }
                cursor.close();
            } else {
                //A null cursor - no Run to associate this location with.
                return;
            }
            /*Retrieve list of locations for the designated Run in order to get last previous
             *location to determine whether the Run can be continued at this point and time.
             */
            cursor = RunTracker2.getInstance().getContentResolver().query(Uri.withAppendedPath(
                    Constants.URI_TABLE_LOCATION,
                    String.valueOf(mRunId)),
                    null,
                    Constants.COLUMN_LOCATION_RUN_ID + " = ?",
                    new String[]{String.valueOf(mRunId),},
                    Constants.COLUMN_LOCATION_TIMESTAMP + " desc"
            );
            if (cursor != null) {
                cursor.moveToFirst();
                if (!cursor.isAfterLast()) {
                    oldLocation = RunDatabaseHelper.getLocation(cursor);
                }
                cursor.close();
            }
            if (oldLocation != null){
                /*If the location is more than 100 meters distant from the last previous location
                 *and is more than 30 seconds more recent, the user is attempting to "continue" a
                 *Run from too distant a point. We need to check the time difference because
                 *sometimes in a moving vehicle the user can travel more than 100 meters before a
                 *location update gets processed, which would otherwise incorrectly terminate the
                 *Run.
                 */
                if (mLocation.distanceTo(oldLocation) > Constants.CONTINUATION_DISTANCE_LIMIT &&
                        (mLocation.getTime() - oldLocation.getTime() > Constants.CONTINUATION_TIME_LIMIT)){
                    Log.i(TAG, "Aborting Run " + mRunId + " for exceeding continuation distance limit.");
                    //Construct an error message and send it to the UI by broadcast intent.
                    builder.append(r.getString(R.string.current_location_too_distant));
                    resultString = builder.toString();
                    Intent responseIntent = new Intent(Constants.SEND_RESULT_ACTION)
                            .putExtra(Constants.ACTION_ATTEMPTED, Constants.ACTION_INSERT_LOCATION)
                            .putExtra(Constants.EXTENDED_RESULTS_DATA, resultString)
                            .putExtra(Constants.SHOULD_STOP, true);

                    //Broadcast the Intent so that the CombinedRunFragment UI can receive the result
                    LocalBroadcastManager localBroadcastManager = LocalBroadcastManager
                                                            .getInstance(RunTracker2.getInstance());
                    boolean receiver = localBroadcastManager.sendBroadcast(responseIntent);
                    if (!receiver) {
                        Log.i(TAG, "No receiver for Insert Location responseIntent!");
                    }
                    return;
                }
            } else {
                Log.i(TAG, "oldLocation for Run " + mRunId + " is null");
                /*If oldLocation is null, this is the first location entry for this run, so the
                 *"inappropriate continuation" situation is inapplicable.
                 */
            }


            /*Now that we know we have valid run, we can enter the new location in the Location
             *Table.
             */
            cv.put(Constants.COLUMN_LOCATION_LATITUDE, mLocation.getLatitude());
            cv.put(Constants.COLUMN_LOCATION_LONGITUDE, mLocation.getLongitude());
            cv.put(Constants.COLUMN_LOCATION_ALTITUDE, mLocation.getAltitude());
            cv.put(Constants.COLUMN_LOCATION_TIMESTAMP, mLocation.getTime());
            cv.put(Constants.COLUMN_LOCATION_PROVIDER, mLocation.getProvider());
            cv.put(Constants.COLUMN_LOCATION_RUN_ID, mRunId);

            Uri resultUri = RunTracker2.getInstance().getContentResolver()
                                                     .insert(Constants.URI_TABLE_LOCATION, cv);
            String locationResult = "";
            try {
                locationResult = resultUri != null ? resultUri.getLastPathSegment() : null;
            } catch(NullPointerException npe){
                Log.e(TAG, "Caught an NPE while trying to extract a path segment from a Uri");
            }
            if (!locationResult.equals("")) {
                //A -1 return from the ContentResolver means the operation failed - report that.
                if (Integer.parseInt(resultUri != null ? resultUri.getLastPathSegment() : null) == -1) {
                    builder.append(r.getString(R.string.location_insert_failed, mRunId));
                } else {
                    /*Upon successful insertion, notify the ContentResolver the Location table has
                     *changed.
                     */
                    RunTracker2.getInstance().getContentResolver()
                             .notifyChange(Constants.URI_TABLE_LOCATION, null);
                }
            }
            //With a valid new location, the Run's distance and duration can be updated.
            distance = run.getDistance();
            duration = run.getDuration();

            if (oldLocation != null) {
                /*This isn't the first location for this run, so calculate the increments of
                 *distance and time and add them to the cumulative total taken from the database.
                 */
                distance += mLocation.distanceTo(oldLocation);
                long timeDifference = (mLocation.getTime() - oldLocation.getTime());
                /*If it's been more than 30 seconds since the last location entry, the user must
                 *have hit the Stop button before and is now continuing the run. Rather than include
                 *all the time elapsed during the "interruption," keep the old Duration and add to
                 *that as the Run continues.
                 */
                if (timeDifference < Constants.CONTINUATION_TIME_LIMIT) {

                    duration += timeDifference;
                }
            }
            /*If oldLocation is null, this is the first location entry for this run, so we
             *just keep the initial 0.0 and 0 values for the run's Distance and Duration. Now insert
             *the Run's distance and duration values into the Run table.
             */
            ContentValues runCv = new ContentValues();
            runCv.put(Constants.COLUMN_RUN_DISTANCE, distance);
            runCv.put(Constants.COLUMN_RUN_DURATION, duration);
            int runResult = RunTracker2.getInstance().getContentResolver()
                    .update(Uri.withAppendedPath(Constants.URI_TABLE_RUN, String.valueOf(run.getId())),
                            runCv,
                            Constants.COLUMN_RUN_ID + " = ?",
                            new String[] {String.valueOf(mRunId)});
            //Report a failure and upon success, notify ContentResolver that the Run table has changed.
            if (runResult == -1){
                builder.append(r.getString(R.string.duration_and_distance_update_failure, mRunId));
            } else {
                RunTracker2.getInstance().getContentResolver()
                        .notifyChange(Constants.URI_TABLE_RUN, null);
            }
            resultString = builder.toString();
            if (!resultString.equals("")) {
                /*Create an Intent with Extras to report the results of the operation to the
                 *CombinedRunFragment UI and advise the user if there was an error. The
                 *CombinedRunFragment and RunRecyclerListFragment UIs get the new data fed to them
                 *automatically by loaders.
                 */
                Intent responseIntent = new Intent(Constants.SEND_RESULT_ACTION)
                        .putExtra(Constants.ACTION_ATTEMPTED, Constants.ACTION_INSERT_LOCATION)
                        .putExtra(Constants.EXTENDED_RESULTS_DATA, resultString)
                        .putExtra(Constants.SHOULD_STOP, false);
                //Broadcast the Intent so that the CombinedFragment UI can receive the result
                LocalBroadcastManager localBroadcastManager = LocalBroadcastManager
                                                            .getInstance(RunTracker2.getInstance());
                boolean receiver = localBroadcastManager.sendBroadcast(responseIntent);
                if (!receiver)
                    Log.i(TAG, "No receiver for Insert Location responseIntent!");
            }
        }
    }
    /*This Runnable task will update a Run's starting date when the first location update for this
     *Run is received to the Location's date and time. A StartAddress may be available also, so
     *check for that and update that field of the Run also.
     */
    private static class updateStartDateTask implements Runnable{

        final Run mRun;

        updateStartDateTask(Run run){
            mRun = run;
        }
        @Override
        public void run(){
            if (mRun == null){
                return;
            }
            //Load the new values in ContentValues and insert into this Run's record.
            ContentValues cv = new ContentValues();
            cv.put(Constants.COLUMN_RUN_START_DATE, mRun.getStartDate().getTime());
            cv.put(Constants.COLUMN_RUN_START_ADDRESS, mRun.getStartAddress());
            int result = RunTracker2.getInstance().getContentResolver()
                    .update(Uri.withAppendedPath(
                            Constants.URI_TABLE_RUN, String.valueOf(mRun.getId())),
                            cv,
                            Constants.COLUMN_RUN_ID + " = ?",
                            new String[]{String.valueOf(mRun.getId())});
            /*This operation should always update only one row of the Run table, so if result is
             *anything other than 1, report the result to the UI fragments.
             */
            if (result != 1) {
                /*Create an Intent with Extras to report the results of the operation to the
                 *CombinedFragment UI where the relevant loaders can be restarted.
                 *RunRecyclerListFragment relies on its cursor loader to get this data.
                 */
                Intent responseIntent = new Intent(Constants.SEND_RESULT_ACTION)
                        .putExtra(Constants.ACTION_ATTEMPTED, Constants.ACTION_UPDATE_START_DATE)
                        .putExtra(Constants.ARG_RUN_ID, mRun.getId())
                        .putExtra(Constants.EXTENDED_RESULTS_DATA, result);
                //Broadcast the Intent so that the UI can receive the result
                LocalBroadcastManager localBroadcastManager = LocalBroadcastManager
                                    .getInstance(RunTracker2.getInstance());
                boolean receiver = localBroadcastManager.sendBroadcast(responseIntent);
                if (!receiver)
                    Log.i(TAG, "No receiver for Update Start Date responseIntent!");
            } else {
                //Upon success, notify the ContentResolver that the Run table has changed.
                RunTracker2.getInstance().getContentResolver()
                        .notifyChange(Constants.URI_TABLE_RUN, null);
            }
        }
    }

    //If a bad or non-existent StartAddress got recorded the first around, this task will update it.
    private static class updateStartAddressTask implements Runnable{

        private final Run mRun;

        updateStartAddressTask(Run run){
            mRun = run;
        }
        @Override
        public void run(){

            //Get address from Geocoder for first location received for this Run.
            try {
                Location location = RunManager.getStartLocationForRun(mRun.getId());
                LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
                String startAddress = RunManager.getAddress(RunTracker2.getInstance(), latLng);
                //Update the current Run with the address we get
                mRun.setStartAddress(startAddress);
                //Update the database with the new start address
                ContentValues cv = new ContentValues();
                cv.put(Constants.COLUMN_RUN_START_ADDRESS, startAddress);
                int i = RunTracker2.getInstance().getContentResolver().update(
                        Uri.withAppendedPath(Constants.URI_TABLE_RUN, String.valueOf(mRun.getId())),
                        cv,
                        Constants.COLUMN_RUN_ID + " = ?",
                        new String[]{String.valueOf(mRun.getId())}
                );
                /*This operation should update only one row of the Run table, so i should be 1. If
                 *not, something went wrong, so report the error back to the UI fragments.
                 */
                if (i != 1) {
                    //Send the results of the update operation to the UI using a local broadcast
                    LocalBroadcastManager localBroadcastManager =
                            LocalBroadcastManager.getInstance(RunTracker2.getInstance());
                    Intent resultIntent = new Intent(Constants.SEND_RESULT_ACTION)
                            .putExtra(Constants.ACTION_ATTEMPTED,
                                    Constants.ACTION_UPDATE_START_ADDRESS)
                            .putExtra(Constants.EXTENDED_RESULTS_DATA, i)
                            .putExtra(Constants.UPDATED_ADDRESS_RESULT, startAddress);
                    boolean receiver = localBroadcastManager.sendBroadcast(resultIntent);
                    if (!receiver)
                        Log.i(TAG, "No receiver for StartAddressUpdate resultIntent!");
                } else {
                    //Upon success, notify ContentResolver that the Run table has changed.
                    RunTracker2.getInstance().getContentResolver()
                            .notifyChange(Constants.URI_TABLE_RUN, null);
                }
            } catch (NullPointerException npe){
                Log.i(TAG, "No Start Location available - updateStartAddressTask skipped");
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

    /*This Runnable task updates the Run's End Address periodically and when location updates
     *stopped.
     */
    static class updateEndAddressTask implements Runnable {

        private final Run mRun;

        updateEndAddressTask(Run run) {
            mRun = run;
        }
        @Override
        public void run() {
            Log.i(TAG, "In run() function for updateEndAddressTask in BackgroundLocationService for Run " + mRun.getId());

            //Get address from Geocoder for latest location received for this Run.
            try {
                Location location = RunManager.getLastLocationForRun(mRun.getId());
                LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
                String endAddress = RunManager.getAddress(RunTracker2.getInstance(), latLng);
                //Update the current run object with the address we get
                mRun.setEndAddress(endAddress);
                //Now save the new EndAddress to the Run's record the Run table.
                ContentValues cv = new ContentValues();
                cv.put(Constants.COLUMN_RUN_END_ADDRESS, endAddress);
                int i = RunTracker2.getInstance().getContentResolver().update(
                        Uri.withAppendedPath(Constants.URI_TABLE_RUN, String.valueOf(mRun.getId())),
                        cv,
                        Constants.COLUMN_RUN_ID + " = ?",
                        new String[]{String.valueOf(mRun.getId())}
                );
                /*This operation should update only one row of the Run table, so i should be 1. If
                 *not, something went wrong, so report the error back to the UI fragments.
                 */
                if (i != 1) {
                    //Send the results of the update operation to the UI using a local broadcast
                    LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(RunTracker2.getInstance());
                    Intent resultIntent = new Intent(Constants.SEND_RESULT_ACTION)
                            .putExtra(Constants.ACTION_ATTEMPTED,
                                    Constants.ACTION_UPDATE_END_ADDRESS)
                            .putExtra(Constants.EXTENDED_RESULTS_DATA, i)
                            .putExtra(Constants.UPDATED_ADDRESS_RESULT, endAddress);
                    boolean receiver = localBroadcastManager.sendBroadcast(resultIntent);
                    if (!receiver)
                        Log.i(TAG, "No receiver for EndAddressUpdate resultIntent!");
                } else {
                    //Upon success, notify ContentResolver that the Run table has changed.
                    RunTracker2.getInstance().getContentResolver()
                            .notifyChange(Constants.URI_TABLE_RUN, null);
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

    /*This Runnable task takes a list of Runs and deletes each of them.
     */
    private static class deleteRunsTask implements Runnable{

        private final ArrayList<Long> mRunIds;

        deleteRunsTask(ArrayList<Long> runIds){
            mRunIds = runIds;
        }
        @Override
        public void run(){
            //Keep track of number of Runs deleted.
            long runsDeleted = 0;
            //Keep track of number of Locations deleted.
            long locationsDeleted = 0;
            Resources r = RunTracker2.getInstance().getResources();
            //Create a String to report the results of the deletion operation.
            StringBuilder stringBuilder = new StringBuilder();
            /*We need to keep track of which Runs were successfully deleted and which were not so
             *results can be accurately reported. We must use a LinkedHashMap so that the results
             *will be recorded in the same order the Runs were processed.
             */
            LinkedHashMap<Long, Boolean> wasRunDeleted = new LinkedHashMap<>(mRunIds.size());
            //Iterate over all the items in the List selected for deletion.
            for (int i = 0; i < mRunIds.size(); i++) {
                //First, delete all the locations associated with a Run to be deleted.
                int deletedLocations = RunTracker2.getInstance().getContentResolver().delete(
                        Constants.URI_TABLE_LOCATION,
                        Constants.COLUMN_LOCATION_RUN_ID + " = ?",
                        new String[]{String.valueOf(mRunIds.get(i))}
                );
                /*Update total number of locations deleted and notify ContentResolver that the
                 *Location table has changed.
                 */
                if (deletedLocations >= 0) {
                    locationsDeleted += deletedLocations;
                    RunTracker2.getInstance().getContentResolver()
                            .notifyChange(Constants.URI_TABLE_LOCATION, null);
                }
                //After deleting its Locations, delete the selected Run
                int deletedRun = RunTracker2.getInstance().getContentResolver().delete(
                        Uri.withAppendedPath(Constants.URI_TABLE_RUN, String.valueOf(mRunIds.get(i))),
                        Constants.COLUMN_RUN_ID + " = ?",
                        new String[]{String.valueOf(mRunIds.get(i))}
                );
                /*Update number of Runs deleted and notify the ContentResolver that the Run table
                 *changed.
                 */
                if (deletedRun >= 0) {
                    runsDeleted += deletedRun;
                    RunTracker2.getInstance().getContentResolver()
                            .notifyChange(Constants.URI_TABLE_RUN, null);
                }
                if (deletedRun == 1) {
                    /*One Run deleted means success, to mark it as so in the LinkedHashMap and append
                     *the result to the report of the entire operation.
                     */
                    stringBuilder.append(r.getString(R.string.delete_run_success, mRunIds.get(i)));
                    wasRunDeleted.put(mRunIds.get(i), true);
                } else if (deletedRun == 0) {
                    /*Zero Runs deleted means failure to delete, so mark it as such in the
                     *LinkedHashMap and append the result to the report of the entire operation.
                     */
                    stringBuilder.append(r.getString(R.string.delete_run_failure, mRunIds.get(i)));
                    wasRunDeleted.put(mRunIds.get(i), false);
                } else if (deletedRun == -1) {
                    /*A -1 result means there was an error. Mark the deletion as a failure in the
                     *LinkedHashMap and add a description of the result to the report on results of
                     *the entire operation.
                     */
                    stringBuilder.append(r.getString(R.string.delete_run_error, mRunIds.get(i)));
                    wasRunDeleted.put(mRunIds.get(i), false);
                } else {
                    stringBuilder.append(r.getString(R.string.delete_run_unexpected_return, mRunIds.get(i)));
                    wasRunDeleted.put(mRunIds.get(i), false);
                }
                //Append report on deletion of locations for this Run to the report on the Run.
                if (deletedLocations == -1) {
                    stringBuilder.append(r.getString(
                                                R.string.delete_locations_error, mRunIds.get(i)));
                } else {
                    stringBuilder.append(r.getQuantityString(R.plurals.location_deletion_results,
                                                            deletedLocations,
                                                            deletedLocations,
                                                            mRunIds.get(i)));
                }
            }
            //Insert a total summary report sentence at the beginning of the entire report of results.
            stringBuilder.insert(0, r.getQuantityString(R.plurals.runs_deletion_results,
                                        (int)runsDeleted, (int)runsDeleted, locationsDeleted));
            //Now construct the report String and put in into a broadcast intent for the UI element.
            String resultString = stringBuilder.toString();
            /*Create an Intent with Extras to report the results of the operation. This Intent is
             *aimed at a different Activity/Fragment than the other broadcasts, the
             *RunRecyclerListFragment, so it has a different Action specified. All the others are
             *directed at the CombinedFragment or the RunManager. The RunRecyclerListFragment needs
             *to get this broadcast so it can call a floating dialog to display the results of the
             *delete operation. Its RecyclerView will update automatically by operation of its
             *cursor loader.
             */
            Intent responseIntent = new Intent(Constants.ACTION_DELETE_RUNS)
                    .putExtra(Constants.EXTENDED_RESULTS_DATA, resultString)
                    .putExtra(Constants.EXTRA_VIEW_HASHMAP, wasRunDeleted);
            //Broadcast the Intent so that the UI can receive the result
            LocalBroadcastManager localBroadcastManager =
                                    LocalBroadcastManager.getInstance(RunTracker2.getInstance());
            boolean receiver = localBroadcastManager.sendBroadcast(responseIntent);
            if (!receiver)
                Log.i(TAG, "No receiver for Delete Runs responseIntent!");
        }
    }

    /*This Runnable task deletes a single Run. It is used from the RunPagerActivity where only a
     *single Run at a time can be selected for deletion - the Run currently displayed by the ViewPager.
     */
    private static class deleteRunTask implements Runnable {

        private final long mRunId;

        deleteRunTask(long runId){
            mRunId = runId;
        }

        @Override
        public void run(){
            String resultsString;
            StringBuilder builder = new StringBuilder();
            //First delete the locations for the selected Run.
            int locationsDeleted = RunTracker2.getInstance().getContentResolver().delete(
                    Constants.URI_TABLE_LOCATION,
                    Constants.COLUMN_LOCATION_RUN_ID + " = ?",
                    new String[]{String.valueOf(mRunId)}
            );
            if (locationsDeleted == -1){
                builder.append("There was an error deleting locations associated with Run ")
                        .append(mRunId).append(".\n");
            } else if (locationsDeleted == 0){
                builder.append("There were no locations associated with Run ").append(mRunId)
                        .append(" to delete.\n");
            } else if (locationsDeleted > 0){
                //On success, notify the ContentResolver that the Location table has changed.
                RunTracker2.getInstance().getContentResolver()
                        .notifyChange(Constants.URI_TABLE_LOCATION, null);
                builder.append(locationsDeleted).append(" locations associated with Run ")
                        .append(mRunId).append(" were also deleted.\n");
            } else {
                builder.append("There was an unexpected result from the ContentProvider while " +
                        "attempting to delete locations for Run ").append(mRunId).append(".\n");
            }
            //Now delete the selected Run.
            int runsDeleted = RunTracker2.getInstance().getContentResolver().delete(
                    Uri.withAppendedPath(Constants.URI_TABLE_RUN, String.valueOf(mRunId)),
                    Constants.COLUMN_RUN_ID + " = ?",
                    new String[]{String.valueOf(mRunId)}
            );
            if (runsDeleted == -1){
                builder.insert(0, "There was an error attempting to delete Run " + mRunId + ".\n");
            } else if (runsDeleted == 0){
                builder.insert(0, "Failed to deleted Run " + mRunId + ".\n");
            } else if (runsDeleted == 1){
                RunTracker2.getInstance().getContentResolver()
                        .notifyChange(Constants.URI_TABLE_RUN, null);
                builder.insert(0, "Successfully deleted Run " + mRunId + ".\n");
            } else {
                builder.insert(0, "Unknown response from ContentProvider in attempting to delete Run " + mRunId + ".\n");
            }
            resultsString = builder.toString();

            Intent responseIntent = new Intent(Constants.ACTION_DELETE_RUN)
                    .putExtra(Constants.EXTENDED_RESULTS_DATA, resultsString)
                    /*Put the runId here so the CombinedFragment of the Run being deleted can know
                     *to call finish().
                     */
                    .putExtra(Constants.PARAM_RUN, mRunId);
            LocalBroadcastManager localBroadcastManager =
                                    LocalBroadcastManager.getInstance(RunTracker2.getInstance());
            boolean receiver = localBroadcastManager.sendBroadcast(responseIntent);
            if (!receiver)
                Log.i(TAG, "No receiver for Delete Run responseIntent!");
        }
    }
}

