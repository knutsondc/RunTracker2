package com.dknutsonlaw.android.runtracker2;

import android.*;
import android.Manifest;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.app.FragmentTransaction;
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

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Created by dck on 9/6/15.
 *
 * A simple {@link android.app.Fragment} subclass to manage the tracking of a particular Run and display
 * statistics relating to the Run. This Fragment is used for both pre-recorded and on-going Runs.
 */
public class RunFragment extends Fragment {
    private static final String TAG = "RunFragment";

    private RunManager mRunManager;
    private BoundsHolder mBoundsHolder;
    private PointsHolder mPointsHolder;
    private Run mRun;
    private long mRunId;
    private Location mStartLocation, mLastLocation = null;
    private Button mStartButton, mStopButton, mMapButton;
    private TextView mStartedTextView, mStartingLatitudeTextView,
            mStartingLongitudeTextView, mStartingAltitudeTextView, mStartingAddressTextView,
            mEndedTextView, mEndingLatitudeTextView, mEndingLongitudeTextView,
            mEndingAltitudeTextView, mEndingAddressTextView, mDurationTextView,
            mDistanceCoveredTextView;
    private Menu mOptionsMenu;
    private LoaderManager mLoaderManager;
    //We load two data objects in this Fragment, the Run and its last location, so we set up a
    //loader for a cursor of each of them so that the loading takes place on a different thread.
    //private final RunLoaderCallbacks mRunLoaderCallbacks = new RunLoaderCallbacks();
    //private final LastLocationLoaderCallbacks mLastLocationLoaderCallbacks = new LastLocationLoaderCallbacks();
    private RunCursorLoaderCallbacks mRunCursorLoaderCallbacks = new RunCursorLoaderCallbacks();
    private LastLocationCursorCallbacks mLastLocationCursorCallbacks = new LastLocationCursorCallbacks();
    //Data structures needed to select and receive local broadcast messages sent by the Intent
    //Service
    private IntentFilter mResultsFilter;
    private ResultsReceiver mResultsReceiver;
    //Set up Service Connection for BackgroundLocationService
    private BackgroundLocationService mLocationService;
    public ArrayList<LatLng> mPoints = new ArrayList<>();
    private LatLngBounds mBounds = null;
    private LatLngBounds.Builder mBuilder = new LatLngBounds.Builder();
    boolean mIsBound = false;
    boolean mStarted, mIsTrackingThisRun, mMapButtonEnabled = false;
    private ServiceConnection mLocationServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BackgroundLocationService.LocalBinder binder =
                    (BackgroundLocationService.LocalBinder)service;
            mLocationService = binder.getService();
            mIsBound = true;
            //Following is needed when the Activity is destroyed and recreated so that the Fragment
            //in the foreground will have a Run in mRun and thereby present the user with location
            //updates
            if (mRunManager.isTrackingRun() && mRun == null) {
                mRun = new Run();
            }
            updateUI();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
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

    @Override
    public void onSaveInstanceState(Bundle out){
        out.putLong(Constants.ARG_RUN_ID, mRunId);
        out.putParcelable(Constants.PARAM_RUN, mRun);
        out.putBoolean(Constants.TRACKING_THIS_RUN, mIsTrackingThisRun);
        out.putBoolean(Constants.TRACKING, mStarted);
        out.putParcelable(Constants.STARTING_LOCATION, mStartLocation);
        out.putParcelable(Constants.LAST_LOCATION, mLastLocation);
        out.putParcelableArrayList(Constants.LATLNG_LIST, mPoints);
        out.putParcelable(Constants.MAP_BOUNDS, mBounds);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //Retain this fragment instance across configuration changes so we don't lose the threads
        //doing location and run updates
        //setRetainInstance(true);
        setHasOptionsMenu(true);
        mRunManager = RunManager.get(getActivity());
        mBoundsHolder = BoundsHolder.get(getActivity());
        mPointsHolder = PointsHolder.get(getActivity());

        if (NavUtils.getParentActivityName(getActivity()) != null && ((AppCompatActivity)getActivity()).getSupportActionBar() != null) {
            ((AppCompatActivity)getActivity()).getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        if (savedInstanceState != null){
            mRunId = savedInstanceState.getLong(Constants.ARG_RUN_ID);
            mRun = savedInstanceState.getParcelable(Constants.PARAM_RUN);
            mIsTrackingThisRun = savedInstanceState.getBoolean(Constants.TRACKING_THIS_RUN);
            mStarted = savedInstanceState.getBoolean(Constants.TRACKING);
            mStartLocation = savedInstanceState.getParcelable(Constants.STARTING_LOCATION);
            mLastLocation = savedInstanceState.getParcelable(Constants.LAST_LOCATION);
            mPoints = savedInstanceState.getParcelableArrayList(Constants.LATLNG_LIST);
            mBounds = savedInstanceState.getParcelable(Constants.MAP_BOUNDS);
        } else {
            Bundle args = getArguments();
            if (args != null) {
                long runId = args.getLong(Constants.ARG_RUN_ID, -1);
                Log.i(TAG, "onCreate() runId is " + runId);
                //If the run already has an id, it will have database records associated with it that
                //need to be loaded using Loaders.
                if (runId != -1) {
                    mRunId = runId;
                    mRun = mRunManager.getRun(mRunId);
                    Log.i(TAG, "mRunId is " + mRunId);
                }
            }
            RunDatabaseHelper.LocationCursor locationCursor = mRunManager.queryLocationsForRun(mRunId);
            Location location;
            LatLng latLng;
            locationCursor.moveToFirst();
            while (!locationCursor.isAfterLast()){
                location = locationCursor.getLocation();
                latLng = new LatLng(location.getLatitude(), location.getLongitude());
                mPoints.add(latLng);
                mBuilder.include(latLng);
                locationCursor.moveToNext();
            }
            if (mPoints.size() > 0) {
                mBounds = mBuilder.build();
            }
        }
        //Set up Broadcast Receiver to get reports of results from TrackingLocationIntentService
        //First set up the IntentFilter for the Receiver so it will receive the Intents intended for it
        mResultsFilter = new IntentFilter(Constants.SEND_RESULT_ACTION);
        //Now instantiate the Broadcast Receiver
        mResultsReceiver = new ResultsReceiver();
        //Register the IntentFilter and ResultsReceiver with the LocalBroadcastManager
        //LocalBroadcastManager broadcastManager = LocalBroadcastManager.getInstance((AppCompatActivity)getActivity());
        //broadcastManager.registerReceiver(mResultsReceiver, mResultsFilter);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState){
        super.onActivityCreated(savedInstanceState);

        //Make sure the loaders are initialized for the newly-launched Run.
        //Loaders need to be initialized here because their life cycle is
        //actually tied to the Activity, not the Fragment. If initialized
        //earlier, we'll get runtime errors complaining that we're trying
        //to start loaders that have already been started and to stop loaders
        //that have already been stopped.
        Log.i(TAG, "In onActivityCreated(), mRunId is " + mRunId);
        mLoaderManager = getLoaderManager();
        Bundle args = new Bundle();
        args.putLong(Constants.ARG_RUN_ID, mRunId);
        //Start the loaders for the run and the last location
        mLoaderManager.initLoader(Constants.LOAD_RUN, args, mRunCursorLoaderCallbacks);
        Log.i(TAG, "initLoader for LOAD_RUN called in onActivityCreated for Run " + mRunId);
        mLoaderManager.initLoader(Constants.LOAD_LOCATION, args, mLastLocationCursorCallbacks);
        //mLoaderManager.initLoader(LOAD_LOCATION, args, mLastLocationCursorLoaderCallbacks);
        Log.i(TAG, "initLoader for LOAD_LOCATION called in onActivityCreated for Run " + mRunId);
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

        mStartButton = (Button) v.findViewById(R.id.run_startButton);
        mStartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "Run Id in StartButton is " + mRun.getId());
                //Start housekeeeping for tracking a run
                mRunManager.startTrackingRun(mRunId);
                //start BackgroundLocationService so we'll get location updates until we explicitly stop
                //them, even if this fragment goes away.
                getActivity().startService(new Intent(getActivity(), BackgroundLocationService.class));
            }
        });
        mStopButton = (Button) v.findViewById(R.id.run_stopButton);
        mStopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "Stop Button Pressed. Run is " + mRunId);
                //Do housekeeping for stopping tracking a run.
                mRunManager.stopRun(mRunId);
                //Use binding to BackgroundLocationservice to stop location updates because simply calling
                //stopService() won't work if any component is still bound to the service.
                mLocationService.stopLocationUpdates();
                //Call stopService() so the BackgroundLocationService will stop if nothing is still bound to it.
                getActivity().stopService(new Intent(getActivity(), BackgroundLocationService.class));
                updateUI();
            }
        });


        mMapButton = (Button) v.findViewById(R.id.run_mapButton);
        mMapButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Create new instance of RunMapActivity and pass it a reference to this run.
                Intent i = new Intent(getActivity(), RunMapActivity.class);
                Log.i(TAG, "Started RunMapActivity for Run " + mRunId);
                i.putExtra(Constants.EXTRA_RUN_ID, mRun.getId());
                startActivity(i);
            }
        });
        //If this isn't a new run, we should immediately populate the textviews.
        //Start with information concerning the starting point.
        Log.i(TAG, "In onCreateView() for Run " + mRunId + ", mStartLocation null?" + (mRunManager.getStartLocationForRun(mRunId) == null));
        if (mRunManager.getStartLocationForRun(mRunId) != null) {
            mStartLocation = mRunManager.getStartLocationForRun(mRunId);
            Log.i(TAG, "In onCreateView(), mRunId is " + mRunId + " and mStartLocation is " + mStartLocation.toString());
            mStartedTextView.setText(Constants.DATE_FORMAT.format(mRun.getStartDate()));
            //Report latitude and longitude in degrees, minutes and seconds
            mStartingLatitudeTextView.setText(Location.convert(mStartLocation.getLatitude(), Location.FORMAT_SECONDS));
            mStartingLongitudeTextView.setText(Location.convert(mStartLocation.getLongitude(), Location.FORMAT_SECONDS));
            //Report altitude values in feet
            mStartingAltitudeTextView.setText(getString(R.string.altitude_format, String.format("%.2f", mStartLocation.getAltitude() * Constants.METERS_TO_FEET)));
            //Load what this Run has in the database for its Starting Address
            mStartingAddressTextView.setText(mRun.getStartAddress());
            //If what we're showing for the Starting Address is bad, try to get a good address from the
            ///geocoder and record it to the database
            if (mRunManager.addressBad(mStartingAddressTextView.getText().toString())) {
                mRunManager.updateRunStartAddress(mRun, mStartLocation);
            }
            mStartingAddressTextView.setText(mRunManager.getRun(mRunId).getStartAddress());

        }
        //Now display what we concerning the ending point.
        mLastLocation = mRunManager.getLastLocationForRun(mRunId);
        Log.i(TAG, "In onCreateView for Run " + mRunId + ", mLastLocation null? " + (mRunManager.getLastLocationForRun(mRunId) == null));
        //If we have a last location, display the data we have concerning it.
        if (mLastLocation != null) {
            mEndedTextView.setText(Constants.DATE_FORMAT.format(mLastLocation.getTime()));
            mEndingLatitudeTextView.setText(Location.convert(mLastLocation.getLatitude(), Location.FORMAT_SECONDS));
            mEndingLongitudeTextView.setText(Location.convert(mLastLocation.getLongitude(), Location.FORMAT_SECONDS));
            mEndingAltitudeTextView.setText(getString(R.string.altitude_format, String.format("%.2f", mLastLocation.getAltitude() * Constants.METERS_TO_FEET)));
            mEndingAddressTextView.setText(mRun.getEndAddress());
            //If our Ending Address loaded from the database is bad, get a new value from the geocoder and store it
            //to the database,then display it
            if (mRunManager.addressBad(mEndingAddressTextView.getText().toString())){
                mRunManager.updateRunEndAddress(mRun, mLastLocation);
                mEndedTextView.setText(mRunManager.getRun(mRunId).getEndAddress());
            }
            //If the End Address doesn't correspond to the address associated with the last location recorded, update
            //the End Address
            mRunManager.checkEndAddress(mRun, mLastLocation);
            mDurationTextView.setText(Run.formatDuration((int) (mRun.getDuration() / 1000)));
            double miles = mRun.getDistance() * Constants.METERS_TO_MILES;
            mDistanceCoveredTextView.setText(getString(R.string.miles_travelled_format, String.format("%.2f", miles)));
        }
        //If we have at least one location in addition to the mStartLocation, we can make a map, so
        //enable the map button
        //boolean enableMapButton = (mLastLocation == null) ? false : true;
        mMapButtonEnabled = (mLastLocation == null) ? false : true;
        //Log.i(TAG, "enableMapButton for Run " + mRun.getId() + " is " + enableMapButton);
        //mMapButton.setEnabled(enableMapButton);
        mMapButton.setEnabled(mMapButtonEnabled);
        Log.i(TAG, "In onCreateView() for Run " + mRunId + ", enableMapButton is " + mMapButtonEnabled);
        //Enable Start button only if we're not tracking ANY run at this time
        mStartButton.setEnabled(!mStarted);
        Log.i(TAG, "In onCreateView() for Run " + mRunId + ", Start Button enabled is " + !mStarted);
        //Log.i(TAG, "Start Button enabled for Run " + mRun.getId() + " is " + !started);
        //Enable Stop button only if we're tracking and tracking THIS run
        mStopButton.setEnabled(mStarted && mIsTrackingThisRun);
        Log.i(TAG, "In onCreateView() for Run " + mRunId +", Stop Button enabled is " + (mStarted && mIsTrackingThisRun));
        //Log.i(TAG, "Stop Button enabled for Run " + mRun.getId() + " is " + (started && trackingThisRun));
        updateUI();
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
                Intent runPagerActivityIntent = NavUtils.getParentActivityIntent((AppCompatActivity)getActivity());
                if (NavUtils.shouldUpRecreateTask((AppCompatActivity)getActivity(), runPagerActivityIntent)) {
                    //If no instance of RunPagerActivity can be found in the backstack,
                    //create a new one.
                    Log.d(TAG, "Recreating RunPagerActivity");
                    runPagerActivityIntent.putExtra(Constants.EXTRA_RUN_ID, mRun.getId());
                    startActivity(runPagerActivityIntent);
                    return true;
                }

                if (NavUtils.getParentActivityName((AppCompatActivity)getActivity()) != null) {
                    //If we get here, this condition *should* always be true, but better
                    //to check anyway.
                    //noinspection ConstantConditions
                    Log.i(TAG, "onOptionsItemSelected: parent activity name is " +
                            NavUtils.getParentActivityName((AppCompatActivity)getActivity()));
                    NavUtils.navigateUpFromSameTask((AppCompatActivity)getActivity());
                    return true;
                }
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void updateUI() {
        //Are we tracking ANY run? We call the RunManager method because the PendingIntent that
        //the BackgrounLocationService uses to request and remove location updates is supplied by
        //RunManager's getLocationPendingIntent(boolean) method.
        //boolean started = mRunManager.isTrackingRun();
        mStarted = mRunManager.isTrackingRun();
        //Log.i(TAG, "isTrackingRun() is " + started);
        //Are we tracking THIS run?
        ///boolean trackingThisRun = mRunManager.isTrackingRun(mRun);
        mIsTrackingThisRun = mRunManager.isTrackingRun(mRun);
        //Log.i(TAG, "isTrackingRun for Run " + mRun.getId() + " is " + trackingThisRun);
        //Log.i(TAG, "isTrackingRun(mRun) for Run " + mRun.getId() + " is " + trackingThisRun);

        //Check to see if our Starting Address is good and fix it if it isn't.
        /*if (mStartLocation != null && mRunManager.addressBad(mRun.getStartAddress())){
                Log.i(TAG, "Picked up a stale bad Starting Address");
                mRunManager.updateRunStartAddress(mRun, mStartLocation);
                mStartingAddressTextView.setText(mRunManager.getRun(mRunId).getStartAddress());
                Log.i(TAG, "Refreshed Starting Address is: " + mRun.getStartAddress());
        }*/
        //It's possible for the Fragment to try to update its state when not attached to the under-
        //lying Activity - result then is crash!
        if (isAdded()) {

            if (mRun == null){
                return;
            }
            if (mOptionsMenu != null) {
                //Disable the New Run menu item if we're tracking another Run - program will crash if
                //a new Run is started when another is being tracked.
                //if (started){
                if (mStarted){
                    mOptionsMenu.findItem(R.id.run_pager_menu_item_new_run).setEnabled(false);
                } else {
                    mOptionsMenu.findItem(R.id.run_pager_menu_item_new_run).setEnabled(true);
                }
            }
            //If we haven't yet gotten a starting location for this run, try to get one. Once we've
            //gotten a starting location, no need to ask for it again.
            if (mRun != null &&  mStartLocation == null) {
                Log.i(TAG, "In updateUI(), at beginning of section re mStartLocation, mRunId is " +  mRunId + " and mStartLocation is null");
                 mStartLocation = mRunManager.getStartLocationForRun(mRunId);
                if (mStartLocation != null) {
                    //Now that we've gotten a Starting Location, record and display information about it.
                    //Log.i(TAG, "In updateUI(), got a Starting Location for Run " + mRunId + ": " + mStartLocation.toString());
                    //Change the start date to the timestamp of the first Location object received.
                    mRun.setStartDate(new Date(mStartLocation.getTime()));
                    //Log.i(TAG, "Setting Start Date for Run " + mRun.getId() + " to " + new Date(mStartLocation.getTime()).toString());
                    //Now write the new start date to the database
                    mRunManager.updateRunStartDate(mRun, mStartLocation);
                    //Log.i(TAG, "Wrote new Start Date for Run " + mRun.getId() + " to database.");
                    //Finally, display the new start date
                    mStartedTextView.setText(Constants.DATE_FORMAT.format(mRun.getStartDate()));
                    //Log.i(TAG, "New start date for Run " + mRunId + " :" + mStartedTextView.getText());
                    mStartingLatitudeTextView.setText(Location.convert(mStartLocation.getLatitude(), Location.FORMAT_SECONDS));
                    //Log.i(TAG, "New Starting Latitude for Run " + mRun.getId() + " is " + mStartingLatitudeTextView.getText());
                    mStartingLongitudeTextView.setText(Location.convert(mStartLocation.getLongitude(), Location.FORMAT_SECONDS));
                    //Log.i(TAG, "New Starting Latitude for Run " + mRun.getId() + " is " + mStartingLongitudeTextView.getText());
                    mStartingAltitudeTextView.setText(getString(R.string.altitude_format, String.format("%.2f", ( mStartLocation.getAltitude() * Constants.METERS_TO_FEET))));
                    //Log.i(TAG, "New Starting Altitude for Run " + mRun.getId() + " is " + mStartingAltitudeTextView.getText());
                    mRunManager.updateRunStartAddress(mRun, mStartLocation);
                    mStartingAddressTextView.setText(mRun.getStartAddress());
                    //Log.i(TAG, "New Starting Address for Run " + mRun.getId() + " is "  + mStartingAddressTextView.getText());
                    //Log.i(TAG, "In updateUI() at end of section concerning mStartLocation. mRunId is " +  mRunId);
                    //If we get here, mBounds should be null, but better to check. Put the starting location into the LatLngBounds
                    //Builder and later, when at least one additional location has also been included, build mBounds.
                    if (mBounds == null){
                        mBuilder.include(new LatLng(mStartLocation.getLatitude(), mStartLocation.getLongitude()));
                    }
                    if (mPoints.size() == 0){
                        mPoints.add(new LatLng(mStartLocation.getLatitude(), mStartLocation.getLongitude()));
                    }

                } else {
                    Log.i(TAG, "getStartLocationForRun returned null for Run " + mRun.getId());
                }
                //mLastLocation gets set by the LastLocationLoader
                //When the Run is returned from the loader, it will have updated Duration and Distance
                //values
            }

            //If we have a starting location but don't yet have a starting address, get one and update
            //the Run Table with a new starting date equal to the time of the first location and with
            //the new starting address. Once we have a starting address,no need to reload any data
            //concerning the Start Location - it won't change as the Run goes on..
            if (mRun != null &&  mStartLocation != null) {
                //Log.i(TAG, "In updateUI() at beginning of section dealing with StartAddress, mRunId is " +  mRunId);
                //if (mRunManager.addressBad(mRun.getStartAddress())) {
                if (mRunManager.addressBad(mStartingAddressTextView.getText().toString())){
                    Log.i(TAG, "mRun.getStartAddress() for Run " + mRun.getId() + " is bad; calling updateRunStartAddress().");
                    String newStartAddress = mRun.getStartAddress();
                    if (!mRunManager.addressBad(newStartAddress)){
                        mStartingAddressTextView.setText(newStartAddress);
                    } else {
                        //Change the StartDate field in the Run Table to equal the timestamp from the first
                        //location recorded for this run; also get the starting address from the geocoder and
                        //record that in the Run Table.
                        mRunManager.updateRunStartAddress(mRun, mStartLocation);
                    }
                }
                Log.i(TAG, "In updateUI(), at end of section dealing with StartAddress, mRunId is " +  mRunId +
                    " and mStartAddress is " +  mRun.getStartAddress());
            }
            //If we have a run and a last location for it, we will have duration and distance values for
            //it in the Run Table, so retrieve and display them. This has to be done every time a new
            //location is recorded and, accordingly, the UI updates.
            if (mRun != null &&  mLastLocation != null && mLastLocation != mStartLocation && mIsTrackingThisRun) {
                Log.i(TAG, "In updateUI() section dealing with mLastLocation, mRunId is " + mRunId + " and mLastLocation is " + mLastLocation.toString());
                mDurationTextView.setText(Run.formatDuration((int) (mRun.getDuration() / 1000)));
                //Log.i(TAG, "New Duration for Run " + mRun.getId() + " is " + mDurationTextView.getText());
                //Convert distance travelled from meters to miles and display to two decimal places
                double miles =  mRun.getDistance() * Constants.METERS_TO_MILES;
                mDistanceCoveredTextView.setText(getString(R.string.miles_travelled_format, String.format("%.2f", miles)));
                //Log.i(TAG, "New DistanceCovered for Run " + mRun.getId() + " is " + mDistanceCoveredTextView.getText());
                mEndingLatitudeTextView.setText(Location.convert( mLastLocation.getLatitude(), Location.FORMAT_SECONDS));
                //Log.i(TAG, "New Ending Latitude for Run " + mRun.getId() + " is " + mEndingLatitudeTextView.getText());
                mEndingLongitudeTextView.setText(Location.convert( mLastLocation.getLongitude(), Location.FORMAT_SECONDS));
                //Log.i(TAG, "New Ending Longitude for Run " + mRun.getId() + " is " + mEndingLongitudeTextView.getText());
                mEndingAltitudeTextView.setText(getString(R.string.altitude_format, String.format("%.2f", ( mLastLocation.getAltitude() * Constants.METERS_TO_FEET))));
                //Log.i(TAG, "New Ending Altitude for Run " + mRun.getId() + "  is " + mEndingAltitudeTextView.getText());
                mEndedTextView.setText(Constants.DATE_FORMAT.format( mLastLocation.getTime()));
                //Log.i(TAG, "New Ending Date for Run " + mRun.getId() + " is " + mEndedTextView.getText());
                mEndingAddressTextView.setText(mRun.getEndAddress());
                //Log.i(TAG, "New Ending Address for Run " + mRun.getId() + " is " + mEndingAddressTextView.getText());
                //If mBounds hasn't been initialized yet, add this location to the Builder and create
                //mBounds. If mBounds has been created, simply add this point to it.
                if (mBounds == null) {
                    mBuilder.include(new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude()));
                    mBounds = mBuilder.build();
                } else {
                    mBounds.including(new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude()));
                }
                mPoints.add(new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude()));
            }

        }
        mBoundsHolder.save(mRunId, mBounds);
        mPointsHolder.save(mRunId, mPoints);
        //If we have at least one location in addition to the mStartLocation, we can make a map, so
        //enable the map button
        //boolean enableMapButton = (mLastLocation == null) ? false : true;
        mMapButtonEnabled = (mLastLocation == null) ? false : true;
        //Log.i(TAG, "enableMapButton for Run " + mRun.getId() + " is " + enableMapButton);
        //mMapButton.setEnabled(enableMapButton);
        mMapButton.setEnabled(mMapButtonEnabled);
        Log.i(TAG, "In updateUI() for Run " + mRunId + ", enableMapButton is " + mMapButtonEnabled);
        //Enable Start button only if we're not tracking ANY run at this time
        mStartButton.setEnabled(!mStarted);
        Log.i(TAG, "In updateUI() for Run " + mRunId + ", Start Button enabled is " + !mStarted);
        //Log.i(TAG, "Start Button enabled for Run " + mRun.getId() + " is " + !started);
        //Enable Stop button only if we're tracking and tracking THIS run
        mStopButton.setEnabled(mStarted && mIsTrackingThisRun);
        Log.i(TAG, "In updateUI() for Run " + mRunId +", Stop Button enabled is " + (mStarted && mIsTrackingThisRun));
        //Log.i(TAG, "Stop Button enabled for Run " + mRun.getId() + " is " + (started && trackingThisRun));
    }

    @Override
    public void onStart() {
        super.onStart();
        //Bind to the BackgroundLocationService here to enable use of its functions throughout this fragment.
        Intent intent = new Intent(getActivity(), BackgroundLocationService.class);
        getActivity().bindService(intent, mLocationServiceConnection, Context.BIND_AUTO_CREATE);
    }

    private void restartLoaders() {
        //Need to check if mRun has been initialized - sometimes on a new run it will not have been
        //before we get here.
        if (mRun != null) {
            Bundle args = new Bundle();
            args.putLong(Constants.ARG_RUN_ID, mRun.getId());
            if (mLoaderManager == null){
                Log.i(TAG, "mLoaderManager is null for Run " + mRunId);
                return;
            }
            if(mRunCursorLoaderCallbacks == null){
                Log.i(TAG, "mRunLoaderCallbacks is null for Run " + mRunId);
                return;
            }
            if(mLastLocationCursorCallbacks == null){
                Log.i(TAG, "mLastLocationLoaderCallbacks is null for Run " + mRunId);
                return;
            }
            mLoaderManager.restartLoader(Constants.LOAD_RUN, args, mRunCursorLoaderCallbacks);
            mLoaderManager.restartLoader(Constants.LOAD_LOCATION, args, mLastLocationCursorCallbacks);
            //updateUI();
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
    public void onStop(){
        //Unbind from BackgroundLocationService in matching lifecycle callback to onStart()
        getActivity().unbindService(mLocationServiceConnection);
        mIsBound = false;
        super.onStop();
    }

    @Override
    public void onResume() {
        super.onResume();
        //Log.i(TAG, "onResume() called");
        //We need to register our broadcast receiver here; it gets unregistered when the Fragment pauses.
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(
                mResultsReceiver,
                mResultsFilter);
        Log.i(TAG, "In onResume() for Run " + mRunId + ", MapButton enable is " + (mMapButtonEnabled));
        Log.i(TAG, "In onResume() for Run " + mRunId + ", Start Button Enabled is " + !mStarted);
        Log.i(TAG, "In onResume() for Run " + mRunId + ", Stop Button Enabled is " + (mStarted && mIsTrackingThisRun));
        //The entire View needs to be reestablished upon Resume.
        restartLoaders();
        updateUI();
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
            //Log.i(TAG, "Reached onLoadFinished() in RunCursorLoader for Run " + mRunId);
            if (cursor != null) {
                //Log.i(TAG, "RunCursor returned by loader was not null");
                //Need to cast the superclass Cursor returned to us to RunCursor it really is so we
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
                    //Log.i(TAG, "In RunLoader Duration for run " + run.getId() + " is " + run.getDuration());
                    //Log.i(TAG, "In RunLoader Distance for run " + run.getId() + " is " + run.getDistance());
                    //Log.i(TAG, "In RunLoader Start Address for run " + run.getId() + " is " + run.getStartAddress());
                    //Log.i(TAG, "In RunLoader End Address for run " + run.getId() + " is " + run.getEndAddress());
                    mRun = run;
                    mRunId = mRun.getId();
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

    /*private class RunLoaderCallbacks implements LoaderManager.LoaderCallbacks<Run> {

        @Override
        public Loader<Run> onCreateLoader(int d, Bundle args){
            //Return a Loader pointing to the run with the specified runId
            return new RunLoader(getActivity(), args.getLong(Constants.ARG_RUN_ID));

        }

        @Override
        public void onLoadFinished(Loader<Run> loader, Run run){
            //Retrieve the data about this run from the loader and display the results
            Log.i(TAG, "Reached onLoadFinished() for Run loader");
            //Don't change mRun here! Now that RunFragments are hosted by RunPagerActivity,
            //doing so will mix results of two Runs into a single Fragment's display!
            Bundle args = RunFragment.this.getArguments();
            if (run.getId() == args.getLong(Constants.ARG_RUN_ID, -1)) {
                mRun = run;
                Log.i(TAG, "Reached mRun = run in RunLoader onLoadFinished for Run " + mRun.getId());
            }
            //mRun = run;
            updateUI();
        }

        @Override
        public void onLoaderReset(Loader<Run> loader){
            //Do nothing
        }
    }*/

    /*private class LastLocationLoaderCallbacks implements LoaderManager.LoaderCallbacks<Location> {

        @Override
        public Loader<Location> onCreateLoader(int d, Bundle args){
            //Return a loader pointing to the last location associated with the specified run
            return new LastLocationLoader(getActivity(), args.getLong(Constants.ARG_RUN_ID));
        }

        @Override
        public void onLoadFinished(Loader<Location> loader, Location location){
            //Retrieve the last location from this run from the loader
             mLastLocation = location;
            if (mLastLocation != null){
                Log.i(TAG, "Updated mLastLocation: " +  mLastLocation.toString() +
                "Run Id is " + mRunId);
                //As each new location is recorded and supplied to the UI, the UI needs to be
                //updated.
                updateUI();
            }
            else {
                Log.i(TAG, "mLastLocation is null!");
            }
        }

        @Override
        public void onLoaderReset(Loader<Location> loader){
            //Do nothing
        }
    }*/

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
            //Log.i(TAG, "Entered onLoadFinished in LastLocationCursorLoader; cursor null? " + (cursor == null));
            if (cursor != null) {
                //Cast the superclass Cursor returned to us to the LocationCursor ir really is so we
                ///can extract the Location from it
                RunDatabaseHelper.LocationCursor newCursor = (RunDatabaseHelper.LocationCursor) cursor;
                if (newCursor.moveToFirst()) {
                    if (!newCursor.isAfterLast()) {
                       mLastLocation = newCursor.getLocation();
                        if (mLastLocation != null) {
                            //mLastLocation = newCursor.getLocation();
                            //Log.i(TAG, "LastLocationCursorLoader returned a good Location: " + mLastLocation.toString());
                            //Log.i(TAG, "In LocationLoader, Duration for Run " + mRun.getId() + " is " + mRun.getDuration());
                            //Log.i(TAG, "In LocationLoader, Distance for Run " + mRun.getId() + " is " + mRun.getDistance());
                            updateUI();
                        } else {
                            Log.i(TAG, "LastLocationCursorLoader returned a null Location");
                        }
                    } else {
                        Log.i(TAG, "In LastLocationCursorLoader, cursor went past last position");
                    }
                } else {
                    Log.i(TAG, "In LastLLocationCursorLoader, couldn't move to first position of cursor.");
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

    //Broadcast receiver for receiving results of actions taken by TrackingLocationIntentService
    private class ResultsReceiver extends BroadcastReceiver {
        //Called when the ResultsReceiver gets an Intent it's registered for -
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            //This should always be SEND_ACTION_RESULT, but check for it anyway
            Log.i(TAG, "onReceive() getAction() is: " + action);
            if (action.equals(Constants.SEND_RESULT_ACTION)) {
                //Dispatch Intents for processing based upon the value passed in the
                //ACTION_ATTEMPTED Extras key. Data specific to each different ACTION_ATTEMPTED
                //value is carried with the EXTENDED_RESULTS_DATA key
                String actionAttempted =
                        intent.getStringExtra(Constants.ACTION_ATTEMPTED);
                Log.i(TAG, "onReceive() actionAttempted: " + actionAttempted);

                switch (actionAttempted) {
                    case Constants.ACTION_UPDATE_START_DATE: {
                        int result =
                                intent.getIntExtra(Constants.EXTENDED_RESULTS_DATA, -1);
                        int toastTextRes = 0;
                        //The getWritableDatabase.update() method returns the number of rows affected; that
                        //value is returned to the IntentService and passed on to here. If no rows were
                        //affected or more than one row was affected, something went wrong!
                        if (result == 1) {
                            Log.i(TAG, "Starting Date successfully updated");
                            /*if (isAdded()) {
                                 mStartedTextView.setText(
                                        Constants.DATE_FORMAT.format( mRun.getStartDate()));
                                restartLoaders();
                                updateUI();
                            } else {
                                Log.i(TAG, "Skipped restartloaders() after Start Update - not Visible!");
                            }*/
                        } else if (result == 0) {
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
                        int toastTextRes = 0;
                        //The getWritableDatabase.update() method returns the number of rows affected; that
                        //value is returned to the IntentService and passed on to here. If no rows were
                        //affected or more than one row was affected, something went wrong!
                        if (result == 1){
                            Log.i(TAG, "Starting Address successfully updated");
                            /*if (isAdded()) {
                                 mStartingAddressTextView.setText(
                                        intent.getStringExtra(Constants.UPDATED_ADDRESS_RESULT));
                                restartLoaders();
                                updateUI();
                            } else {
                                Log.i(TAG, "Skipped restartLoaders() after updating Start Address - not Visible!");
                            }*/
                        } else if (result == 0) {
                            toastTextRes = R.string.update_run_start_address_failed;
                        } else if (result > 1) {
                            toastTextRes = R.string.multiple_start_addresses_error;
                        } else {
                            toastTextRes = R.string.unknown_start_address_error;
                        }
                        if (result != 1) {
                            if (isAdded()) {
                                Toast.makeText((AppCompatActivity) (getActivity()), toastTextRes, Toast.LENGTH_LONG).show();
                            }
                        }
                        break;
                    }
                    case Constants.ACTION_UPDATE_END_ADDRESS: {
                        int result =
                                intent.getIntExtra(Constants.EXTENDED_RESULTS_DATA, -1);
                        int toastTextRes = 0;
                        //The getWritableDatabase.update() method returns the number of rows affected; that
                        //value is returned to the IntentService and passed on to here. If no rows were
                        //affected or more than one row was affected, something went wrong!
                        if (result == 1) {

                            Log.i(TAG, "Ending Address successfully updated");
                           /* if (isAdded()) {
                                 mEndingAddressTextView.setText(
                                        intent.getStringExtra(Constants.UPDATED_ADDRESS_RESULT));
                                restartLoaders();
                                updateUI();
                            } else {
                                Log.i(TAG, "Skipped restartLoaders() after updating End Address - not Visible!");
                            }*/
                        } else if (result == 0) {
                            toastTextRes = R.string.update_end_address_failed;
                        } else if (result > 1) {
                            toastTextRes = R.string.multiple_runs_end_addresses_updated;
                        } else {
                            toastTextRes = R.string.unknown_end_address_update_error;
                        }
                        //If an error occurred, put up a Toast advising the user of how things went wrong.
                        if (result != 1) {
                            if (isAdded()) {
                                Toast.makeText((AppCompatActivity) (getActivity()), toastTextRes, Toast.LENGTH_LONG).show();
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

                            getActivity().stopService(new Intent(getActivity(), BackgroundLocationService.class));
                            toastTextRes = R.string.current_location_too_distant;
                            Toast.makeText(mRunManager.mAppContext, toastTextRes, Toast.LENGTH_LONG).show();
                            return;
                        }

                        //SQLDatabase.insert() returns row number of inserted values upon success, -1
                        //on error. Result is returned to IntentService and passed along to here, where
                        //we restart the loaders because both the Run and the LastLocation will have new
                        //data.
                        if (result[Constants.LOCATION_INSERTION_RESULT] != -1) {
                            Log.i(TAG, "Successfully inserted Location at row " + result[Constants.LOCATION_INSERTION_RESULT]);
                        } else {
                            //Upon error, throw up a Toast advising the user.
                            toastTextRes = R.string.location_insert_failed;
                            Toast.makeText((AppCompatActivity) (getActivity()), toastTextRes, Toast.LENGTH_LONG).show();
                        }
                        if (result[Constants.RUN_UPDATE_RESULT] != -1) {
                            Log.i(TAG, "Successfully updated Run " + result[Constants.RUN_UPDATE_RESULT]);
                        } else {
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
}
