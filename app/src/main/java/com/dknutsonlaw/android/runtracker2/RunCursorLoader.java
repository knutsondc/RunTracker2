package com.dknutsonlaw.android.runtracker2;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;

/**
 * Created by dck on 11/14/15. Created to replace a loader that simply returns a run object
 */
class RunCursorLoader extends MySQLiteCursorLoader{
    private static final String TAG = "runCursorLoader";

    private final long mRunId;

    RunCursorLoader(Context context, long runId){
        super(context, Constants.URI_TABLE_RUN);
        mRunId = runId;
    }

    @Override
    protected Cursor loadCursor(){
        Log.i(TAG, "In loadCursor for RunCursorLoader");
        return RunManager.queryRun(mRunId);
    }
}
