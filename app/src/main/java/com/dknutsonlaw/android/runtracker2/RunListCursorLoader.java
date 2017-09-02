package com.dknutsonlaw.android.runtracker2;

/*
  Created by dck on 9/6/15. A loader for a cursor holding data for a list of runs that automatically
  updates as the underlying data changes.
 */
import android.content.Context;
import android.database.Cursor;
import android.util.Log;

class RunListCursorLoader extends MySQLiteCursorLoader {
    private static final String TAG = ".runlistcursorloader";

    private final int mSortOrder;

    RunListCursorLoader(Context context, int sortOrder) {
        super(context, Constants.URI_TABLE_RUN);
        mSortOrder = sortOrder;
    }

    @Override
    protected Cursor loadCursor() {
        //Query the list of all runs in the database; return different cursor
        //depending upon the sort order selected in the loader's constructor
        RunDatabaseHelper.RunCursor cursor;
        switch (mSortOrder) {
            case Constants.SORT_BY_DATE_ASC:
                cursor = RunManager.queryRunsDateAsc();
                break;
            case Constants.SORT_BY_DATE_DESC:
                cursor = RunManager.queryRunsDateDesc();
                break;
            case Constants.SORT_BY_DISTANCE_ASC:
                cursor = RunManager.queryRunsDistanceAsc();
                break;
            case Constants.SORT_BY_DISTANCE_DESC:
                cursor = RunManager.queryRunsDistanceDesc();
                break;
            case Constants.SORT_BY_DURATION_ASC:
                cursor = RunManager.queryRunsDurationAsc();
                break;
            case Constants.SORT_BY_DURATION_DESC:
                cursor = RunManager.queryRunsDurationDesc();
                break;
            default:
                Log.i(TAG, "How'd you get here?!?");
                cursor = null;
        }
        return cursor;
    }
}
