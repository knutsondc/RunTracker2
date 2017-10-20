package com.dknutsonlaw.android.runtracker2;

/*
  Created by dck on 9/6/15. An {@link IntentService} subclass for handling database task requests asynchronously in
  a service on a separate handler thread.
 */
import android.app.IntentService;
import android.content.ContentValues;
import android.content.Intent;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;
import java.util.LinkedHashMap;

/** Created by dck on 2/11/2015
 * An {@link IntentService} subclass for handling database task requests asynchronously in
 * a service on a separate handler thread.
 *
 * 2/18/2015 - Finally implemented startActionInsertRun(), handleActionInsertRun() and related
 * changes to onHandleIntent().
 *
 * 8/14/2015 - Added reverse geocoding address function.
 *
 * 11/11/15 - Added checkEndAddress method
 */
@SuppressWarnings("unused")
public class TrackingLocationIntentService extends IntentService{
    private static final String TAG = "IntentService";

    //We use local broadcasts to transmit results of the IntentService's actions back
    //to the UI fragments.
    private final LocalBroadcastManager mLocalBroadcastManager = LocalBroadcastManager.getInstance(this);

    /* Public static convenience methods other classes can call to start this service
     * to perform any one of its tasks.
     */

    /* Starts this service to insert a newly-created Run into the database.
     * The Run's runId field gets set by the database's insert operation.
     */
    public static void startActionInsertRun(Context context, Run run) {
        Intent intent = new Intent(context, TrackingLocationIntentService.class);
        intent.setAction(Constants.ACTION_INSERT_RUN);
        intent.putExtra(Constants.PARAM_RUN, run);
        context.startService(intent);
    }

    /*
     *Starts this service to delete a single run. If the  service is already performing a
     *task this action will be queued.
     */

    public static void startActionDeleteRun(Context context, long runId){
        Intent intent = new Intent(context, TrackingLocationIntentService.class);
        intent.setAction(Constants.ACTION_DELETE_RUN);
        intent.putExtra(Constants.PARAM_RUN_IDS, runId);
        context.startService(intent);
    }

    /* Starts this service to delete multiple Runs selected from the RunRecyclerListFragment */

    public static void startActionDeleteRuns(Context context, ArrayList<Long> deleteList/*, ArrayList<Integer> viewsToDelete*/){
        Intent intent = new Intent(context, TrackingLocationIntentService.class);
        intent.setAction(Constants.ACTION_DELETE_RUNS);
        intent.putExtra(Constants.PARAM_RUN_IDS, deleteList);
        //intent.putIntegerArrayListExtra(Constants.VIEWS_TO_DELETE, viewsToDelete);
        context.startService(intent);
    }

    /* Starts this service to insert a new Location associated with the Run identified by
     * the runId parameter
     */
    public static void startActionInsertLocation(Context context, long runId, Location loc) {
        Intent intent = new Intent(context, TrackingLocationIntentService.class);
        intent.setAction(Constants.ACTION_INSERT_LOCATION);
        intent.putExtra(Constants.PARAM_RUN_IDS, runId);
        intent.putExtra(Constants.PARAM_LOCATION, loc);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    /* Starts this service to change the Start Date of the run to the time returned by the first
     * GPS location update from the time the user presses the Start Button, which seems a more
     * accurate measure.
     */

    public static void startActionUpdateStartDate(Context context, Run run) {
        Intent intent = new Intent(context, TrackingLocationIntentService.class);
        intent.setAction(Constants.ACTION_UPDATE_START_DATE);
        intent.putExtra(Constants.PARAM_RUN, run);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    /* Starts this service to change the Starting Address of the run to the address obtained from the
     * reverse geocoding function using the first location obtained after the user starts tracking
     * the run.
     */

    public static void startActionUpdateStartAddress(Context context, Run run, Location location) {
        Intent intent = new Intent(context, TrackingLocationIntentService.class);
        intent.setAction(Constants.ACTION_UPDATE_START_ADDRESS);
        intent.putExtra(Constants.PARAM_RUN, run);
        intent.putExtra(Constants.PARAM_LOCATION, location);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    /* Starts this service to change the Ending Address of the run to the address obtained from the
     * reverse geocoding function using the last location obtained while the user is tracking the run.
     * seconds. Largely replaced by the ScheduledThreadPoolExecutor calling UpdateEndAddressTask */

    public static void startActionUpdateEndAddress(Context context, Run run, Location location) {
        Intent intent = new Intent(context, TrackingLocationIntentService.class);
        intent.setAction(Constants.ACTION_UPDATE_END_ADDRESS);
        intent.putExtra(Constants.PARAM_RUN, run);
        intent.putExtra(Constants.PARAM_LOCATION, location);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public TrackingLocationIntentService() {
        super("TrackingLocationIntentService");
    }


    //onHandleIntent is always the initial entry point in an IntentService.
    @Override
    protected void onHandleIntent(Intent intent) {
        startForeground(Constants.NOTIFICATION_ID, BackgroundLocationService.getNotification());
        //startForeground(Constants.NOTIFICATION_ID, BackgroundLocationService.createNotification(this));
        //Dispatch Intents to different methods for processing, depending upon their Actions,
        if (intent != null) {
            final String action = intent.getAction();
            if (Constants.ACTION_INSERT_RUN.equals(action)) {
                final Run run = intent.getParcelableExtra(Constants.PARAM_RUN);
                handleActionInsertRun(run);
            } else if (Constants.ACTION_DELETE_RUN.equals(action)){
                long runId = intent.getLongExtra(Constants.PARAM_RUN_IDS, -1);
                handleActionDeleteRun(runId);
            } else if (Constants.ACTION_DELETE_RUNS.equals(action)) {
                @SuppressWarnings("unchecked") ArrayList<Long> runIds = (ArrayList<Long>)intent.getSerializableExtra(Constants.PARAM_RUN_IDS);
                //ArrayList<Integer> viewsToDelete = intent.getIntegerArrayListExtra(Constants.VIEWS_TO_DELETE);
                handleActionDeleteRuns(runIds/*, viewsToDelete*/);
            } else if (Constants.ACTION_INSERT_LOCATION.equals(action)) {
                final long runId = intent.getLongExtra(Constants.PARAM_RUN_IDS, -1);
                final Location loc = intent.getParcelableExtra(Constants.PARAM_LOCATION);
                handleActionInsertLocation(runId, loc);
            } else if (Constants.ACTION_UPDATE_START_DATE.equals(action)) {
                final Run run = intent.getParcelableExtra(Constants.PARAM_RUN);
                handleActionUpdateStartDate(run);
            } else if (Constants.ACTION_UPDATE_START_ADDRESS.equals(action)) {
                final Run run = intent.getParcelableExtra(Constants.PARAM_RUN);
                final Location location = intent.getParcelableExtra(Constants.PARAM_LOCATION);
                handleActionUpdateStartAddress(run, location);
            } else if (Constants.ACTION_UPDATE_END_ADDRESS.equals(action)) {
                final Run run = intent.getParcelableExtra(Constants.PARAM_RUN);
                final Location location = intent.getParcelableExtra(Constants.PARAM_LOCATION);
                handleActionUpdateEndAddress(run, location);
            } else {
                Log.d(TAG, "How'd you get here!?! Unknown Action type!");
            }
        }
    }

    /**
     * Call the DatabaseHelper's method to insert a new Run into the Run table on
     * the provided background thread.
     */
    @SuppressWarnings("ConstantConditions")
    private void handleActionInsertRun(Run run) {
        Log.i(TAG, "Reached handleActionInsertRun");
        ContentValues cv = new ContentValues();
        cv.put(Constants.COLUMN_RUN_START_DATE, run.getStartDate().getTime());
        cv.put(Constants.COLUMN_RUN_START_ADDRESS, run.getStartAddress());
        cv.put(Constants.COLUMN_RUN_END_ADDRESS, run.getEndAddress());
        cv.put(Constants.COLUMN_RUN_DISTANCE, run.getDistance());
        cv.put(Constants.COLUMN_RUN_DURATION, run.getDuration());
        Uri runResultUri = getContentResolver().insert(Constants.URI_TABLE_RUN, cv);
        String stringRunId = "";
        try {
            stringRunId = runResultUri != null ? runResultUri.getLastPathSegment() : null;
        } catch (NullPointerException npe){
            Log.e(TAG, "Caught an NPE while extracting a path segment from a Uri");
        }
        if (!stringRunId.equals("")) {
            long runId = Long.valueOf(runResultUri != null ? runResultUri.getLastPathSegment() : null);
            run.setId(runId);
        }

        //Create an Intent with Extras to report the results of the operation. If the new Run was
        //created from the the RunRecyclerListFragment, the intent will return the Run to the
        //RunRecyclerListFragment, which will start the RunPagerActivity with the new Run's
        //RunId as an argument to set the current item for the ViewPager. If the new Run is created
        //from the RunPagerActivity, the intent will be returned to the
        //RunPagerActivity and the RunId will again be used to set the current item for the
        //ViewPager. The ViewPager will load the CombinedFragment for the new Run where the user can hit
        //the Start button to begin tracking the Run, which will start the loaders for the run and
        //set a Notification. The cursor loaders for the RunPagerActivity and the
        //RunRecyclerListFragment automatically update when the new Run is added to the Run table in
        //the database.
        Intent responseIntent = new Intent(Constants.SEND_RESULT_ACTION)
                .putExtra(Constants.ACTION_ATTEMPTED, Constants.ACTION_INSERT_RUN)
                .putExtra(Constants.EXTENDED_RESULTS_DATA, run);
        boolean receiver = mLocalBroadcastManager.sendBroadcast(responseIntent);
        if (!receiver)
            Log.i(TAG, "No receiver for Insert Run responseIntent!");
        stopForeground(true);
    }

    /*
     * Handle action InsertLocation in the provided background thread with the provided runId
     * and location parameters
     */
    @SuppressWarnings("ConstantConditions")
    private void handleActionInsertLocation(long runId, Location location) {
        long viewRunId = RunTracker2.getPrefs().getLong(Constants.CURRENTLY_VIEWED_RUN, -1);
        if (runId != viewRunId || Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            startForeground(Constants.NOTIFICATION_ID, BackgroundLocationService.createNotification(this));
        }
        if (runId == -1){
            Log.d(TAG, "RunId is -1 in attempt to insert location");
            stopForeground(true);
            return;
        }
        Resources r = getResources();
        double distance;
        long duration;
        ContentValues cv = new ContentValues();
        Location oldLocation = null;
        String resultString = "";
        StringBuilder builder = new StringBuilder(resultString);
        Run run = null;
        //Retrieve the Run specified in the method argument to make sure it's valid
        Cursor cursor = getContentResolver().query(Uri.withAppendedPath(Constants.URI_TABLE_RUN, String.valueOf(runId)),
                null,
                Constants.COLUMN_RUN_ID + " = ?",
                new String[]{String.valueOf(runId)},
                null);
        if (cursor != null) {
            Log.i(TAG, "Run cursor is not null");
            cursor.moveToFirst();
            if (!cursor.isAfterLast()) {
                run = RunDatabaseHelper.getRun(cursor);
                Log.i(TAG, "Is run null? " + (run == null));
                if (run == null){
                    stopForeground(true);
                    return;
                }
            }
            cursor.close();
        } else {
            Log.i(TAG, "Run cursor was null");
            stopForeground(true);
            return;
        }
        //Retrieve list of locations for the designated Run in order to get last previous location
        //to determine whether the Run can be continued at this point and time
        cursor = getContentResolver().query(Uri.withAppendedPath(Constants.URI_TABLE_LOCATION, String.valueOf(runId)),
                                        null,
                                         Constants.COLUMN_LOCATION_RUN_ID + " = ?",
                                        new String[]{String.valueOf(runId),},
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
            //If the location is more than 100 meters distant from the last previous location and is
            //more than 30 seconds more recent, the user is attempting to "continue" a run from too
            //distant a point. We need to check the time difference because sometimes in a moving
            //vehicle the user can travel more than 100 meters before a location update gets
            //processed, which would otherwise incorrectly terminate the run.
            if (location.distanceTo(oldLocation) > Constants.CONTINUATION_DISTANCE_LIMIT &&
                    (location.getTime() - oldLocation.getTime() > Constants.CONTINUATION_TIME_LIMIT)){
                Log.i(TAG, "Aborting Run " + runId + " for exceeding continuation distance limit.");
                builder.append(r.getString(R.string.current_location_too_distant));
                resultString = builder.toString();
                Intent responseIntent = new Intent(Constants.SEND_RESULT_ACTION)
                        .putExtra(Constants.ACTION_ATTEMPTED, Constants.ACTION_INSERT_LOCATION)
                        .putExtra(Constants.EXTENDED_RESULTS_DATA, resultString)
                        .putExtra(Constants.SHOULD_STOP, true);

                //Broadcast the Intent so that the CombinedRunFragment UI can receive the result
                boolean receiver = mLocalBroadcastManager.sendBroadcast(responseIntent);
                if (!receiver) {
                    Log.i(TAG, "No receiver for Insert Location responseIntent!");
                }
                stopForeground(true);
                return;
            }
        } else {
            Log.i(TAG, "oldLocation for Run " + runId + " is null");
            //If oldLocation is null, this is the first location entry for this run, so the
            //"inappropriate continuation" situation is inapplicable.
        }

        if (run != null) {
            //Now that we know we have valid run, we can enter the new location in the Location Table.
            Log.i(TAG, "Now inserting location data in the ContentValues");
            cv.put(Constants.COLUMN_LOCATION_LATITUDE, location.getLatitude());
            cv.put(Constants.COLUMN_LOCATION_LONGITUDE, location.getLongitude());
            cv.put(Constants.COLUMN_LOCATION_ALTITUDE, location.getAltitude());
            cv.put(Constants.COLUMN_LOCATION_TIMESTAMP, location.getTime());
            cv.put(Constants.COLUMN_LOCATION_PROVIDER, location.getProvider());
            cv.put(Constants.COLUMN_LOCATION_RUN_ID, runId);
        } else {
            Log.d(TAG, "run in IntentService insertLocation is null!");
            stopForeground(true);
            return;
        }
        Log.d(TAG, "URI_TABLE_LOCATION is: " + Constants.URI_TABLE_LOCATION.toString());
        Uri resultUri = getContentResolver().insert(Constants.URI_TABLE_LOCATION, cv);
        String locationResult = "";
        try {
            locationResult = resultUri != null ? resultUri.getLastPathSegment() : null;
        } catch(NullPointerException npe){
            Log.e(TAG, "Caught an NPE while trying to extract a path segment from a Uri");
        }
        if (!locationResult.equals("")) {
            if (Integer.parseInt(resultUri != null ? resultUri.getLastPathSegment() : null) == -1) {
                builder.append(r.getString(R.string.location_insert_failed, runId));
            } else {
                getContentResolver().notifyChange(Constants.URI_TABLE_LOCATION, null);
            }
        }

        distance = run.getDistance();
        duration = run.getDuration();

        if (oldLocation != null) {
            //This isn't the first location for this run, so calculate the increments of distance
            //and time and add them to the cumulative total taken from the database
            distance += location.distanceTo(oldLocation);
            long timeDifference = (location.getTime() - oldLocation.getTime());
            //If it's been more than 30 seconds since the last location entry, the user must
            //have hit the Stop button before and is now continuing the run. Rather than include
            //all the time elapsed during the "interruption," keep the old Duration and add to
            //that as the Run continues..
            if (timeDifference < Constants.CONTINUATION_TIME_LIMIT) {

                duration += timeDifference;
            }
        } else {
            //If oldLocation is null, this is the first location entry for this run, so we
            //just keep the initial 0.0 and 0 values for the run's Distance and Duration
            Log.i(TAG, "oldLocation for Run " + runId + " is null");
        }
        ContentValues runCv = new ContentValues();
        runCv.put(Constants.COLUMN_RUN_DISTANCE, distance);
        runCv.put(Constants.COLUMN_RUN_DURATION, duration);
        Log.d(TAG, "URI for updating Run in IntentService insertLocation is " + Uri.withAppendedPath(Constants.URI_TABLE_RUN, String.valueOf(runId)).toString());

        int runResult = getContentResolver().update(Uri.withAppendedPath(Constants.URI_TABLE_RUN, String.valueOf(run.getId())),
                                                        runCv,
                                                        Constants.COLUMN_RUN_ID + " = ?",
                                                        new String[] {String.valueOf(runId)});

        if (runResult == -1){
            builder.append(r.getString(R.string.duration_and_distance_update_failure, runId));
        } else {
            getContentResolver().notifyChange(Constants.URI_TABLE_RUN, null);
        }
        resultString = builder.toString();
        if (!resultString.equals("")) {
            //Create an Intent with Extras to report the results of the operation to the CombinedRunFragment
            //UI and advise the user if there was an error. The CombinedRunFragment, RunRecyclerListFragment
            //and RunMapFragment UIs get the new data fed to them automatically by loaders.
            Intent responseIntent = new Intent(Constants.SEND_RESULT_ACTION)
                    .putExtra(Constants.ACTION_ATTEMPTED, Constants.ACTION_INSERT_LOCATION)
                    .putExtra(Constants.EXTENDED_RESULTS_DATA, resultString)
                    .putExtra(Constants.SHOULD_STOP, false);
            //Broadcast the Intent so that the CombinedRunFragment UI can receive the result
            boolean receiver = mLocalBroadcastManager.sendBroadcast(responseIntent);
            if (!receiver)
                Log.i(TAG, "No receiver for Insert Location responseIntent!");
        }
        stopForeground(true);
    }

    /*
     * Handle action UpdateStartDate in the provided background thread for Run provided in the
     * parameter
     */
    private void handleActionUpdateStartDate(Run run) {
        //Perform the update on the database and get the result
        //int result = RunManager.getHelper().updateRunStartDate(mRunManager.mAppContext, run);
        //int result = RunManager.getHelper().updateRunStartDate(this, run);
        if (run == null){
            stopForeground(true);
            return;
        }
        long viewRunId = RunTracker2.getPrefs().getLong(Constants.CURRENTLY_VIEWED_RUN, -1);
        /*if (run.getId() != viewRunId || Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            startForeground(Constants.NOTIFICATION_ID, BackgroundLocationService.createNotification(this));
        }*/
        ContentValues cv = new ContentValues();
        cv.put(Constants.COLUMN_RUN_START_DATE, run.getStartDate().getTime());
        cv.put(Constants.COLUMN_RUN_START_ADDRESS, run.getStartAddress());
        int result = getContentResolver().update(Uri.withAppendedPath(
                                            Constants.URI_TABLE_RUN, String.valueOf(run.getId())),
                                            cv,
                                            Constants.COLUMN_RUN_ID + " = ?",
                                            new String[]{String.valueOf(run.getId())});
        //This operation should always update only one row of the Run table, so if result is anything
        //other than 1, report the result to the UI fragments.
        if (result != 1) {
            //Create an Intent with Extras to report the results of the operation to the CombinedRunFragment
            //UI where the relevant loaders can be restarted. RunRecyclerListFragment relies on its cursor
            //loader to get this data.
            Intent responseIntent = new Intent(Constants.SEND_RESULT_ACTION)
                    .putExtra(Constants.ACTION_ATTEMPTED, Constants.ACTION_UPDATE_START_DATE)
                    .putExtra(Constants.ARG_RUN_ID, run.getId())
                    .putExtra(Constants.EXTENDED_RESULTS_DATA, result);
            //Broadcast the Intent so that the UI can receive the result
            boolean receiver = mLocalBroadcastManager.sendBroadcast(responseIntent);
            if (!receiver)
                Log.i(TAG, "No receiver for Update Start Date responseIntent!");
        }
        stopForeground(true);
    }

    /*
     * Handle action UpdateStartAddress in the background thread for the Run parameter using the
     * location parameter
     */
    private void handleActionUpdateStartAddress(Run run, Location location){
        if (run == null || location == null){
            Log.i(TAG, "Null value parameter passed into handleActionUpdateStartAddress()");
            stopForeground(true);
            return;
        }
        /*long viewRunId = RunTracker2.getPrefs().getLong(Constants.CURRENTLY_VIEWED_RUN, -1);
        if (run.getId() != viewRunId || Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            startForeground(Constants.NOTIFICATION_ID, BackgroundLocationService.createNotification(this));
        }*/
        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
        String startAddress = RunManager.getAddress(this, latLng);
        run.setStartAddress(startAddress);
        ContentValues cv = new ContentValues();
        cv.put(Constants.COLUMN_RUN_START_ADDRESS, startAddress);
        //Perform the update on the database and get the result
        int result = getContentResolver().update(
                                                Uri.withAppendedPath(Constants.URI_TABLE_RUN, String.valueOf(run.getId())),
                                                cv,
                                                Constants.COLUMN_RUN_ID + " = ?",
                                                new String[]{String.valueOf(run.getId())}
        );
        //This operation should only affect one row of the Run table, so report any result other
        //than 1 back to the UI fragments.
        if (result != 1) {
            //Create an Intent with Extras to report the results of the operation to the CombinedRunFragment
            //UI where the relevant loaders can be restarted. RunRecyclerListFragment relies on its cursor
            //loader to get this data.
            Intent responseIntent = new Intent(Constants.SEND_RESULT_ACTION)
                    .putExtra(Constants.ACTION_ATTEMPTED, Constants.ACTION_UPDATE_START_ADDRESS)
                    .putExtra(Constants.ARG_RUN_ID, run.getId())
                    .putExtra(Constants.EXTENDED_RESULTS_DATA, result)
                    .putExtra(Constants.UPDATED_ADDRESS_RESULT, startAddress);
            //Broadcast the Intent so that the UI can receive the result
            boolean receiver = mLocalBroadcastManager.sendBroadcast(responseIntent);
            if (!receiver)
                Log.i(TAG, "No receiver for Update Start Date responseIntent!");
        }
        stopForeground(true);
    }

    /*
     * Handle action UpdateEndAddress in the background thread for the Run parameter using the
     * location parameter
     */
    private void handleActionUpdateEndAddress(Run run, Location location){
        if (run == null || location == null){
            Log.i(TAG, "Null value parameter passed into handleActionUpdateEndAddress()");
            stopForeground(true);
            return;
        }
        /*long viewRunId = RunTracker2.getPrefs().getLong(Constants.CURRENTLY_VIEWED_RUN, -1);
        if (run.getId() != viewRunId || Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            startForeground(Constants.NOTIFICATION_ID, BackgroundLocationService.createNotification(this));
        }*/
        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
        String endAddress = RunManager.getAddress(this, latLng);
        run.setEndAddress(endAddress);
        ContentValues cv = new ContentValues();
        cv.put(Constants.COLUMN_RUN_END_ADDRESS, endAddress);
        //Perform the update on the database and get the result
        int result = getContentResolver().update(
                                                Uri.withAppendedPath(Constants.URI_TABLE_RUN, String.valueOf(run.getId())),
                                                cv,
                                                Constants.COLUMN_RUN_ID + " = ?",
                                                new String[]{String.valueOf(run.getId())}
        );
        //This operation should always affect only one row of the Run table, so report any result
        //other than 1 back to the UI fragments.
        if (result != 1) {
            //Create an Intent with Extras to report the results of the operation to the CombinedRunFragment
            //UI where the relevant loaders can be restarted. RunRecyclerListFragment relies on its cursor
            //loader to get this data.
            Intent responseIntent = new Intent(Constants.SEND_RESULT_ACTION)
                    .putExtra(Constants.ACTION_ATTEMPTED, Constants.ACTION_UPDATE_END_ADDRESS)
                    .putExtra(Constants.ARG_RUN_ID, run.getId())
                    .putExtra(Constants.EXTENDED_RESULTS_DATA, result)
                    .putExtra(Constants.UPDATED_ADDRESS_RESULT, endAddress);

            //Broadcast the Intent so that the UI can receive the result
            boolean receiver = mLocalBroadcastManager.sendBroadcast(responseIntent);
            if (!receiver)
                Log.i(TAG, "No receiver for Update End Date responseIntent!");
        }
        stopForeground(true);
    }

    /**
     * Handle action DeleteRuns in the provided background thread with the provided
     * parameter - ArrayList of runIds identifying the Runs to delete.
     */
    private void handleActionDeleteRuns(ArrayList<Long>runIds/*, ArrayList<Integer> viewsToDelete*/) {
        long runsDeleted = 0;
        //Keep track of number of Locations deleted
        long locationsDeleted = 0;
        Resources r = getResources();
        //Create a String to report the results of the deletion operation
        StringBuilder stringBuilder = new StringBuilder();
        LinkedHashMap<Long, Boolean> wasRunDeleted = new LinkedHashMap<>(runIds.size());
        //Iterate over all the items in the List selected for deletion
        for (int i = 0; i < runIds.size(); i++) {
            //First, delete all the locations associated with a Run to be deleted.
            int deletedLocations = getContentResolver().delete(
                    Constants.URI_TABLE_LOCATION,
                    Constants.COLUMN_LOCATION_RUN_ID + " = ?",
                    new String[]{String.valueOf(runIds.get(i))}
            );
            if (deletedLocations >= 0) {
                locationsDeleted += deletedLocations;
            }
            //After deleting its Locations, delete the selected Run
            int deletedRun = getContentResolver().delete(
                    Uri.withAppendedPath(Constants.URI_TABLE_RUN, String.valueOf(runIds.get(i))),
                    Constants.COLUMN_RUN_ID + " = ?",
                    new String[]{String.valueOf(runIds.get(i))}
            );
            if (deletedRun >= 0) {
                runsDeleted += deletedRun;
            }
            if (deletedRun == 1) {
                stringBuilder.append(r.getString(R.string.delete_run_success, runIds.get(i)));
                wasRunDeleted.put(runIds.get(i), true);
            } else if (deletedRun == 0) {
                stringBuilder.append(r.getString(R.string.delete_run_failure, runIds.get(i)));
                wasRunDeleted.put(runIds.get(i), false);
            } else if (deletedRun == -1) {
                stringBuilder.append(r.getString(R.string.delete_run_error, runIds.get(i)));
                wasRunDeleted.put(runIds.get(i), false);
            } else {
                stringBuilder.append(r.getString(R.string.delete_run_unexpected_return, runIds.get(i)));
                wasRunDeleted.put(runIds.get(i), false);
            }

            if (deletedLocations == -1) {
                stringBuilder.append(r.getString(R.string.delete_locations_error, runIds.get(i)));
            } else {
                stringBuilder.append(r.getQuantityString(R.plurals.location_deletion_results, deletedLocations, deletedLocations, runIds.get(i)))
;           }    /*if (deletedLocations == 1) {
                stringBuilder.append("One locations associated with Run ").append(runIds.get(i)).append(" was deleted.\n\n");
            } else if (deletedLocations == 0) {
                stringBuilder.append("No locations associated with Run ").append(runIds.get(i)).append(" were deleted.\n\n");
            } else if (deletedLocations == -1) {
                stringBuilder.append("There was an error attempting to delete locations associated with Run ").append(runIds.get(i)).append(".\n\n");
            } else {
                stringBuilder.append("Unrecognized return value while attempting to delete locations associated with Run ").append(runIds.get(i)).append(".\n\n");
            }*/
        }
        stringBuilder.insert(0, r.getQuantityString(R.plurals.runs_deletion_results, (int)runsDeleted, (int)runsDeleted, locationsDeleted));
        String resultString = stringBuilder.toString();
        //Create an Intent with Extras to report the results of the operation
        //This Intent is aimed at a different Activity/Fragment, the RunRecyclerListFragment,
        //so it has a different Action specified. All the others are directed at
        //the CombinedRunFragment. The RunRecyclerListFragment needs to get this broadcast so it can
        //display the results of the delete operation in a Toast; its RecyclerView will
        //update automatically by operation of its cursor loader.
        Intent responseIntent = new Intent(Constants.ACTION_DELETE_RUNS)
                .putExtra(Constants.EXTENDED_RESULTS_DATA, resultString)
                //.putExtra(Constants.EXTRA_VIEW_HASHMAP, shouldDeleteView);
                .putExtra(Constants.EXTRA_VIEW_HASHMAP, wasRunDeleted);
        //Broadcast the Intent so that the UI can receive the result
        boolean receiver = mLocalBroadcastManager.sendBroadcast(responseIntent);
        if (!receiver)
            Log.i(TAG, "No receiver for Delete Runs responseIntent!");
        stopForeground(true);
    }

    /*
     * Handle action DeleteRun in the provided background thread with the provided
     * parameter - a runId identifying a Run to delete
     */
    private void handleActionDeleteRun(long runId){

        String resultsString;
        StringBuilder builder = new StringBuilder();
        int locationsDeleted = getContentResolver().delete(
                Constants.URI_TABLE_LOCATION,
                Constants.COLUMN_LOCATION_RUN_ID + " = ?",
                new String[]{String.valueOf(runId)}
        );
        if (locationsDeleted == -1){
            builder.append("There was an error deleting locations associated with Run ").append(runId).append(".\n");
        } else if (locationsDeleted == 0){
            builder.append("There were no locations associated with Run ").append(runId).append(" to delete.\n");
        } else if (locationsDeleted > 0){
            builder.append(locationsDeleted).append(" locations associated with Run ").append(runId).append(" were also deleted.\n");
        } else {
            builder.append("There was an unexpected result from the ContentProvider while attempting to delete locations for Run ").append(runId).append(".\n");
        }

        int runsDeleted = getContentResolver().delete(
                Uri.withAppendedPath(Constants.URI_TABLE_RUN, String.valueOf(runId)),
                Constants.COLUMN_RUN_ID + " = ?",
                new String[]{String.valueOf(runId)}
        );
        if (runsDeleted == -1){
            builder.insert(0, "There was an error attempting to delete Run " + runId + ".\n");
        } else if (runsDeleted == 0){
            builder.insert(0, "Failed to deleted Run " + runId + ".\n");
        } else if (runsDeleted == 1){
            builder.insert(0, "Successfully deleted Run " + runId + ".\n");
        } else {
            builder.insert(0, "Unknown response from ContentProvider in attempting to delete Run " + runId + ".\n");
        }
        resultsString = builder.toString();

        Intent responseIntent = new Intent(Constants.ACTION_DELETE_RUN)
                .putExtra(Constants.EXTENDED_RESULTS_DATA, resultsString)
                //Put the runId here so the CombinedRunFragment of the Run being deleted can know to call
                //finish()
                .putExtra(Constants.PARAM_RUN, runId);
        boolean receiver = mLocalBroadcastManager.sendBroadcast(responseIntent);
        if (!receiver)
            Log.i(TAG, "No receiver for Delete Run responseIntent!");
        stopForeground(true);
    }
}
