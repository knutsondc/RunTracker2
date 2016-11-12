package com.dknutsonlaw.android.runtracker2;

import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Typeface;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Date;

/**
 * Created by dck on 11/10/16. Combines display of RunFragment and RunMapFragment into a single
 * combined Fragment, with the RunFragment material in the top (portrait mode) or left (landscape
 * mode) of the screen and the MapView from RunMapFragment in the bottom (portrait mode) or right
 * (landscape mode) of the screen.
 */

public class CombinedRunFragment extends Fragment {
    private static final String TAG = "CombinedRunFragment";

    private RunManager mRunManager;
    private Context mAppContext;
    private Run mRun;
    private long mRunId;
    private Location mStartLocation, mLastLocation = null;
    private Button mStartButton, mStopButton;
    private TextView mStartedTextView, mStartingLatitudeTextView,
            mStartingLongitudeTextView, mStartingAltitudeTextView, mStartingAddressTextView,
            mEndedTextView, mEndingLatitudeTextView, mEndingLongitudeTextView,
            mEndingAltitudeTextView, mEndingAddressTextView, mDurationTextView,
            mDistanceCoveredTextView;
    private GoogleMap mGoogleMap;
    private MapView mMapView;
    private Menu mOptionsMenu;
    private LoaderManager mLoaderManager;
    private RunDatabaseHelper.LocationCursor mLocationCursor;
    private RunDatabaseHelper.RunCursor mRunCursor;
    private Polyline mPolyline;
    private Marker mEndMarker;
    private ArrayList<LatLng> mPoints;
    //Bounds to define area a map for this run
    private LatLngBounds mBounds = null;
    private final LatLngBounds.Builder mBuilder = new LatLngBounds.Builder();
    private final LocalBroadcastManager mBroadcastManager = LocalBroadcastManager.getInstance(getActivity());
    //We load two data objects in this Fragment, the Run and its last location, so we set up a
    //loader for a cursor of each of them so that the loading takes place on a different thread.
    private final RunCursorLoaderCallbacks mRunCursorLoaderCallbacks = new RunCursorLoaderCallbacks();
    private final LocationListCursorLoaderCallbacks mLocationListCursorCallbacks = new LocationListCursorLoaderCallbacks();
    //Data structures needed to select and receive local broadcast messages sent by the Intent
    //Service
    private IntentFilter mResultsFilter;
    private ResultsReceiver mResultsReceiver;
    //Set up Service Connection for BackgroundLocationService
    private Messenger mLocationService = null;
    private final Messenger mMessenger = new Messenger(new IncomingHandler(this));
    //ArrayList to hold the LatLngs needed to build a Polyline iin a RunMapFragment
    private boolean mStarted = false;
    private boolean mIsTrackingThisRun =false;
    private boolean mEndAddressUpdating = false;
    private boolean mPrepared = false;
    private boolean mIsBound = false;
    //Set default map tracking mode
    private int mViewMode = Constants.SHOW_ENTIRE_ROUTE;

    private final ServiceConnection mLocationServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {

            mLocationService = new Messenger(service);
            mIsBound = true;
            try {
                Message msg = Message.obtain(null, Constants.MESSAGE_REGISTER_CLIENT, Constants.MESSENGER_COMBINEDRUNFRAGMENT, 0);
                msg.replyTo = mMessenger;
                mLocationService.send(msg);
            } catch (RemoteException e){
                Log.i(TAG, "RemoteException thrown when trying to send MESSAGE_REGISTER_CLIENT");
            }
            //updateUI();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mLocationService = null;
            mIsBound = false;
            updateUI();
        }
    };

    public CombinedRunFragment(){
        //Required empty constructor
    }

    public static CombinedRunFragment newInstance(long runId){
        Bundle args = new Bundle();
        args.putLong(Constants.ARG_RUN_ID, runId);
        CombinedRunFragment crf = new CombinedRunFragment();
        crf.setArguments(args);
        return crf;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        //It's easier to keep the connection to the BackgroundLocationService by retaining the fragment
        //instance than any other method I've found
        setRetainInstance(true);
        mRunManager = RunManager.get(getActivity());
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
                try {
                    mPoints = new ArrayList<>(mRunManager.retrievePoints(mRunId));
                    Log.i(TAG, "For Run #" + mRunId + " mPoints retrieved.");
                } catch (NullPointerException e){
                    mPoints = new ArrayList<>();
                    Log.i(TAG, "For Run #" + mRunId + " created new ArrayList<LatLng> for mPoints.");
                }

                mBounds = mRunManager.retrieveBounds(mRunId);

                Log.i(TAG, "In onCreate for Run #" + mRunId +" is mBounds null? " + (mBounds == null));
            }
            //}

            //Load a cursor with all the locations for this Run and hand it to an AsyncTask to initialize
            //the LatLngBounds and List<LatLng> we're going to store for the RunMapFragment to use.
            mLocationCursor = mRunManager.queryLocationsForRun(mRunId);
        }
        //Set up Broadcast Receiver to get reports of results from TrackingLocationIntentService
        //First set up the IntentFilter for the Receiver so it will receive the Intents intended for it
        mResultsFilter = new IntentFilter(Constants.SEND_RESULT_ACTION);
        mResultsFilter.addAction(Constants.ACTION_REFRESH);
        mResultsFilter.addAction(Constants.ACTION_REFRESH_UNITS);
        mResultsFilter.addAction(Constants.ACTION_REFRESH_MAPS);

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
        mLoaderManager.initLoader(Constants.LOAD_LOCATION, args, mLocationListCursorCallbacks);
        //Following is needed when the Activity is destroyed and recreated so that the Fragment
        //in the foreground will have a Run in mRun and thereby present the user with location
        //updates
        if (mRunManager.isTrackingRun() && mRun == null) {
            mRun = new Run();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){
        View v = inflater.inflate(R.layout.combined_fragment, container, false);
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
            public void onClick(View view) {
                Log.i(TAG, " Pressed StartButton. Run Id is " + mRun.getId());
                mRunManager.startTrackingRun(getActivity(), mRunId);
                Message msg = Message.obtain(null, Constants.MESSAGE_START_LOCATION_UPDATES);
                msg.replyTo = new Messenger(new IncomingHandler(CombinedRunFragment.this));
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
        //Enable Start button only if we're not tracking ANY run at this time
        mStartButton.setEnabled(!mStarted);
        //Enable Stop button only if we're tracking and tracking THIS run
        mStopButton.setEnabled(mStarted && mIsTrackingThisRun);

        mMapView = (MapView) v.findViewById(R.id.mapViewContainer);
        mMapView.onCreate(savedInstanceState);
        mMapView.onResume(); //needed to get map to display immediately

        Log.i(TAG, "Is mMapView for Run " + mRunId + " null? " + (mMapView == null));
        try{
            MapsInitializer.initialize(getActivity().getApplicationContext());
        } catch (Exception e){
            e.printStackTrace();
        }


        //Turn off DisplayHomeAsUpEnabled so that more of the ActionBar's subtitle will appear in portrait mode
        if (((AppCompatActivity)getActivity()).getSupportActionBar() != null) {
            //noinspection ConstantConditions
            ((AppCompatActivity)getActivity()).getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        }
        return v;
    }

    private static void doBindService(CombinedRunFragment fragment){
        fragment.getActivity().getApplicationContext().bindService(new Intent(fragment.getActivity(), BackgroundLocationService.class),
                fragment.mLocationServiceConnection, Context.BIND_AUTO_CREATE);
        fragment.mIsBound = true;
    }

    private static void doUnbindService(CombinedRunFragment fragment){
        if (fragment.mIsBound){
            fragment.getActivity().getApplicationContext().unbindService(fragment.mLocationServiceConnection);
            fragment.mIsBound = false;
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater){
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.run_map_list_options, menu);
        mOptionsMenu = menu;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item){
        Log.i(TAG, "Entered onOptionsItemSelected for Run #" + mRunId + ".");
        //Select desired map updating mode, then call setTrackingMode() to act on it. We use a
        //separate function for setTrackingMode() so that it can be invoked when the fragment
        //restarts with the last previous tracking mode still in effect, rather than going with the
        //default of SHOW_ENTIRE_ROUTE
        switch (item.getItemId()){
            case R.id.show_entire_route_menu_item:
                mViewMode = Constants.SHOW_ENTIRE_ROUTE;
                break;
            case R.id.track_end_point_menu_item:
                mViewMode = Constants.FOLLOW_END_POINT;
                break;
            case R.id.track_start_point_menu_item:
                mViewMode = Constants.FOLLOW_STARTING_POINT;
                break;
            case R.id.tracking_off_menu_item:
                mViewMode = Constants.NO_UPDATES;
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        mRunManager.mPrefs.edit().putInt(Constants.TRACKING_MODE, mViewMode).apply();
        setTrackingMode();
        Intent trackingModeIntent = new Intent(Constants.ACTION_REFRESH_MAPS)
                .putExtra(Constants.ARG_RUN_ID, mRunId);
        boolean receiver = mBroadcastManager.sendBroadcast(trackingModeIntent);
        if (!receiver){
            Log.i(TAG, "No receiver for trackingModeIntent!");
        }
        return true;
    }

    //Force loading of the run's locations from the database so that the map adornments will
    //be loaded and up to date - live updating of the map takes place only when it's visible,
    //but the database gets updated regardless of the state of the UI, so upon Resume, we get a
    //fresh set of location data. Because the map view is getting reconstructed, we need to set
    //mNeedToPrepare to true so that the reconstruction will start from the beginning.
    @Override
    public void onResume() {
        super.onResume();
        Log.i(TAG, "onResume() called for Run #" + mRunId + ".");
        mMapView.onResume();
        mBroadcastManager.registerReceiver(mResultsReceiver, mResultsFilter);
        restartLoaders();
        mPrepared = false;
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
            mLoaderManager.restartLoader(Constants.LOAD_LOCATION, args, mLocationListCursorCallbacks);
        }
    }

    @Override
    public void onPause(){
        mMapView.onPause();
        mBroadcastManager.unregisterReceiver(mResultsReceiver);
        super.onPause();
    }

    @Override
    public void onDestroy(){
        //doUnbindService(this);
        mMapView.onDestroy();
        super.onDestroy();
    }

    @Override
    public void onLowMemory(){
        mMapView.onLowMemory();
        super.onLowMemory();
    }

    @Override
    public void onStop() {
        //Clear map adornments to ensure that duplicate, stale end markers won't appear on the map
        //when the MapFragment resumes.
        if (mGoogleMap != null)
            mGoogleMap.clear();
        //Close the location cursor before shutting down
        if (mLocationCursor != null) {
            if (!mLocationCursor.isClosed())
                mLocationCursor.close();
        }
        if (mRunCursor != null){
            if (!mRunCursor.isClosed())
                mRunCursor.close();
        }
        doUnbindService(this);
        super.onStop();
    }

    private void updateUI(){
        //It's possible for the Fragment to try to update its state when not attached to the under-
        //lying Activity - result then is crash!
        if (isAdded()) {
            if (mRun == null) {
                return;
            }
            updatetabularDisplay();
            if (!mPrepared) {
                prepareMap();
                if (mGoogleMap != null){
                    updateMap();
                }
            } else {
                updateMap();
            }
        }
    }

    private void updatetabularDisplay(){
        //Are we tracking ANY run? We call the RunManager method because the PendingIntent that
        //the BackgroundLocationService uses to request and remove location updates is supplied by
        //RunManager's getLocationPendingIntent(boolean) method.
        //mStarted = mRunManager.isTrackingRun(mAppContext);
        mStarted = mRunManager.isTrackingRun();
        //Are we tracking THIS run?
        mIsTrackingThisRun = mRunManager.isTrackingRun(mRun);

        //Enable Start button only if we're not tracking ANY run at this time
        mStartButton.setEnabled(!mStarted);
        //Enable Stop button only if we're tracking and tracking THIS run
        mStopButton.setEnabled(mStarted && mIsTrackingThisRun);
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
        //Save mBounds and mPoints to singletons created by RunManager so they will be available to
        //the RunMapFragment for this run
        mRunManager.saveBounds(mRunId, mBounds);
        mRunManager.savePoints(mRunId, mPoints);
    }

    private void updateMap(){
        Log.i(TAG, "mPrepared is true for Run #" + mRunId + ".");
        if (mLocationCursor == null){
            Log.i(TAG, "In updateMap(), mLocationCursor is null");
            return;
        }

        mLocationCursor.moveToLast();
        mLastLocation = mLocationCursor.getLocation();
        Log.i(TAG, "In updateMap() for Run #" + mRunId +", is mLastLocation null? " + (mLastLocation == null));
        if (mLastLocation != null) {
            //Remember to check whether mLastLocation is null which it could be if this
            //is a newly-opened run and no location data have been recorded to the database
            //before the map got opened.
            LatLng latLng = new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude());
            Log.i(TAG, "In onLoadFinished() for Run #" + mRunId + ", latLng is " + latLng.toString());
            //Get the time of the latest update into the correct format
            //Update mPolyline's set of points
            if (latLng != mPoints.get(mPoints.size() - 1)) {
                Log.i(TAG, "In onLoadFinished() for Run #" + mRunId +", adding latLng to mPoints");
                mPoints.add(latLng);
            }
            //Update mPolyline with the updated set of points.
            mPolyline.setPoints(mPoints);
            //Update the position of mEndMarker. To avoid the overhead of looking up an address
            //for every location update we don't update the snippet until the user clicks on it.
            if (mEndMarker != null) {
                mEndMarker.setPosition(latLng);
            }
            //Make sure the bounds for the map are updated to include the new location
            if (mBounds != null) {
                mBounds = mBounds.including(latLng);
            }
            //Update the map's camera position depending upon the desired map updating mode and
            //the last reported location. We shouldn't have to specify the display size again
            //because the map has already been initialized and laid out.
            CameraUpdate movement = updateCamera(mViewMode, latLng);
            //If the user has chosen to turn tracking off, updateCamera() will return null and
            //the Camera will just stay where it was before.
            if (movement != null)
                //Animate the camera for the cool effect...
                mGoogleMap.animateCamera(movement);
        }
    }

    //Method to initialize the map
    private void prepareMap() {
        Log.i(TAG, "Entered prepareMap() for Run #" + mRunId);
        mMapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(GoogleMap googleMap) {
                //Stash a reference to the GoogleMap
                mGoogleMap = googleMap;
                if (mGoogleMap != null) {
                    Log.i(TAG, "In on ActivityCreated for Run #" + mRunId + " got a map.");
                    //Rather than define our own zoom controls, just enable the UiSettings' zoom controls and
                    //listen for changes in CameraPosition to update mZoomLevel
                    mGoogleMap.getUiSettings().setZoomControlsEnabled(true);
                    mGoogleMap.getUiSettings().setZoomGesturesEnabled(true);
                    //Disable map scrolling so we can easily swipe from one map to another.
                    mGoogleMap.getUiSettings().setScrollGesturesEnabled(false);
                    //Set up an overlay on the map for this run's prerecorded locations. We need a custom
                    //InfoWindowAdapter to allow multiline text snippets in markers.
                    mGoogleMap.setInfoWindowAdapter(new GoogleMap.InfoWindowAdapter() {
                        @Override
                        public View getInfoWindow(Marker marker) {
                            return null;
                        }

                        //Define layout for markers' information windows.
                        @Override
                        public View getInfoContents(Marker marker) {
                            //Set up vertical Linear Layout to hold TextViews for a marker's Title and Snippet
                            //fields.
                            LinearLayout layout = new LinearLayout(getActivity());
                            layout.setOrientation(LinearLayout.VERTICAL);
                            //Define TextView with desired text attributes for the marker's Title
                            TextView title = new TextView(getActivity());
                            title.setTextColor(Color.BLACK);
                            title.setGravity(Gravity.CENTER);
                            title.setTypeface(null, Typeface.BOLD);
                            title.setText(marker.getTitle());
                            //Define TextView with desired text attributes for the marker's Snippet
                            TextView snippet = new TextView(getActivity());
                            snippet.setTextColor(Color.BLACK);
                            snippet.setText(marker.getSnippet());
                            //Add the TextViews to the Linear Layout and return the layout for addition to the
                            //Fragment's overall View hierarchy.
                            layout.addView(title);
                            layout.addView(snippet);
                            return layout;
                        }
                    });
                    //Set up a listener for when the user clicks on the End Marker. We update its snippet while
                    //tracking this run only when the user clicks on it to avoid the overhead of updating the
                    //EndAddress on every location update. If we're not tracking the run, just load the end address
                    //from the database. The Start Marker's data never changes, so the listener can ignore clicks
                    //on it and simply allow the default behavior to occur.
                    mGoogleMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
                        @Override
                        public boolean onMarkerClick(Marker marker) {
                            if (marker.equals(mEndMarker)) {
                                String endDate = "";
                                String snippetAddress;
                                if (mRunManager.getLastLocationForRun(mRunId) != null) {
                                    endDate = Constants.DATE_FORMAT.format(
                                            mRunManager.getLastLocationForRun(mRunId).getTime());
                                }
                                if (mRunManager.isTrackingRun(mRunManager.getRun(mRunId))) {
                                    snippetAddress = mRunManager.getAddress(getActivity(), marker.getPosition());
                                } else {
                                    snippetAddress = mRunManager.getRun(mRunId).getEndAddress();
                                }
                                marker.setSnippet(endDate + "\n" + snippetAddress);
                            }
                            //Need to return "false" so the default action for clicking on a marker will also occur
                            //for the Start Marker and for the End Marker after we've updated its snippet.
                            return false;
                        }
                    });
                }
                Log.i(TAG, "mLocationCursor has " + mLocationCursor.getCount() + " entries.");
                //We need to use the LocationCursor for the map markers because we need time data, which the
                //LatLng objects in mPoints lack.
                mLocationCursor.moveToFirst();
                mStartLocation = mLocationCursor.getLocation();
                if (mStartLocation != null) {
                    if (mBounds == null) {
                        Log.i(TAG, "For Run " + mRunId + " mBounds was null in prepareMap()");
                        mBuilder.include(new LatLng(mStartLocation.getLatitude(), mStartLocation.getLongitude()));
                        mBounds = mBuilder.build();
                    } else {
                        Log.i(TAG, "For Run " + mRunId + " mBounds was NOT null in prepareMap()");
                    }
                    if (mPoints.size() == 0) {
                        Log.i(TAG, "For Run " + mRunId + " mPoints.size() was 0");
                        mPoints.add(new LatLng(mStartLocation.getLatitude(), mStartLocation.getLongitude()));
                    } else {
                        Log.i(TAG, "For Run " + mRunId + " mPoints.size() was " + mPoints.size());
                    }
                    String startDate = Constants.DATE_FORMAT.format(mStartLocation.getTime());
                    Log.i(TAG, "mStartDate for Run #" + mRunId + " is " + startDate);
                    Resources r = getActivity().getResources();
                    //Get the address where we started the Run from the database. If the database's StartAddress
                    //is bad, get a new Starting Address from the geocoder. The geocoder needs a LatLng object,
                    //so use the first element of the mPoints List.
                    String snippetAddress = mRunManager.getRun(mRunId).getStartAddress();
                    if (mRunManager.addressBad(getActivity(), snippetAddress)) {
                        snippetAddress = mRunManager.getAddress(getActivity(), mPoints.get(0));
                    }
                    //Now create a marker for the starting point and put it on the map. The starting marker
                    //doesn't need to be updated, so we don't even need to keep the return value from the call
                    //to mGoogleMap.addMarker().
                    MarkerOptions startMarkerOptions = new MarkerOptions()
                            .position(mPoints.get(0))
                            .title(r.getString(R.string.run_start))
                            .snippet(startDate + "\n" + snippetAddress)
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
                            .flat(false);
                    mGoogleMap.addMarker(startMarkerOptions);
                    //Now set up the EndMarker
                    mLocationCursor.moveToLast();
                    mLastLocation = mLocationCursor.getLocation();
                    String endDate = Constants.DATE_FORMAT.format(mLastLocation.getTime());
                    Log.i(TAG, "endDate for Run #" + mRunId + " is " + endDate);
                    //Get the address where the run ended from the database. If that address is bad, get a new
                    //end address from the geocoder. The geocoder needs a LatLng, so we feed it the last element
                    //in mPoints.
                    snippetAddress = mRunManager.getRun(mRunId).getEndAddress();
                    if (mRunManager.addressBad(getActivity(), snippetAddress)) {
                        snippetAddress = mRunManager.getAddress(getActivity(), mPoints.get(mPoints.size() - 1));
                    }
                    //We use the default red icon for the EndMarker. We need to keep a reference to it because
                    //it will be updated as the Run progresses.
                    MarkerOptions endMarkerOptions = new MarkerOptions()
                            //.position(mPoints.get(mPoints.size() - 1))
                            .position(new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude()))
                            .title(r.getString(R.string.run_finish))
                            .snippet(endDate + "\n" + snippetAddress)
                            .flat(false);
                    //Update mPoints, mPolyline, mBounds and the position of mEndMarker with additional location
                    //data if the number of locations in the cursor exceeds the number of locations stored in
                    //mPoints.
                    if (mLocationCursor.getCount() > mPoints.size()) {
                        Log.i(TAG, "Cursor for Run #" + mRunId + " has " + mLocationCursor.getCount() + " and " +
                                "mPoints has " + mPoints.size() + " elements.");
                        //Set the cursor to the first location after the locations already in memory
                        mLocationCursor.moveToPosition(mPoints.size());
                        LatLng latLng;
                        //Iterate over the remaining location points in the cursor, updating mPoints, mPolyline,
                        //and mBounds; fix position of mEndMarker when we get to the last entry in the cursor.
                        while (!mLocationCursor.isAfterLast()) {
                            Log.i(TAG, "Entered reinstateMap loop for Run #" + mRunId);
                            mLastLocation = mLocationCursor.getLocation();
                            latLng = new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude());
                            mPoints.add(latLng);
                            mBounds = mBounds.including(latLng);
                            if (mLocationCursor.isLast()) {
                                endMarkerOptions.position(latLng);
                                endMarkerOptions.snippet(mRunManager.getAddress(getActivity(), latLng));
                            }
                            mLocationCursor.moveToNext();
                        }
                        //mPolyline.setPoints(mPoints);
                    } else {
                        Log.i(TAG, "Cursor is equal in size to existing mPoints for Run #" + mRunId + ".");
                    }
                    PolylineOptions line = new PolylineOptions();
                    //mPoints is the List of LatLngs used to create mPolyline created in this Run's RunFragment
                    //and stored by the RunManager singleton. mPoints represents all the location data collected
                    //for this run.
                    line.addAll(mPoints);
                    //Create mPolyline using the List of LatLngs RunFragment stored for us in the singleton
                    //class rather than reading them in again from the database.
                    mPolyline = mGoogleMap.addPolyline(line);
                    //Now that we've fixed the position of mEndMarker, add it to the map.
                    mEndMarker = mGoogleMap.addMarker(endMarkerOptions);
                    Log.i(TAG, "In prepareMap(), mViewMode for Run #" + mRunId + " is " + mViewMode);
                    //Now we need to set up the map for the first time by telling the system how large the
                    //map needs to be and then render the map. Then set the camera over the center of a map
                    //Bounds large enough to take in all the points in mPolyline.
                    CameraUpdate movement;
                    //First, get the size of the display
                    Display display = getActivity().getWindowManager().getDefaultDisplay();
                    Point point = new Point();
                    display.getSize(point);
                    //Place the Bounds so that it takes up the whole screen with a 110 pixel pad at the
                    //edges. The map hasn't been rendered yet, so we need to tell it about the display
                    //size.
                    movement = CameraUpdateFactory.newLatLngBounds(mBounds,
                            point.x, point.y, 110);
                    //Move the camera to set the map within the bounds we set.
                    mGoogleMap.moveCamera(movement);
                    //We need to render the map first in SHOW_ENTIRE_ROUTE mode so the GoogleMap will know how
                    //large the map needs to be. Once that is done, we can use the View Mode that's been set in
                    //the setTrackingMode() method.

                    mViewMode = mRunManager.mPrefs.getInt(Constants.TRACKING_MODE, Constants.SHOW_ENTIRE_ROUTE);
                    if (mViewMode == Constants.FOLLOW_END_POINT || mViewMode == Constants.FOLLOW_STARTING_POINT) {
                        movement = CameraUpdateFactory.zoomTo(mRunManager.mPrefs.getFloat(Constants.ZOOM_LEVEL, 17f));
                        mGoogleMap.animateCamera(movement);
                    }

                    setTrackingMode();
                    //The graphic elements of the map display have now all been configured, so clear the
                    //mNeedToPrepare flag so that succeeding calls to onLoadFinished will merely update them as
                    //new location data comes in.
                    mPrepared = true;
                }
            }

        });

    }

    /*Set up tracking depending up mViewMode. SHOW_ENTIRE_ROUTE is the default, but mViewMode may
    * be set to another value when the map is recreated. This method is also used when the user
    * affirmatively selects a different View Mode in the OptionsMenu*/
    private void setTrackingMode() {
        CameraUpdate movement;
        LatLng latLng;
        switch (mViewMode) {
            case Constants.FOLLOW_END_POINT:
                //Fix the camera on the last location in the run at the zoom level that was last used
                //for this or the FOLLOW_START_POINT view mode. Also make sure the End Marker is
                //placed at the same location so that the End Marker stays in sync with mLastLocation..
                latLng = new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude());
                //Make sure the zoom level for this mode is at least 17.0

                if (mGoogleMap.getCameraPosition().zoom >= 17.0f) {
                    Log.i(TAG, "zoom level greater than 17.0, so using current zoom level to position camera.");
                    movement = CameraUpdateFactory.newLatLng(latLng);
                } else {
                    Log.i(TAG, "zoom level less than 17.0, so setting camera zoom level to 17.0.");
                    movement = CameraUpdateFactory.newLatLngZoom(latLng, 17.0f);
                }
                //Move the camera to the end point at the appropriate zoom level
                mGoogleMap.animateCamera(movement);
                //Update the position of the end marker
                mEndMarker.setPosition(latLng);
                break;
            case Constants.FOLLOW_STARTING_POINT:
                //Fix the camera on the first location in the run at the zoom level that was last used
                //for this or the FOLLOW_END_POINT view mode. The start marker never moves, so there's
                //never any need to update it.
                latLng = new LatLng(mStartLocation.getLatitude(), mStartLocation.getLongitude());

                if (mGoogleMap.getCameraPosition().zoom >= 17.0f) {
                    Log.i(TAG, "zoom level greater than 17.0, so using current zoom level to position camera.");
                    movement = CameraUpdateFactory.newLatLng(latLng);
                } else {
                    Log.i(TAG, "zoom level less than 17.0, so setting camera zoom level to 17.0.");
                    movement = CameraUpdateFactory.newLatLngZoom(latLng, 17.0f);
                }
                mGoogleMap.animateCamera(movement);
                break;
            default:
                /*This handles both SHOW_ENTIRE_ROUTE and NO_UPDATES.
                 *On initial layout of the map done in prepareMap(), the border on the top of the map
                 *apparently doesn't take into account the space taken by the ActionBar, so we have
                 *to move the camera again using the same bounds and margin. This time, we don't have
                 *to give display info because the map has already been rendered.
                 *Set the camera at the center of the area defined by mBounds, again leaving a 110
                 *pixel buffer zone at the edges of the screen. The map is already created, so we
                 *don't need to tell the CameraUpdate about the display size. The zoom level is
                 *set according to the size of the area that needs to be displayed. We don't save
                 *the zoom level here, so that any switch to FOLLOW_END_POINT or FOLLOW_START_POINT
                 *mode will use the zoom level that was last used for one of those modes.*/
                movement = CameraUpdateFactory.newLatLngBounds(mBounds, 110);
                mGoogleMap.animateCamera(movement);
        }
    }

    private CameraUpdate updateCamera(int mode, LatLng latLng) {
        //This method will be called after every location update and move the Camera location
        //according to the map updating mode selected by the user and the current location of
        //the run's end point.
        CameraUpdate cameraUpdate;

        switch (mode) {
            case Constants.SHOW_ENTIRE_ROUTE: {
                //To show the entire route, we supply the newly-updated mBounds containing all the
                //LatLngs in the route to the relevant CameraUpdateFactory method. The map has
                //already been created, so we don't need to tell the CameraUpdate about the display,
                //like we had to when the map was initialized.
                cameraUpdate = CameraUpdateFactory.newLatLngBounds(mBounds, 110);
                Log.i(TAG, "SHOW_ENTIRE_ROUTE zoom level: " + mGoogleMap.getCameraPosition().zoom);
                break;
            }
            case Constants.FOLLOW_END_POINT: {
                //To track the end point of the Run, move the camera to the new end point at the
                //zoom level last used for this mode or FOLLOW_START_POINT mode.
                Log.i(TAG, "FOLLOW_END_POINT zoom level: " + mGoogleMap.getCameraPosition().zoom);
                cameraUpdate = CameraUpdateFactory.newLatLng(latLng);
                break;
            }
            case Constants.FOLLOW_STARTING_POINT: {
                //To center on the start point of the Run, move the camera to the starting point at
                //the zoom level last set for this mode or FOLLOW_END_POINT mode.
                cameraUpdate = CameraUpdateFactory.newLatLng(mPoints.get(0));
                Log.i(TAG, "FOLLOW_STARTING_POINT zoom level: " + mGoogleMap.getCameraPosition().zoom);
                break;
            }
            case Constants.NO_UPDATES: {
                //To turn off tracking, return a null so that the Camera will stay where it was
                //following the previous location update.
                cameraUpdate = null;
                break;
            }
            default:
                cameraUpdate = null;
        }
        return cameraUpdate;
    }
    private void updateDistanceTextViews(){
        //This is in a separate method so that we need call only this when changing between imperial
        //and metric measures instead of the whole updateMap() method.
        mStartingAltitudeTextView.setText(mRunManager.formatDistance(mStartLocation.getAltitude()));
        mEndingAltitudeTextView.setText(mRunManager.formatDistance(mLastLocation.getAltitude()));
        mDistanceCoveredTextView.setText(getString(R.string.distance_traveled_format,
                mRunManager.formatDistance(mRunManager.getRun(mRunId).getDistance())));

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

    private static void showErrorDialog(CombinedRunFragment fragment, int errorCode){
        ErrorDialogFragment dialogFragment = new ErrorDialogFragment();
        Bundle args = new Bundle();
        args.putInt(Constants.ARG_ERROR_CODE, errorCode);
        dialogFragment.setArguments(args);
        dialogFragment.show(fragment.getActivity().getSupportFragmentManager(), "errordialog");
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

    private class LocationListCursorLoaderCallbacks implements LoaderManager.LoaderCallbacks<Cursor> {

        @Override
        public Loader<Cursor> onCreateLoader(int d, Bundle args) {
            long runId = args.getLong(Constants.ARG_RUN_ID, -1);
            //Create a loader than returns a cursor holding all the location data for this Run.
            return new MyLocationListCursorLoader(getActivity(),
                    Constants.URI_TABLE_LOCATION, runId);
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
            Log.i(TAG, "Reached onLoadFinished() for location cursor loader for Run " + mRunId);
            Log.i(TAG, "In onLoadFinished() for location cursor loader for Run " + mRunId + ", is cursor null? " + (cursor == null));
            Log.i(TAG, "In onLoadFinished() for location cursor loader for Run " + mRunId + ", is cursor closed? " + cursor.isClosed());
            if (cursor != null) {
                mLocationCursor = (RunDatabaseHelper.LocationCursor)cursor;
            } else {
                Log.i(TAG, "For Run " + mRunId + " cursor returned by MyLocationListCursorLoader is null!");
                return;
            }
            if (mLocationCursor.getCount() < 2){
                Log.i(TAG, "For Run " + mRunId + " cursor returned by MyLocationListCursorLoader had fewer than 2 entries.");
                return;
            } else {
                Log.i(TAG, "For Run " + mRunId + " mLocationCursor has " + mLocationCursor.getCount() + " entries in onLoadFinished()");
            }
            if (mLocationCursor.isClosed()){
                Log.i(TAG, "For Run " + mRunId + " mLocationCursor is closed in onLoadFinished");
            } else {
                Log.i(TAG, "For Run " + mRunId + " mLocationCursor is ready for updateUI()");
                updateUI();
            }
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {

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
            Log.i(TAG, "In onLoadFinished() for RunCursorLoader for Run " + mRunId + ", is  cursor null? " + (cursor  == null));
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
                    Log.i(TAG, "Couldn't move RunCursor to First Position in RunCursorLoader onLoadFinished");
                }
                if (run != null) {
                    mRun = run;
                    mRunId = mRun.getId();
                    Log.i(TAG, "Got a run from RunCursorLoader. RunId = " + mRunId);
                    if (mIsTrackingThisRun){
                        Log.i(TAG, "Tracking Run " + mRunId);
                    }
                    updatetabularDisplay();
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

    private static class IncomingHandler extends Handler {

        private final WeakReference<CombinedRunFragment> mFragment;

        IncomingHandler(CombinedRunFragment fragment){
            mFragment = new WeakReference<>(fragment);
        }
        @Override
        public void handleMessage(Message msg){

            CombinedRunFragment fragment = mFragment.get();
            switch (msg.what){
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
                /*case Constants.MESSAGE_LOCATION_UPDATES_STARTED:
                    Log.i(TAG, "Received MESSAGE_LOCATION_UPDATES_STARTED");
                    fragment.updateOnStartingLocationUpdates();
                    //fragment.updateOnStartingLocationUpdates();
                    break;
                case Constants.MESSAGE_LOCATION_UPDATES_STOPPED:
                    Log.i(TAG, "Received MESSAGE_LOCATION_UPDATES_STOPPED");
                    fragment.updateOnStoppingLocationUpdates();
                    //fragment.updatepingLocationUpdates();
                    break;*/

            }
        }
    }

    private class ResultsReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent){

            String action = intent.getAction();
            Log.i(TAG, "onReceive() getAction() is: " + action + " for Run " + mRunId);
            if (action.equals(Constants.ACTION_REFRESH_UNITS)) {
                Log.i(TAG, "Received refresh units broadcast - calling updateDistanceCoveredTextView().");
                updateDistanceTextViews();
                return;
            }
            if (action.equals(Constants.ACTION_REFRESH_MAPS)){
                long senderId = intent.getLongExtra(Constants.ARG_RUN_ID, -1);
                if (senderId == mRunId){
                    Log.i(TAG, "This is a message this fragment sent - we've already switched tracking mode.");
                    return;
                } else {
                    mViewMode = mRunManager.mPrefs.getInt(Constants.TRACKING_MODE, Constants.SHOW_ENTIRE_ROUTE);
                    setTrackingMode();
                }
                return;
            }
            //All the actions following this if statement are relevant only to Runs being tracked,
            //so if this Run isn't, we just bail out here before looking for matches to mResultsFilter.
            if (!mIsTrackingThisRun){
                Log.i(TAG, "Not tracking Run " + mRunId + " - ignoring broadcast");
                return;
            }
            if (action.equals(Constants.ACTION_REFRESH)) {
                Log.i(TAG, "Received refresh broadcast - calling updateUI().");
                updateUI();
            }

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
                            Toast.makeText(CombinedRunFragment.this.getActivity(), toastTextRes, Toast.LENGTH_LONG).show();
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
}
