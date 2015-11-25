package com.dknutsonlaw.android.runtracker2;

/**
 * Created by dck on 9/6/15.
 */
import android.content.Context;
import android.location.Location;

/**
 * Created by dck on 1/15/15.
 * A loader to provide the most recent (last) Location object stored in the database for a given
 * run.
 *
 * 8/12/15 - This loader is now used to supply fresh location data for use in RunFragment.
 */

class LastLocationLoader extends DataLoader<Location> {
    private final long mRunId;

    public LastLocationLoader(Context context, long runId) {
        super(context);
        mRunId = runId;
    }

    @Override
    public Location loadInBackground() {
        return RunManager.get(getContext()).getLastLocationForRun(mRunId);
    }

}
