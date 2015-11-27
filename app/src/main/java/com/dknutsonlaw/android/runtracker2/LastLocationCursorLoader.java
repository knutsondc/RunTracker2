package com.dknutsonlaw.android.runtracker2;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

/**
 * Created by dck on 11/14/15. Created to replace a loader that simply returned the location object
 * directly
 */
@SuppressWarnings("unused")
public class LastLocationCursorLoader extends MySQLiteCursorLoader{
    private static final String TAG = "lastLocationCursorLoader";

    private final long mRunId;

    public LastLocationCursorLoader(Context context, @SuppressWarnings("SameParameterValue") Uri uri, long runId){
        super(context, uri);
        mRunId = runId;
    }

    @Override
    protected Cursor loadCursor(){
        return RunManager.get(getContext()).queryLastLocationForRun(mRunId);
    }
}
