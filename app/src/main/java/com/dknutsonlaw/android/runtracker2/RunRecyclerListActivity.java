package com.dknutsonlaw.android.runtracker2;

import android.support.v4.app.Fragment;
import android.util.Log;

/**
 * Created by dck on 10/28/15. Activity to host a Fragment displaying a RecyclerView displaying all
 * the recorded Runs.
 */
public class RunRecyclerListActivity extends SingleFragmentActivity implements DeleteRunsDialog.DeleteRunsDialogListener {

    private static final String TAG = "RunRecyclerListActivity";

    @Override
    protected Fragment createFragment(){
        return new RunRecyclerListFragment();
    }

    @Override
    public void onDeleteRunsDialogPositiveClick(int which){
        /*Check to see if this call is for us and, if so, forward to the fragment's
         *onDeleteRunsDialogPositiveClick method.
         */
        Log.i(TAG, "Reached RunRecyclerListActivity PositiveClick callback");
        if (which == Constants.RUN_LIST_RECYCLER_FRAGMENT){
            RunRecyclerListFragment fragment = (RunRecyclerListFragment)getSupportFragmentManager()
                    .findFragmentById(R.id.fragmentContainer);
            fragment.onDeleteRunsDialogPositiveClick();
        }
    }

    @Override
    public void onDeleteRunsDialogNegativeClick(int which){
        /*Check to see if this call is for us and, if so, forward to the fragment's
         *onDeleteRunsDialogNegativeClick method.
         */
        Log.i(TAG, "Reached RunRecyclerListActivity NegativeClick callback");
        if (which == Constants.RUN_LIST_RECYCLER_FRAGMENT){
            RunRecyclerListFragment fragment = (RunRecyclerListFragment)getSupportFragmentManager()
                    .findFragmentById(R.id.fragmentContainer);
            fragment.onDeleteRunsDialogNegativeClick();
        }
    }
}
