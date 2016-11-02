package com.dknutsonlaw.android.runtracker2;

import android.support.v4.app.Fragment;
import android.util.Log;

/**
 * Created by dck on 10/28/15. Activity to host a Fragment displaying a RecyclerView displaying all
 * the recorded Runs.
 */
public class RunRecyclerListActivity extends SingleFragmentActivity {

    private static final String TAG = "RunRecyclerListActivity";

    @Override
    protected Fragment createFragment(){
        Log.i(TAG, "Inside RunRecyclerListActivity createFragment");
        return new RunRecyclerListFragment();
    }

}
