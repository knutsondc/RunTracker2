package com.dknutsonlaw.android.runtracker2;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;

/**
 * Created by dck on 9/6/15.
 *
 *
 * Created by dck on 12/7/14.
 *
 * Taken from the Big Nerd Ranch book. This class is a general template for an Activity whose sole
 * purpose is to host a single Fragment that does all the real work of the Activity.
 */
 public abstract class SingleFragmentActivity extends AppCompatActivity{
    @SuppressWarnings("unused")
    private static final String TAG = "SingleFrameActivity";

    protected abstract Fragment createFragment();

    //Override this function if a particular subclass needs a layout with more than just a
    //container for the Fragment we're hosting.
    @SuppressWarnings("SameReturnValue")
    private int getLayoutResId(){
        return R.layout.activity_fragment;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(getLayoutResId());

        FragmentManager fm = getSupportFragmentManager();
        Fragment fragment = fm.findFragmentById(R.id.fragmentContainer);

        if (fragment == null){
            fragment = createFragment();
            fm.beginTransaction().add(R.id.fragmentContainer, fragment).commit();
        }
    }
}
