package com.dknutsonlaw.android.runtracker2;

/*
  Created by dck on 9/6/15 for RunTracker2.
  Created by dck on 2/12/15 for original RunTracker program..
  This loader needs only to take the Id of the run we're tracking to feed into the query into the
  database, so we need only provide a constructor and override the loadCursor method. Note that
  it extends MySQLiteCursorLoader which supports automatic updating upon change in the relevant
  database table.

  8/12/15 - No longer used in RunFragment; RunFragment only displays the location data for the
  first and last locations for a run, so we really don't need a cursor holding ALL the locations
  for a run. This has been replaced by a LastLocationLoader.
 */
import android.content.Context;
import android.database.Cursor;

class MyLocationListCursorLoader extends MySQLiteCursorLoader {
    @SuppressWarnings("unused")
    private static final String TAG = "MyLocationListCursorLoader";

    private final long mRunId;

    MyLocationListCursorLoader(Context c, long runId) {
        super(c, Constants.URI_TABLE_LOCATION);
        mRunId = runId;
    }

    @Override
    protected Cursor loadCursor() {

        //return RunManager.queryLocationsForRun(mRunId);
        return getContext().getContentResolver().query(
                Constants.URI_TABLE_LOCATION,
                null,
                Constants.COLUMN_LOCATION_RUN_ID + " = ?",
                new String[]{String.valueOf(mRunId)},
                Constants.COLUMN_LOCATION_TIMESTAMP + "desc"
        );
    }
}
