package com.dknutsonlaw.android.runtracker2;

/*
  Created by dck on 9/6/15. The class that creates the database used to track Runs and their associated Locations and
  implements basic database CRUD functions needed to implement the program.
 */

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.location.Location;
import android.util.Log;

import java.util.ArrayList;
import java.util.Date;

/**
 * Created by dck on 1/14/15.
 * The class that creates the database used to track Runs and their associated Locations and
 * implements basic database CRUD functions needed to implement the program.
 *
 * 2/18/15 - Completed work on Intent Service so that all database write operations run on the
 * Intent Service's thread, while all database read operations run on Loader threads.
 *
 * 8/12/15 - Changed handling of inserting locations so that notifyChange() is called on both
 * database tables from here so that live updates of tracked runs occur in the ListView.
 *
 * 8/14/15 - Added Run Table columns for starting and ending addresses and related changes to
 * methods.
 */
@SuppressWarnings("ALL")
public class RunDatabaseHelper extends SQLiteOpenHelper {
    private static final String TAG = "RunDatabaseHelper";

    public RunDatabaseHelper(Context context) {
        super(context, Constants.DB_NAME, null, Constants.VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        //Create the "run" table
        db.execSQL("create table run (" +
                "_id integer primary key autoincrement, start_date integer, end_address varchar(100), " +
                "start_address varchar(100), distance real, duration integer)");
        //Create the "location" table
        db.execSQL("create table location (" +
                " timestamp integer, latitude real, longitude real, altitude real," +
                " provider varchar(100), run_id integer references run(_id))");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        //implement if database schema changes
    }

    public long insertRun(Context context, Run run) {
        long results;
        ContentValues cv = new ContentValues();
        cv.put(Constants.COLUMN_RUN_START_DATE, run.getStartDate().getTime());
        cv.put(Constants.COLUMN_RUN_START_ADDRESS, run.getStartAddress());
        cv.put(Constants.COLUMN_RUN_END_ADDRESS, run.getEndAddress());
        cv.put(Constants.COLUMN_RUN_DISTANCE, run.getDistance());
        cv.put(Constants.COLUMN_RUN_DURATION, run.getDuration());
        Log.i(TAG, "cv values in insertRun for Run " + run.getId() +": " + cv.toString());
        results = getWritableDatabase().insert(Constants.TABLE_RUN, null, cv);
        context.getContentResolver().notifyChange(Constants.URI_TABLE_RUN, null);
        Log.i(TAG, "Called notifyChange on URI_TABLE_RUN in insertRun for Run " + results);
        return results;
    }
    /*This function is needed to update the Run Table so that the StartDate field is equal to
    * the timestamp of the Run's first location. The Run is updated in memory in RunFragment and
    * is then routed here through the Intent Service.*/
    public int updateRunStartDate(Context context, Run run) {
        ContentValues cv = new ContentValues();
        cv.put(Constants.COLUMN_RUN_START_DATE, run.getStartDate().getTime());
        cv.put(Constants.COLUMN_RUN_START_ADDRESS, run.getStartAddress());

        int result = getWritableDatabase().update(Constants.TABLE_RUN,
                cv,
                Constants.COLUMN_RUN_ID + " =?",
                new String[]{String.valueOf(run.getId())});
        if (result == 1){
            Log.i(TAG, "In updateRunStartDate(), successfully updated Start Address for Run " + run.getId() +
                    "to " + run.getStartAddress());
        }
        context.getContentResolver().notifyChange(Constants.URI_TABLE_RUN, null);
        Log.i(TAG, "Called notifyChange on TABLE_RUN from updateRunStartDate() for Run " + run.getId());
        return result;
    }

    /*This function is needed to update the Run Table with the Starting Address value derived from
    * the first location received after the user presses the Start button in the RunFragment
    * UI
    */
    public int updateStartAddress(Context context, Run run) {
        Log.i(TAG, "Entering updateStartAddress(), run.getStartAddress() for Run " + run.getId() + " is: " + run.getStartAddress());
        ContentValues cv = new ContentValues();
        cv.put(Constants.COLUMN_RUN_START_ADDRESS, run.getStartAddress());
        Log.i(TAG, "ContentValues in updateStartAddress() for Run " + run.getId() + ": " + cv.toString());
        int result = getWritableDatabase().update(Constants.TABLE_RUN,
                cv,
                Constants.COLUMN_RUN_ID + " =?",
                new String[]{String.valueOf(run.getId())});
        context.getContentResolver().notifyChange(Constants.URI_TABLE_RUN, null);
        Log.i(TAG, "Called notifyChange on TABLE_RUN from updateStartAddress() for Run " + run.getId());
        return result;
    }
    /*This function is needed to update the Run Table with the Ending Address value derived from
    * the last location received when an EndAddressUpdateTask runs or when  the user presses the
    * Stop button in the RunFragment UI.
    */
    public int updateEndAddress(Context context, Run run) {
        Log.i(TAG, "Entering updateEndAddress() for Run " + run.getId() + " run.getEndAddress() is: " + run.getEndAddress());
        ContentValues cv = new ContentValues();
        cv.put(Constants.COLUMN_RUN_END_ADDRESS, run.getEndAddress());
        Log.i(TAG, "ContentValues for Run " + run.getId() + " in updateEndAddress(): " + cv.toString());
        int result = getWritableDatabase().update(Constants.TABLE_RUN,
                cv,
                Constants.COLUMN_RUN_ID + " =?",
                new String[]{String.valueOf(run.getId())});
        context.getContentResolver().notifyChange(Constants.URI_TABLE_RUN, null);
        Log.i(TAG, "Called notifyChange on TABLE_RUN from updateEndAddress() for Run " + run.getId());
        return result;
    }

    public long [] insertLocation(Context context, long runId, Location location) {
        //We'll be updating the Location Table and the Run Table and those operations return
        //long values, so we set up an array of longs to hold those returns, as well as the
        //return value to signal whether the user has attempted to "continue" a run more then
        //100 meters from its ending point. The return values are initialized at -1, which
        //indicates failure; each return value gets set to 1 upon successful completion of the
        //relevant task.
        long [] result = {-1, -1, -1};
        ContentValues locationCv = new ContentValues();
        ContentValues runCv = new ContentValues();
        //Before adding the new location to the database, retrieve the last recorded location and
        //check to see if it's "too far away" to be considered a continuation of the run. If so,
        //stop processing the new location, stop tracking the run and return failure
        //CONTINUATION_LIMIT_RESULT to the Intent Service so it can tell the RunFragment to display
        //a Toast advising the user.
        Location oldLocation = null;
        LocationCursor locationCursor = queryLastLocationForRun(runId);
        locationCursor.moveToFirst();
        if (!locationCursor.isAfterLast()) {
            oldLocation = locationCursor.getLocation();
        }
        locationCursor.close();
        //If we're trying to insert the first location entry for this run, oldLocation will be null,
        //so we have to check for it.
        if (oldLocation != null){
            //If the location is more than 100 meters distant from the last previous location and is
            //more than 30 seconds more recent, the user is attempting to "continue" a run from too
            //distant a point. We need to check the time difference because sometimes in a moving
            //vehicle the user can travel more than 100 meters before a location update gets
            //processed, which would otherwise incorrectly terminate the run.
            if (location.distanceTo(oldLocation) > Constants.CONTINUATION_DISTANCE_LIMIT &&
                    (location.getTime() - oldLocation.getTime() > Constants.CONTINUATION_TIME_LIMIT)){
                Log.i(TAG, "Aborting Run " + runId + " for exceeding continuation distance limit.");
                return result;
            }else {
                result[Constants.CONTINUATION_LIMIT_RESULT] = 1;
            }
        } else {
            Log.i(TAG, "oldLocation for Run " + runId + " is null");
            //If oldLocation is null, this is the first location entry for this run, so the
            //"inappropriate continuation" situation is inapplicable.
            result[Constants.CONTINUATION_LIMIT_RESULT] = 1;
        }
        //Sanity check to make sure there's a run entry in the database corresponding to the runId
        //passed into this function so we don't try to update a nonexistent run. Location updates in
        //the database should always have a run associated with them, but better safe than sorry!
        Log.i(TAG, "runId is: " + runId);
        Run run = null;
        RunCursor runCursor = queryRun(runId);
        runCursor.moveToFirst();
        if (!runCursor.isAfterLast())
            run = runCursor.getRun();
        runCursor.close();
        if (run != null){
            //Now that we know we have valid run, we can enter the new location in the Location Table.
            locationCv.put(Constants.COLUMN_LOCATION_LATITUDE, location.getLatitude());
            locationCv.put(Constants.COLUMN_LOCATION_LONGITUDE, location.getLongitude());
            locationCv.put(Constants.COLUMN_LOCATION_ALTITUDE, location.getAltitude());
            locationCv.put(Constants.COLUMN_LOCATION_TIMESTAMP, location.getTime());
            locationCv.put(Constants.COLUMN_LOCATION_PROVIDER, location.getProvider());
            locationCv.put(Constants.COLUMN_LOCATION_RUN_ID, runId);
            result[Constants.LOCATION_INSERTION_RESULT] = getWritableDatabase().insert(Constants.TABLE_LOCATION, null, locationCv);
            if (result[Constants.LOCATION_INSERTION_RESULT] != -1){
                Log.i(TAG, "For Run " + runId + " successfully inserted location at row " + result[Constants.LOCATION_INSERTION_RESULT]
                    + " in insertLocation()");
            }
            context.getContentResolver().notifyChange(Constants.URI_TABLE_LOCATION, null);
            Log.i(TAG, "Called notifyChange on URI_TABLE_LOCATION for row " + result[Constants.LOCATION_INSERTION_RESULT] +
                    " in insertLocation for Run " + runId);

            //As each location for a run is entered in the database, we add the distance and time
            //differences with the last previous location to running totals of Distance
            //and Duration and entered in the Run Table for this Run. When a Run is first created,
            //Distance is set to 0.0 and Duration to 0. Here we retrieve the cumulative totals as of
            //the last previous update to the location table.
            double distance = run.getDistance();
            long duration = run.getDuration();

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

            //Now that we have good Distance and Duration values for the Run, update the Run's entry
            //in the Run Table and notify the RunCursor in RunRecyclerListFragment that the data
            //underlying the cursor has changed so the relevant loader has to update the cursor.
            runCv.put(Constants.COLUMN_RUN_DISTANCE, distance);
            runCv.put(Constants.COLUMN_RUN_DURATION, duration);
            result[Constants.RUN_UPDATE_RESULT] = getWritableDatabase().update(Constants.TABLE_RUN,
                    runCv,
                    Constants.COLUMN_RUN_ID + " =?",
                    new String[]{String.valueOf(run.getId())});
            if (result[Constants.RUN_UPDATE_RESULT] == 1){
                Log.i(TAG, "insertLocation() successfully updated Distance and Duration for Run " + runId);
            }

            context.getContentResolver().notifyChange(Constants.URI_TABLE_RUN, null);
            Log.i(TAG, "Called notifyChange on URI_TABLE_RUN in insertLocation for Run " + runId);
        } else Log.i(TAG, "Run " + runId + " is null!");

        return result;
    }
    //Return a RunCursor with no data - used to help initialize the adapter,
    //the constructor for which requires a non-null RunCursor
    public RunCursor queryForNoRuns() {

        Cursor wrapped = getReadableDatabase().query(Constants.TABLE_RUN,
                null, null, null, null, null, null);
        return new RunCursor(wrapped);
    }
    //Return a cursor with data concerning all the runs in the database,
    //sorted by Start Date, in ascending order
    public RunCursor queryRunsDateAsc() {
        //Equivalent to "select * from run order by start_date asc"
        Cursor wrapped = getReadableDatabase().query(Constants.TABLE_RUN,
                null, null, null, null, null, Constants.COLUMN_RUN_START_DATE + " asc");
        return new RunCursor(wrapped);
    }

    //Return a cursor with data concerning all the runs in the database,
    //sorted by Start Date, in descending order
    public RunCursor queryRunsDateDesc() {
        //Equivalent to "select * from run order by start_date desc"
        Cursor wrapped = getReadableDatabase().query(Constants.TABLE_RUN,
                null, null, null, null, null, Constants.COLUMN_RUN_START_DATE + " desc");
        return new RunCursor(wrapped);
    }

    //Return a cursor with data concerning all the runs in the database,
    //sorted by Distance, in ascending order
    public RunCursor queryRunsDistanceAsc() {
        //Equivalent to "select * from run order by distance asc"
        Cursor wrapped = getReadableDatabase().query(Constants.TABLE_RUN,
                null, null, null, null, null, Constants.COLUMN_RUN_DISTANCE + " asc");
        return new RunCursor(wrapped);
    }

    //Return a cursor with data concerning all the runs in the database,
    //sorted by Distance, in descending order
    public RunCursor queryRunsDistanceDesc() {
        //Equivalent to "select * from run order by distance desc"
        Cursor wrapped = getReadableDatabase().query(Constants.TABLE_RUN,
                null, null, null, null, null, Constants.COLUMN_RUN_DISTANCE + " desc");
        return new RunCursor(wrapped);
    }

    //Return a cursor with data concerning all the runs in the database,
    //sorted by Duration, in ascending order
    public RunCursor queryRunsDurationAsc() {
        //Equivalent to "select * from run order by duration asc"
        Cursor wrapped = getReadableDatabase().query(Constants.TABLE_RUN,
                null, null, null, null,null, Constants.COLUMN_RUN_DURATION + " asc");
        return new RunCursor(wrapped);
    }

    //Return a cursor with data concerning all the runs in the database,
    //sorted by Duration, in descending order
    public RunCursor queryRunsDurationDesc() {
        //Equivalent to "select * from run order by duration desc"
        Cursor wrapped = getReadableDatabase().query(Constants.TABLE_RUN,
                null, null, null, null,null, Constants.COLUMN_RUN_DURATION + " desc");
        return new RunCursor(wrapped);
    }

    //Return a cursor with data concerning a particular Run identified by its RunId
    public RunCursor queryRun(long id) {
        Cursor wrapped = getReadableDatabase().query(Constants.TABLE_RUN,
                null, //All columns
                Constants.COLUMN_RUN_ID + " = ?", //Look for a run ID
                new String[]{String.valueOf(id) },//with this value
                null, //group by
                null, //having
                null, //order by
                "1"); //limit 1 row
        return new RunCursor(wrapped);
    }
    //Return a cursor holding the first (starting) Location for a Run identified by RunId
    public LocationCursor queryFirstLocationForRun(long runId) {
        Cursor wrapped = getReadableDatabase().query(Constants.TABLE_LOCATION,
                null, //All Columns
                Constants.COLUMN_LOCATION_RUN_ID + " =?", //limit to the given run
                new String[]{ String.valueOf(runId) },
                null,//group by
                null, //having
                Constants.COLUMN_LOCATION_TIMESTAMP + " asc", //order by earliest first
                "1"); //limit to one row
        return new LocationCursor(wrapped);
    }
    //Return a cursor holding the last (ending) Location for a Run identified by RunId
    public LocationCursor queryLastLocationForRun(long runId) {
        Cursor wrapped = getReadableDatabase().query(Constants.TABLE_LOCATION,
                null, //All Columns
                Constants.COLUMN_LOCATION_RUN_ID + " = ?", //limit to the given run
                new String[]{ String.valueOf(runId) },
                null, //group by
                null, //having
                Constants.COLUMN_LOCATION_TIMESTAMP + " desc", //order by latest first
                "1"); //limit to one row
        return new LocationCursor(wrapped);
    }
    //Return a cursor holding all the Locations for a Run identified by RunId
    public LocationCursor queryLocationsForRun(long runId) {
        Cursor wrapped = getReadableDatabase().query(Constants.TABLE_LOCATION,
                null,
                Constants.COLUMN_LOCATION_RUN_ID + " = ?", //limit to the given run
                new String[]{ String.valueOf(runId) },
                null, //group by
                null, //having
                Constants.COLUMN_LOCATION_TIMESTAMP + " asc"); //order by timestamp
        return  new LocationCursor(wrapped);
    }

    //Method to delete one specified Run and all its associated Location entries.
    public int[] deleteRun(Context context, long runId){
        Log.i(TAG, "In RunDataBaseHelper deleteRun(), runId is " + runId);
        RunManager runManager = RunManager.get(context);

        int[] result = {-1, -1};
        try {
            //First, delete all the Locations associated with the Run
            int locationsDeleted = getWritableDatabase().delete(
                    Constants.TABLE_LOCATION,
                    Constants.COLUMN_LOCATION_RUN_ID + " = ?",//Limit to this Run
                    new String[]{String.valueOf(runId)}
            );
            Log.i(TAG, locationsDeleted + " locations deleted for Run " + runId + " in RunDataBaseHelper.DeleteRun()");
            switch (locationsDeleted) {
                case Constants.DELETION_ERROR:
                    //Upon error in deleting Locations, stop the delete operation and
                    //report the results
                    Log.d(TAG, "Error trying to delete location entries.");
                    result[Constants.LOCATION_DELETIONS] = -1;
                    //return result;
                    break;
                case 0:
                    //This isn't necessarily an error; a Run without associated Location entries can
                    //exist, such as when the user stops tracking a Run before GPS lock
                    //and reporting of Locations. If this is the first run through the
                    //for loop, result[LOCATION_DELETIONS] will be -1, signifying an error,
                    //so we need to change this to 0; on subsequent runs through the for
                    //loop, we can just leave result[LOCATION_DELETIONS] alone.
                    Log.i(TAG, "Didn't delete any location entries.");
                    if (result[Constants.LOCATION_DELETIONS] == -1)
                        result[Constants.LOCATION_DELETIONS] = 0;
                    break;
                default:
                    Log.i(TAG, "Deleted " + locationsDeleted + " locations for runId " + runId);
                    //If this is the first time through the for loop, the result value for
                    //Locations deleted will still be at the initial -1 (error) value.
                    //Substitute the actual results of the Locations deletions. On
                    //subsequent runs through the for loop, we need only increment the
                    //result value obtained in earlier trips through the for loop
                    if (result[Constants.LOCATION_DELETIONS] == -1) {
                        result[Constants.LOCATION_DELETIONS] = locationsDeleted;
                    }
                    context.getContentResolver().notifyChange(Constants.URI_TABLE_LOCATION, null);
                    Log.i(TAG, "Called notifyChange() on TABLE_LOCATION in deleteRuns()");
            }
            //Now that its associated Location entries have been deleted, we can delete
            //the Run entry itself
            int runDeleted = getWritableDatabase().delete(
                    Constants.TABLE_RUN,
                    Constants.COLUMN_RUN_ID + " =?",
                    new String[]{String.valueOf(runId)}
            );
            switch (runDeleted) {
                case Constants.DELETION_ERROR:
                    //Upon error in deleting a Run, terminate the operation and report the
                    //result
                    Log.d(TAG, "Error trying to delete run entry.");
                    result[Constants.RUN_DELETIONS] = -1;
                    break;
                case 0:
                    //We had a RunId, so there should have been a Run to delete; a zero
                    //return value here means something went wrong. Terminate the operation
                    //and report the result
                    Log.d(TAG, "Error: no run entry was deleted.");
                    result[Constants.RUN_DELETIONS] = -1;
                    break;
                default:
                    Log.d(TAG, "Deleted " + runDeleted + " run entry for runId " + runId);
                    //On the first run through the for loop, the result value will still be
                    //its initial -1 (error) value; substitute the actual result obtained.
                    //On subsequent passes through the for loop, simply increment the
                    //running total by the latest result.
                    if (result[Constants.RUN_DELETIONS] == -1) {
                        result[Constants.RUN_DELETIONS] = runDeleted;
                    }
                    context.getContentResolver().notifyChange(Constants.URI_TABLE_RUN, null);
                    Log.i(TAG, "Called notifyChange() on TABLE_RUN in deleteRuns()");

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        Log.d(TAG, "deleteRun result is: " + result[Constants.RUN_DELETIONS]
                + ":" + result[Constants.LOCATION_DELETIONS]);
        return result;

    }

    //Method to delete specified Runs and all their associated Location entries.
    public int[] deleteRuns(Context context, ArrayList<Long> runIds) {
        Log.i(TAG, "Entered deleteRuns. runIds are " + runIds);
        RunManager runManager = RunManager.get(context);

        int[] result = {-1, -1};
        //For each Run identified in the ArrayList...
        for (int i = 0; i < runIds.size(); i++) {

            try {
                //First, delete all the Locations associated with the Run
                int locationsDeleted = getWritableDatabase().delete(
                        Constants.TABLE_LOCATION,
                        Constants.COLUMN_LOCATION_RUN_ID + " = ?",//Limit to this Run
                        new String[]{String.valueOf(runIds.get(i))}
                );
                switch (locationsDeleted) {
                    case Constants.DELETION_ERROR:
                        //Upon error in deleting Locations, stop the delete operation and
                        //report the results
                        Log.d(TAG, "Error trying to delete location entries.");
                        result[Constants.LOCATION_DELETIONS] = -1;
                        break;
                    case 0:
                        //This isn't an error; a Run without associated Location entries can
                        //exist, such as when the user stops tracking a Run before GPS lock
                        //and reporting of Locations. If this is the first run through the
                        //for loop, result[LOCATION_DELETIONS] will be -1, signifying an error,
                        //so we need to change this to 0; on subsequent runs through the for
                        //loop, we can just leave result[LOCATION_DELETIONS] alone.
                        Log.i(TAG, "Didn't delete any location entries.");
                        if (result[Constants.LOCATION_DELETIONS] == -1)
                            result[Constants.LOCATION_DELETIONS] = 0;
                        break;
                    default:
                        Log.i(TAG, "Deleted " + locationsDeleted + " locations for runId " + runIds.get(i));
                        //If this is the first time through the for loop, the result value for
                        //Locations deleted will still be at the initial -1 (error) value.
                        //Substitute the actual results of the Locations deletions. On
                        //subsequent runs through the for loop, we need only increment the
                        //result value obtained in earlier trips through the for loop
                        if (result[Constants.LOCATION_DELETIONS] == -1) {
                            result[Constants.LOCATION_DELETIONS] = locationsDeleted;
                        } else {
                            result[Constants.LOCATION_DELETIONS] =
                                    result[Constants.LOCATION_DELETIONS] + locationsDeleted;
                        }
                        context.getContentResolver().notifyChange(Constants.URI_TABLE_LOCATION, null);
                        Log.i(TAG, "Called notifyChange() on TABLE_LOCATION in deleteRuns()");
                }
                //Now that its associated Location entries have been deleted, we can delete
                //the Run entry itself
                int runDeleted = getWritableDatabase().delete(
                        Constants.TABLE_RUN,
                        Constants.COLUMN_RUN_ID + " =?",
                        new String[]{String.valueOf(runIds.get(i))}
                );
                switch (runDeleted) {
                    case Constants.DELETION_ERROR:
                        //Upon error in deleting a Run, terminate the operation and report the
                        //result
                        Log.d(TAG, "Error trying to delete run entry.");
                        result[Constants.RUN_DELETIONS] = -1;
                        //return result;
                        break;
                    case 0:
                        //We had a RunId, so there should have been a Run to delete; a zero
                        //return value here means something went wrong. Terminate the operation
                        //and report the result
                        Log.d(TAG, "Error: no run entry was deleted.");
                        result[Constants.RUN_DELETIONS] = -1;
                        break;
                    default:
                        Log.d(TAG, "Deleted " + runDeleted + " run entry for runId " + runIds.get(i));
                        //On the first run through the for loop, the result value will still be
                        //its initial -1 (error) value; substitute the actual result obtained.
                        //On subsequent passes through the for loop, simply increment the
                        //running total by the latest result.
                        if (result[Constants.RUN_DELETIONS] == -1) {
                            result[Constants.RUN_DELETIONS] = runDeleted;
                        } else {
                            result[Constants.RUN_DELETIONS] = result[Constants.RUN_DELETIONS] + runDeleted;
                        }
                        context.getContentResolver().notifyChange(Constants.URI_TABLE_RUN, null);
                        Log.i(TAG, "Called notifyChange() on TABLE_RUN in deleteRuns()");

                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        //Call notifyChange on the Run Table so that the now-modified list of Runs will be
        //served up to the RunRecyclerListAdapter to update the RecyclerListView of the Runs in
        // RunRecyclerListFragment.
        Log.d(TAG, "deleteRun result is: " + result[Constants.RUN_DELETIONS]
                + ":" + result[Constants.LOCATION_DELETIONS]);
        return result;
    }
    //Return the number of locations associated with a given Run
    public long getRunLocationCount(long runId){
        SQLiteDatabase db = getReadableDatabase();
        return DatabaseUtils.queryNumEntries(db, Constants.TABLE_LOCATION,
                                            Constants.COLUMN_LOCATION_RUN_ID + " =?",
                                            new String[]{String.valueOf(runId)});
    }

    /**
     * A convenience class to wrap a cursor that returns rows from the "Run" table,
     * The {@link getRun())} method will give you a Run instance representing
     * the current row.
     */
    @SuppressWarnings("JavadocReference")
    public static class RunCursor extends CursorWrapper {

        public RunCursor(Cursor c) {
            super(c);
        }

        /**
         * Returns a Run object configured for the current row,
         * or null if the current row is invalid.
         */
        public Run getRun() {
            if (isBeforeFirst() || isAfterLast())
                return null;
            Run run = new Run();

            long runId = getLong(getColumnIndex(Constants.COLUMN_RUN_ID));
            run.setId(runId);
            long startDate = getLong(getColumnIndex(Constants.COLUMN_RUN_START_DATE));
            run.setStartDate(new Date(startDate));
            String startAddress = getString(getColumnIndex(Constants.COLUMN_RUN_START_ADDRESS));
            run.setStartAddress(startAddress);
            double distance = getDouble(getColumnIndex(Constants.COLUMN_RUN_DISTANCE));
            run.setDistance(distance);
            long duration = getLong(getColumnIndex(Constants.COLUMN_RUN_DURATION));
            run.setDuration(duration);
            String endAddress = getString(getColumnIndex(Constants.COLUMN_RUN_END_ADDRESS));
            run.setEndAddress(endAddress);
            return run;
        }
    }

    /**
     * A convenience class to wrap a cursor that returns rows from the "location" table,
     * The {@link getLocation())} method will give you a Location instance representing
     * the current row.
     */

    public static class LocationCursor extends CursorWrapper {

        public LocationCursor(Cursor c) {
            super(c);
        }

        public Location getLocation() {
            if (isBeforeFirst() || isAfterLast())
                return null;
            //First get the provider out so you can use the constructor
            String provider = getString(getColumnIndex(Constants.COLUMN_LOCATION_PROVIDER));
            Location loc = new Location(provider);
            //Populate the remaining properties
            loc.setLongitude(getDouble(getColumnIndex(Constants.COLUMN_LOCATION_LONGITUDE)));
            loc.setLatitude(getDouble(getColumnIndex(Constants.COLUMN_LOCATION_LATITUDE)));
            loc.setAltitude(getDouble(getColumnIndex(Constants.COLUMN_LOCATION_ALTITUDE)));
            loc.setTime(getLong(getColumnIndex(Constants.COLUMN_LOCATION_TIMESTAMP)));
            return loc;
        }
    }
}
