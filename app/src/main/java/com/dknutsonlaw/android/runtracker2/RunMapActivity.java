package com.dknutsonlaw.android.runtracker2;

/**
 * Created by dck on 9/6/15.
 */

import android.os.Bundle;
import android.support.v4.app.Fragment;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;

import java.lang.reflect.Array;
import java.util.ArrayList;

/**
 * Created by dck on 1/15/15.
 *
 * This Activity hosts a Fragment that implements display and update of a GoogleMap showing
 * the course of the Run.
 */
@SuppressWarnings("ALL")
public class RunMapActivity extends SingleFragmentActivity{
    private static final String TAG = "RunMapActivity";

    @Override
        //We need a layout file here that holds more than a container for the Fragment. We also want
        //to have a couple of TextViews "floating" above the map.
    int getLayoutResId() {
        return R.layout.map_activity_fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        //We want the map to fill the whole display - this needs to be called BEFORE creation of
        //the Activity's and Fragment's View hierarchy starts, so the following call must precede
        //the obligatory call to super.onCreate(savedInstanceState).
        //requestWindowFeature(Window.FEATURE_NO_TITLE); This call now deleted so we can have an
        //OptionsMenu in the RunMapFragment
        super.onCreate(savedInstanceState);
        setContentView(getLayoutResId());
    }

    @Override
    protected Fragment createFragment() {
        long runId = getIntent().getLongExtra(Constants.EXTRA_RUN_ID, -1);
        if (runId != -1){
            return RunMapFragment.newInstance(runId);
        } else {
            return new RunMapFragment();
        }
    }
}
