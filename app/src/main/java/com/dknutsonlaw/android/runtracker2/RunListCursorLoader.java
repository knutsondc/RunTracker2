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
        /*Query the list of all runs in the database; return different cursor
         *depending upon the sort order selected in the loader's constructor.
         */
        Cursor cursor;
        switch (mSortOrder) {
            case Constants.SORT_BY_DATE_ASC:
                cursor = getContext().getContentResolver().query(
                        Constants.URI_TABLE_RUN,
                        null,
                        null,
                        null,
                        String.valueOf(Constants.SORT_BY_DATE_ASC)
                );
                break;
            case Constants.SORT_BY_DATE_DESC:
                cursor = getContext().getContentResolver().query(
                        Constants.URI_TABLE_RUN,
                        null,
                        null,
                        null,
                        String.valueOf(Constants.SORT_BY_DATE_DESC)
                );
                break;
            case Constants.SORT_BY_DISTANCE_ASC:
                cursor = getContext().getContentResolver().query(
                        Constants.URI_TABLE_RUN,
                        null,
                        null,
                        null,
                        String.valueOf(Constants.SORT_BY_DISTANCE_ASC)
                );
                break;
            case Constants.SORT_BY_DISTANCE_DESC:
                cursor = getContext().getContentResolver().query(
                        Constants.URI_TABLE_RUN,
                        null,
                        null,
                        null,
                        String.valueOf(Constants.SORT_BY_DISTANCE_DESC)
                );
                break;
            case Constants.SORT_BY_DURATION_ASC:
                cursor = getContext().getContentResolver().query(
                        Constants.URI_TABLE_RUN,
                        null,
                        null,
                        null,
                        String.valueOf(Constants.SORT_BY_DURATION_ASC)
                );
                break;
            case Constants.SORT_BY_DURATION_DESC:
                cursor = getContext().getContentResolver().query(
                        Constants.URI_TABLE_RUN,
                        null,
                        null,
                        null,
                        String.valueOf(Constants.SORT_BY_DURATION_DESC)
                );
                break;
            case Constants.SORT_NO_RUNS:
                //noinspection UnusedAssignment
                cursor = getContext().getContentResolver().query(
                        Constants.URI_TABLE_RUN,
                        null,
                        null,
                        null,
                        null
                );
            default:
                Log.i(TAG, "How'd you get here?!?");
                cursor = null;
        }
        return cursor;
    }
}
