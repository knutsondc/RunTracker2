package com.dknutsonlaw.android.runtracker2;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.database.Cursor;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

/**
 * Created by dck on 9/7/15. Display all the Runs we've recorded in a ViewPager that displays a
 * RunFragment in each of its pages.
 */
@SuppressWarnings("ConstantConditions")
public class RunPagerActivity extends AppCompatActivity implements LoaderManager.LoaderCallbacks<Cursor> {
    private static final String TAG = "run_pager_activity";

    private RunManager mRunManager;
    private ViewPager mViewPager;
    private ResultsReceiver mResultsReceiver;
    private IntentFilter mIntentFilter;
    private BackgroundLocationService mLocationService;
    //Custom Adapter to feed RunFragments to the ViewPager
    private RunCursorFragmentStatePagerAdapter mAdapter;
    private long mRunId = -1;
    //Set a default sort order
    private int mSortOrder = Constants.SORT_BY_DATE_DESC;
    //Static method to invoke this Activity and cause it to make the designated RunFragment the
    //current view in the ViewPager
    public static Intent newIntent(Context packageContext, int sortOrder, long runId){
        Intent intent = new Intent(packageContext, RunPagerActivity.class);
        intent.putExtra(Constants.EXTRA_SORT_ORDER, sortOrder);
        intent.putExtra(Constants.EXTRA_RUN_ID, runId);
        return intent;
    }

    private final ServiceConnection mLocationServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BackgroundLocationService.LocalBinder binder =
                    (BackgroundLocationService.LocalBinder)service;
            mLocationService = binder.getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
        }
    };

    private final ViewPager.OnPageChangeListener mListener = new ViewPager.OnPageChangeListener() {
        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            //Do nothing
        }

        @Override
        public void onPageSelected(int position) {
            //Make sure that mRunId is always equal to the run id of the currently viewed
            //RunFragment as we page through them.
            //RunCursorFragmentStatePagerAdapter adapter =
            //        (RunCursorFragmentStatePagerAdapter)mViewPager.getAdapter();
            //RunFragment fragment = (RunFragment)adapter.getItem(position);
            RunFragment fragment = (RunFragment)mAdapter.getItem(position);
            mRunId = fragment.getArguments().getLong(Constants.ARG_RUN_ID, -1);
            //mRunManager.mPrefs.edit().putLong(Constants.SAVED_RUN_ID, mRunId).commit();
        }

        @Override
        public void onPageScrollStateChanged(int state) {
            //Do nothing
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_run_pager);

        mRunManager = RunManager.get(this);
        //The sort order and the Id of the RunFragment to make the current view in the ViewPager are
        //the critical initialization variable, so either retrieve them from the Intent that started
        //this Activity or retrieve them from the savedInstanceState Bundle.
        if (savedInstanceState != null) {
            Log.i(TAG, "Retrieved savedInstanceState");
            mSortOrder = savedInstanceState.getInt(Constants.SORT_ORDER);
            Log.i(TAG, "mSortOrder is " + mSortOrder);
            mRunId = savedInstanceState.getLong(Constants.SAVED_RUN_ID);
            Log.i(TAG, "mRunId is " + savedInstanceState.getLong(Constants.SAVED_RUN_ID));
        } else {
            Log.i(TAG, "Taking values from the Intent");
            mSortOrder = getIntent().getIntExtra(Constants.EXTRA_SORT_ORDER, Constants.KEEP_EXISTING_SORT);
            Log.i(TAG, "mSortOrder is " + mSortOrder);
            if (mSortOrder == Constants.KEEP_EXISTING_SORT) {
                Log.i(TAG, "SortOrder is KEEP_EXISTING_SORT - using SharedPrefs");
                mSortOrder = mRunManager.mPrefs.getInt(Constants.SORT_ORDER, Constants.SORT_BY_DATE_DESC);
            }
            mRunId = getIntent().getLongExtra(Constants.EXTRA_RUN_ID, -1);
            Log.i(TAG, "runId is " + mRunId);
        }
        mViewPager = (ViewPager) findViewById(R.id.activity_run_pager_view_pager);
        //Set up BroadcastReceiver to receive results of operations we're interested in.
        mIntentFilter = new IntentFilter(Constants.SEND_RESULT_ACTION);
        mIntentFilter.addAction(Constants.ACTION_DELETE_RUN);
        mResultsReceiver = new ResultsReceiver();
        Bundle args = setupAdapterLoaderAndSubtitle();
        getSupportLoaderManager().initLoader(Constants.RUN_LIST_LOADER, args, this);
    }

    public Bundle setupAdapterLoaderAndSubtitle(){
        //Set up the Adapter, Loader and Subtitle by constructing the initial data cursor based upon
        //the selected sort order
        Resources r = getResources();
        Bundle args = new Bundle();
        String subtitle;
        switch (mSortOrder){
            case Constants.SORT_BY_DATE_ASC:
                mAdapter = new RunCursorFragmentStatePagerAdapter(this, getSupportFragmentManager(),
                        mRunManager.queryRunsDateAsc());
                subtitle = r.getQuantityString(R.plurals.subtitle_date_asc,
                        mAdapter.getCount(), mAdapter.getCount());
                break;
            case Constants.SORT_BY_DATE_DESC:
                mAdapter = new RunCursorFragmentStatePagerAdapter(this, getSupportFragmentManager(),
                        mRunManager.queryRunsDateDesc());
                subtitle = r.getQuantityString(R.plurals.subtitle_date_desc,
                        mAdapter.getCount(), mAdapter.getCount());
                break;
            case Constants.SORT_BY_DISTANCE_ASC:
                mAdapter = new RunCursorFragmentStatePagerAdapter(this, getSupportFragmentManager(),
                        mRunManager.queryRunsDistanceAsc());
                subtitle = r.getQuantityString(R.plurals.subtitle_distance_asc,
                        mAdapter.getCount(), mAdapter.getCount());
                break;
            case Constants.SORT_BY_DISTANCE_DESC:
                mAdapter = new RunCursorFragmentStatePagerAdapter(this, getSupportFragmentManager(),
                        mRunManager.queryRunsDistanceDesc());
                subtitle = r.getQuantityString(R.plurals.subtitle_distance_desc,
                        mAdapter.getCount(), mAdapter.getCount());
                break;
            case Constants.SORT_BY_DURATION_ASC:
                mAdapter = new RunCursorFragmentStatePagerAdapter(this, getSupportFragmentManager(),
                        mRunManager.queryRunsDurationAsc());
                subtitle = r.getQuantityString(R.plurals.subtitle_duration_asc,
                        mAdapter.getCount(), mAdapter.getCount());
                break;
            case Constants.SORT_BY_DURATION_DESC:
                mAdapter = new RunCursorFragmentStatePagerAdapter(this, getSupportFragmentManager(),
                        mRunManager.queryRunsDurationDesc());
                subtitle = r.getQuantityString(R.plurals.subtitle_duration_desc,
                        mAdapter.getCount(), mAdapter.getCount());
                break;
            default:
                subtitle = r.getString(R.string.goof_up);
                Log.i(TAG, "Invalid sort order - how'd you get here!?!");
        }
        if (mAdapter.getCount() == 0) {
            getSupportActionBar().setSubtitle(r.getString(R.string.no_runs_recorded));
        } else {
            getSupportActionBar().setSubtitle(subtitle);
        }
        mViewPager.setAdapter(mAdapter);
        Log.i(TAG, "onCreate(), run id for current item is " + mRunId);
        //Make sure the ViewPager makes the designated Run's RunFragment the current view
        setViewPager((RunDatabaseHelper.RunCursor) mAdapter.getCursor(), mRunId);
        args.putInt(Constants.SORT_ORDER, mSortOrder);
        return args;
    }

    @Override
    public void onPause(){
        mViewPager.removeOnPageChangeListener(mListener);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mResultsReceiver);
        super.onPause();
    }

    @Override
    public void onStop(){
        unbindService(mLocationServiceConnection);
        super.onStop();
    }

    @Override
    public void onResume(){
        super.onResume();
        mViewPager.addOnPageChangeListener(mListener);
        LocalBroadcastManager.getInstance(this).registerReceiver(
                mResultsReceiver,
                mIntentFilter);

    }

    @Override
    public void onStart(){
        super.onStart();
        //Bind to the BackgroundLocationService here to enable use of its functions throughout this Activity.
        Intent intent = new Intent(this, BackgroundLocationService.class);
        bindService(intent, mLocationServiceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu){
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.run_pager_options, menu);
        //If we have fewer than two Runs, there's nothing to sort, so disable sort menu
        if (mAdapter.getCount() < 2){
            menu.findItem(R.id.run_pager_menu_item_sort_runs).setEnabled(false);
        } else {
            menu.findItem(R.id.run_pager_menu_item_sort_runs).setEnabled(true);
        }
        //If we're tracking a Run, don't allow creation of a new Run - trying to track more than one
        //Run will crash the app!
        menu.findItem(R.id.run_pager_menu_item_new_run).setEnabled(!mRunManager.isTrackingRun());
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item){
        Log.i(TAG, "In onOptionsItemSelected(), mRunId is " + mRunId);
        Resources r = getResources();
        //Create a new blank Run or change the sort order of the recorded Runs and the Activity's
        //subtitle to match
        switch(item.getItemId()){
            case R.id.run_pager_menu_item_new_run:
                Log.i(TAG, "In New Run menu, Runs in the adapter: " + mViewPager.getAdapter().getCount());
                //First, tell the ViewPager's adapter that its content is receiving an update
                mViewPager.getAdapter().startUpdate(mViewPager);
                //Now start a new blank run with nothing but a Start Date and a runId.
                mRunManager.startNewRun();
                //Update the subtitle
                //setSubtitle();
                setupAdapterLoaderAndSubtitle();
                return true;
            case R.id.menu_item_pager_delete_run:
                Log.i(TAG, "In Delete Run Menu, Runs in the adapter: " + mViewPager.getAdapter().getCount());
                //First, stop location updates if the Run we're deleting is currently being tracked
                if (mRunManager.isTrackingRun(mRunManager.getRun(mRunId))){
                    mLocationService.stopLocationUpdates();
                }
                Log.i(TAG, "Runs is Adapter before Run deletion: " + mAdapter.getCount());
                //Now order the Run to be deleted
                mRunManager.deleteRun(mRunId);
                return true;
            case R.id.run_pager_menu_item_sort_by_date_asc:
                changeSortOrder(Constants.SORT_BY_DATE_ASC);
                if (mAdapter.getCount() > 1) {
                    getSupportActionBar().setSubtitle(r.getQuantityString(
                            R.plurals.subtitle_date_asc, mAdapter.getCount(),
                            mAdapter.getCount()));
                } else {
                    getSupportActionBar().setSubtitle(r.getString(R.string.no_runs_recorded));
                }
                return true;
            case R.id.run_pager_menu_item_sort_by_date_desc:
                changeSortOrder(Constants.SORT_BY_DATE_DESC);
                if (mAdapter.getCount() > 1) {
                    getSupportActionBar().setSubtitle(r.getQuantityString(
                            R.plurals.subtitle_date_desc, mAdapter.getCount(),
                            mAdapter.getCount()));
                } else {
                    getSupportActionBar().setSubtitle(r.getString(R.string.no_runs_recorded));
                }
                return true;
            case R.id.run_pager_menu_item_sort_by_distance_asc:
                changeSortOrder(Constants.SORT_BY_DISTANCE_ASC);
                if (mAdapter.getCount() > 1) {
                    getSupportActionBar().setSubtitle(r.getQuantityString(
                            R.plurals.subtitle_distance_asc, mAdapter.getCount(),
                            mAdapter.getCount()));
                } else {
                    getSupportActionBar().setSubtitle(r.getString(R.string.no_runs_recorded));
                }
                return true;
            case R.id.run_pager_menu_item_sort_by_distance_desc:
                changeSortOrder(Constants.SORT_BY_DISTANCE_DESC);
                if (mAdapter.getCount() > 1) {
                    getSupportActionBar().setSubtitle(r.getQuantityString(
                            R.plurals.subtitle_distance_desc, mAdapter.getCount(),
                            mAdapter.getCount()));
                } else {
                    getSupportActionBar().setSubtitle(r.getString(R.string.no_runs_recorded));
                }
                return true;
            case R.id.run_pager_menu_item_sort_by_duration_asc:
                changeSortOrder(Constants.SORT_BY_DURATION_ASC);
                if (mAdapter.getCount() > 1) {
                    getSupportActionBar().setSubtitle(r.getQuantityString(
                            R.plurals.subtitle_duration_asc, mAdapter.getCount(),
                            mAdapter.getCount()));
                } else {
                    getSupportActionBar().setSubtitle(r.getString(R.string.no_runs_recorded));
                }
                return true;
            case R.id.run_pager_menu_item_sort_by_duration_desc:
                changeSortOrder(Constants.SORT_BY_DURATION_DESC);
                if (mAdapter.getCount() > 1) {
                    getSupportActionBar().setSubtitle(r.getQuantityString(
                            R.plurals.subtitle_duration_desc, mAdapter.getCount(),
                            mAdapter.getCount()));
                } else {
                    getSupportActionBar().setSubtitle(r.getString(R.string.no_runs_recorded));
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
    //Set the desired sort order, put it in a Bundle, restart the loader using the Bundle as an
    //argument, set the subtitle according to the sort order, and tell the user what's happened..
    private void changeSortOrder(int sortOrder){
        Bundle args = new Bundle();
        mSortOrder = sortOrder;
        mRunManager.mPrefs.edit().putInt(Constants.SORT_ORDER, sortOrder).commit();
        args.putInt(Constants.SORT_ORDER, sortOrder);
        getSupportLoaderManager().restartLoader(Constants.RUN_LIST_LOADER, args, this);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int d, Bundle args){
        //Construct a cursor loader according to the selected sort order
        if (args != null){
           mSortOrder = args.getInt(Constants.SORT_ORDER);
        } else {
            mSortOrder = Constants.SORT_BY_DATE_DESC;
        }
        Log.i(TAG, "onCreateLoader for sort order " + mSortOrder);
        return new RunListCursorLoader(this, Constants.URI_TABLE_RUN, mSortOrder);

    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor){
        RunDatabaseHelper.RunCursor newCursor = (RunDatabaseHelper.RunCursor)cursor;
        Log.i(TAG, "RunListCursorLoader onLoadFinished() called");
        //Swap the newly-returned cursor for the old cursor and close the old cursor
        mAdapter.changeCursor(newCursor);
        //If there are no Runs in the cursor, shut down this Activity and go back to the
        //RunRecyclerListActivity/Fragment, which has a special UI for when the database has no runs.
        if (mAdapter.getCount() == 0){
            finish();
        }
        //Reconstruct the adapter based upon the new cursor the loader has supplied us and set it
        //as the ViewPager's adapter
        //mAdapter = new RunCursorFragmentStatePagerAdapter(this, getSupportFragmentManager(), newCursor);
        //mViewPager.setAdapter(mAdapter);
        //Make sure we keep looking at the Run we were on when we changed sort order and created
        //a new cursor and adapter
        setViewPager(newCursor, mRunId);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader){
        //mViewPager.setAdapter(null);
    }

    @Override
    public void onSaveInstanceState(Bundle outState){
        outState.putLong(Constants.SAVED_RUN_ID, mRunId);
        outState.putInt(Constants.SORT_ORDER, mSortOrder);
    }
    //Set the ViewPager's current (displayed) item to the specified Run.
    private void setViewPager(RunDatabaseHelper.RunCursor cursor, long runId){
        cursor.moveToFirst();
        Log.i(TAG, "In setViewPager(), runId is " + runId);
        //Iterate over the Runs in the cursor until we find the one with an Id equal to the one we
        //specified in the runId parameter, then set the ViewPager's current item to that Run
        while (!cursor.isAfterLast()){
            if (cursor.getRun().getId() == runId){
                mViewPager.setCurrentItem(cursor.getPosition());
                break;
            }
            cursor.moveToNext();
        }
    }

    //Custom adapter to feed RunFragments to the ViewPager
    private class RunCursorFragmentStatePagerAdapter extends CursorFragmentStatePagerAdapter{

        public RunCursorFragmentStatePagerAdapter(Context context, FragmentManager fm, Cursor cursor){
            super(context, fm, cursor);
        }
        //Pull a Run from the supplied cursor and retrieve a RunFragment for it using its RunId
        @Override
        public Fragment getItem(Context context, Cursor cursor){
            RunDatabaseHelper.RunCursor runCursor = (RunDatabaseHelper.RunCursor)cursor;
            long runId = runCursor.getRun().getId();
            if (runId != -1){
                return RunFragment.newInstance(runId);
            } else {
                //We should never get here - Runs are assigned a RunId as soon as get created and
                //before they get added to the ViewPager, but we have return something in an "else"
                //block to keep the compiler happy.
                return null;
            }
        }
    }
    //Class to allow us to receive reports of results of the operation the ViewPager is interested
    //in, ACTION_INSERT_RUN.
    private class ResultsReceiver extends BroadcastReceiver{
        @Override
        public void onReceive(Context context, Intent intent){
            String action = intent.getAction();
            Log.i(TAG, "Action in ResultsReceiver is " + action);
            if (action.equals(Constants.SEND_RESULT_ACTION)){
                Log.i(TAG, "RunPagerActivity action is SEND_RESULT_ACTION");
                String actionAttempted = intent.getStringExtra(Constants.ACTION_ATTEMPTED);
                Log.i(TAG, "actionAttempted is " + actionAttempted);
                if (actionAttempted.equals(Constants.ACTION_INSERT_RUN)){
                    Run run = intent.getParcelableExtra(Constants.EXTENDED_RESULTS_DATA);
                    if (run.getId() != -1) {
                        //Now that the new Run has been added to the database, we need to tell the
                        //adapter that its update has been completed and its dataset changed.
                        mRunId = run.getId();
                        setupAdapterLoaderAndSubtitle();
                    } else {
                        Toast.makeText(RunPagerActivity.this, R.string.insert_run_error,
                                Toast.LENGTH_LONG).show();
                    }
                    //mViewPager.getAdapter().finishUpdate(mViewPager);
                    //mViewPager.getAdapter().notifyDataSetChanged();
                    Log.i(TAG, "in ResultsReceiver on Insert Run, Runs in adapter: " + mViewPager.getAdapter().getCount());
                } //ViewPager isn't interested in any other ACTION_ATTEMPTED, so no "else" clauses
                  //specifying what to do with them needed.
            } else if (action.equals(Constants.ACTION_DELETE_RUN)){
                Log.i(TAG, "RunPagerActivity action is ACTION_DELETE_RUN");
                //RunDataBaseHelper's deleteRun() function returns to the IntentService
                //an int[] with two members,the number of Locations deleted as element 0
                //(LOCATION_DELETIONS) and the number of Runs deleted as element 1 (RUN_DELETIONS).
                //That array is passed along here for display to the user.
                int [] results =
                        intent.getIntArrayExtra(Constants.EXTENDED_RESULTS_DATA);
                //The getWritableDatabase().delete() method returns the number of rows affected upon
                //success and -1 upon error. Check if either result returned is an error.
                if (results[Constants.RUN_DELETIONS] == -1 ||
                        results[Constants.LOCATION_DELETIONS] == -1) {
                    //Tell the user if there was an error deleting a Run entry.
                    if (results[Constants.RUN_DELETIONS] == -1) {
                        Toast.makeText(RunPagerActivity.this, R.string.delete_run_error,
                                Toast.LENGTH_LONG).show();
                    }
                    //Tell the user if there was an error deleting a Location entry.
                    if (results[Constants.LOCATION_DELETIONS] == -1) {
                        Toast.makeText(RunPagerActivity.this, R.string.delete_locations_error,
                                Toast.LENGTH_LONG).show();
                    }
                    //Report results to the user upon successful deletions.
                } else {
                    setupAdapterLoaderAndSubtitle();
                    Resources r = getResources();

                    Toast.makeText(RunPagerActivity.this, r.getQuantityString(R.plurals.runs_deletion_results,
                                    results[Constants.RUN_DELETIONS],
                                    results[Constants.RUN_DELETIONS],
                                    results[Constants.LOCATION_DELETIONS]),
                            Toast.LENGTH_LONG).show();
                }

                Log.i(TAG, "In ResultsReceiver ACTION_RUN_DELETE, Runs in adapter: " + mViewPager.getAdapter().getCount());
            } else {
                //Shouldn't ever get here - intent filter limits us to SEND_RESULT_ACTION
                //and ACTION_DELETE_RUN
                Log.i(TAG, "Intent Action wasn't SEND_RESULT_ACTION or ACTION_DELETE_RUN");
            }
        }
    }
}
