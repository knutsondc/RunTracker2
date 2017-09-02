package com.dknutsonlaw.android.runtracker2;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;

/**
 * Created by dck on 11/14/15. Created to replace a loader that simply returned the location object
 * directly
 */

class LastLocationCursorLoader extends MySQLiteCursorLoader{
    private static final String TAG = "lastLocCursorLoader";

    private final long mRunId;

    LastLocationCursorLoader(Context context, long runId){
        super(context, Constants.URI_TABLE_LOCATION);
        mRunId = runId;
    }

    @Override
    protected Cursor loadCursor(){
        Log.i(TAG, "In loadCursor in LastLocationCursorLoader");
        return RunManager.queryLastLocationForRun(mRunId);
    }
}
