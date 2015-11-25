package com.dknutsonlaw.android.runtracker2;

/**
 * Created by dck on 9/6/15.
 */

import android.content.Context;

/**
 * Created by dck on 1/15/15. This loader does not implement automatic updating; when Runs are
 * added to the database, the UI is instructed to update in the onActivityResult() method that's
 * called when the StartActivity() call to add the new Run returns. When a Run is deleted, the UI
 * gets instructed to update in a callback method that's invoked when the deletion task completes.
 *
 * 8/3/15 - This has been replaced by MyRunLoader.
 */
@SuppressWarnings("ALL")
public class RunLoader extends DataLoader<Run> {
    private static final String TAG = "RunLoader";
    private final long mRunId;

    public RunLoader(Context context, long runId) {
        super(context);
        mRunId = runId;
    }

    @Override
    public Run loadInBackground() {
        return RunManager.get(getContext()).getRun(mRunId);
    }
}
