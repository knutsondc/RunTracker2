package com.dknutsonlaw.android.runtracker2;

import android.content.BroadcastReceiver;
//import android.content.ComponentName;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
//import android.content.ServiceConnection;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.database.Cursor;
import android.os.Bundle;
//import android.os.Handler;
//import android.os.IBinder;
//import android.os.Message;
//import android.os.Messenger;
//import android.os.RemoteException;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

/**
 * Created by dck on 9/7/15. Display all the Runs we've recorded in a ViewPager that displays a
 * CombinedFragment in each of its pages.
 */
@SuppressWarnings("ConstantConditions")
public class RunPagerActivity extends AppCompatActivity
        implements LoaderManager.LoaderCallbacks<Cursor>,
                   DeleteRunsDialog.DeleteRunsDialogListener{

    private static final String TAG = "run_pager_activity";

    private ViewPager mViewPager;
    private BackgroundLocationService mService;
    private boolean mBound = false;
    private ResultsReceiver mResultsReceiver;
    private IntentFilter mIntentFilter;
    private Menu mMenu;
    //Custom Adapter to feed CombinedFragments to the ViewPager.
    private RunCursorFragmentStatePagerAdapter mAdapter;
    private long mRunId = -1;
    //Set a default sort order
    private int mSortOrder = Constants.SORT_BY_DATE_DESC;
    /*Static method to invoke this Activity and cause it to make the designated CombinedFragment the
     *current view in the ViewPager.
     */
    public static Intent newIntent(Context packageContext, int sortOrder, long runId){
        Intent intent = new Intent(packageContext, RunPagerActivity.class);
        intent.putExtra(Constants.EXTRA_SORT_ORDER, sortOrder);
        intent.putExtra(Constants.EXTRA_RUN_ID, runId);
        return intent;
    }

    private final ViewPager.SimpleOnPageChangeListener mPageChangeListener
                                                                      = new ViewPager.SimpleOnPageChangeListener() {

        @Override
        public void onPageSelected(int position) {
        /*Keep the currently displayed Run's position in the ViewPager and Adapter so the
         *RecyclerView can scroll that Run to the top of its display when we go back there.
         */
        RunTracker2.getPrefs().edit().putInt(Constants.ADAPTER_POSITION, position).apply();
        /*Make sure that mRunId is always equal to the run id of the currently viewed
         *CombinedFragment as we page through them in the ViewPager.
         */
        CombinedFragment fragment = (CombinedFragment)mAdapter.getItem(position);
        mRunId = fragment.getArguments().getLong(Constants.ARG_RUN_ID, -1);
        RunTracker2.getPrefs().edit().putLong(Constants.ARG_RUN_ID, mRunId).apply();
        RunTracker2.getPrefs().edit().putLong(Constants.CURRENTLY_VIEWED_RUN, mRunId).apply();
        /*Update the subtitle to show position of this Run in the current sort of Runs and the
         *total number of Runs in the ViewPager.
         */
        setSubtitle();
        invalidateFragmentMenus(position);
        }

    };
    /*Disable menu items added by CombinedFragments for CombinedFragments that aren't currently
     *displayed to avoid duplication of those menu items - mViewPager initializes up to three
     *CombinedFragments at one time and all their menu items will be displayed simultaneously
     *if the ones for fragments no visible aren't suppressed.
     */
    private void invalidateFragmentMenus(int position){
        for (int i = 0; i < mAdapter.getCount(); i++){
           CombinedFragment fragment = (CombinedFragment) mAdapter.getItem(i);
            if (fragment != null) {
                /*The argument to this method will evaluate as true only for fragment currently
                 *displayed by the ViewPager, so the menus for the others will be suppressed.
                 */
                fragment.setHasOptionsMenu(i == position);
            }
        }
        //We've changed the menu, so invalidate it to get it redisplayed with the new configuration.
        invalidateOptionsMenu();
    }

    private final ViewPager.OnAdapterChangeListener mAdapterChangeListener =
                                                        new ViewPager.OnAdapterChangeListener() {
        @Override
        public void onAdapterChanged(@NonNull ViewPager viewPager, @Nullable PagerAdapter oldAdapter,
                                                                @Nullable PagerAdapter newAdapter) {
            /*A new Adapter means a new SortOrder, so we need to update the Run's position in the
             *Adapter and ViewPager so that the RecyclerView can scroll to it when we go back there.
             */
            RunTracker2.getPrefs().edit().putInt(Constants.ADAPTER_POSITION,
                                                 mViewPager.getCurrentItem()).apply();
            /*The order of the fragments in the adapter and ViewPager has changed, so the suppression
             *of menus for non-visible fragments has to be redone.
             */
            invalidateFragmentMenus(mViewPager.getCurrentItem());
        }
    };

    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            BackgroundLocationService.LocationBinder binder = (BackgroundLocationService.LocationBinder)iBinder;
            mService = binder.getService();
            mBound = true;
            Log.i(TAG, "BackgroundLocationService connected");
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mService = null;
            mBound = false;
            Log.i(TAG, "BackgroundLocationService disconnected.");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_run_pager);
        /*The sort order and the Id of the CombinedFragment to make the current view in the
         *ViewPager are the critical initialization variables, so either retrieve them from the
         *Intent that started this Activity or retrieve them from the savedInstanceState Bundle.
         */
        if (savedInstanceState != null) {
            mSortOrder = savedInstanceState.getInt(Constants.SORT_ORDER);
            mRunId = savedInstanceState.getLong(Constants.SAVED_RUN_ID);
        } else {
            mSortOrder = getIntent().getIntExtra(Constants.EXTRA_SORT_ORDER,
                                                 Constants.KEEP_EXISTING_SORT);
            //If we're keeping he same sort order, get it from SharedPrefs.
            if (mSortOrder == Constants.KEEP_EXISTING_SORT) {
                mSortOrder = RunTracker2.getPrefs().getInt(Constants.SORT_ORDER,
                                                           Constants.SORT_BY_DATE_DESC);
            }
            mRunId = getIntent().getLongExtra(Constants.EXTRA_RUN_ID, -1);
        }
        /*Make the relevant run ID an Argument to the CombinedFragment to be opened so it will be
         *easily available there.
         */
        RunTracker2.getPrefs().edit().putLong(Constants.ARG_RUN_ID, mRunId).apply();
        /*Default zoom value for CombinedFragments' maps is 17.0f, but higher zoom levels are
         *preserved.
         */
        float zoom = RunTracker2.getPrefs().getFloat(Constants.ZOOM_LEVEL, 17.0f) > 17.0f ?
                RunTracker2.getPrefs().getFloat(Constants.ZOOM_LEVEL, 17.0f) :
                17.0f;
        RunTracker2.getPrefs().edit().putFloat(Constants.ZOOM_LEVEL, zoom).apply();
        mViewPager = findViewById(R.id.activity_run_pager_view_pager);
        //Set up BroadcastReceiver to receive results of operations we're interested in.
        mIntentFilter = new IntentFilter(Constants.SEND_RESULT_ACTION);
        mIntentFilter.addAction(Constants.ACTION_DELETE_RUN);
        mResultsReceiver = new ResultsReceiver();
        Bundle args = setupAdapterAndLoader();
        getSupportLoaderManager().initLoader(Constants.RUN_LIST_LOADER, args, this);
    }

    @Override
    public void onStart(){
        super.onStart();
        Intent bindIntent = new Intent(this, BackgroundLocationService.class);
        bindService(bindIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    private Bundle setupAdapterAndLoader(){
        /*Set up the Adapter and  Loader by constructing the initial data cursor based upon
         *the selected sort order.
         */

        Bundle args = new Bundle();
        Cursor cursor = null;

        switch (mSortOrder){
            case Constants.SORT_BY_DATE_ASC:
                cursor = getContentResolver().query(
                        Constants.URI_TABLE_RUN,
                        null,
                        null,
                        null,
                        String.valueOf(Constants.SORT_BY_DATE_ASC)
                );
                break;
            case Constants.SORT_BY_DATE_DESC:
                cursor = getContentResolver().query(
                        Constants.URI_TABLE_RUN,
                        null,
                        null,
                        null,
                        String.valueOf(Constants.SORT_BY_DATE_DESC)
                );
                break;
            case Constants.SORT_BY_DISTANCE_ASC:
                cursor = getContentResolver().query(
                        Constants.URI_TABLE_RUN,
                        null,
                        null,
                        null,
                        String.valueOf(Constants.SORT_BY_DISTANCE_ASC)
                );
                break;
            case Constants.SORT_BY_DISTANCE_DESC:
                cursor = getContentResolver().query(
                        Constants.URI_TABLE_RUN,
                        null,
                        null,
                        null,
                        String.valueOf(Constants.SORT_BY_DISTANCE_DESC)
                );
                break;
            case Constants.SORT_BY_DURATION_ASC:
                cursor = getContentResolver().query(
                        Constants.URI_TABLE_RUN,
                        null,
                        null,
                        null,
                        String.valueOf(Constants.SORT_BY_DURATION_ASC)
                );
                break;
            case Constants.SORT_BY_DURATION_DESC:
                cursor = getContentResolver().query(
                        Constants.URI_TABLE_RUN,
                        null,
                        null,
                        null,
                        String.valueOf(Constants.SORT_BY_DURATION_DESC)
                );
                break;
            case Constants.SORT_NO_RUNS:
                cursor = getContentResolver().query(
                        Constants.URI_TABLE_RUN,
                        null,
                        null,
                        null,
                        null
                );
            default:
                Log.i(TAG, "Invalid sort order - how'd you get here!?!");
        }
        mAdapter = new RunCursorFragmentStatePagerAdapter(this,
                                                          getSupportFragmentManager(),
                                                          cursor);
        mViewPager.setAdapter(mAdapter);
        //Make sure the ViewPager makes the designated Run's CombinedRunFragment the current view.
        setViewPager(mAdapter.getCursor(), mRunId);
        //Change the Activity's subtitle to display sort and number of Runs.
        setSubtitle();
        /*If there aren't any Runs left to display, close this Activity and go back to the
         *RunRecyclerListActivity and Fragment, which displays a message to the user.
         */
        if (mAdapter.getCount() == 0) {
            finish();
        }
        args.putInt(Constants.SORT_ORDER, mSortOrder);
        return args;
    }

    /*Setting the subtitle is broken out into a separate method from setting up the adapter
    * and loader because we need to change the subtitle whenever the Run displayed is changed and
    * we don't want to construct a new adapter and a new loader every time we do that.*/
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
        getSupportActionBar().setSubtitle(subtitle);
    }

    @Override
    public void onPause(){
        mViewPager.removeOnPageChangeListener(mPageChangeListener);
        mViewPager.removeOnAdapterChangeListener(mAdapterChangeListener);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mResultsReceiver);
        super.onPause();
    }

    @Override
    public void onStop(){
        if (mBound){
            unbindService(mServiceConnection);
        }
        super.onStop();
    }

    @Override
    public void onResume(){
        super.onResume();
        mViewPager.addOnPageChangeListener(mPageChangeListener);
        mViewPager.addOnAdapterChangeListener(mAdapterChangeListener);
        LocalBroadcastManager.getInstance(this).registerReceiver(
                mResultsReceiver,
                mIntentFilter);

    }

    /*@Override
    public void onRestart(){
        super.onRestart();
        *//*If the user gets to the RunPagerActivity by hitting the Back button in the RunMapPagerActivity,
         *we need to retrieve the RunId and Sort Order from SharedPrefs. This code has to go here, not
         *in onResume(), because we only want this behavior the happen when the Activity has already been
         *opened before with an Intent. When this Activity is opened for the first time, it gets its
         *values from the Intent dispatched by the RunRecyclerListFragment.
         *//*
        //Log.i(TAG, "Calling onRestart()");
        mRunId = RunTracker2.getPrefs().getLong(Constants.ARG_RUN_ID, -1);
        //Log.i(TAG, "mRunId in onRestart() is " + mRunId);
        mSortOrder = RunTracker2.getPrefs().getInt(Constants.SORT_ORDER, Constants.KEEP_EXISTING_SORT);
        //Log.i(TAG, "mSortOrder in onRestart() is " + mSortOrder);
        Bundle args = setupAdapterAndLoader();
        getSupportLoaderManager().restartLoader(Constants.RUN_LIST_LOADER, args, this);
    }*/

    @Override
    public boolean onCreateOptionsMenu(Menu menu){
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.run_pager_options, menu);
        mMenu = menu;
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu){

        super.onPrepareOptionsMenu(menu);

        //If we have fewer than two Runs, there's nothing to sort, so disable sort menu
        if (mAdapter.getCount() < 2) {
            menu.findItem(R.id.run_map_pager_menu_item_sort_runs).setEnabled(false);
        } else {
            menu.findItem(R.id.run_map_pager_menu_item_sort_runs).setEnabled(true);
        }
        /*If we're tracking a Run, don't allow creation of a new Run - trying to track more than one
         *Run will crash the app!
         */
        menu.findItem(R.id.run_map_pager_menu_item_new_run).setEnabled(!RunManager.isTrackingRun());
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item){
        Bundle args;
        switch(item.getItemId()){
            case R.id.run_map_pager_menu_item_new_run:
                /*Don't need to tell the Adapter its getting an update because we're recreating the
                 *Adapter shortly. First, tell the ViewPager's adapter that its content is receiving
                 *an update.
                 */
                mAdapter.startUpdate(mViewPager);
                //Now start a new blank run with nothing but a Start Date and a runId.
                RunManager.insertRun();
                /*The Adapter, Subtitle and Loader get reset when the results of the Insert Run
                 *action get reported to the ResultsReceiver in this Activity..
                 */
                return true;
            case R.id.menu_item_map_pager_delete_run:
                /*Bring up a confirmation dialog to allow the user to change his mind about deletion.
                 *We pass along this Activity's identity and that only a single Run is to be deleted
                 *so the dialog message will be accurate.
                 */
                Bundle bundle = new Bundle();
                bundle.putInt(Constants.FRAGMENT, Constants.COMBINED_FRAGMENT);
                bundle.putInt(Constants.NUMBER_OF_RUNS, 1);
                DeleteRunsDialog dialog = new DeleteRunsDialog();
                dialog.setArguments(bundle);
                dialog.show(getSupportFragmentManager(), "DeleteDialog");
                return true;
            /*To change the sort order, set mSortOrder, store it to SharedPrefs, reinitialize the
             *adapter and subtitle and restart the RunListLoader.
             */
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
            case R.id.show_entire_route_menu_item:
                //This is implemented in the CombinedFragment
                return false;
            case R.id.track_end_point_menu_item:
                //This is implemented in the CombinedFragment
                return false;
            case R.id.track_start_point_menu_item:
                //This is implemented in the CombinedFragment
                return false;
            case R.id.tracking_off_menu_item:
                //This is implemented in the CombinedFragment
                return false;
            case R.id.run_map_pager_activity_units:
                //This is implemented in the CombinedFragment
                return false;
            case R.id.run_map_pager_activity_scroll:
                //This is implemented in the CombinedFragment
                return false;
            default:
                super.onOptionsItemSelected(item);
                return true;
        }
        RunTracker2.getPrefs().edit().putInt(Constants.SORT_ORDER, mSortOrder).apply();
        args = setupAdapterAndLoader();
        getSupportLoaderManager().restartLoader(Constants.RUN_LIST_LOADER, args, this);
        return true;
    }
    //Method that's called by onDeleteRunDialogPositiveClick callback confirming deletion.
    private void deleteRun(){
        //First, stop location updates if the Run we're deleting is currently being tracked.
        if (RunManager.isTrackingRun(RunManager.getRun(mRunId))){
            mService.stopLocationUpdates();
            //We've stopped tracking any Run, so enable the "New Run" menu item.
            mMenu.findItem(R.id.run_map_pager_menu_item_new_run).setEnabled(true);
            invalidateOptionsMenu();
        }
        /*Now order the Run to be deleted. The Adapter, Subtitle and Loader will get reset
         *when the results of the Run deletion get reported to the ResultsReceiver. First
         *notify the adapter that its contents will be updated.
         */
        mAdapter.startUpdate(mViewPager);
        RunManager.deleteRun(mRunId);
    }

    @Override
    public void onDeleteRunsDialogPositiveClick(int which){
        //Check to see if this call from the dialog is for us; if so, delete the Run
        if (which == Constants.COMBINED_FRAGMENT){
            deleteRun();
        }
    }

    @Override
    public void onDeleteRunsDialogNegativeClick(int which){
        /*We don't need to do anything to cancel the deletion, but the interface requires that
         *this method be implemented.
         */
    }

    @Override
    public Loader<Cursor> onCreateLoader(int d, Bundle args){
        //Construct a cursor loader according to the selected sort order
        if (args != null){
           mSortOrder = args.getInt(Constants.SORT_ORDER);
        } else {
            mSortOrder = Constants.SORT_BY_DATE_DESC;
        }
        return new RunListCursorLoader(this, mSortOrder);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor){
        //The loader takes care of releasing the old cursor, so call swapCursor(), not changeCursor()
        mAdapter.swapCursor(cursor);
        /*If there are no Runs in the cursor, shut down this Activity and go back to the
         *RunRecyclerListActivity/Fragment, which has a special UI for when the database has no runs.
         */
        if (mAdapter.getCount() == 0){
            finish();
        }
        //Make sure we keep looking at the Run we were on before the Loader updated.
        setViewPager(cursor, mRunId);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader){

    }

    @Override
    public void onSaveInstanceState(Bundle outState){
        super.onSaveInstanceState(outState);
        outState.putLong(Constants.SAVED_RUN_ID, mRunId);
        outState.putInt(Constants.SORT_ORDER, mSortOrder);
    }
    /*Set the ViewPager's current (displayed) item to the specified Run and save the Adapter and
     *ViewPager position of the Run so the RecyclerView can scroll to it when we go back there.
     */
    private void setViewPager(Cursor cursor, long runId){
        cursor.moveToFirst();
        /*Iterate over the Runs in the cursor until we find the one with an Id equal to the one we
         *specified in the runId parameter, then set the ViewPager's current item to that Run and
         *save the Adapter/ViewPager position.
         */
        while (!cursor.isAfterLast()){
            if (RunDatabaseHelper.getRun(cursor).getId() == runId){
                mViewPager.setCurrentItem(cursor.getPosition());
                RunTracker2.getPrefs().edit().putInt(Constants.ADAPTER_POSITION,
                                                     mViewPager.getCurrentItem()).apply();
                break;
            }
            cursor.moveToNext();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        //This method processes results of startResolutionForResult when Location Settings are bad.
        if (requestCode == Constants.LOCATION_SETTINGS_CHECK){

            if (resultCode == RESULT_OK){
                RunTracker2.setLocationSettingsState(true);
                Toast.makeText(this, "Location Settings now enabled.",
                                                                    Toast.LENGTH_LONG).show();
            } else {
                RunTracker2.setLocationSettingsState(false);
                Toast.makeText(this, "Location Settings were not enabled. " +
                                                    "Cannot track Run.", Toast.LENGTH_LONG).show();
            }
        }
    }

    //Custom adapter to feed CombinedRunFragments to the ViewPager
    protected class RunCursorFragmentStatePagerAdapter extends CursorFragmentStatePagerAdapter{

        RunCursorFragmentStatePagerAdapter(Context context, FragmentManager fm, Cursor cursor){
            super(context, fm, cursor);
        }
        //Pull a Run from the supplied cursor and retrieve its CombinedFragment using its RunId.
        @Override
        public Fragment getItem(Context context, Cursor cursor){
            long runId = RunDatabaseHelper.getRun(cursor).getId();
            if (runId != -1){
                return CombinedFragment.newInstance(runId);
            } else {
                /*We should never get here - Runs are assigned a RunId as soon as they get created and
                 *before they get added to the ViewPager, but we have return something in an "else"
                 *block to keep the compiler happy.
                 */
                return null;
            }
        }
    }
    /*Class to allow us to receive reports of results of the operations the ViewPager is interested
     *in, ACTION_INSERT_RUN and ACTION_DELETE_RUN.
     */
    private class ResultsReceiver extends BroadcastReceiver{
        @Override
        public void onReceive(Context context, Intent intent){
            String action = intent.getAction();
            switch (action) {
                case Constants.SEND_RESULT_ACTION:
                    String actionAttempted = intent.getStringExtra(Constants.ACTION_ATTEMPTED);
                    if (actionAttempted.equals(Constants.ACTION_INSERT_RUN)) {
                        Run run = intent.getParcelableExtra(Constants.EXTENDED_RESULTS_DATA);
                        if (run.getId() != -1) {
                            /*Now that the new Run has been added to the database, we need to reset
                             *the Adapter, Subtitle and Loader.
                             */
                            mRunId = run.getId();
                            setupAdapterAndLoader();
                        } else {
                            Toast.makeText(RunPagerActivity.this, R.string.insert_run_error,
                                    Toast.LENGTH_LONG).show();
                        }
                        /*Now that the new Run has been entered into the database and the adapter,
                         *the ViewPager can finish the update of its View.
                         */
                        mAdapter.finishUpdate(mViewPager);
                        setViewPager(mAdapter.getCursor(), mRunId);
                    }
                    /*ViewPager isn't interested in any other ACTION_ATTEMPTED, so no "else" clauses
                     *specifying what to do with them needed.
                     */
                    break;
                case Constants.ACTION_DELETE_RUN:
                    //Display a dialog displaying the results of the deletion operation.
                    String resultsString = intent.getStringExtra(Constants.EXTENDED_RESULTS_DATA);
                    AlertDialog.Builder builder = new AlertDialog.Builder(RunPagerActivity.this)
                        .setPositiveButton("OK", (dialogInterface, i) -> dialogInterface.dismiss())
                        .setTitle("Run Deletion Report")
                        .setMessage(resultsString);
                    builder.create().show();
                    /*Trying to delete the last Run from this Activity after having deleted other
                     *Runs results in a problem: mRunId remains set to the last previous Run that
                     *was deleted, so we get an error for trying to delete a Run that's already
                     *been deleted. Thus, we need some technique to set a valid RunId for the
                     *new current view after deleting a Run.
                     *
                     *We use the position in the ViewPager held by the Run we just deleted to select
                     *what RunId should be after the deletion. If the ViewPager held only one
                     *child view before the deletion, we know we just deleted the last Run so we
                     *can just finish this activity and go back to RunRecyclerView.
                     */
                    if (mViewPager.getChildCount() == 1){
                        mAdapter.finishUpdate(mViewPager);
                        finish();
                    /*If there was more than one Run held in the ViewPager, set the ViewPager's
                     *current view item to the view that's in the next higher position in the
                     *ViewPager unless we were already at the highest position, in which case
                     *set the ViewPager's current view item to the view that's in the next lower
                     *position in the ViewPager.
                     */
                    } else {
                        int currentPosition = mViewPager.getCurrentItem();
                        /*Get the fragment associated with the child view we're going to move
                         *to and get its RunId from the arguments that were attached to the
                         *fragment when it was created. Is there a better way to do this? Why
                         *doesn't the onPageChangeListener correctly report the fragment displayed
                         *in the last remaining page of a ViewPager?
                         */
                        int index;
                        if (currentPosition < mViewPager.getChildCount() - 1) {
                            index = currentPosition + 1;
                        } else {
                            index = currentPosition - 1;
                        }
                        mViewPager.setCurrentItem(index);
                        CombinedFragment fragment = (CombinedFragment) mAdapter.getItem(index);
                        mRunId = fragment.getArguments().getLong(Constants.ARG_RUN_ID);
                    }
                    /*Now that we've got a "legal" mRunId, we can fetch a new cursor, reconstruct
                     *the adapter, and set the subtitle accordingly.
                     */
                    setupAdapterAndLoader();
                    //We've finished with deletions, so the  View's update can be finished.
                    mAdapter.finishUpdate(mViewPager);
                    break;
                default:
                    /*Shouldn't ever get here - intent filter limits us to SEND_RESULT_ACTION
                     *and ACTION_DELETE_RUN.
                     */
                    Log.i(TAG, "Intent Action wasn't SEND_RESULT_ACTION or ACTION_DELETE_RUN");
                    break;
            }
        }
    }
}
