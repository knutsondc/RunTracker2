package com.dknutsonlaw.android.runtracker2;

import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.content.Loader;
import android.support.v4.app.LoaderManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.NavUtils;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
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

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Date;

/**
 * Created by dck on 9/6/15.
 *
 * A simple {@link android.app.Fragment} subclass to manage the tracking of a particular Run and display
 * statistics relating to the Run. This Fragment is used for both pre-recorded and on-going Runs.
 */
@SuppressWarnings("Convert2Lambda")
public class RunFragment extends Fragment {
    private static final String TAG = "RunFragment";

    private RunManager mRunManager;
    private Context mAppContext;
    private Run mRun;
    private long mRunId;
    private Location mStartLocation, mLastLocation = null;
    private Button mStartButton, mStopButton, mMapButton;
    private TextView mStartedTextView, mStartingLatitudeTextView,
            mStartingLongitudeTextView, mStartingAltitudeTextView, mStartingAddressTextView,
            mEndedTextView, mEndingLatitudeTextView, mEndingLongitudeTextView,
            mEndingAltitudeTextView, mEndingAddressTextView, mDurationTextView,
            mDistanceCoveredTextView, mRunIdTextView;
    private Menu mOptionsMenu;
    private LoaderManager mLoaderManager;
    //We load two data objects in this Fragment, the Run and its last location, so we set up a
    //loader for a cursor of each of them so that the loading takes place on a different thread.
    private final RunCursorLoaderCallbacks mRunCursorLoaderCallbacks = new RunCursorLoaderCallbacks();
    private final LastLocationCursorCallbacks mLastLocationCursorCallbacks = new LastLocationCursorCallbacks();
    //Data structures needed to select and receive local broadcast messages sent by the Intent
    //Service
    private IntentFilter mResultsFilter;
    private ResultsReceiver mResultsReceiver;
    //Set up Service Connection for BackgroundLocationService
    private Messenger mLocationService = null;
    private final Messenger mMessenger = new Messenger(new IncomingHandler(this));
    //ArrayList to hold the LatLngs needed to build a Polyline iin a RunMapFragment
    private final ArrayList<LatLng> mPoints = new ArrayList<>();
    //Bounds to define area a map for this run
    private LatLngBounds mBounds = null;
    private final LatLngBounds.Builder mBuilder = new LatLngBounds.Builder();
    private boolean mStarted = false;
    private boolean mIsTrackingThisRun =false;
    private boolean mIsBound = false;
    private boolean mEndAddressUpdating = false;

    private final ServiceConnection mLocationServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {

            mLocationService = new Messenger(service);
            mIsBound = true;
            try {
                Message msg = Message.obtain(null, Constants.MESSAGE_REGISTER_CLIENT, Constants.MESSENGER_RUNFRAGMENT, 0);
                msg.replyTo = mMessenger;
                mLocationService.send(msg);
            } catch (RemoteException e){
                Log.i(TAG, "RemoteException thrown when trying to send MESSAGE_REGISTER_CLIENT");
            }
            updateUI();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mLocationService = null;
            mIsBound = false;
            updateUI();
        }
    };

    public static RunFragment newInstance(long runId) {
        Bundle args = new Bundle();
        args.putLong(Constants.ARG_RUN_ID, runId);
        RunFragment rf = new RunFragment();
        rf.setArguments(args);
        return rf;
    }

    public RunFragment() {
        // Required empty public constructor
    }
    //Save essential variables of the run upon a configuration change
    /*@Override
    public void onSaveInstanceState(Bundle out){
        out.putLong(Constants.ARG_RUN_ID, mRunId);
        out.putParcelable(Constants.PARAM_RUN, mRun);
        out.putBoolean(Constants.TRACKING_THIS_RUN, mIsTrackingThisRun);
        out.putBoolean(Constants.TRACKING, mStarted);
        out.putParcelable(Constants.STARTING_LOCATION, mStartLocation);
        out.putParcelable(Constants.LAST_LOCATION, mLastLocation);
        out.putParcelableArrayList(Constants.LATLNG_LIST, mPoints);
        out.putParcelable(Constants.MAP_BOUNDS, mBounds);
        out.putParcelable(Constants.LOCATION_SERVICE, mLocationService);
        out.putParcelable(Constants.LOCAL_MESSENGER, mMessenger);
        out.putBoolean(Constants.IS_BOUND, mIsBound);
    }*/

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        //It's easier to keep the connection to the BackgroundLocationService by retaining the fragment
        //instance than any other method I've found
        setRetainInstance(true);
        mRunManager = RunManager.get(getActivity());
        //Turn off DisplayHomeAsUpEnabled so that more of the ActionBar's subtitle will appear in portrait mode
        if (((AppCompatActivity)getActivity()).getSupportActionBar() != null) {
            //noinspection ConstantConditions
            ((AppCompatActivity)getActivity()).getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        }
        /*if (savedInstanceState != null){
            mRunId = savedInstanceState.getLong(Constants.ARG_RUN_ID);
            mRun = savedInstanceState.getParcelable(Constants.PARAM_RUN);
            mIsTrackingThisRun = savedInstanceState.getBoolean(Constants.TRACKING_THIS_RUN);
            mStarted = savedInstanceState.getBoolean(Constants.TRACKING);
            mStartLocation = savedInstanceState.getParcelable(Constants.STARTING_LOCATION);
            mLastLocation = savedInstanceState.getParcelable(Constants.LAST_LOCATION);
            mPoints = savedInstanceState.getParcelableArrayList(Constants.LATLNG_LIST);
            mBounds = savedInstanceState.getParcelable(Constants.MAP_BOUNDS);
            mLocationService = savedInstanceState.getParcelable(Constants.LOCATION_SERVICE);
            mMessenger = savedInstanceState.getParcelable(Constants.LOCAL_MESSENGER);
            mIsBound = savedInstanceState.getBoolean(Constants.IS_BOUND);
        } else {*/
            Bundle args = getArguments();
            if (args != null) {
                long runId = args.getLong(Constants.ARG_RUN_ID, -1);
                Log.i(TAG, "onCreate() runId is " + runId);
                //If the run already has an id, it will have database records associated with it that
                //need to be loaded using Loaders.
                if (runId != -1) {
                    mRunId = runId;
                    mRun = mRunManager.getRun(mRunId);
                    Log.i(TAG, "mRunId is " + mRunId + " in onCreate()");
                }
            //}

            //Load a cursor with all the locations for this Run and hand it to an AsyncTask to initialize
            //the LatLngBounds and List<LatLng> we're going to store for the RunMapFragment to use.
            RunDatabaseHelper.LocationCursor locationCursor = mRunManager.queryLocationsForRun(mRunId);
            LoadPointsAndBounds initTask = new LoadPointsAndBounds(locationCursor);
            initTask.execute();
        }
        //Set up Broadcast Receiver to get reports of results from TrackingLocationIntentService
        //First set up the IntentFilter for the Receiver so it will receive the Intents intended for it
        mResultsFilter = new IntentFilter(Constants.SEND_RESULT_ACTION);
        mResultsFilter.addAction(Constants.ACTION_REFRESH);
        //Now instantiate the Broadcast Receiver
        mResultsReceiver = new ResultsReceiver();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState){
        super.onActivityCreated(savedInstanceState);

        doBindService(this);
        //Get and save the Application Context for use with some functions that might get called
        //when the Activity isn't around or this RunFragment isn't attached.
        mAppContext = getActivity().getApplicationContext();
        //Make sure the loaders are initialized for the newly-launched Run.
        //Loaders need to be initialized here because their life cycle is
        //actually tied to the Activity, not the Fragment. If initialized
        //earlier, we'll get runtime errors complaining that we're trying
        //to start loaders that have already been started and to stop loaders
        //that have already been stopped.
        mLoaderManager = getLoaderManager();
        Bundle args = new Bundle();
        args.putLong(Constants.ARG_RUN_ID, mRunId);
        //Start the loaders for the run and the last location
        mLoaderManager.initLoader(Constants.LOAD_RUN, args, mRunCursorLoaderCallbacks);
        mLoaderManager.initLoader(Constants.LOAD_LOCATION, args, mLastLocationCursorCallbacks);
        //Following is needed when the Activity is destroyed and recreated so that the Fragment
        //in the foreground will have a Run in mRun and thereby present the user with location
        //updates
        if (mRunManager.isTrackingRun() && mRun == null) {
            mRun = new Run();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.i(TAG, "Called onCreateView() for Run " + mRunId);
        View v = inflater.inflate(R.layout.fragment_run, container, false);

        mStartedTextView = (TextView) v.findViewById(R.id.run_startedTextView);
        mStartingLatitudeTextView = (TextView) v.findViewById(R.id.run_starting_latitudeTextView);
        mStartingLongitudeTextView = (TextView) v.findViewById(R.id.run_starting_longitudeTextView);
        mStartingAltitudeTextView = (TextView) v.findViewById(R.id.run__starting_altitudeTextView);
        mStartingAddressTextView = (TextView) v.findViewById(R.id.run_starting_addressTextView);

        mEndedTextView = (TextView) v.findViewById(R.id.run_endedTextView);
        mEndingLatitudeTextView = (TextView) v.findViewById(R.id.run_ending_latitudeTextView);
        mEndingLongitudeTextView = (TextView) v.findViewById(R.id.run_ending_longitudeTextView);
        mEndingAltitudeTextView = (TextView) v.findViewById(R.id.run__ending_altitudeTextView);
        mEndingAddressTextView = (TextView) v.findViewById(R.id.run_ending_address_TextView);
        mDurationTextView = (TextView) v.findViewById(R.id.run_durationTextView);
        mDistanceCoveredTextView = (TextView) v.findViewById(R.id.distance_coveredTextView);
        mRunIdTextView = (TextView) v.findViewById(R.id.runIdTextView);

        mStartButton = (Button) v.findViewById(R.id.run_startButton);
        mStartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.i(TAG, " Pressed StartButton. Run Id is " + mRun.getId());
                mRunManager.startTrackingRun(getActivity(), mRunId);
                Message msg = Message.obtain(null, Constants.MESSAGE_START_LOCATION_UPDATES);
                msg.replyTo = new Messenger(new IncomingHandler(RunFragment.this));
                try {
                    mLocationService.send(msg);
                } catch (RemoteException e){
                    Log.i(TAG, "RemoteException thrown when trying to send MESSAGE_START_LOCATION_UPDATES");
                }
                updateUI();
            }
        });
        mStopButton = (Button) v.findViewById(R.id.run_stopButton);
        mStopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.i(TAG, "Stop Button Pressed. Run is " + mRunId);
                //Do housekeeping for stopping tracking a run.
                mEndAddressUpdating = false;
                mRunManager.stopRun();
                //Tell the BackgroundLocationService to stop location updates
                try {
                    mLocationService.send(Message.obtain(null, Constants.MESSAGE_STOP_LOCATION_UPDATES));
                } catch (RemoteException e){
                    Log.i(TAG, "RemoteException thrown when trying to send MESSAGE_STOP_LOCATION_UPDATES");
                }
                if (mRun.getEndAddress() != null) {
                    TrackingLocationIntentService.startActionUpdateEndAddress(getActivity(),
                            mRun, mRunManager.getLastLocationForRun(mRunId));
                }
                //We've stopped tracking the Run, so refresh the menu to enable "New Run" item
                updateUI();
            }
        });


        mMapButton = (Button) v.findViewById(R.id.run_mapButton);
        mMapButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //Create new instance of RunMapPagerActivity and pass it a reference to this run and the
                //desired sort order.
                //Intent i = new Intent(getActivity(), RunMapPagerActivity.class);
                //i.putExtra(Constants.EXTRA_RUN_ID, mRun.getId());
                Intent i = RunMapPagerActivity.newIntent(getActivity(), Constants.KEEP_EXISTING_SORT, mRunId);
                Log.i(TAG, "Started RunMapPagerActivity for Run " + mRunId);
                startActivity(i);
            }
        });
        //If this isn't a new run, we should immediately populate the textviews.
        //Start with information concerning the starting point.
        //Log.i(TAG, "In onCreateView() for Run " + mRunId + ", mStartLocation null?" + (mRunManager.getStartLocationForRun(mRunId) == null));
        if ((mStartLocation = mRunManager.getStartLocationForRun(mRunId)) != null) {
            //Log.i(TAG, "In onCreateView(), mRunId is " + mRunId + " and mStartLocation is " + mStartLocation.toString());
            mStartedTextView.setText(Constants.DATE_FORMAT.format(mRun.getStartDate()));
            //Report latitude and longitude in degrees, minutes and seconds
            mStartingLatitudeTextView.setText(Location.convert(mStartLocation.getLatitude(), Location.FORMAT_SECONDS));
            mStartingLongitudeTextView.setText(Location.convert(mStartLocation.getLongitude(), Location.FORMAT_SECONDS));
            //Report altitude values in feet
            //mStartingAltitudeTextView.setText(getString(R.string.altitude_format, String.format(Locale.US, "%.2f", mStartLocation.getAltitude() * Constants.METERS_TO_FEET)));
            mStartingAltitudeTextView.setText(mRunManager.formatAltitude(mStartLocation.getAltitude()));
            //Load what this Run has in the database for its Starting Address
            mStartingAddressTextView.setText(mRun.getStartAddress());
            //If what we're showing for the Starting Address is bad, try to get a good address from the
            ///geocoder and record it to the database
            if (mRunManager.addressBad(getActivity(), mStartingAddressTextView.getText().toString())) {
                //mStartingAddressTextView.setText(mRunManager.getRun(mRunId).getStartAddress());
                //mRunManager.updateRunStartAddress(mRun, mStartLocation);
                TrackingLocationIntentService.startActionUpdateStartAddress(getActivity(), mRun, mStartLocation);
            }
            //runIdTextView.setText(String.valueOf(mRunId));
            //runIdTextView.setText(String.valueOf(getArguments().getLong(Constants.ARG_RUN_ID)));
        }
        //Now display what we have concerning the ending point.
        mLastLocation = mRunManager.getLastLocationForRun(mRunId);
        //Log.i(TAG, "In onCreateView for Run " + mRunId + ", mLastLocation null? " + (mRunManager.getLastLocationForRun(mRunId) == null));
        //If we have a last location, display the data we have concerning it.
        if (mLastLocation != null && mLastLocation != mStartLocation) {
            mEndedTextView.setText(Constants.DATE_FORMAT.format(mLastLocation.getTime()));
            mEndingLatitudeTextView.setText(Location.convert(mLastLocation.getLatitude(), Location.FORMAT_SECONDS));
            mEndingLongitudeTextView.setText(Location.convert(mLastLocation.getLongitude(), Location.FORMAT_SECONDS));
            //mEndingAltitudeTextView.setText(getString(R.string.altitude_format, String.format(Locale.US, "%.2f", mLastLocation.getAltitude() * Constants.METERS_TO_FEET)));
            mEndingAltitudeTextView.setText(mRunManager.formatAltitude(mLastLocation.getAltitude()));
            mEndingAddressTextView.setText(mRun.getEndAddress());
            //If our Ending Address loaded from the database is bad, get a new value from the geocoder and store it
            //to the database,then display it
            if (mRunManager.addressBad(getActivity(), mEndingAddressTextView.getText().toString())){
                TrackingLocationIntentService.startActionUpdateEndAddress(getActivity(), mRun, mLastLocation);
            }
            mDurationTextView.setText(Run.formatDuration((int) (mRun.getDuration() / 1000)));
            //double miles = mRun.getDistance() * Constants.METERS_TO_MILES;
            mDistanceCoveredTextView.setText(mRunManager.formatDistance(mRun.getDistance()));
            //mDistanceCoveredTextView.setText(getString(R.string.miles_travelled_format, String.format(Locale.US, "%.2f", miles)));
        }
        //If we have at least one location in addition to the mStartLocation, we can make a map, so
        //enable the map button
        mMapButton.setEnabled(mLastLocation != null && mLastLocation != mStartLocation);
        //Enable Start button only if we're not tracking ANY run at this time
        mStartButton.setEnabled(!mStarted);
        //Enable Stop button only if we're tracking and tracking THIS run
        mStopButton.setEnabled(mStarted && mIsTrackingThisRun);
        //Call updateUI() in case one or both of the addresses are bad and need to be updated.
        //updateUI();
        return v;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater){
        //Assign the options menu to a member variable so we can enable or disable the "New Run" item
        //depending up whether a Run is currently being tracked.
        mOptionsMenu = menu;
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                Intent runPagerActivityIntent = NavUtils.getParentActivityIntent(getActivity());
                if (NavUtils.shouldUpRecreateTask(getActivity(), runPagerActivityIntent)) {
                    //If no instance of RunPagerActivity can be found in the backstack,
                    //create a new one.
                    Log.d(TAG, "Recreating RunPagerActivity");
                    runPagerActivityIntent.putExtra(Constants.EXTRA_RUN_ID, mRun.getId());
                    startActivity(runPagerActivityIntent);
                    return true;
                }

                if (NavUtils.getParentActivityName(getActivity()) != null) {
                    //If we get here, this condition *should* always be true, but better
                    //to check anyway.
                    //noinspection ConstantConditions
                    Log.i(TAG, "onOptionsItemSelected: parent activity name is " +
                            NavUtils.getParentActivityName(getActivity()));
                    NavUtils.navigateUpFromSameTask(getActivity());
                    return true;
                }
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void updateUI() {
        //Are we tracking ANY run? We call the RunManager method because the PendingIntent that
        //the BackgroundLocationService uses to request and remove location updates is supplied by
        //RunManager's getLocationPendingIntent(boolean) method.
        //mStarted = mRunManager.isTrackingRun(mAppContext);
        mStarted = mRunManager.isTrackingRun();
        //Are we tracking THIS run?
        mIsTrackingThisRun = mRunManager.isTrackingRun(mRun);
        //It's possible for the Fragment to try to update its state when not attached to the under-
        //lying Activity - result then is crash!
        if (isAdded()) {
            if (mRun == null) {
                return;
            }
            //If we have at least one location in addition to the mStartLocation, we can make a map, so
            //enable the map button
            mMapButton.setEnabled(mLastLocation != null && mLastLocation != mStartLocation);
            //Enable Start button only if we're not tracking ANY run at this time
            mStartButton.setEnabled(!mStarted);
            //Enable Stop button only if we're tracking and tracking THIS run
            mStopButton.setEnabled(mStarted && mIsTrackingThisRun);
            //Fill in value for RunId TextView
            mRunIdTextView.setText(String.valueOf(mRunId));
            //The OptionsMenu Item needs to be checked here so that the New Run menu item will be
            //re-enabled after the user presses Stop.
            if (mOptionsMenu != null) {
                //Disable the New Run menu item if we're tracking another Run - program will crash if
                //a new Run is started when another is being tracked.
                if (mStarted) {
                    mOptionsMenu.findItem(R.id.run_pager_menu_item_new_run).setEnabled(false);
                } else {
                    mOptionsMenu.findItem(R.id.run_pager_menu_item_new_run).setEnabled(true);
                }
            }

            //If we haven't yet gotten a starting location for this run, try to get one. Once we've
            //gotten a starting location, no need to ask for it again.
            if (mRun != null && mStartLocation == null) {
                Log.i(TAG, "For Run " + mRunId + "at beginning of updateUI() section re mStartLocation mStartLocation is null");
                mStartLocation = mRunManager.getStartLocationForRun(mRunId);
                if (mStartLocation != null) {
                    //Now that we've gotten a Starting Location, record and display information about it.
                    //Change the start date to the timestamp of the first Location object received.
                    mRun.setStartDate(new Date(mStartLocation.getTime()));
                    //Now write the new start date to the database
                    TrackingLocationIntentService.startActionUpdateStartDate(mAppContext, mRun);
                    //Finally, display the new start date
                    mStartedTextView.setText(Constants.DATE_FORMAT.format(mRun.getStartDate()));
                    mStartingLatitudeTextView.setText(Location.convert(mStartLocation.getLatitude(), Location.FORMAT_SECONDS));
                    mStartingLongitudeTextView.setText(Location.convert(mStartLocation.getLongitude(), Location.FORMAT_SECONDS));
                    mStartingAltitudeTextView.setText(mRunManager.formatAltitude(mStartLocation.getAltitude()));
                    //mStartingAltitudeTextView.setText(getString(R.string.altitude_format, String.format(Locale.US, "%.2f", (mStartLocation.getAltitude() * Constants.METERS_TO_FEET))));
                    //We won't have a Starting Address yet, so ask for one and record it.
                    TrackingLocationIntentService.startActionUpdateStartAddress(mAppContext, mRun, mStartLocation);
                    mStartingAddressTextView.setText(mRun.getStartAddress());
                    //If we get here, mBounds should be null, but better to check. Put the starting location into the LatLngBounds
                    //Builder and later, when at least one additional location has also been included, build mBounds.
                    if (mBounds == null) {
                        mBuilder.include(new LatLng(mStartLocation.getLatitude(), mStartLocation.getLongitude()));
                    }
                    //If we get here, mPoints should have zero elements, but better to check, then add the
                    //mStartLocation as the first element.
                    if (mPoints.size() == 0) {
                        mPoints.add(new LatLng(mStartLocation.getLatitude(), mStartLocation.getLongitude()));
                    }

                } else {
                    Log.i(TAG, "getStartLocationForRun returned null for Run " + mRun.getId());
                }
            }

            //If we have a starting location but don't yet have a starting address, get one and update
            //the Run Table with a new starting date equal to the time of the first location and with
            //the new starting address. Once we have a starting address,no need to reload any data
            //concerning the Start Location - it won't change as the Run goes on..
            if (mRun != null && mStartLocation != null) {
                mStartingAltitudeTextView.setText(mRunManager.formatAltitude(mStartLocation.getAltitude()));
                if (mRunManager.addressBad(getActivity(), mStartingAddressTextView.getText().toString())) {
                    Log.i(TAG, "mRun.getStartAddress() for Run " + mRun.getId() + " is bad; calling updateRunStartAddress().");
                    //Get the starting address from the geocoder and record that in the Run Table
                    TrackingLocationIntentService.startActionUpdateStartAddress(mAppContext, mRun, mStartLocation);
                    mStartingAddressTextView.setText(mRun.getStartAddress());
                    Log.i(TAG, "After getting bad Start Address for Run " + mRunId + " and updating, Start Address is " + mRun.getStartAddress());
                    //}
                }
            }
            //mLastLocation gets set by the LastLocationLoader
            //When the Run is returned from the loader, it will have updated Duration and Distance
            //values. If we have a run and a last location for it, we will have duration and
            //distance values for it in the Run Table, so retrieve and display them. This has to
            //be done every time a new location is recorded and, accordingly, the UI updates.
            if (mRun != null && mLastLocation != null && mLastLocation != mStartLocation) {
                //If we're tracking this Run and haven't started updating the ending address, start
                //doing so
                if (!mEndAddressUpdating && mRunManager.isTrackingRun(mRun)) {
                    mRunManager.startUpdatingEndAddress(getActivity());
                    mEndAddressUpdating = true;
                    Log.i(TAG, "Called mRunManager.startUpdatingEndAddress(getActivity()) for Run " + mRunId);
                }
                Log.i(TAG, "In updateUI() section dealing with mLastLocation, mRunId is " + mRunId + " and mLastLocation is " + mLastLocation.toString());
                mDurationTextView.setText(Run.formatDuration((int) (mRun.getDuration() / 1000)));
                mDistanceCoveredTextView.setText(mRunManager.formatDistance(mRun.getDistance()));
                mEndingLatitudeTextView.setText(Location.convert(mLastLocation.getLatitude(), Location.FORMAT_SECONDS));
                mEndingLongitudeTextView.setText(Location.convert(mLastLocation.getLongitude(), Location.FORMAT_SECONDS));
                mEndingAltitudeTextView.setText(mRunManager.formatAltitude(mLastLocation.getAltitude()));
                mEndedTextView.setText(Constants.DATE_FORMAT.format(mLastLocation.getTime()));
                mEndingAddressTextView.setText(mRun.getEndAddress());
                Log.i(TAG, "In updateUI() Ending Address for Run " + mRun.getId() + " is " + mEndingAddressTextView.getText());
                //We don't check for bad Ending Addresses because the Ending Address gets updated every five seconds
                //while the Run is being tracked.
                //If mBounds hasn't been initialized yet, add this location to the Builder and create
                //mBounds. If mBounds has been created, simply add this point to it.
                if (mBounds == null) {
                    mBuilder.include(new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude()));
                    mBounds = mBuilder.build();
                } else {
                    mBounds.including(new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude()));
                }
                //Add this point to the collection of points for updating the polyline
                mPoints.add(new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude()));
            }

        }
        //Save mBounds and mPoints to singletons created by RunManager so they will be available to
        //the RunMapFragment for this run
        mRunManager.saveBounds(mRunId, mBounds);
        mRunManager.savePoints(mRunId, mPoints);
    }

    private void updateOnStartingLocationUpdates(){
        updateUI();
       /* mStartButton.setEnabled(!mRunManager.isTrackingRun(getActivity().getApplicationContext()));
        mStopButton.setEnabled(mRunManager.isTrackingRun(getActivity().getApplicationContext()) && mRunManager.isTrackingRun(mRun));*/
        Log.i(TAG, "In updateOnStartingLocationUpdates(), is mStartButton enabled? " + mStartButton.isEnabled() + " mStopButton enabled? " + mStopButton.isEnabled());
    }

    private void updateOnStoppingLocationUpdates(){
        updateUI();
        /*mStartButton.setEnabled(!mRunManager.isTrackingRun(getActivity().getApplicationContext()));
        mStopButton.setEnabled(mRunManager.isTrackingRun(getActivity().getApplicationContext()) && mRunManager.isTrackingRun(mRun));*/
        Log.i(TAG, "In updateOnStoppingLocationUpdates(), is mStartButton enabled? " + mStartButton.isEnabled() + " mStopButton enabled? " + mStopButton.isEnabled());
    }

    private void restartLoaders() {
        //Need to check if mRun has been initialized - sometimes on a new run it will not have been
        //before we get here.
        if (mRun != null) {
            Bundle args = new Bundle();
            args.putLong(Constants.ARG_RUN_ID, mRun.getId());
            if (mLoaderManager == null){
                Log.i(TAG, "In restartLoaders(), mLoaderManager is null for Run " + mRunId);
                return;
            }
            mLoaderManager.restartLoader(Constants.LOAD_RUN, args, mRunCursorLoaderCallbacks);
            mLoaderManager.restartLoader(Constants.LOAD_LOCATION, args, mLastLocationCursorCallbacks);
        }
    }

    @Override
    public void onPause() {
        //Need to unregister the Receiver for local broadcasts when the Fragment's entering the
        //Paused state to avoid memory leak.
        LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(mResultsReceiver);
        super.onPause();
    }

    @Override
    public void onDestroy(){
        doUnbindService(this);
        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
        //We need to register our broadcast receiver here; it gets unregistered when the Fragment pauses.
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(
                mResultsReceiver,
                mResultsFilter);

        restartLoaders();
        //The entire View needs to be reestablished upon Resume.
        updateUI();
    }

    private static void doBindService(RunFragment fragment){
        //Bind service to the ApplicationContext so that it won't die during configuration changes.
        fragment.getActivity().getApplicationContext().bindService(new Intent(fragment.getActivity(), BackgroundLocationService.class),
                fragment.mLocationServiceConnection, Context.BIND_AUTO_CREATE);
        fragment.mIsBound = true;
    }

    private static void doUnbindService(RunFragment fragment){
        if (fragment.mIsBound){
            fragment.getActivity().getApplicationContext().unbindService(fragment.mLocationServiceConnection);
            fragment.mIsBound = false;
            fragment.mLocationService = null;
        }
    }

    private class RunCursorLoaderCallbacks implements LoaderManager.LoaderCallbacks<Cursor> {

        @Override
        public Loader<Cursor> onCreateLoader(int d, Bundle args){
            long runId  = args.getLong(Constants.ARG_RUN_ID);
            //Return a Loader pointing to a cursor containing a Run with specified RunId
            return new RunCursorLoader(getActivity(), Constants.URI_TABLE_RUN, runId);
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor cursor){

            if (cursor != null) {
                //Need to cast the superclass Cursor returned to us to the RunCursor it really is so we
                //can extract the Run from it.
                RunDatabaseHelper.RunCursor newCursor = (RunDatabaseHelper.RunCursor)cursor;
                Run run = null;
                if (newCursor.moveToFirst()){
                    if (!newCursor.isAfterLast()){
                        run = newCursor.getRun();
                    } else {
                        Log.i(TAG, "In RunCursorLoaderCallbacks, cursor went past last position");
                    }
                } else {
                    Log.i(TAG, "Couldn't move RunCursor to First Position in onLoadFinished");
                }
                if (run != null) {
                    mRun = run;
                    mRunId = mRun.getId();
                    Log.i(TAG, "Got a run from RunCursorLoader. RunId = " + mRunId);
                    if (mIsTrackingThisRun){
                        Log.i(TAG, "Tracking Run " + mRunId);
                    }
                    updateUI();
                } else {
                    Log.i(TAG, "Run returned by RunCursor was null: " + mRunId);
                }
            } else {
                Log.i(TAG, "Cursor returned by RunCursorLoader was null");
            }
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader){
            //do nothing
        }
    }

    private class LastLocationCursorCallbacks implements LoaderManager.LoaderCallbacks<Cursor>{
        //Return a loader with a cursor containing a the last location recorded for the designated
        //run.
        @Override
        public Loader<Cursor> onCreateLoader(int d, Bundle args){
            long runId = args.getLong(Constants.ARG_RUN_ID);
            return new LastLocationCursorLoader(getActivity(), Constants.URI_TABLE_LOCATION, runId);
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor cursor){

            if (cursor != null) {
                //Cast the superclass Cursor returned to us to the LocationCursor it really is so we
                ///can extract the Location from it
                RunDatabaseHelper.LocationCursor newCursor = (RunDatabaseHelper.LocationCursor) cursor;
                if (newCursor.moveToFirst()) {
                    if (!newCursor.isAfterLast()) {
                        mLastLocation = newCursor.getLocation();
                        if (mLastLocation != null) {
                            updateUI();
                        } else {
                            Log.i(TAG, "LastLocationCursorLoader returned a null Location");
                        }
                    } else {
                        Log.i(TAG, "In LastLocationCursorLoader, cursor went past last position");
                    }
                } else {
                    Log.i(TAG, "In LastLocationCursorLoader, couldn't move to first position of cursor.");
                }
            } else {
                Log.i(TAG, "LastLocationCursorLoader returned a null cursor");
            }
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader){
            //do nothing
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int grantResults[]){
        if (requestCode == Constants.REQUEST_LOCATION_PERMISSIONS){
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                grantResults[1] == PackageManager.PERMISSION_GRANTED){
                Message msg = Message.obtain(null, Constants.MESSAGE_PERMISSION_REQUEST_SUCCEEDED);
                try {
                    mLocationService.send(msg);
                } catch (RemoteException e){
                    Log.i(TAG, "RemoteException thrown when trying to send MESSAGE_PERMISSION_REQUEST_SUCCEEDED");
                }
                Toast.makeText(getActivity(), "Location permissions granted.",
                                Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(getActivity(), "Location permissions denied. No tracking possible.",
                                Toast.LENGTH_LONG).show();
                if (mRun.getDuration() == 0){
                    Toast.makeText(getActivity(), "Never got any locations for this Run; deleting.",
                            Toast.LENGTH_LONG).show();
                    //mRunManager.deleteRun(mRunId);
                    TrackingLocationIntentService.startActionDeleteRun(getActivity(), mRunId);
                    Message msg = Message.obtain(null, Constants.MESSAGE_PERMISSION_REQUEST_CANCELED);
                    try {
                        mLocationService.send(msg);
                    } catch (RemoteException e){
                        Log.i(TAG, "RemoteException thrown when trying to send MESSAGE_PERMISSION_REQUEST_CANCELED");
                    }
                }
                /*if (mIsBound) {
                    doUnbindService(this);
                }*/
            }
        } else {
            Log.i(TAG, "REQUEST_LOCATION_PERMISSIONS is the only requestCode used. How'd you get here!?!");
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data){
        Log.i(TAG, "Reached onActivityResult()");
        Log.i(TAG, "requestCode is " + requestCode);
        Log.i(TAG, "resultCode is " +resultCode);
        if (requestCode == Constants.LOCATION_CHECK){
            switch (resultCode){
                case Activity.RESULT_OK:
                    Toast.makeText(getActivity(), "All Location Settings requirements now met.", Toast.LENGTH_LONG).show();
                    try {
                        mLocationService.send(Message.obtain(null, Constants.MESSAGE_LOCATION_SETTINGS_RESOLUTION_SUCCEEDED));
                    } catch (RemoteException e){
                        Log.i(TAG, "RemoteException thrown when trying to send MESSAGE_LOCATION_SETTINGS_SUCCEEDED");
                    }
                    break;
                case Activity.RESULT_CANCELED:
                    Toast.makeText(getActivity(), "You declined to enable Location Services.\n" +
                                                    "Stopping tracking of this run.", Toast.LENGTH_LONG).show();
                    try {
                        mLocationService.send(Message.obtain(null, Constants.MESSAGE_LOCATION_SETTINGS_RESOLUTION_FAILED));
                    } catch (RemoteException e){
                        Log.i(TAG, "RemoteException thrown when trying to send MESSAGE_LOCATION_SETTINGS_RESOLUTION_FAILED");
                    }
                    if (mRun.getDuration() == 0){
                        Toast.makeText(getActivity(), "Never got any locations for this Run. Deleting Run.", Toast.LENGTH_LONG).show();
                        //mRunManager.deleteRun(mRunId);
                        TrackingLocationIntentService.startActionDeleteRun(getActivity(), mRunId);
                    }
                    doUnbindService(this);
                    break;
            }
        } else if (requestCode == Constants.MESSAGE_PLAY_SERVICES_RESOLUTION_REQUEST){
            switch (resultCode){
                case Activity.RESULT_OK:
                    try {
                        mLocationService.send(Message.obtain(null, Constants.MESSAGE_TRY_GOOGLEAPICLIENT_RECONNECTION));
                    } catch (RemoteException e){
                        Log.i(TAG, "RemoteException thrown when trying to send MESSAGE_TRY_GOOGLEAPICLIENT_RECONNECTION");
                    }
                    break;
                case Activity.RESULT_CANCELED:
                    Toast.makeText(getActivity(), "You canceled recovery of Google Play Services. Stopping Tracking.", Toast.LENGTH_LONG).show();
                    doUnbindService(this);
                    break;
            }
        }
    }
    private static void showErrorDialog(RunFragment fragment, int errorCode){
        ErrorDialogFragment dialogFragment = new ErrorDialogFragment();
        Bundle args = new Bundle();
        args.putInt(Constants.ARG_ERROR_CODE, errorCode);
        dialogFragment.setArguments(args);
        dialogFragment.show(fragment.getActivity().getSupportFragmentManager(), "errordialog");
    }

    //Broadcast receiver for receiving results of actions taken by TrackingLocationIntentService
    private class ResultsReceiver extends BroadcastReceiver {
        //Called when the ResultsReceiver gets an Intent it's registered for -
        @Override
        public void onReceive(Context context, Intent intent) {

            String action = intent.getAction();
            if (action.equals(Constants.ACTION_REFRESH)) {
                Log.i(TAG, "Received refresh broadcast - calling updateUI().");
                updateUI();
            }
            if (!mIsTrackingThisRun){
                Log.i(TAG, "Not tracking Run " + mRunId + " - ignoring broadcast");
                return;
            }
            //This should always be SEND_ACTION_RESULT, but check for it anyway
            Log.i(TAG, "onReceive() getAction() is: " + action + " for Run " + mRunId);
            if (action.equals(Constants.SEND_RESULT_ACTION)) {
                //Dispatch Intents for processing based upon the value passed in the
                //ACTION_ATTEMPTED Extras key. Data specific to each different ACTION_ATTEMPTED
                //value is carried with the EXTENDED_RESULTS_DATA key
                String actionAttempted =
                        intent.getStringExtra(Constants.ACTION_ATTEMPTED);
                Log.i(TAG, "onReceive() actionAttempted: " + actionAttempted + " for Run " + mRunId);

                switch (actionAttempted) {
                    case Constants.ACTION_UPDATE_START_DATE: {
                        int result =
                                intent.getIntExtra(Constants.EXTENDED_RESULTS_DATA, -1);
                        int toastTextRes;
                        //The getWritableDatabase.update() method returns the number of rows affected; that
                        //value is returned to the IntentService and passed on to here. If no rows were
                        //affected or more than one row was affected, something went wrong! The
                        //IntentService no longer reports successful updates, so no need to check for
                        //those.
                        /*if (result == 1) {
                            Log.i(TAG, "ResultsReceiver reports Starting Date successfully updated for Run " + mRunId);
                        } else */
                        if (result == 0) {
                            toastTextRes = R.string.update_run_start_date_failed;
                        } else if (result > 1) {
                            toastTextRes = R.string.multiple_runs_dates_updated;
                        } else {
                            toastTextRes = R.string.unknown_start_date_error;
                        }
                        //If an error occurred, put up a Toast advising the user of how things went wrong.
                        if (result != 1) {
                            if (isAdded()) {
                                Toast.makeText(getActivity(), toastTextRes, Toast.LENGTH_LONG).show();
                            }
                        }
                        break;
                    }
                    case Constants.ACTION_UPDATE_START_ADDRESS: {
                        int result = intent.getIntExtra(Constants.EXTENDED_RESULTS_DATA, -1);
                        int toastTextRes;
                        //The getWritableDatabase.update() method returns the number of rows affected; that
                        //value is returned to the IntentService and passed on to here. If no rows were
                        //affected or more than one row was affected, something went wrong! The IntentService
                        //no longer reports successful updates, so no need to check for those.
                        /*if (result == 1){
                            Log.i(TAG, "ResultsReceiver reports Starting Address successfully updated for Run " + mRunId);
                        } else */
                        if (result == 0) {
                            toastTextRes = R.string.update_run_start_address_failed;
                        } else if (result > 1) {
                            toastTextRes = R.string.multiple_start_addresses_error;
                        } else {
                            toastTextRes = R.string.unknown_start_address_error;
                        }
                        if (result != 1) {
                            if (isAdded()) {
                                Toast.makeText(getActivity(), toastTextRes, Toast.LENGTH_LONG).show();
                            }
                        }
                        break;
                    }
                    case Constants.ACTION_UPDATE_END_ADDRESS: {
                        int result =
                                intent.getIntExtra(Constants.EXTENDED_RESULTS_DATA, -1);
                        int toastTextRes;
                        //The getWritableDatabase.update() method returns the number of rows affected; that
                        //value is returned to the IntentService and passed on to here. If no rows were
                        //affected or more than one row was affected, something went wrong! The
                        //IntentService no longer reports successful updates, so no need to check
                        //for those.
                        /*if (result == 1) {
                            Log.i(TAG, "ResultsReceiver reports Ending Address successfully updated for Run " + mRunId);
                        } else */
                        if (result == 0) {
                            toastTextRes = R.string.update_end_address_failed;
                        } else if (result > 1) {
                            toastTextRes = R.string.multiple_runs_end_addresses_updated;
                        } else {
                            toastTextRes = R.string.unknown_end_address_update_error;
                        }
                        //If an error occurred, put up a Toast advising the user of how things went wrong.
                        if (result != 1) {
                            if (isAdded()) {
                                Toast.makeText(getActivity(), toastTextRes, Toast.LENGTH_LONG).show();
                            }
                        }
                        break;
                    }
                    case Constants.ACTION_INSERT_LOCATION: {
                        long result[] =
                                intent.getLongArrayExtra(Constants.EXTENDED_RESULTS_DATA);
                        int toastTextRes;
                        //If insertion of new location entry failed because it was more than 100 meters from
                        //the last previous location, stop tracking this run, update the UI, and tell the
                        //user why.
                        if (result[Constants.CONTINUATION_LIMIT_RESULT] == -1) {
                            mRunManager.stopRun();
                            mEndAddressUpdating = false;
                            //getActivity().stopService(new Intent(getActivity(), BackgroundLocationService.class));
                            try {
                                mLocationService.send(Message.obtain(null, Constants.MESSAGE_STOP_LOCATION_UPDATES));
                            } catch (RemoteException e){
                                Log.i(TAG, "RemoteException thrown when trying to send MESSAGE_STOP_LOCATION_UPDATES");
                            }
                            if (mRun.getEndAddress() != null) {
                                TrackingLocationIntentService.startActionUpdateEndAddress(getActivity(),
                                        mRun, mRunManager.getLastLocationForRun(mRunId));
                            }
                            //We've stopped tracking the Run, so refresh the menu to enable "New Run" item
                            updateUI();
                            toastTextRes = R.string.current_location_too_distant;
                            Toast.makeText(RunFragment.this.getActivity(), toastTextRes, Toast.LENGTH_LONG).show();
                            return;
                        }

                        //SQLDatabase.insert() returns row number of inserted values upon success, -1
                        //on error. Any error result is returned to IntentService and passed along
                        //here so it can be reported to the user. Reports of success are not sent
                        //by the IntentService.
                        /*if (result[Constants.LOCATION_INSERTION_RESULT] != -1) {
                            Log.i(TAG, "Successfully inserted Location at row " + result[Constants.LOCATION_INSERTION_RESULT] +
                                    " for Run " + mRunId);
                        } else */
                        if (result[Constants.LOCATION_INSERTION_RESULT] == -1){
                            //Upon error, throw up a Toast advising the user.
                            toastTextRes = R.string.location_insert_failed;
                            Toast.makeText(getActivity(), toastTextRes, Toast.LENGTH_LONG).show();
                        }
                        /*if (result[Constants.RUN_UPDATE_RESULT] != -1) {
                            Log.i(TAG, "Successfully updated Run " + mRunId);
                        } else */
                        if (result[Constants.RUN_UPDATE_RESULT] == -1){
                            toastTextRes = R.string.update_run_error;
                            if (isAdded()) {
                                Toast.makeText(getActivity(), toastTextRes, Toast.LENGTH_LONG).show();
                            }
                        }
                        break;
                    }
                }
            }
        }

    }
    //Simple AsyncTask to load the locations for this Run into a LatLngBounds and a List<LatLng> for
    //the use of the RunMapFragment for this RunId.
    private class LoadPointsAndBounds extends AsyncTask<Void, Void, Void>{
        private final RunDatabaseHelper.LocationCursor mCursor;

        LoadPointsAndBounds(RunDatabaseHelper.LocationCursor cursor){
            mCursor = cursor;
        }
        @Override
        protected Void doInBackground(Void... params){
            Location location;
            LatLng latLng;
            mCursor.moveToFirst();
            while (!mCursor.isAfterLast()) {
                location = mCursor.getLocation();
                latLng = new LatLng(location.getLatitude(), location.getLongitude());
                mPoints.add(latLng);
                mBuilder.include(latLng);
                mCursor.moveToNext();
            }
            if (mPoints.size() > 0){
                mBounds = mBuilder.build();
            }
            return null;
        }
    }

    public static class ErrorDialogFragment extends DialogFragment {
        public ErrorDialogFragment(){

        }
        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState){
            int errorCode = this.getArguments().getInt(Constants.ARG_ERROR_CODE);
            return GoogleApiAvailability.getInstance().getErrorDialog(this.getActivity(), errorCode,
                    Constants.MESSAGE_PLAY_SERVICES_ERROR_DIALOG_REQUEST);
        }
    }

    private static class IncomingHandler extends Handler {
        private final WeakReference<RunFragment> mFragment;

        IncomingHandler(RunFragment fragment) {
            mFragment = new WeakReference<>(fragment);
        }

        public void handleMessage(Message msg){
            RunFragment fragment = mFragment.get();
            if (fragment != null) {
                switch (msg.what) {
                    case Constants.MESSAGE_GOOGLEAPICLIENT_CONNECTION_FAILED:
                        ConnectionResult connectionResult = (ConnectionResult) msg.obj;
                        try {
                            connectionResult.startResolutionForResult(fragment.getActivity(), Constants.MESSAGE_PLAY_SERVICES_RESOLUTION_REQUEST);
                        } catch (IntentSender.SendIntentException e) {
                            Log.i(TAG, "Caught IntentSender.SentIntentException while trying to invoke startResolutionForResult" +
                                    "with request code MESSAGE_PLAY_SERVICES_RESOLUTION_REQUEST");
                        }
                        break;
                    case Constants.MESSAGE_PLAY_SERVICES_ERROR_DIALOG_REQUEST:
                        showErrorDialog(fragment, msg.arg1);
                        break;
                    case Constants.MESSAGE_GOOGLEAPICLIENT_CONNECTION_SUSPENDED:
                        switch (msg.arg1) {
                            case GoogleApiClient.ConnectionCallbacks.CAUSE_NETWORK_LOST:
                                Toast.makeText(fragment.getActivity(), R.string.connection_suspended_network_lost, Toast.LENGTH_LONG).show();
                                break;
                            case GoogleApiClient.ConnectionCallbacks.CAUSE_SERVICE_DISCONNECTED:
                                Toast.makeText(fragment.getActivity(), R.string.connection_suspended_service_disconnected, Toast.LENGTH_LONG).show();
                                break;
                            default:
                                break;
                        }
                        doUnbindService(fragment);
                        break;
                    case Constants.MESSAGE_LOCATION_SETTINGS_RESOLUTION_NEEDED:
                        LocationSettingsResult locationSettingsResult = (LocationSettingsResult) msg.obj;
                        Status status = locationSettingsResult.getStatus();
                        try {
                            status.startResolutionForResult(fragment.getActivity(), Constants.LOCATION_CHECK);
                        } catch (IntentSender.SendIntentException e) {
                            Log.i(TAG, "Caught IntentSender.SentIntentException while trying to invoke startResolutionForResult" +
                                    "with request code LOCATION_CHECK");
                        }
                        break;
                    case Constants.MESSAGE_LOCATION_SETTINGS_NOT_AVAILABLE:
                        Toast.makeText(fragment.getActivity(), "Location Settings not available. Can't track this run.", Toast.LENGTH_LONG).show();
                        doUnbindService(fragment);
                        break;
                    case Constants.MESSAGE_PERMISSION_REQUEST_NEEDED:
                        Log.i(TAG, "Now asking user for permission to use location data.");
                        fragment.requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION,
                                        android.Manifest.permission.ACCESS_COARSE_LOCATION},
                                Constants.REQUEST_LOCATION_PERMISSIONS);
                        break;
                    case Constants.MESSAGE_LOCATION_UPDATES_STARTED:
                        Log.i(TAG, "Received MESSAGE_LOCATION_UPDATES_STARTED");
                        fragment.updateOnStartingLocationUpdates();
                        //fragment.updateOnStartingLocationUpdates();
                        break;
                    case Constants.MESSAGE_LOCATION_UPDATES_STOPPED:
                        Log.i(TAG, "Received MESSAGE_LOCATION_UPDATES_STOPPED");
                        fragment.updateOnStoppingLocationUpdates();
                        //fragment.updatepingLocationUpdates();
                        break;
                    default:
                        break;
                }
            }
        }
    }
}
