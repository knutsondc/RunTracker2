package com.dknutsonlaw.android.runtracker2;

/**
 * Created by dck on 9/6/15.
 */
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

/**
 * Created by dck on 2/12/15.
 * This loader needs only to take the Id of the run we're tracking to feed into the query into the
 * database, so we need only provide a constructor and override the loadCursor method. Note that
 * it extends MySQLiteCursorLoader which supports automatic updating upon change in the relevant
 * database table.
 *
 * 8/12/15 - No longer used in RunFragment; RunFragment only displays the location data for the
 * first and last locations for a run, so we really don't need a cursor holding ALL the locations
 * for a run. This has been replaced by a LastLocationLoader.
 */
public class MyLocationListCursorLoader extends MySQLiteCursorLoader {
    @SuppressWarnings("unused")
    private static final String TAG = "MyLocationListCursorLoader";

    private final long mRunId;

    public MyLocationListCursorLoader(Context c, @SuppressWarnings("SameParameterValue") Uri uri, long runId) {
        super(c, uri);
        mRunId = runId;
    }

    @Override
    protected Cursor loadCursor() {

        return RunManager.get(getContext()).queryLocationsForRun(mRunId);
    }
}
