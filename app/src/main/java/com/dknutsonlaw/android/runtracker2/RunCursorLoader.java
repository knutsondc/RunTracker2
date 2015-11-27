package com.dknutsonlaw.android.runtracker2;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

/**
 * Created by dck on 11/14/15. Created to replace a loader that simply returns a run object
 */
@SuppressWarnings("unused")
public class RunCursorLoader extends MySQLiteCursorLoader{
    private static final String TAG = "runCursorLoader";

    private final long mRunId;

    public RunCursorLoader(Context context, @SuppressWarnings("SameParameterValue") Uri uri, long runId){
        super(context, uri);
        mRunId = runId;
    }

    @Override
    protected Cursor loadCursor(){
        return RunManager.get(getContext()).queryRun(mRunId);
    }
}
