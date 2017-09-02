package com.dknutsonlaw.android.runtracker2;

/*
  Created by dck on 10/28/15. A Fragment to display a RecyclerView showing all the Runs recorded
  utilizing a loader serving up a cursor holding data concerning all the Runs in the database.
 */

import android.content.BroadcastReceiver;
//import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
//import android.content.IntentSender;
//import android.content.ServiceConnection;
import android.content.res.Resources;
import android.database.Cursor;
import android.os.Bundle;
//import android.os.Handler;
//import android.os.IBinder;
//import android.os.Message;
//import android.os.Messenger;
//import android.os.RemoteException;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.DefaultItemAnimator;
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
//import com.google.android.gms.common.ConnectionResult;
//import com.google.android.gms.common.api.GoogleApiClient;

//import java.lang.ref.WeakReference;
import java.util.List;

public class RunRecyclerListFragment extends Fragment
        implements LoaderManager.LoaderCallbacks<Cursor> {

    private static final String TAG = "RunRecyclerListFragment";

    private IntentFilter mIntentFilter;
    private ResultsReceiver mResultsReceiver;
    private Menu mOptionsMenu;
    //Default sort order is most recent first
    private int mSortOrder = Constants.SORT_BY_DATE_DESC;
    private String mSubtitle;
    private RecyclerView mRunListRecyclerView;
    private RunRecyclerListAdapter mAdapter;
    private TextView mEmptyViewTextView;
    private Button mEmptyViewButton;
    private List<Integer> mDeleteList;
    private ActionMode mActionMode;
    //Are we newly opening this fragment or are we coming back from RunPagerActivity?
    private boolean mFirstVisit;

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
        //The action mode simply gathers the Runs selected for deletion into a List<Integer> member
        //variable. If the confirmation dialog confirms deletion, deleteRuns is called, deletes one-by-one
        //the Runs selected using the member variable List, and then clears the multiselector and
        //finishes the ActionMode. If the deletion is cancelled, the NegativeClick callback simply
        //clears the multiselector and finishes the ActionMode.
        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item){
            //Fetch a List of the Runs by Adapter position that have been selected for deletion.
            mDeleteList = mMultiSelector.getSelectedPositions();
            mActionMode = mode;
            Log.i(TAG, "Runs to be deleted: " + mDeleteList);
            //Now invoke the deletion confirmation dialog, telling where the request comes from and
            //passing along the number of Runs to be deleted so the dialog's message will be accurate.
            Bundle args = new Bundle();
            args.putInt(Constants.FRAGMENT, Constants.RUN_LIST_RECYCLER_FRAGMENT);
            args.putInt(Constants.NUMBER_OF_RUNS, mDeleteList.size());
            DeleteRunsDialog dialog = new DeleteRunsDialog();
            dialog.setArguments(args);
            dialog.show(getActivity().getSupportFragmentManager(), "DeleteDialog");
            return true;
        }
    };

    private void deleteRuns(){
        //If a Run to be deleted is being tracked, stop tracking - otherwise location updates will
        //continue with updates not associated with any Run and really with no way to
        //turn updates off. First, check to see if we're tracking any run.
        if (RunManager.isTrackingRun()){
            //We know a Run's being tracked; check the Runs selected for deletion to see if
            //it's included; if so, stop location updates before deleting
            for (int i = mDeleteList.size()-1; i >= 0; i--) {
                if (RunManager.isTrackingRun(RunManager.getRun(mAdapter.getItemId(mDeleteList.get(i))))) {
                    Log.i(TAG, "Run #" + mAdapter.getItemId(mDeleteList.get(i)) + " and in RecyclerView" +
                            "position #" + mDeleteList.get(i) + " being tracked. Now trying to stop tracking.");
                    Intent serviceIntent = new Intent(RunTracker2.getInstance(), BackgroundLocationService.class);
                    getActivity().stopService(serviceIntent);
                }
                Log.i(TAG, "Did we successfully stop Location Updates? " + RunManager.isTrackingRun());
            }
        }
        //Keep track of number of Runs deleted
        long runsDeleted = 0;
        //Keep track of number of Locations deleted
        long locationsDeleted = 0;
        //Iterate over all the items in the List selected for deletion
        for (int i = mDeleteList.size() - 1; i >= 0; i--) {

            Log.i(TAG, "Now dealing with Run in RecyclerView position #" + mDeleteList.get(i) + " with RunId #" +
                    mAdapter.getItemId(mDeleteList.get(i)));
            //Delete the Run from the database and update the numbers of Runs and locations deleted.
            int results[] = RunManager.getHelper().deleteRun(getActivity(), mAdapter.getItemId(mDeleteList.get(i)));
            Log.i(TAG, "Deleted Run #" + mAdapter.getItemId(mDeleteList.get(i)));
            runsDeleted += results[Constants.RUN_DELETIONS];
            locationsDeleted += results[Constants.LOCATION_DELETIONS];
            //Notify the adapter that we're removing the item in the specified recyclerview position
            Log.i(TAG, "Notify adapter that item in position #" + (mDeleteList.get(i)) + " has been removed.");
            mAdapter.notifyItemRemoved(mDeleteList.get(i));
            //If the Run's RunHolder has been instantiated, the View for the deleted Run also has to
            //be removed from the RecyclerView.
            if (mRunListRecyclerView.getChildAt(mDeleteList.get(i)) != null) {
                mRunListRecyclerView.removeViewAt(mDeleteList.get(i));
            }
            //Finally, notify the adapter that the items from the deleted item to the adapter's end
            //have been modified.
            mAdapter.notifyItemRangeChanged(mDeleteList.get(i), mAdapter.getItemCount());
            Log.i(TAG, "Notified Adapter that Items in range " + mDeleteList.get(i) + " to " + mAdapter.getItemCount() + " changed.");
        }
        Resources r = getResources();
        Toast.makeText(getActivity(), r.getQuantityString(R.plurals.runs_deletion_results,
                (int)runsDeleted,
                (int)runsDeleted,
                (int)locationsDeleted), Toast.LENGTH_LONG).show();
        Log.i(TAG, "Finished deleting.");
        //Clean up the MultiSelector, finish the ActionMode, and refresh the UI now that our
        //dataset has changed
        mMultiSelector.clearSelections();
        Log.i(TAG, "Passed mMultiSelector.clearSelections");
        mActionMode.finish();
        Log.i(TAG, "Passed mode.finish()");
        refreshUI();
        Log.i(TAG, "Passed refreshUI()");
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        //Get the singleton RunManager instance
        //mRunManager = RunManager.get(getActivity());
        if (savedInstanceState != null) {
            //Get sort order and subtitle from savedInstanceState Bundle if the Activity is
            //getting recreated
            mSortOrder = savedInstanceState.getInt(Constants.SORT_ORDER);
            Log.i(TAG, "Getting mSortOrder from savedInstanceState");
            //noinspection ConstantConditions
            ((AppCompatActivity)getActivity()).getSupportActionBar().setSubtitle(savedInstanceState.getString(Constants.SUBTITLE));
        } else {
            //When Activity is created for the first time or if the Fragment is getting created
            //for the first time even though the Activity isn't, get sort order and subtitle
            //from SharedPreferences.
            Log.i(TAG, "Getting Sort Order from SharedPreferences in onCreate");
            mSortOrder = RunTracker2.getPrefs().getInt(Constants.SORT_ORDER, Constants.SORT_BY_DATE_DESC);
            mSubtitle = RunTracker2.getPrefs().getString(Constants.SUBTITLE, /*getActivity().getResources()
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
                    subtitle = r.getQuantityString(R.plurals.recycler_subtitle_date_asc,
                            mAdapter.getItemCount(), mAdapter.getItemCount());
                    break;
                case Constants.SORT_BY_DATE_DESC:
                    subtitle = r.getQuantityString(R.plurals.recycler_subtitle_date_desc,
                            mAdapter.getItemCount(), mAdapter.getItemCount());
                    break;
                case Constants.SORT_BY_DISTANCE_ASC:
                    subtitle = r.getQuantityString(R.plurals.recycler_subtitle_distance_asc,
                            mAdapter.getItemCount(), mAdapter.getItemCount());
                    break;
                case Constants.SORT_BY_DISTANCE_DESC:
                    subtitle = r.getQuantityString(R.plurals.recycler_subtitle_distance_desc,
                            mAdapter.getItemCount(), mAdapter.getItemCount());
                    break;
                case Constants.SORT_BY_DURATION_ASC:
                    subtitle = r.getQuantityString(R.plurals.recycler_subtitle_duration_asc,
                            mAdapter.getItemCount(), mAdapter.getItemCount());
                    break;
                case Constants.SORT_BY_DURATION_DESC:
                    subtitle = r.getQuantityString(R.plurals.recycler_subtitle_duration_desc,
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
        RunTracker2.getPrefs().edit().putInt(Constants.SORT_ORDER, mSortOrder).apply();
        //noinspection ConstantConditions
        saveInstanceState.putString(Constants.SUBTITLE,
                ((AppCompatActivity) getActivity()).getSupportActionBar().getSubtitle().toString());
        try {
            //noinspection ConstantConditions,ConstantConditions
            RunTracker2.getPrefs().edit().putString(Constants.SUBTITLE,
                    ((AppCompatActivity) getActivity()).getSupportActionBar().getSubtitle().toString()).apply();
        } catch (NullPointerException npe){
            Log.i(TAG, "Couldn't write subtitle to default preferences file - attempt to get SupportActionBar" +
                    "returned a null pointer");
        }
        saveInstanceState.putInt(Constants.ADAPTER_ITEM_COUNT, mAdapter.getItemCount());
        RunTracker2.getPrefs().edit().putInt(Constants.ADAPTER_ITEM_COUNT, mAdapter.getItemCount()).apply();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState){
        super.onCreateView(inflater,  parent, savedInstanceState);

        View v = inflater.inflate(R.layout.fragment_recycler_run_list, parent, false);
        mRunListRecyclerView = v.findViewById(R.id.run_recycler_view);
        mRunListRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mRunListRecyclerView.addItemDecoration(new SimpleDividerItemDecoration(getActivity()));
        RecyclerView.ItemAnimator itemAnimator = new DefaultItemAnimator();
        itemAnimator.setRemoveDuration(1000);
        itemAnimator.setAddDuration(1000);
        mRunListRecyclerView.setItemAnimator(itemAnimator);
        mAdapter = new RunRecyclerListAdapter(getActivity(),  RunManager.queryForNoRuns());
        mAdapter.setHasStableIds(true);
        mRunListRecyclerView.setAdapter(mAdapter);
        //We use the sort order of the runs to define the cursor we want to use, so put that into
        //the args Bundle for the OnCreateLoader() callback to use in creating the
        //RunListCursorLoader.
        Bundle args = new Bundle();
        args.putInt(Constants.SORT_ORDER, mSortOrder);
        getLoaderManager().initLoader(Constants.RUN_LIST_LOADER, args, this);
        //Set up UI elements to display if there are no Runs recorded to display in the RecyclerView
        mEmptyViewTextView = v.findViewById(R.id.empty_view_textview);
        mEmptyViewButton = v.findViewById(R.id.empty_view_button);
        mEmptyViewButton.setOnClickListener(v1 -> {
            TrackingLocationIntentService.startActionInsertRun(getActivity(), new Run());
        });
        //Flag first visit so we don't check for which Run we were on when we pressed the Back button
        //in RunPagerAdapter
        mFirstVisit = true;
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
            if (RunManager.isTrackingRun()){
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

    @Override
    public void onPrepareOptionsMenu(Menu menu){
        //Set title of menuItem to change distance measurement units according
        //to the units currently used
        MenuItem item = mOptionsMenu.findItem(R.id.recycler_menu_item_units);
        if (RunTracker2.getPrefs().getBoolean(Constants.MEASUREMENT_SYSTEM, Constants.IMPERIAL)){
            item.setTitle(R.string.imperial);
        } else {
            item.setTitle(R.string.metric);
        }
    }

    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.recycler_menu_item_units:
                //Changed shared prefs value for measurement system and force adapter to redraw all
                //items in the RecyclerView
                RunTracker2.getPrefs().edit().putBoolean(Constants.MEASUREMENT_SYSTEM,
                        !RunTracker2.getPrefs().getBoolean(Constants.MEASUREMENT_SYSTEM, Constants.IMPERIAL)).apply();
                mAdapter.notifyDataSetChanged();
                getActivity().invalidateOptionsMenu();
                return true;
            case R.id.menu_item_new_run:
                //Now that we're using an auto-updating loader for the list of runs, we don't need
                //to call startActivityForResult() - the loader and content observer system take
                //care of updating the list to reflect the presence of a new Run. Note that we
                //don't have to call restartLoader() on the RUN_LIST_LOADER because the query on
                //the database hasn't changed.
                TrackingLocationIntentService.startActionInsertRun(getActivity(), new Run());
                //RunManager.startNewRun();
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
        RunTracker2.getPrefs().edit().putInt(Constants.SORT_ORDER, sortOrder).apply();
        setSubtitle();
        RunTracker2.getPrefs().edit().putString(Constants.SUBTITLE, mSubtitle).apply();
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
        mSortOrder = RunTracker2.getPrefs().getInt(Constants.SORT_ORDER, Constants.SORT_BY_DATE_DESC);
        mSubtitle = RunTracker2.getPrefs().getString(Constants.SUBTITLE,
               /* r.getQuantityString(R.plurals.subtitle_date_desc, mAdapter.getItemCount()*/
                "Stub Value");
        changeSortOrder(mSortOrder);
        refreshUI();
        //If we're coming back here from the RunPagerActivity, check which Run was displayed there
        //and scroll the RecyclerList to place that Run at the top of the display
        if (!mFirstVisit){
            //First fetch the position the displayed Run had in the RunPager - all positions in
            //the RunPager map directly to positions in the adapter and the RecyclerView
            int adapterPosition = RunTracker2.getPrefs().getInt(Constants.ADAPTER_POSITION, 0);
            LinearLayoutManager lm = (LinearLayoutManager)mRunListRecyclerView.getLayoutManager();
            //Scroll RecyclerView so the designated Run is displayed 20 pixels below the top of the
            //display
            lm.scrollToPositionWithOffset(adapterPosition, 20);
        }
        //We will now have displayed the RecyclerView at least once, so clear the FirstVisit flag.
        mFirstVisit = false;
        Log.i(TAG, "onResume called - mSortOrder is " + mSortOrder);
    }

    @Override
    public void onStart(){
        super.onStart();
    }
    @Override
    public void onPause() {
        Log.i(TAG, "onPause called.");
        LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(mResultsReceiver);
        super.onPause();
    }

    @Override
    public void onStop(){
        super.onStop();
    }

    public void onDeleteRunsDialogPositiveClick(){
            deleteRuns();
    }

    public void onDeleteRunsDialogNegativeClick(){
            //If we don't select Delete from the ActionMode menu, just clear the MultiSelector and
            //the ActionMode without doing anything.
            mMultiSelector.clearSelections();
            mActionMode.finish();
            refreshUI();
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

        return new RunListCursorLoader(getActivity(), mSortOrder);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor){
        //Now that we've got a fresh cursor of data, swap out from the adapter the old cursor for
        //the new one and notify the adapter that its data has changed so it will refresh the
        //RecyclerView display. An alternative approach is simply to create a new adapter here, load
        //the cursor into it, and attach the new adapter to the RecyclerListView.
        RunDatabaseHelper.RunCursor newCursor = (RunDatabaseHelper.RunCursor)cursor;
        //The loader should take care of closing the old cursor, so use swapCursor(), not changeCursor()
        mAdapter.swapCursor(newCursor);
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
        final TextView mRunNumberTextView;
        final TextView mStartDateTextView;
        final TextView mStartAddressTextView;
        final TextView mDistanceTextView;
        final TextView mDurationTextView;
        final TextView mEndAddressTextView;

        //Pass the itemView to SwappingHolder constructor to get its features in our ViewHolder class.
        RunHolder(View itemView){
            super(itemView, mMultiSelector);
            itemView.setOnClickListener(this);
            itemView.setLongClickable(true);
            itemView.setOnLongClickListener(this);

            mRunNumberTextView = itemView.findViewById(R.id.run_number_textview);
            mStartDateTextView = itemView.findViewById(R.id.list_item_date_textview);
            mStartAddressTextView = itemView.findViewById(R.id.list_item_start_address_textview);
            mDistanceTextView = itemView.findViewById(R.id.list_item_distance_textview);
            mDurationTextView = itemView.findViewById(R.id.list_item_duration_textview);
            mEndAddressTextView = itemView.findViewById(R.id.list_item_end_address_textview);
        }
        //Plug values from a specific Run into the View elements of the RunHolder
        void bindRun(Run run){
            mRun = run;
            Resources r = getActivity().getResources();
            mRunNumberTextView.setText(r.getString(R.string.run_list_run_number, this.getAdapterPosition() + 1,
                    (int)mAdapter.getItemId(getLayoutPosition())));
            String startDateText = Constants.DATE_FORMAT.format(mRun.getStartDate());
            mStartDateTextView.setText(startDateText);
            mStartAddressTextView.setText(mRun.getStartAddress());
            mDistanceTextView.setText(r.getString(R.string.list_distance_text, RunManager.formatDistance(mRun.getDistance())));
            String durationText = Run.formatDuration((int)mRun.getDuration()/1000);
            mDurationTextView.setText(r.getString(R.string.list_duration_text, durationText));
            mEndAddressTextView.setText(mRun.getEndAddress());
        }

        @Override
        public void onClick(View  v){
            if (mRun == null){
                return;
            }
            //If this RunHolder hasn't been selected for deletion in an ActionMode, start
            //RunPagerActivity specifying its mRun as the one to be displayed when the ViewPager
            //first opens.
            if (!mMultiSelector.tapSelection(this)){
                Intent i = RunPagerActivity.newIntent(getActivity(),
                           RunRecyclerListFragment.this.mSortOrder,
                           mRun.getId());
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
    public class RunRecyclerListAdapter extends CursorRecyclerViewAdapter<RunHolder>{

        RunRecyclerListAdapter(Context context, RunDatabaseHelper.RunCursor cursor){
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
            if (RunManager.isTrackingRun(run)){
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

            if (action != null && action.equals(Constants.ACTION_DELETE_RUNS)) {
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
            } else if (action != null && action.equals(Constants.SEND_RESULT_ACTION)) {
                String actionAttempted = intent
                        .getStringExtra(Constants.ACTION_ATTEMPTED);
                if (actionAttempted
                        .equals(Constants.ACTION_INSERT_RUN)) {
                    //Now that the Intent Service has gotten the new Run inserted into the Run table
                    //of the database, we have a RunId assigned to it that can be used to start the
                    //RunPagerActivity with the new Run's RunFragment as the current item in the
                    //ViewPager.
                    Run run = intent.getParcelableExtra(Constants.EXTENDED_RESULTS_DATA);
                    long runId = run.getId();
                    if (runId != -1) {
                        Intent i = RunPagerActivity.newIntent(getActivity(), mSortOrder, runId);
                        startActivity(i);
                    } else {
                        Toast.makeText(getActivity(),
                                       R.string.insert_run_error,
                                       Toast.LENGTH_LONG)
                                       .show();
                    }
                }
                if (actionAttempted.equals(Constants.ACTION_UPDATE_END_ADDRESS)){
                    int results = intent.getIntExtra(Constants.EXTENDED_RESULTS_DATA, -1);
                    //Successful updates are not reported by the IntentService, so no need to check
                    //for them
                    if (results > 1) {
                        Toast.makeText(getActivity(),
                                       R.string.multiple_runs_end_addresses_updated,
                                       Toast.LENGTH_LONG)
                                       .show();
                    } else if (results == 0) {
                        Toast.makeText(getActivity(),
                                       R.string.update_end_address_failed,
                                       Toast.LENGTH_LONG)
                                       .show();
                    } else {
                        Toast.makeText(getActivity(),
                                       R.string.unknown_end_address_update_error,
                                       Toast.LENGTH_LONG)
                                       .show();
                    }
                }
            }
        }
    }
}
