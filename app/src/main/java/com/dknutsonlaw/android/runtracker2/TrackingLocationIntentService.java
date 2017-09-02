package com.dknutsonlaw.android.runtracker2;

/*
  Created by dck on 9/6/15. An {@link IntentService} subclass for handling database task requests asynchronously in
  a service on a separate handler thread.
 */
import android.app.IntentService;
import android.content.Intent;
import android.content.Context;
import android.location.Location;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;

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
public class TrackingLocationIntentService extends IntentService{
    private static final String TAG = "IntentService";

    //Fetch the singleton RunManager so we can use our one RunDatabaseHelper, which is a member
    //variable of the singleton RunManager, and RunManager's mAppContext member variable
    //private final RunManager mRunManager = RunManager.get(this);
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

    /* Starts this service to insert a new Location associated with the Run identified by
     * the runId parameter
     */
    public static void startActionInsertLocation(Context context, long runId, Location loc) {
        Intent intent = new Intent(context, TrackingLocationIntentService.class);
        intent.setAction(Constants.ACTION_INSERT_LOCATION);
        intent.putExtra(Constants.PARAM_RUN_IDS, runId);
        intent.putExtra(Constants.PARAM_LOCATION, loc);
        context.startService(intent);
    }

    /* Starts this service to change the Start Date of the run to the time returned by the first
     * GPS location update from the time the user presses the Start Button, which seems a more
     * accurate measure.
     */

    public static void startActionUpdateStartDate(Context context, Run run) {
        Intent intent = new Intent(context, TrackingLocationIntentService.class);
        intent.setAction(Constants.ACTION_UPDATE_START_DATE);
        intent.putExtra(Constants.PARAM_RUN, run);
        context.startService(intent);
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
        context.startService(intent);
    }

    /* Starts this service to change the Ending Address of the run to the address obtained from the
     * reverse geocoding function using the last location obtained while the user is tracking the run.
     * seconds. Largely replaced by the ScheduledThreadPoolExecutor calling UpdateEndAddressTask */

    public static void startActionUpdateEndAddress(Context context, Run run, Location location) {
        Intent intent = new Intent(context, TrackingLocationIntentService.class);
        intent.setAction(Constants.ACTION_UPDATE_END_ADDRESS);
        intent.putExtra(Constants.PARAM_RUN, run);
        intent.putExtra(Constants.PARAM_LOCATION, location);
        context.startService(intent);
    }

    public TrackingLocationIntentService() {
        super("TrackingLocationIntentService");
    }


    //onHandleIntent is always the initial entry point in an IntentService.
    @Override
    protected void onHandleIntent(Intent intent) {
        //Dispatch Intents to different methods for processing, depending upon their Actions,
        if (intent != null) {
            final String action = intent.getAction();
            if (Constants.ACTION_INSERT_RUN.equals(action)) {
                final Run run = intent.getParcelableExtra(Constants.PARAM_RUN);
                handleActionInsertRun(run);
            } else if (Constants.ACTION_DELETE_RUN.equals(action)){
                long runId = intent.getLongExtra(Constants.PARAM_RUN_IDS, -1);
                handleActionDeleteRun(runId);
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
    private void handleActionInsertRun(Run run) {
        Log.i(TAG, "Reached handleActionInsertRun");
        //Insert the newly-created Run using the RunManager and its Context.
        //long runId = RunManager.getHelper().insertRun(mRunManager.mAppContext, run);
        long runId = RunManager.getHelper().insertRun(this, run);
        //The database returns the Run's row number as a unique value for mRunId or -1 on error
        run.setId(runId);
        //Create an Intent with Extras to report the results of the operation. If the new Run was
        //created from the the RunRecyclerListFragment, the intent will return the Run to the
        //RunRecyclerListFragment, which will start the RunPagerActivity with the new Run's
        //RunId as an argument to set the current item for the ViewPager. If the new Run is created
        //from the RunPagerActivity or the RunMapPPagerActivity, the intent will be returned to the
        //RunPagerActivity and the RunId will again be used to set the current item for the
        //ViewPager. The ViewPager will load the RunFragment for the new Run where the user can hit
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
    }

    /*
     * Handle action InsertLocation in the provided background thread with the provided runId
     * and location parameters
     */
    private void handleActionInsertLocation(long runId, Location loc) {
        //Perform the Location insertion using the runId parameter as the _id field
        long result[] = RunManager.getHelper().insertLocation(this, runId, loc);
        //If there's been an error, send an intent with the results to the UI fragments so it can be
        //reported to the user.
        if (result[Constants.CONTINUATION_LIMIT_RESULT] == -1 ||
            result[Constants.RUN_UPDATE_RESULT] == -1 ||
            result[Constants.LOCATION_INSERTION_RESULT] == -1) {
            //Create an Intent with Extras to report the results of the operation to the CombinedRunFragment
            //UI and advise the user if there was an error. The CombinedRunFragment, RunRecyclerListFragment
            //and RunMapFragment UIs get the new data fed to them automatically by loaders.
            Intent responseIntent = new Intent(Constants.SEND_RESULT_ACTION)
                    .putExtra(Constants.ACTION_ATTEMPTED, Constants.ACTION_INSERT_LOCATION)
                    .putExtra(Constants.EXTENDED_RESULTS_DATA, result);
            //Broadcast the Intent so that the CombinedRunFragment UI can receive the result
            boolean receiver = mLocalBroadcastManager.sendBroadcast(responseIntent);
            if (!receiver)
                Log.i(TAG, "No receiver for Insert Location responseIntent!");
        }
    }

    /*
     * Handle action UpdateStartDate in the provided background thread for Run provided in the
     * parameter
     */
    private void handleActionUpdateStartDate(Run run) {
        //Perform the update on the database and get the result
        //int result = RunManager.getHelper().updateRunStartDate(mRunManager.mAppContext, run);
        int result = RunManager.getHelper().updateRunStartDate(this, run);
        //Log.i(TAG, "Result of UpdateStartDate: " + result);
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
    }

    /*
     * Handle action UpdateStartAddress in the background thread for the Run parameter using the
     * location parameter
     */
    private void handleActionUpdateStartAddress(Run run, Location location){
        if (run == null || location == null){
            Log.i(TAG, "Null value parameter passed into handleActionUpdateStartAddress()");
            return;
        }
        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
        String startAddress = RunManager.getAddress(this, latLng);
        run.setStartAddress(startAddress);
        //Perform the update on the database and get the result
        //int result = RunManager.getHelper().updateStartAddress(mRunManager.mAppContext, run);
        int result = RunManager.getHelper().updateStartAddress(this, run);
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
    }

    /*
     * Handle action checkStartAddress in the background thread for the run and location provided
     * in the parameters.
     */
    /*private void handleActionCheckStartAddress(Run run, Location location){
        //Get the Starting Address recorded in the database
        String recordedStartAddress = run.getStartAddress();
        //Get the address the geocoder returns for the starting location provided in the location
        //parameter
        if (location != null){
            LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
            String checkStartAddress  = mRunManager.getAddress(this, latLng);
            //If the two addresses aren't the same, update the Start Address in the database
            if (recordedStartAddress.compareTo(checkStartAddress) != 0){
                handleActionUpdateStartAddress(run, location);
            }
        }
    }*/

    /*
     * Handle action UpdateEndAddress in the background thread for the Run parameter using the
     * location parameter
     */
    private void handleActionUpdateEndAddress(Run run, Location location){
        if (run == null || location == null){
            Log.i(TAG, "Null value parameter passed into handleActionUpdateEndAddress()");
            return;
        }
        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
        String endAddress = RunManager.getAddress(this, latLng);
        run.setEndAddress(endAddress);
        //Perform the update on the database and get the result
        //int result = RunManager.getHelper().updateEndAddress(mRunManager.mAppContext, run);
        int result = RunManager.getHelper().updateEndAddress(this, run);
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
    }

    /*
     * Handle action CheckEndAddress in the provided background thread for the Run parameter
     * using the provided location parameter/
     */

    /*private void handleActionCheckEndAddress(Run run, Location location){
        //Get the End Address recorded in the database
        String recordedEndAddress = run.getEndAddress();
        //Get the address the geocoder returns for the end location supplied in the location parameter
        if (location != null) {
            LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
            String checkEndAddress = mRunManager.getAddress(this, latLng);
            //If the two addresses aren't identical, update the database with the new, correct value
            if (recordedEndAddress.compareTo(checkEndAddress) != 0) {
                handleActionUpdateEndAddress(run, location);
            }
        }
    }*/

    /**
     * Handle action DeleteRuns in the provided background thread with the provided
     * parameter - ArrayList of runIds identifying the Runs to delete.
     */
    /*private void handleActionDeleteRuns(ArrayList<Long>runIds) {
        //Delete the Runs identified in runIds
        //int results[] = RunManager.getHelper().deleteRuns(mRunManager.mAppContext, runIds);
        int results[] = RunManager.getHelper().deleteRuns(this, runIds);
        //Log.i(TAG, "results are " + results[1] + " runs deleted and "
        //        + results[0] + " locations deleted.");
        //Create an Intent with Extras to report the results of the operation
        //This Intent is aimed at a different Activity/Fragment, the RunRecyclerListFragment,
        //so it has a different Action specified. All the others are directed at
        //the CombinedRunFragment. The RunRecyclerListFragment needs to get this broadcast so it can
        //display the results of the delete operation in a Toast; its RecyclerView will
        //update automatically by operation of its cursor loader.
        Intent responseIntent = new Intent(Constants.ACTION_DELETE_RUNS)
                .putExtra(Constants.EXTENDED_RESULTS_DATA, results);
        //Broadcast the Intent so that the UI can receive the result
        boolean receiver = mLocalBroadcastManager.sendBroadcast(responseIntent);
        if (!receiver)
            Log.i(TAG, "No receiver for Delete Runs responseIntent!");
    }*/

    /*
     * Handle action DeleteRun in the provided background thread with the provided
     * parameter - a runId identifying a Run to delete
     */
    private void handleActionDeleteRun(long runId){
        //int results[] = RunManager.getHelper().deleteRun(mRunManager.mAppContext, runId);
        int results[] = RunManager.getHelper().deleteRun(this, runId);
        //Log.i(TAG, "results are " + results[1] + " runs deleted and " + results[0] +
        //            " locations deleted.");
        Intent responseIntent = new Intent(Constants.ACTION_DELETE_RUN)
                .putExtra(Constants.EXTENDED_RESULTS_DATA, results)
                //Put the runId here so the CombinedRunFragment of the Run being deleted can know to call
                //finish()
                .putExtra(Constants.PARAM_RUN, runId);
        boolean receiver = mLocalBroadcastManager.sendBroadcast(responseIntent);
        if (!receiver)
            Log.i(TAG, "No receiver for Delete Run responseIntent!");
    }
}
