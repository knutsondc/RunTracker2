package com.dknutsonlaw.android.runtracker2;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * Created by dck on 11/12/16. A fragment designed to hold in two FrameLayouts the RunFragment and
 * RunMapFragment for a given Run.
 */

public class HolderFragment extends Fragment {
    private static final String TAG = "HolderFragment";

    //private RunFragment mRunFragment;
    private RunMapFragment mRunMapFragment;
    private long mRunId;

    public HolderFragment(){

    }

    public static HolderFragment newInstance(long runId){
        Bundle args = new Bundle();
        args.putLong(Constants.ARG_RUN_ID, runId);
        HolderFragment hf = new HolderFragment();
        hf.setArguments(args);
        return hf;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        //It's easier to keep the connection to the BackgroundLocationService by retaining the fragment
        //instance than any other method I've found
        setRetainInstance(true);
        Bundle args = getArguments();
        if (args != null) {
            long runId = args.getLong(Constants.ARG_RUN_ID, -1);
            Log.i(TAG, "onCreate() runId is " + runId);
            //If the run already has an id, it will have database records associated with it that
            //need to be loaded using Loaders.
            if (runId != -1) {
                mRunId = runId;
                Log.i(TAG, "mRunId is " + mRunId + " in onCreate()");
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){
        super.onCreateView(inflater, container, savedInstanceState);
        View v = inflater.inflate(R.layout.holder_fragment, container, false);
        FragmentManager childFragmentManager = getChildFragmentManager();
        FragmentTransaction childFragmentTransaction = childFragmentManager.beginTransaction();
        RunFragment runFragment = RunFragment.newInstance(mRunId);
        childFragmentTransaction.add(R.id.runfragment_holder, runFragment);
        childFragmentTransaction.commit();
        //Turn off the DisplayHomeAsUp - we might not return to the correct instance of RunFragment
        //noinspection ConstantConditions
        if (((AppCompatActivity)getActivity()).getSupportActionBar() != null){
            //noinspection ConstantConditions
            ((AppCompatActivity)getActivity()).getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        }
        return v;
    }

    public void onReadyForMap(){
        FragmentManager childFragmentManager = getChildFragmentManager();
        FragmentTransaction childFragmentTransaction = childFragmentManager.beginTransaction();
        mRunMapFragment = RunMapFragment.newInstance(mRunId);
        childFragmentTransaction.add(R.id.runmapfragment_holder, mRunMapFragment);
        childFragmentTransaction.commit();
    }

    public RunMapFragment getRunMapFragment(){
        return mRunMapFragment;
    }

    /*public RunFragment getRunFragment(){
        return mRunFragment;
    }*/
}
