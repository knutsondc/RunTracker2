package com.dknutsonlaw.android.runtracker2;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import java.lang.ref.WeakReference;

/**
 * Created by dck on 9/25/16.
 * This Activity hosts a ViewPager within which RunMapFragments are displayed.
 */

@SuppressLint("Registered")
public class RunMapPagerActivity extends AppCompatActivity
        implements LoaderManager.LoaderCallbacks<Cursor>,
        DeleteRunsDialog.DeleteRunsDialogListener{
    private static final String TAG = "runmappageractivity";

    private RunManager mRunManager;
    private ViewPager mViewPager;
    private final Messenger mMessenger = new Messenger(new RunMapPagerActivity.IncomingMessenger(this));
    private Messenger mLocationService = null;
    private ResultsReceiver mResultsReceiver;
    private IntentFilter mIntentFilter;
    //Custom Adapter to feed RunFragments to the ViewPager
    private RunMapPagerActivity.RunCursorMapFragmentStatePagerAdapter mAdapter;
    private long mRunId = -1;
    //Set a default sort order
    private int mSortOrder = Constants.SORT_BY_DATE_DESC;
    //Static method to invoke this Activity and cause it to make the designated RunMapFragment the
    //current view in the ViewPager
    public static Intent newIntent(Context packageContext, int sortOrder, long runId){
        Intent intent = new Intent(packageContext, RunMapPagerActivity.class);
        intent.putExtra(Constants.EXTRA_SORT_ORDER, sortOrder);
        intent.putExtra(Constants.EXTRA_RUN_ID, runId);
        return intent;
    }

    private final ServiceConnection mLocationServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mLocationService = new Messenger(service);
            try{
                Message msg = Message.obtain(null, Constants.MESSAGE_REGISTER_CLIENT, Constants.MESSENGER_RUNMAPPAGERACTIVITY, 0);
                msg.replyTo = mMessenger;
                mLocationService.send(msg);
            } catch (RemoteException e){
                Log.i(TAG, "Caught RemoteException while trying to send MESSAGE_REGISTER_CLIENT");
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mLocationService = null;
        }
    };

    private final ViewPager.SimpleOnPageChangeListener mOnPageChangeListener = new ViewPager.SimpleOnPageChangeListener() {

        @Override
        public void onPageSelected(int position) {
            //Make sure that mRunId is always equal to the run id of the currently viewed
            //RunFragment as we page through them.
            Log.i(TAG, "PageChangedListener fetching fragment from position " + position);
            RunMapFragment fragment = (RunMapFragment)mAdapter.getItem(position);
            mRunId = fragment.getArguments().getLong(Constants.ARG_RUN_ID, -1);
            Log.i(TAG, "PageChangeListener fetched Run #" + mRunId + " at position " + position);
            setSubtitle();
            setViewPager((RunDatabaseHelper.RunCursor)mAdapter.getCursor(), mRunId);
            //Write the RunId to Shared Prefs so that if the user goes back to RunPagerActivity, the
            //same Run will be displayed there
            mRunManager.mPrefs.edit().putLong(Constants.ARG_RUN_ID, mRunId).apply();
            Log.i(TAG, "Wrote RunId " + mRunId + " to Shared Prefs");
        }

    };

    private final ViewPager.OnAdapterChangeListener mOnAdapterChangeListener = new ViewPager.OnAdapterChangeListener() {
        @Override
        public void onAdapterChanged(@NonNull ViewPager viewPager, @Nullable PagerAdapter oldAdapter, @Nullable PagerAdapter newAdapter) {
            //A new Adapter means a new SortOrder, so we need to update the Run's position in the
            //Adapter and ViewPager so that the RecyclerView can scroll to it when we go back there
            mRunManager.mPrefs.edit().putInt(Constants.ADAPTER_POSITION, mViewPager.getCurrentItem()).apply();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_run_map_pager);

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
        mViewPager = (ViewPager) findViewById(R.id.activity_run_map_pager_view_pager);
        //Force all RunMapFragments to be created only when called to be displayed
        //mViewPager.setOffscreenPageLimit(0);
        //Set up BroadcastReceiver to receive results of operations we're interested in.
        mIntentFilter = new IntentFilter(Constants.SEND_RESULT_ACTION);
        mIntentFilter.addAction(Constants.ACTION_DELETE_RUN);
        mResultsReceiver = new ResultsReceiver();
        Bundle args = setupAdapterAndLoader();
        getSupportLoaderManager().initLoader(Constants.RUN_LIST_LOADER, args, this);
    }

    private Bundle setupAdapterAndLoader(){
        //Set up the Adapter and  Loader by constructing the initial data cursor based upon
        //the selected sort order

        Bundle args = new Bundle();
        RunDatabaseHelper.RunCursor cursor = null;
        switch (mSortOrder){
            case Constants.SORT_BY_DATE_ASC:
                cursor = mRunManager.queryRunsDateAsc();
                break;
            case Constants.SORT_BY_DATE_DESC:
                cursor = mRunManager.queryRunsDateDesc();
                break;
            case Constants.SORT_BY_DISTANCE_ASC:
                cursor = mRunManager.queryRunsDistanceAsc();
                break;
            case Constants.SORT_BY_DISTANCE_DESC:
                cursor = mRunManager.queryRunsDistanceDesc();
                break;
            case Constants.SORT_BY_DURATION_ASC:
                cursor = mRunManager.queryRunsDurationAsc();
                break;
            case Constants.SORT_BY_DURATION_DESC:
                cursor = mRunManager.queryRunsDurationDesc();
                break;
            default:
                Log.i(TAG, "Invalid sort order - how'd you get here!?!");
                //setSubtitle();
        }
        mAdapter = new RunCursorMapFragmentStatePagerAdapter(this, getSupportFragmentManager(), cursor);
        mViewPager.setAdapter(mAdapter);
        //Make sure the ViewPager makes the designated Run's RunFragment the current view
        setViewPager((RunDatabaseHelper.RunCursor) mAdapter.getCursor(), mRunId);
        setSubtitle();
        //If there aren't any Runs left to display, close this Activity and go back to the RunRecyclerList
        //Activity and Fragment, which displays a message to the user.
        if (mAdapter.getCount() == 0) {
            finish();
        }
        Log.i(TAG, "onCreate(), run id for current item is " + mRunId);
        args.putInt(Constants.SORT_ORDER, mSortOrder);
        return args;
    }

    /*Setting the subtitle is broken out into a separate method from setting up the adapter
    * and loader because we need to change the subtitle whenever the Run displayed is changed and
    * we don't want to construct a new adapter and a new loader every time we do that*/
    private void setSubtitle(){
        Resources r = getResources();
        String subtitle;
        switch (mSortOrder){
            case Constants.SORT_BY_DATE_ASC:
                subtitle = r.getQuantityString(R.plurals.viewpager_subtitle_date_asc,
                        mAdapter.getCount(), mViewPager.getCurrentItem() + 1, mAdapter.getCount());
                break;
            case Constants.SORT_BY_DATE_DESC:
                subtitle = r.getQuantityString(R.plurals.viewpager_subtitle_date_desc,
                        mAdapter.getCount(), mViewPager.getCurrentItem() + 1, mAdapter.getCount());
                break;
            case Constants.SORT_BY_DISTANCE_ASC:
                subtitle = r.getQuantityString(R.plurals.viewpager_subtitle_distance_asc,
                        mAdapter.getCount(), mViewPager.getCurrentItem() +1, mAdapter.getCount());
                break;
            case Constants.SORT_BY_DISTANCE_DESC:
                subtitle = r.getQuantityString(R.plurals.viewpager_subtitle_distance_desc,
                        mAdapter.getCount(), mViewPager.getCurrentItem() + 1, mAdapter.getCount());
                break;
            case Constants.SORT_BY_DURATION_ASC:
                subtitle = r.getQuantityString(R.plurals.viewpager_subtitle_duration_asc,
                        mAdapter.getCount(), mViewPager.getCurrentItem() + 1, mAdapter.getCount());
                break;
            case Constants.SORT_BY_DURATION_DESC:
                subtitle = r.getQuantityString(R.plurals.viewpager_subtitle_duration_desc,
                        mAdapter.getCount(), mViewPager.getCurrentItem() + 1, mAdapter.getCount());
                break;
            default:
                subtitle = r.getString(R.string.goof_up);
        }
        if (getSupportActionBar() != null) {
            getSupportActionBar().setSubtitle(subtitle);
        }
    }

    @Override
    public void onPause(){
        mViewPager.removeOnPageChangeListener(mOnPageChangeListener);
        mViewPager.removeOnAdapterChangeListener(mOnAdapterChangeListener);
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
        mViewPager.addOnPageChangeListener(mOnPageChangeListener);
        mViewPager.addOnAdapterChangeListener(mOnAdapterChangeListener);
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
        inflater.inflate(R.menu.run_map_pager_options, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu){
        //Set title of menuItem to change distance display units according to current setting
        if (mRunManager.mPrefs.getBoolean(Constants.MEASUREMENT_SYSTEM, Constants.IMPERIAL)){
            menu.findItem(R.id.run_map_pager_activity_units).setTitle(R.string.imperial);
        } else {
            menu.findItem(R.id.run_map_pager_activity_units).setTitle(R.string.metric);
        }
        //If we have fewer than two Runs, there's nothing to sort, so disable sort menu
        if (mAdapter.getCount() < 2){
            menu.findItem(R.id.run_map_pager_menu_item_sort_runs).setEnabled(false);
        } else {
            menu.findItem(R.id.run_map_pager_menu_item_sort_runs).setEnabled(true);
        }
        //If we're tracking a Run, don't allow creation of a new Run - trying to track more than one
        //Run will crash the app!
        menu.findItem(R.id.run_map_pager_menu_item_new_run).setEnabled(!mRunManager.isTrackingRun());
        //Change title of menuItem for map scrolling depending upon current map scrolling setting for
        //the RunMapFragment that's currently displayed.
        RunMapFragment fragment = (RunMapFragment) mAdapter.getRegisteredFragment(mViewPager.getCurrentItem());
        //Have to check if the fragment is null -- it will be when we get here immediately after deleting
        //the currently displayed RunMapFragment!
        /*if (fragment != null) {
            //Need to check whether the RunMapFragment's GoogleMap is null because when this Activity is
            //first instantiated, the currently displayed RunMapFragment's GoogleMap will not yet have
            //been created.
            if (fragment.mGoogleMap != null) {
                if (fragment.mGoogleMap.getUiSettings().isScrollGesturesEnabled()) {
                    mOptionsMenu.findItem(R.id.run_map_pager_activity_scroll).setTitle(R.string.map_scrolling_off);
                } else {
                    mOptionsMenu.findItem(R.id.run_map_pager_activity_scroll).setTitle(R.string.map_scrolling_on);
                }
            }
        }*/
        Log.i(TAG, "Entered onPrepareOptionsMenu() section concerning scroll menu for Run " + mRunId);
        if (mRunManager.isTrackingRun(mRunManager.getRun(mRunId))){
            Log.i(TAG, "We are tracking Run " + mRunId);

            if(!mRunManager.mPrefs.getBoolean(Constants.SCROLLABLE, false)) {
                Log.i(TAG, "Tracking Run " + mRunId + " in ViewMode inconsistent with scrolling." +
                        "Turning scrolling off.");
                menu.findItem(R.id.run_map_pager_activity_scroll)
                        .setEnabled(false)
                        .setTitle(R.string.map_scrolling_on);
                Log.i(TAG, "After attempting to turn off scrolling, is it off? " + !menu.findItem(R.id.run_map_pager_activity_scroll).isEnabled());
            } else {
                Log.i(TAG, "No need to disable scroll menu for Run " + mRunId + ", so enable it.");
                if (fragment.mGoogleMap != null) {
                    //Toggle scrolling menu title
                    if (fragment.mGoogleMap.getUiSettings().isScrollGesturesEnabled()) {
                        Log.i(TAG, "Scrolling is enabled for Run " + mRunId + " so display Scrolling Off in menu.");
                        menu.findItem(R.id.run_map_pager_activity_scroll)
                                .setEnabled(true)
                                .setTitle(R.string.map_scrolling_off);
                    } else {
                        Log.i(TAG, "Scrolling not enabled for Run " + mRunId + "so display Scrolling On in menu.");
                        menu.findItem(R.id.run_map_pager_activity_scroll)
                                .setEnabled(true)
                                .setTitle(R.string.map_scrolling_on);
                    }
                }
            }
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item){
        Log.i(TAG, "In onOptionsItemSelected(), mRunId is " + mRunId);
        Bundle args;
        //Create a new blank Run or change the sort order of the recorded Runs and the Activity's
        //subtitle to match
        switch(item.getItemId()){

            case R.id.run_map_pager_activity_units:
                //Swap the measurement system value stored in shared prefs
                mRunManager.mPrefs.edit().putBoolean(Constants.MEASUREMENT_SYSTEM,
                        !mRunManager.mPrefs.getBoolean(Constants.MEASUREMENT_SYSTEM, Constants.IMPERIAL)).apply();
                Intent refreshIntent = new Intent(Constants.ACTION_REFRESH_MAPS);
                //Tell all the RunMapFragments to update so their textviews will display the newly-chosen
                //distance units
                boolean receiver = LocalBroadcastManager.getInstance(this).sendBroadcast(refreshIntent);
                if(!receiver){
                    Log.i(TAG, "No receiver for RunFragment REFRESH broadcast!");
                }
                //We want to change the menuItem title in onPrepareOptionsMenu, so we need to invalidate
                //the menu and recreated it.
                invalidateOptionsMenu();
                return true;

            case R.id.run_map_pager_activity_scroll:
                //This setting gets made in a RunMapFragment's MapView, but we want to put this together
                //with the measurement units menu item here in the Activity's OptionsMenu, so we first
                //have to retrieve the RunMapFragment that's currently displayed
                RunMapFragment fragment = (RunMapFragment) mAdapter.getRegisteredFragment(mViewPager.getCurrentItem());
                fragment.mGoogleMap.getUiSettings()
                        .setScrollGesturesEnabled(!fragment.mGoogleMap.getUiSettings().isScrollGesturesEnabled());
                //We want to change the menuItem title in onPrepareOptionsMenu, so we need to invalidate
                //the menu and recreated it.
                invalidateOptionsMenu();
                return true;

            case R.id.run_map_pager_menu_item_new_run:
                //Now that we're using an auto-updating loader for the list of runs, we don't need
                //to call startActivityForResult() - the loader and content observer system take
                //care of updating the list to reflect the presence of a new Run. Note that we
                //don't have to call restartLoader() on the RUN_LIST_LOADER because the query on
                //the database hasn't changed.
                TrackingLocationIntentService.startActionInsertRun(this, new Run());
                return true;

            case R.id.menu_item_map_pager_delete_run:
                //Bring up a dialog asking for confirmation of deletion of this Run, passing along
                //identity of this Activity and that there's only one Run to be deleted so that
                //the dialog's message will be correct.
                Bundle bundle = new Bundle();
                bundle.putInt(Constants.FRAGMENT, Constants.RUN_MAP_FRAGMENT);
                bundle.putInt(Constants.NUMBER_OF_RUNS, 1);
                DeleteRunsDialog dialog = new DeleteRunsDialog();
                dialog.setArguments(bundle);
                dialog.show(getSupportFragmentManager(), "DeleteDialog");
                return true;
            //To change the sort order, set mSortOrder, store it to SharedPrefs, reinitialize the
            //adapter and subtitle and restart the RunListLoader
            case R.id.run_map_pager_menu_item_sort_by_date_asc:
                mSortOrder = Constants.SORT_BY_DATE_ASC;
                break;
            case R.id.run_map_pager_menu_item_sort_by_date_desc:
                mSortOrder = Constants.SORT_BY_DATE_DESC;
                break;
            case R.id.run_map_pager_menu_item_sort_by_distance_asc:
                mSortOrder = Constants.SORT_BY_DISTANCE_ASC;
                break;
            case R.id.run_map_pager_menu_item_sort_by_distance_desc:
                mSortOrder = Constants.SORT_BY_DISTANCE_DESC;
                break;
            case R.id.run_map_pager_menu_item_sort_by_duration_asc:
                mSortOrder = Constants.SORT_BY_DURATION_ASC;
                break;
            case R.id.run_map_pager_menu_item_sort_by_duration_desc:
                mSortOrder = Constants.SORT_BY_DURATION_DESC;
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        //Save Sort Order to Shared Prefs so that when when we go back to the RunPagerActivity,
        //the Runs will be sorted in the same order
        mRunManager.mPrefs.edit().putInt(Constants.SORT_ORDER, mSortOrder).apply();
        args = setupAdapterAndLoader();
        getSupportLoaderManager().restartLoader(Constants.RUN_LIST_LOADER, args, this);
        return true;
    }

    private void deleteRun(){
        Log.i(TAG, "In Delete Run Menu, Runs in the adapter: " + mViewPager.getAdapter().getCount());
        //First, stop location updates if the Run we're deleting is currently being tracked
        if (mRunManager.isTrackingRun(mRunManager.getRun(mRunId))){
            try {
                mLocationService.send(Message.obtain(null, Constants.MESSAGE_STOP_LOCATION_UPDATES));
            } catch (RemoteException e){
                Log.i(TAG, "Caught RemoteException while trying to send MESSAGE_STOP_LOCATION_UPDATES");
            }
            mRunManager.stopRun();
        }
        Log.i(TAG, "Runs in Adapter before Run deletion: " + mAdapter.getCount());
        //Now order the Run to be deleted. The Adapter, Subtitle and Loader will get reset
        //when the results of the Run deletion get reported to the ResultsReceiver
        Log.i(TAG, "Trying to delete Run " + mRunId);
        int locations = mRunManager.queryLocationsForRun(mRunId).getCount();
        Log.i(TAG, "There are " + locations + " locations to be deleted for Run " + mRunId);
        mAdapter.startUpdate(mViewPager);
        TrackingLocationIntentService.startActionDeleteRun(this, mRunId);
    }

    @Override
    public void onDeleteRunsDialogPositiveClick(int which){
        //Callback from dialog confirming deletion of the currently displayed Run
        if (which == Constants.RUN_MAP_FRAGMENT) {
            deleteRun();
        }
    }

    @Override
    public void onDeleteRunsDialogNegativeClick(int which){
        //We need do nothing here, but the interface requires it to be implemented.
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
                mRunManager.mPrefs.edit().putInt(Constants.ADAPTER_POSITION, mViewPager.getCurrentItem()).apply();
                break;
            }
            cursor.moveToNext();
        }
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
        Log.i(TAG, "RunListCursorLoader onLoadFinished() called for Run #" + mRunId);
        //The loader takes care of releasing the old cursor, so call swapCursor(), not changeCursor()
        mAdapter.swapCursor(newCursor);
        //If there are no Runs in the cursor, shut down this Activity and go back to the
        //RunRecyclerListActivity/Fragment, which has a special UI for when the database has no runs.
        if (mAdapter.getCount() == 0){
            finish();
        }
        //Make sure we keep looking at the Run we were on before the Loader updated
        setViewPager(newCursor, mRunId);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader){

    }

    //Custom adapter to feed RunMapFragments to the ViewPager
    protected class RunCursorMapFragmentStatePagerAdapter extends CursorFragmentStatePagerAdapter {

        RunCursorMapFragmentStatePagerAdapter(Context context, FragmentManager fm, Cursor cursor){
            super(context, fm, cursor);
        }

        //Pull a run from the supplied Cursor and retrieve a RunMapFragment for it using its RunId
        @Override
        public Fragment getItem(Context context, Cursor cursor){
            RunDatabaseHelper.RunCursor runCursor = (RunDatabaseHelper.RunCursor)cursor;
            long runId = runCursor.getRun().getId();
            if (runId != -1){
                return RunMapFragment.newInstance(runId);
            } else {
                //We should never get here - Runs are assigned a RunId as soon as they get created and
                //before they get added to the RunFragment ViewPager, but we have return something in
                //an "else" block to keep the compiler happy.
                return null;
            }
        }
    }

    //Handler to receive Messages from the  BackgroundLocationService that location updates have
    //been started or stopped. Static class to avoid memory leaks by preventing an implicit reference
    //to the Activity from stopping the Activity from getting garbage collected.
    private static class IncomingMessenger extends Handler {
        //Use a WeakReference to the Activity to all access to the Activity instance's methods from
        //a static context
        private final WeakReference<RunMapPagerActivity> mActivity;

        IncomingMessenger(RunMapPagerActivity activity){
            mActivity = new WeakReference<>(activity);
        }

        public void handleMessage(Message msg){
            RunMapPagerActivity activity = mActivity.get();
            if (activity != null) {

                switch (msg.what) {
                    case Constants.MESSAGE_LOCATION_UPDATES_STARTED:
                        Log.i(TAG, "Reached MESSAGE_LOCATION_UPDATES_STARTED in RunPagerActivity");
                        activity.mViewPager.getAdapter().notifyDataSetChanged();
                        break;
                    case Constants.MESSAGE_LOCATION_UPDATES_STOPPED:
                        Log.i(TAG, "Reached MESSAGE_LOCATION_UPDATES_STOPPED in RunPagerActivity");
                        activity.mViewPager.getAdapter().notifyDataSetChanged();
                        break;
                }
            }
        }
    }

    //Class to allow us to receive reports of results of the operations the ViewPager is interested
    //in, ACTION_INSERT_RUN and ACTION_DELETE_RUN.
    private class ResultsReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent){
            String action = intent.getAction();
            Log.i(TAG, "Action in RunMapPagerActivity ResultsReceiver is " + action);
            switch (action) {
                case Constants.SEND_RESULT_ACTION:
                    Log.i(TAG, "RunMapPagerActivity action is SEND_RESULT_ACTION");
                    String actionAttempted = intent.getStringExtra(Constants.ACTION_ATTEMPTED);
                    Log.i(TAG, "actionAttempted is " + actionAttempted);
                    if (actionAttempted.equals(Constants.ACTION_INSERT_RUN)) {
                        Run run = intent.getParcelableExtra(Constants.EXTENDED_RESULTS_DATA);
                        if (run.getId() != -1) {
                            //Now that the new Run has been added to the database, we need to open
                            //its RunFragment in the RunPagerActivity
                            mRunManager.mPrefs.edit().putLong(Constants.ARG_RUN_ID, run.getId()).apply();
                            Log.i(TAG, "Got Run " + run.getId() + " in ResultsReceiver.");
                            Intent runPagerIntent = RunPagerActivity.newIntent(getApplicationContext(), Constants.KEEP_EXISTING_SORT, run.getId());
                            startActivity(runPagerIntent);
                            //setupAdapterAndLoader();
                        } else {
                            Toast.makeText(RunMapPagerActivity.this, R.string.insert_run_error,
                                    Toast.LENGTH_LONG).show();
                        }
                        //Log.i(TAG, "in ResultsReceiver on Insert Run, Runs in adapter: " + mViewPager.getAdapter().getCount());
                        //mAdapter.finishUpdate(mViewPager);
                    } //ViewPager isn't interested in any other ACTION_ATTEMPTED, so no "else" clauses
                    //specifying what to do with them needed.
                    break;
                case Constants.ACTION_DELETE_RUN:
                    Log.i(TAG, "RunMapPagerActivity action is ACTION_DELETE_RUN");
                    //RunDataBaseHelper's deleteRun() function returns to the IntentService
                    //an int[] with two members,the number of Locations deleted as element 0
                    //(LOCATION_DELETIONS) and the number of Runs deleted as element 1 (RUN_DELETIONS).
                    //That array is passed along here for display to the user.
                    int[] results =
                            intent.getIntArrayExtra(Constants.EXTENDED_RESULTS_DATA);
                    //The getWritableDatabase().delete() method returns the number of rows affected upon
                    //success and -1 upon error. Check if either result returned is an error.
                    if (results[Constants.RUN_DELETIONS] == -1 ||
                            results[Constants.LOCATION_DELETIONS] == -1) {
                        //Tell the user if there was an error deleting a Run entry.
                        if (results[Constants.RUN_DELETIONS] == -1) {
                            Toast.makeText(RunMapPagerActivity.this, R.string.delete_run_error,
                                    Toast.LENGTH_LONG).show();
                        }
                        //Tell the user if there was an error deleting a Location entry.
                        if (results[Constants.LOCATION_DELETIONS] == -1) {
                            Toast.makeText(RunMapPagerActivity.this, R.string.delete_locations_error,
                                    Toast.LENGTH_LONG).show();
                        }
                        //Report results to the user upon successful deletions and reset the Adapter,
                        //Subtitle and Loader.
                    } else {
                        //Trying to delete the last Run from this Activity after having deleted other
                        //Runs results in a problem: mRunId remains set to the last previous Run that
                        //was deleted, so we get an error for trying to delete a Run that's already
                        //been deleted. Thus, we need some technique to set a valid RunId for the
                        //new current view after deleting a Run.

                        //We use the position in the ViewPager held by the Run we just deleted to select
                        //what RunId should be after the deletion. If the ViewPager held only one
                        //child view before the deletion, we know we just deleted the last Run so we
                        //can just finish this activity and go back to RunRecyclerView
                        if (mViewPager.getChildCount() == 1){
                            Log.i(TAG, "Upon entry to ResultsReceiver, ACTION_DELETE_RUN:, getChildCount() is 1, so call finish()");
                            mAdapter.finishUpdate(mViewPager);
                            finish();
                            //If there was more than one Run held in the ViewPager, set the ViewPager's
                            //current view item to the view that's in the next higher position in the
                            //ViewPager unless we were already at the highest position, in which case
                            //set the ViewPager's current view item to the view that's in the next lower
                            //position in the ViewPager.
                        } else {
                            int currentPosition = mViewPager.getCurrentItem();
                            Log.i(TAG, "In ResultsReceiver, currentPosition is " + currentPosition + " and getChildCount() is " + mViewPager.getChildCount());
                            //Get the fragment associated with the child view we're going to move
                            //to and get its RunId from the arguments that were attached to the
                            //fragment when it was created. Is there a better way to do this? Why
                            //doesn't the onPageChangeListener correctly report the fragment displayed
                            //in the last remaining page of a ViewPager?
                            if (currentPosition < mViewPager.getChildCount() - 1) {
                                int index = currentPosition + 1;
                                mViewPager.setCurrentItem(index);
                                RunMapFragment fragment = (RunMapFragment) mAdapter.getItem(index);
                                mRunId = fragment.getArguments().getLong(Constants.ARG_RUN_ID);
                                Log.i(TAG, "After Run deletion, we moved UP one position and RunId is " + mRunId);
                            } else {
                                int index = currentPosition - 1;
                                mViewPager.setCurrentItem(index);
                                RunMapFragment fragment = (RunMapFragment)mAdapter.getItem(index);
                                mRunId = fragment.getArguments().getLong(Constants.ARG_RUN_ID);
                                Log.i(TAG, "After Run deletion, we moved DOWN one position and RunId is " + mRunId);
                            }
                        }
                        //Now that we've got a "legal" mRunId, we can fetch a new cursor, reconstruct
                        //the adapter, and set the subtitle accordingly.
                        setupAdapterAndLoader();
                        Resources r = getResources();

                        Toast.makeText(RunMapPagerActivity.this, r.getQuantityString(R.plurals.runs_deletion_results,
                                results[Constants.RUN_DELETIONS],
                                results[Constants.RUN_DELETIONS],
                                results[Constants.LOCATION_DELETIONS]),
                                Toast.LENGTH_LONG).show();
                    }
                    mAdapter.finishUpdate(mViewPager);
                    Log.i(TAG, "In ResultsReceiver ACTION_RUN_DELETE, Runs in adapter: " + mViewPager.getAdapter().getCount());
                    break;
                default:
                    //Shouldn't ever get here - intent filter limits us to SEND_RESULT_ACTION
                    //and ACTION_DELETE_RUN
                    Log.i(TAG, "Intent Action wasn't SEND_RESULT_ACTION or ACTION_DELETE_RUN");
            }
        }
    }
}
