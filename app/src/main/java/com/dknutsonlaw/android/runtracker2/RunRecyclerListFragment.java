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
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.bignerdranch.android.multiselector.ModalMultiSelectorCallback;
import com.bignerdranch.android.multiselector.MultiSelector;
import com.bignerdranch.android.multiselector.SwappingHolder;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by dck on 10/28/15.A Fragment to display a RecyclerView showing all the Runs recorded.
 */
public class RunRecyclerListFragment extends Fragment
        implements LoaderManager.LoaderCallbacks<Cursor>{

    private static final String TAG = "RunRecyclerListFragment";

    private RunManager mRunManager;
    private IntentFilter mIntentFilter;
    private ResultsReceiver mResultsReceiver;
    private Menu mOptionsMenu;
    private int mSortOrder = Constants.SORT_BY_DATE_DESC;
    private String mSubtitle;
    private RecyclerView mRunListRecyclerView;
    private RunRecyclerListAdapter mAdapter;
    private TextView mEmptyViewTextView;
    private Button mEmptyViewButton;
    private BackgroundLocationService mLocationService;
    //Are we bound to the BackgroundLocationService/
    private boolean mIsBound = false;
    //Was an attempt to delete Runs affected by the need to stop location updates on a Run being
    //tracked that was selected for deletion?
    private boolean mShouldDelete = false;
    //Callback invoked when binding to BackgroundLocationService is accomplished
    private final ServiceConnection mLocationServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BackgroundLocationService.LocalBinder binder =
                    (BackgroundLocationService.LocalBinder)service;
            //Make the reference to the BackgroundLocationService a member variable so we can easily
            //call the Service's methods.
            mLocationService = binder.getService();
            mIsBound = true;
            //If we've marked a Run being tracked for deletion, we need to stop location updates. If
            //the Service wasn't connected when the delete method was called, make sure we stop
            //location updates when we get connected.
            if (mRunManager.isTrackingRun() && mShouldDelete){
                mLocationService.stopLocationUpdates();
                mShouldDelete = false;
                //Make sure the OptionsMenu gets updated now that location updates have been stopped.
                getActivity().invalidateOptionsMenu();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mIsBound = false;
        }
    };
    //Using Big Nerd Ranch MultiSelector library for RecyclerView to enable multiselect for deletion
    //of Runs.
    private final MultiSelector mMultiSelector = new MultiSelector();
    //Callback invoked upon long click on RunHolder that creates an ActionMode used for deletion of
    //the selected Runs.
    private final ModalMultiSelectorCallback mDeleteMode = new ModalMultiSelectorCallback(mMultiSelector) {
        @Override
        public boolean onCreateActionMode(ActionMode actionMode, Menu menu){
            //Create an ActionMode menu with a Delete item
            super.onCreateActionMode(actionMode, menu);
            getActivity().getMenuInflater().inflate(R.menu.run_list_item_context, menu);
            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {

            if (item.getItemId() == R.id.menu_item_delete_run){
                //Fetch a List of the Runs that have been selected for deletion.
                List<Integer> deleteList = mMultiSelector.getSelectedPositions();
                //Create the ArrayList<Long> the deleteRuns() method in RunManager expects.
                ArrayList<Long> longDeleteList  = new ArrayList<>();
                //Iterate over all the items in the List selected for deletion
                for (int i = deleteList.size() - 1; i >= 0; i--){
                    //If the Run is being tracked, stop tracking - otherwise location updates will
                    //continue with updates not associated with any Run and really with no way to
                    //turn updates off..
                    if (mRunManager.isTrackingRun(mRunManager.getRun(mAdapter.getItemId(deleteList.get(i))))){
                        //If we're bound to the BackgroundLocationService, tell it to stop location updates
                        if (mIsBound & mLocationService != null){
                            Log.i(TAG, "Run at adapter position " + deleteList.get(i) + " being tracked. Now stopping. " +
                                    "mClient is connected");
                            mLocationService.stopLocationUpdates();
                            //Have RunManager do the other housekeeping associated with stopping a Run.
                            //mRunManager.stopRun(mRunManager.getRun(mAdapter.getItemId(deleteList.get(i))));
                            mRunManager.stopRun(mAdapter.getItemId(deleteList.get(i)));
                            //Update the OptionsMenu now that we're no longer tracking a Run.
                            getActivity().invalidateOptionsMenu();
                        } else {
                            Log.i(TAG, "Binding so we can delete the run being tracked");
                            //Set flag so the tracked run will be deleted upon binding to the
                            //Location Service
                            mShouldDelete = true;
                            //Bind to the BackgroundLocationService so that we can stop location
                            //updates as soon as we're connected with the Service.
                            Intent intent = new Intent(getActivity(), BackgroundLocationService.class);
                            getActivity().bindService(intent, mLocationServiceConnection, Context.BIND_AUTO_CREATE);
                        }
                    }
                    //Add selected Run item from the List to the ArrayList of Runs to be submitted to
                    //the deleteRuns() method in the Intent Service
                    longDeleteList.add(mAdapter.getItemId(deleteList.get(i)));
                    Log.i(TAG, "Added runId " + mAdapter.getItemId(deleteList.get(i)) + " at position " + deleteList.get(i) + " to longDeleteList");
                }
                //Now delete the selected runs.
                mRunManager.deleteRuns(longDeleteList);
                //Iterate over the list of deleted Runs, get their positions in the RecyclerView, and notify the
                //adapter that the item has been removed. Do this instead of a single call to
                //notifyDataSetChanged() so we get to see the cool animations.....Remember to start
                //with the highest numbered item in the list an count down so the items identified to
                //the adapter will correspond to the items that have been deleted throughout the whole
                //process.
                for (int i = deleteList.size() - 1; i >= 0; i--){
                    mAdapter.notifyItemRemoved(deleteList.get(i));
                }
                //Clean up the MultiSelector, finish the ActionMode, and refresh the UI now that our
                //dataset has changed
                mMultiSelector.clearSelections();
                mode.finish();
                refreshUI();
                return true;
            }
            //If we don't select Delete from the ActionMode menu, just clear the MultiSelector and
            //the ActionMode without doing anything.
            mMultiSelector.clearSelections();
            mode.finish();
            return false;
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        //Get the singleton RunManager instance
        mRunManager = RunManager.get(getActivity());
        if (savedInstanceState != null) {
            //Get sort order and subtitle from savedInstanceState Bundle if the Activity is
            //getting recreated
            mSortOrder = savedInstanceState.getInt(Constants.SORT_ORDER);
            Log.i(TAG, "Getting mSortOrder from savedInstanceState");
            //noinspection ConstantConditions
            ((AppCompatActivity)getActivity()).getSupportActionBar().setSubtitle(savedInstanceState.getString(Constants.SUBTITLE));
        } else {
            //When Activity is created for the first time or if the Fragment is getting created
            //for the first time even thought the Activity isn't, get sort order and subtitle
            //from SharedPreferences.
            Log.i(TAG, "Getting Sort Order from SharedPreferences in onCreate");
            mSortOrder = mRunManager.mPrefs.getInt(Constants.SORT_ORDER, Constants.SORT_BY_DATE_DESC);
            mSubtitle = mRunManager.mPrefs.getString(Constants.SUBTITLE, /*getActivity().getResources()
                    .getQuantityString(R.plurals.subtitle_date_desc, mAdapter.getItemCount(),
                            mAdapter.getItemCount())*/"Stub Value");
        }
        //Define the kinds of Intents we want to know about and set up BroadcastReceiver accordingly
        mIntentFilter = new IntentFilter(Constants.ACTION_DELETE_RUNS);
        mIntentFilter.addAction(Constants.SEND_RESULT_ACTION);
        mResultsReceiver = new ResultsReceiver();
    }

    //Function to set subtitle according to the number of Runs recorded and their sort order..
    private void setSubtitle(){
        Resources r = getActivity().getResources();
        String subtitle;
        if (mAdapter.getItemCount() == 0){
            subtitle = r.getString(R.string.no_runs_recorded);
        } else {
            switch (mSortOrder) {
                case Constants.SORT_BY_DATE_ASC:
                    subtitle = r.getQuantityString(R.plurals.subtitle_date_asc,
                            mAdapter.getItemCount(), mAdapter.getItemCount());
                    break;
                case Constants.SORT_BY_DATE_DESC:
                    subtitle = r.getQuantityString(R.plurals.subtitle_date_desc,
                            mAdapter.getItemCount(), mAdapter.getItemCount());
                    break;
                case Constants.SORT_BY_DISTANCE_ASC:
                    subtitle = r.getQuantityString(R.plurals.subtitle_distance_asc,
                            mAdapter.getItemCount(), mAdapter.getItemCount());
                    break;
                case Constants.SORT_BY_DISTANCE_DESC:
                    subtitle = r.getQuantityString(R.plurals.subtitle_distance_desc,
                            mAdapter.getItemCount(), mAdapter.getItemCount());
                    break;
                case Constants.SORT_BY_DURATION_ASC:
                    subtitle = r.getQuantityString(R.plurals.subtitle_duration_asc,
                            mAdapter.getItemCount(), mAdapter.getItemCount());
                    break;
                case Constants.SORT_BY_DURATION_DESC:
                    subtitle = r.getQuantityString(R.plurals.subtitle_duration_desc,
                            mAdapter.getItemCount(), mAdapter.getItemCount());
                    break;
                default:
                    subtitle = r.getString(R.string.goof_up);
                    break;
            }
        }
        //noinspection ConstantConditions
        ((AppCompatActivity)getActivity()).getSupportActionBar().setSubtitle(subtitle);
    }

    @Override
    public void onSaveInstanceState(Bundle saveInstanceState) {
        super.onSaveInstanceState(saveInstanceState);
        //We need to save the sort order for the runs when configurations change.
        saveInstanceState.putInt(Constants.SORT_ORDER, mSortOrder);
        mRunManager.mPrefs.edit().putInt(Constants.SORT_ORDER, mSortOrder).commit();
        //noinspection ConstantConditions
        saveInstanceState.putString(Constants.SUBTITLE,
                ((AppCompatActivity) getActivity()).getSupportActionBar().getSubtitle().toString());
        try {
            //noinspection ConstantConditions,ConstantConditions
            mRunManager.mPrefs.edit().putString(Constants.SUBTITLE,
                    ((AppCompatActivity) getActivity()).getSupportActionBar().getSubtitle().toString());
        } catch (NullPointerException npe){
            Log.i(TAG, "Couldn't write subtitle to default preferences file - attempt to get SupportActionBar" +
                    "returned a null pointer");
        }
        saveInstanceState.putInt(Constants.ADAPTER_ITEM_COUNT, mAdapter.getItemCount());
        mRunManager.mPrefs.edit().putInt(Constants.ADAPTER_ITEM_COUNT, mAdapter.getItemCount());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState){
        super.onCreateView(inflater,  parent, savedInstanceState);

        View v = inflater.inflate(R.layout.fragment_recycler_run_list, parent, false);
        mRunListRecyclerView = (RecyclerView)v.findViewById(R.id.run_recycler_view);
        mRunListRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mRunListRecyclerView.addItemDecoration(new SimpleDividerItemDecoration(getActivity()));
        mAdapter = new RunRecyclerListAdapter(getActivity(),  mRunManager.queryForNoRuns());
        mRunListRecyclerView.setAdapter(mAdapter);
        //We use the sort order of the runs to define the cursor we want to use, so put that into
        //the args Bundle for the OnCreateLoader() callback to use in creating the
        //RunListCursorLoader.
        Bundle args = new Bundle();
        args.putInt(Constants.SORT_ORDER, mSortOrder);
        getLoaderManager().initLoader(Constants.RUN_LIST_LOADER, args, this);
        //Set up UI elements to display if there are no Runs recorded to display in the RecyclerView
        mEmptyViewTextView = (TextView)v.findViewById(R.id.empty_view_textview);
        mEmptyViewButton = (Button)v.findViewById(R.id.empty_view_button);
        mEmptyViewButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mRunManager.startNewRun();
            }
        });
        refreshUI();
        return v;
    }

    private void refreshUI() {
        //Restarting the loader forces an update of the UI: notifyDataSetChanged()
        //gets called on the adapter, which in turn causes onBindHolder() to get called, thus updating
        //the RecyclerView's contents and the background of any selected RunHolder item. When a new
        //sort order is selected, we invoke restartLoader() to get a Loader with fresh data

        //If we have no Runs recorded, hide the RecyclerView and display a TextView and Button
        //inviting the user to record the first Run
        if (mAdapter.getItemCount() == 0){
            mRunListRecyclerView.setVisibility(View.GONE);
            mEmptyViewTextView.setVisibility(View.VISIBLE);
            mEmptyViewButton.setVisibility(View.VISIBLE);
        } else {
            mEmptyViewTextView.setVisibility(View.GONE);
            mEmptyViewButton.setVisibility(View.GONE);
            mRunListRecyclerView.setVisibility(View.VISIBLE);
        }
        //Disable the New Run Menu item if we're tracking a run - trying to start a new run while
        //another's already being tracked will crash the program!
        if (mOptionsMenu != null) {
            if (mRunManager.isTrackingRun()){
                mOptionsMenu.findItem(R.id.menu_item_new_run).setEnabled(false);
            } else {
                mOptionsMenu.findItem(R.id.menu_item_new_run).setEnabled(true);
            }
            //Disable the Sort Runs menu if there're fewer than two Runs - nothing to sort!
            if (mAdapter.getItemCount() < 2){
                mOptionsMenu.findItem(R.id.menu_item_sort_runs).setEnabled(false);
            } else {
                mOptionsMenu.findItem(R.id.menu_item_sort_runs).setEnabled(true);
            }
        }
        setSubtitle();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.run_list_options, menu);
        mOptionsMenu = menu;
    }

    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.menu_item_new_run:
                //Now that we're using an auto-updating loader for the list of runs, we don't need
                //to call startActivityForResult() - the loader and content observer system take
                //care of updating the list to reflect the presence of a new Run. Note that we
                //don't have to call restartLoader() on the RUN_LIST_LOADER because the query on
                //the database hasn't changed.
                mRunManager.startNewRun();
                //Update the subtitle to reflect that there's a new run
                setSubtitle();
                return true;
            //Change the sort order of the RecyclerView and the Activity subtitle to match based upon
            //the menuItem selected.
            case R.id.menu_item_sort_by_date_asc:
                changeSortOrder(Constants.SORT_BY_DATE_ASC);
                return true;
            case R.id.menu_item_sort_by_date_desc:
                changeSortOrder(Constants.SORT_BY_DATE_DESC);
                return true;
            case R.id.menu_item_sort_by_distance_asc:
                changeSortOrder(Constants.SORT_BY_DISTANCE_ASC);
                return true;
            case R.id.menu_item_sort_by_distance_desc:
                changeSortOrder(Constants.SORT_BY_DISTANCE_DESC);
                return true;
            case R.id.menu_item_sort_by_duration_asc:
                changeSortOrder(Constants.SORT_BY_DURATION_ASC);
                return true;
            case R.id.menu_item_sort_by_duration_desc:
                changeSortOrder(Constants.SORT_BY_DURATION_DESC);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
    //Whenever we change the sort order, a new query must be made on the database, which
    //in turn requires creation of a new RUN_LIST_LOADER, so we need to call
    //restartLoader() to provide the RecyclerView's adapter with a new cursor of data
    //reflecting the new sort order. We also change the ActionBar's subtitle to display
    //the new sort order.
    private void changeSortOrder(int sortOrder) {
        Bundle args = new Bundle();
        mSortOrder = sortOrder;
        mRunManager.mPrefs.edit().putInt(Constants.SORT_ORDER, sortOrder).commit();
        setSubtitle();
        try {
            //noinspection ConstantConditions
            mSubtitle = (String) ((AppCompatActivity) getActivity()).getSupportActionBar().getSubtitle();
        } catch (NullPointerException npe){
            Log.i(TAG, "Couldn't write new subtitle - attempt to get SupportActionBar returned a " +
                    "null pointer");
        }
        mRunManager.mPrefs.edit().putString(Constants.SUBTITLE, mSubtitle).commit();
        args.putInt(Constants.SORT_ORDER, sortOrder);
        getLoaderManager().restartLoader(Constants.RUN_LIST_LOADER, args, this);
        setSubtitle();
    }

    @Override
    public void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(
                mResultsReceiver,
                mIntentFilter);
        //Get the sort order from SharedPreferences here in case we're resumed from the RunPagerActivity
        //without being recreated.  That way, the sort order will be what it was in the RunPagerActivity.
        mSortOrder = mRunManager.mPrefs.getInt(Constants.SORT_ORDER, Constants.SORT_BY_DATE_DESC);
        mSubtitle = mRunManager.mPrefs.getString(Constants.SUBTITLE,
               /* r.getQuantityString(R.plurals.subtitle_date_desc, mAdapter.getItemCount()*/
                "Stub Value");
        changeSortOrder(mSortOrder);
        refreshUI();
        Log.i(TAG, "onResume called - mSortOrder is " + mSortOrder);
    }

    @Override
    public void onStart(){
        super.onStart();
        //Bind to the BackgroundLocationService here so that its methods will be available to us
        //while interacting with this Fragment
        Intent intent = new Intent(getActivity(), BackgroundLocationService.class);
        getActivity().bindService(intent, mLocationServiceConnection, Context.BIND_AUTO_CREATE);
    }
    @Override
    public void onPause() {
        Log.i(TAG, "onPause called.");
        LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(mResultsReceiver);
        super.onPause();
    }

    @Override
    public void onStop(){
        //Unbind to the BackgroundLocationService in this matching lifecycle method so we won't leak
        //the Service Connection.
        if (mIsBound) {
            getActivity().unbindService(mLocationServiceConnection);
            mIsBound = false;
        }
        super.onStop();
    }

    @Override
    public Loader<Cursor> onCreateLoader(int d, Bundle args){
        //We only ever load the list of all Runs, so assume here that this is the case.
        //We need to pass along a reference to the Uri on the table in question to allow
        //observation for content changes used in auto-updating by the loader. We also
        //need to extract the sort order for the loader from the Bundle args. Args might
        //be null upon initial start of the program, so check for it; the default value of
        //SORT_DATE_DESC is set in the initialization of RunRecyclerListFragment member variable
        //mSortOrder.

        if (args != null)
            //If args is null, the default value of mSortOrder set at the beginning of this
            //fragment will apply.
            mSortOrder = args.getInt(Constants.SORT_ORDER);

        return new RunListCursorLoader(getActivity(), Constants.URI_TABLE_RUN, mSortOrder);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor){
        //Now that we've got a fresh cursor of data, swap out from the adapter the old cursor for
        //the new one and notify the adapter that its data has changed so it will refresh the
        //RecyclerView display. An alternative approach is simply to create a new adapter here, load
        //the cursor into it, and attach the new adapter to the RecyclerListView.
        /*Cursor oldCursor = mAdapter.swapCursor(cursor);
        if (oldCursor != null){
            Log.i(TAG, "Closing old cursor.");
            oldCursor.close();
        }*/
        RunDatabaseHelper.RunCursor newCursor = (RunDatabaseHelper.RunCursor)cursor;
        mAdapter.changeCursor(newCursor);
        mAdapter.notifyDataSetChanged();
        refreshUI();

        Log.i(TAG, "RunListCursorLoader onLoadFinished() called.");
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader){
        //Stop using the cursor (via the adapter)
        mRunListRecyclerView.setAdapter(null);
    }
    //ViewHolder class for use with RecyclerListView. Big Nerd Ranch's SwappingHolder swaps state
    //depending upon whether the ViewHolder has been selected.
    private class RunHolder extends SwappingHolder
            implements View.OnClickListener, View.OnLongClickListener{
        Run mRun;
        final TextView mStartDateTextView;
        final TextView mStartAddressTextView;
        final TextView mDistanceTextView;
        final TextView mDurationTextView;
        final TextView mEndAddressTextView;

        //Pass the itemView to SwappingHolder constructor to get its features in our ViewHolder class.
        public RunHolder(View itemView){
            super(itemView, mMultiSelector);
            itemView.setOnClickListener(this);
            itemView.setLongClickable(true);
            itemView.setOnLongClickListener(this);

            mStartDateTextView = (TextView)itemView.findViewById(R.id.list_item_date_textview);
            mStartAddressTextView = (TextView)itemView.findViewById(R.id.list_item_start_address_textview);
            mDistanceTextView = (TextView)itemView.findViewById(R.id.list_item_distance_textview);
            mDurationTextView = (TextView)itemView.findViewById(R.id.list_item_duration_textview);
            mEndAddressTextView = (TextView)itemView.findViewById(R.id.list_item_end_address_textview);
        }
        //Plug values from a specific Run into the View elements of the RunHolder
        public void bindRun(Run run){
            mRun = run;
            Resources r = getActivity().getResources();
            //String startDateText = r.getString(R.string.list_date_text, Constants.DATE_FORMAT.format(mRun.getStartDate()));
            String startDateText = Constants.DATE_FORMAT.format(mRun.getStartDate());
            mStartDateTextView.setText(startDateText);
            mStartAddressTextView.setText(mRun.getStartAddress());
            double miles = mRun.getDistance() * Constants.METERS_TO_MILES;
            String distanceText = r.getString(R.string.list_distance_text, miles);
            mDistanceTextView.setText(distanceText);
            String durationText = Run.formatDuration((int)mRun.getDuration()/1000);
            mDurationTextView.setText(r.getString(R.string.list_duration_text, durationText));
            mEndAddressTextView.setText(mRun.getEndAddress());
        }

        @Override
        public void onClick(View  v){
            if (mRun == null){
                return;
            }
            //If this RunHolder hasn't been selected for deletion in an ActionMode, start RunPagerActivity
            //specifying its mRun as the one to be displayed when the ViewPager first opens.
            if (!mMultiSelector.tapSelection(this)){
                Intent i = RunPagerActivity.newIntent(getActivity(), mSortOrder, mRun.getId());
                startActivity(i);
            }
        }

        @Override
        public boolean onLongClick(View v){
            //On a long click, start an ActionMode and mark this RunHolder as selected
            ((AppCompatActivity)getActivity()).startSupportActionMode(mDeleteMode);
            mMultiSelector.setSelected(this, true);
            return true;
        }

    }

    //Custom adapter to feed the RecyclerListView RunHolders filled with data from the correct Runs.
    private class RunRecyclerListAdapter extends CursorRecyclerViewAdapter<RunHolder>{

        public RunRecyclerListAdapter(Context context, RunDatabaseHelper.RunCursor cursor){
            super(context, cursor);
        }

        @Override
        public RunHolder onCreateViewHolder(ViewGroup parent, int viewType){
            View itemView = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.list_item_layout, parent, false);
            return new RunHolder(itemView);
        }

        @Override
        public void onBindViewHolder(RunHolder holder, Cursor cursor){
            RunDatabaseHelper.RunCursor newCursor  = (RunDatabaseHelper.RunCursor)cursor;
            Run run = newCursor.getRun();
            //Change the background of this RunHolder if its Run is being tracked
            if (mRunManager.isTrackingRun(run)){
                holder.itemView.setBackgroundResource(R.drawable.selected_backgound_activated);
            } else {
                holder.itemView.setBackgroundResource(R.drawable.background_activated);
            }
            holder.bindRun(run);
        }
    }
    //Broadcast Receiver to receiver reports of results of operations this Fragment is interested in.
    private class ResultsReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            //Log.i(TAG, "Intent getAction(): " + action);
            if (action.equals(Constants.ACTION_DELETE_RUNS)) {
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
                        Toast.makeText(getActivity(), R.string.delete_runs_error,
                                Toast.LENGTH_LONG).show();
                    }
                    //Tell the user if there was an error deleting a Location entry.
                    if (results[Constants.LOCATION_DELETIONS] == -1) {
                        Toast.makeText(getActivity(), R.string.delete_locations_error,
                                Toast.LENGTH_LONG).show();
                    }
                    //Report results to the user upon successful deletions.
                } else {
                    Resources r = getActivity().getResources();

                    Toast.makeText(getActivity(), r.getQuantityString(R.plurals.runs_deletion_results,
                                    results[Constants.RUN_DELETIONS],
                                    results[Constants.RUN_DELETIONS],
                                    results[Constants.LOCATION_DELETIONS]),
                            Toast.LENGTH_LONG).show();
                }
            } else if (action.equals(Constants.SEND_RESULT_ACTION)) {
                String actionAttempted = intent
                        .getStringExtra(Constants.ACTION_ATTEMPTED);
                if (actionAttempted
                        .equals(Constants.ACTION_INSERT_RUN)) {
                    //Now that the Intent Service has gotten the new Run inserted into the Run table of the
                    //database, we have a RunId assigned to it that can be used to start the RunPagerActivity
                    //with the new Run's RunFragment as the current item in the ViewPager.
                    Run run = intent.getParcelableExtra(Constants.EXTENDED_RESULTS_DATA);
                    long runId = run.getId();
                    if (runId != -1) {
                        Intent i = RunPagerActivity.newIntent(getActivity(), mSortOrder, runId);
                        startActivity(i);
                    } else {
                        Toast.makeText(getActivity(), R.string.insert_run_error, Toast.LENGTH_LONG).show();
                    }
                }
                if (actionAttempted.equals(Constants.ACTION_UPDATE_END_ADDRESS)){
                    int results = intent.getIntExtra(Constants.EXTENDED_RESULTS_DATA, -1);
                    if (results == 1) {
                        Log.i(TAG, "Successfully updated Ending Address.");
                    } else
                    if (results > 1) {
                        Toast.makeText(getActivity(), R.string.multiple_runs_end_addresses_updated,
                                Toast.LENGTH_LONG).show();
                    } else if (results == 0) {
                        Toast.makeText(getActivity(), R.string.update_end_address_failed,
                                Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(getActivity(), R.string.unknown_end_address_update_error, Toast.LENGTH_LONG).show();
                    }
                }
            }
        }
    }
}
