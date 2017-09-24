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
import android.net.Uri;
import android.util.Log;

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

    public long insertRun(Context context, /*Run run*/ContentValues cv) {
        long results;

        Run  run = new Run();
        cv.put(Constants.COLUMN_RUN_START_DATE, run.getStartDate().getTime());
        cv.put(Constants.COLUMN_RUN_START_ADDRESS, run.getStartAddress());
        cv.put(Constants.COLUMN_RUN_END_ADDRESS, run.getEndAddress());
        cv.put(Constants.COLUMN_RUN_DISTANCE, run.getDistance());
        cv.put(Constants.COLUMN_RUN_DURATION, run.getDuration());
        Log.i(TAG, "cv values in insertRun for Run " + run.getId() +": " + cv.toString());
        results = getWritableDatabase().insert(Constants.TABLE_RUN, null, cv);
        run.setId(results);
        context.getContentResolver().notifyChange(Constants.URI_TABLE_RUN, null);
        Log.i(TAG, "Called notifyChange on URI_TABLE_RUN in insertRun for Run " + results);
        return results;
    }
    /*This function is needed to update the Run Table so that the Distance and Duration
     */
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
    public long insertLocation(Uri uri, ContentValues values){
        return getWritableDatabase().insert(Constants.TABLE_LOCATION, null, values);
    }
    //Return the number of locations associated with a given Run
    public long getRunLocationCount(long runId){
        SQLiteDatabase db = getReadableDatabase();
        return DatabaseUtils.queryNumEntries(db, Constants.TABLE_LOCATION,
                                            Constants.COLUMN_LOCATION_RUN_ID + " =?",
                                            new String[]{String.valueOf(runId)});
    }

    /**
     * Returns a Run object configured for the current row,
     * or null if the current row is invalid.
     */
    public static Run getRun(Cursor cursor) {
        if (cursor.isBeforeFirst() || cursor.isAfterLast())
            return null;
        Run run = new Run();

        long runId = cursor.getLong(cursor.getColumnIndex(Constants.COLUMN_RUN_ID));
        run.setId(runId);
        long startDate = cursor.getLong(cursor.getColumnIndex(Constants.COLUMN_RUN_START_DATE));
        run.setStartDate(new Date(startDate));
        String startAddress = cursor.getString(cursor.getColumnIndex(Constants.COLUMN_RUN_START_ADDRESS));
        run.setStartAddress(startAddress);
        double distance = cursor.getDouble(cursor.getColumnIndex(Constants.COLUMN_RUN_DISTANCE));
        run.setDistance(distance);
        long duration = cursor.getLong(cursor.getColumnIndex(Constants.COLUMN_RUN_DURATION));
        run.setDuration(duration);
        String endAddress = cursor.getString(cursor.getColumnIndex(Constants.COLUMN_RUN_END_ADDRESS));
        run.setEndAddress(endAddress);
        return run;
    }

    public static Location getLocation(Cursor cursor){
        if (cursor.isBeforeFirst() || cursor.isAfterLast()){
            return null;
        }

        //First get the provider out so you can use the constructor
        String provider = cursor.getString(cursor.getColumnIndex(Constants.COLUMN_LOCATION_PROVIDER));
        Location loc = new Location(provider);
        //Populate the remaining properties
        loc.setLongitude(cursor.getDouble(cursor.getColumnIndex(Constants.COLUMN_LOCATION_LONGITUDE)));
        loc.setLatitude(cursor.getDouble(cursor.getColumnIndex(Constants.COLUMN_LOCATION_LATITUDE)));
        loc.setAltitude(cursor.getDouble(cursor.getColumnIndex(Constants.COLUMN_LOCATION_ALTITUDE)));
        loc.setTime(cursor.getLong(cursor.getColumnIndex(Constants.COLUMN_LOCATION_TIMESTAMP)));
        return loc;


    }
}
