package com.dknutsonlaw.android.runtracker2;

import android.content.Context;
import android.database.Cursor;

/**
 * Created by dck on 11/14/15. Created to replace a loader that simply returned the location object
 * directly
 */

class LastLocationCursorLoader extends MySQLiteCursorLoader{
    private static final String TAG = "lastLocationCursorLoader";

    private final long mRunId;

    LastLocationCursorLoader(Context context, long runId){
        super(context, Constants.URI_TABLE_LOCATION);
        mRunId = runId;
    }

    @Override
    protected Cursor loadCursor(){
        return RunManager.get(getContext()).queryLastLocationForRun(mRunId);
    }
}
