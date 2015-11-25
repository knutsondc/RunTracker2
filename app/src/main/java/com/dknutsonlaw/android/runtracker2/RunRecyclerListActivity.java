package com.dknutsonlaw.android.runtracker2;

import android.support.v4.app.Fragment;

/**
 * Created by dck on 10/28/15. Activity to host a Fragment displaying a RecyclerView displaying all
 * the recorded Runs.
 */
public class RunRecyclerListActivity extends SingleFragmentActivity{
    @SuppressWarnings("unused")
    public static final String TAG = "RunRecyclerListActivity";

    protected Fragment createFragment(){
        return new RunRecyclerListFragment();
    }
}
