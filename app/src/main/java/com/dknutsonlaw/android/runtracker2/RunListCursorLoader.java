package com.dknutsonlaw.android.runtracker2;

/**
 * Created by dck on 9/6/15.
 */
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

/**
 * Created by dck on 9/6/15.
 */
public class RunListCursorLoader extends MySQLiteCursorLoader {
    private static final String TAG = ".runlistcursorloader";

    private final int mSortOrder;

    public RunListCursorLoader(Context context, @SuppressWarnings("SameParameterValue") Uri uri, int sortOrder) {
        super(context, uri);
        mSortOrder = sortOrder;
    }

    @Override
    protected Cursor loadCursor() {
        //Query the list of all runs in the database; return different cursor
        //depending upon the sort order selected in the loader's constructor
        RunDatabaseHelper.RunCursor cursor;
        switch (mSortOrder) {
            case Constants.SORT_BY_DATE_ASC:
                cursor = RunManager.get(getContext()).queryRunsDateAsc();
                break;
            case Constants.SORT_BY_DATE_DESC:
                cursor = RunManager.get(getContext()).queryRunsDateDesc();
                break;
            case Constants.SORT_BY_DISTANCE_ASC:
                cursor = RunManager.get(getContext()).queryRunsDistanceAsc();
                break;
            case Constants.SORT_BY_DISTANCE_DESC:
                cursor = RunManager.get(getContext()).queryRunsDistanceDesc();
                break;
            case Constants.SORT_BY_DURATION_ASC:
                cursor = RunManager.get(getContext()).queryRunsDurationAsc();
                break;
            case Constants.SORT_BY_DURATION_DESC:
                cursor = RunManager.get(getContext()).queryRunsDurationDesc();
                break;
            default:
                Log.i(TAG, "How'd you get here?!?");
                cursor = null;
        }
        return cursor;
    }
}
