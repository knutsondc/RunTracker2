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
import android.graphics.Typeface;
import android.location.Location;
import android.os.AsyncTask;
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
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
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
 * Created by dck on 11/19/16.
 */

public class CombinedFragment extends Fragment {
    private static final String TAG = "CombinedFragment";

    private RunManager mRunManager;
    private Context mAppContext;
    private Run mRun;
    private long mRunId;
    private Location mStartLocation, mLastLocation = null;
    private Button mStartButton, mStopButton;
    private TextView mStartedTextView, mStartingPointTextView,
            mStartingAltitudeTextView, mStartingAddressTextView,
            mEndedTextView, mEndingPointTextView,
            mEndingAltitudeTextView, mEndingAddressTextView, mDurationTextView,
            mDistanceCoveredTextView;
    private Menu mOptionsMenu;
    private LoaderManager mLoaderManager;
    private final LocalBroadcastManager mBroadcastManager = LocalBroadcastManager.getInstance(getActivity());
    //We load two data objects in this Fragment, the Run and a list of its locations, so we set up a
    //loader for a cursor of each of them so that the loading takes place on a different thread.
    private final RunCursorLoaderCallbacks mRunCursorLoaderCallbacks = new RunCursorLoaderCallbacks();
    private final LocationListCursorCallbacks mLocationListCursorCallbacks = new LocationListCursorCallbacks();
    private RunDatabaseHelper.LocationCursor mLocationCursor;
    private GoogleMap mGoogleMap;
    private MapView mMapView;
    private Polyline mPolyline;
    //Data structures needed to select and receive local broadcast messages sent by the Intent
    //Service
    private IntentFilter mResultsFilter;
    private ResultsReceiver mResultsReceiver;
    //Set up Service Connection for BackgroundLocationService
    private Messenger mLocationService = null;
    private final Messenger mMessenger = new Messenger(new IncomingHandler(this));
    //ArrayList to hold the LatLngs needed to build a Polyline to display the Run's route in the map.
    private ArrayList<LatLng> mPoints = new ArrayList<>();
    //Bounds to define area a map for this run
    private LatLngBounds mBounds = null;
    private final LatLngBounds.Builder mBuilder = new LatLngBounds.Builder();
    //Designator for how to place the Camera on the map - default is to show the entire route as it's
    //generated
    private int mViewMode = Constants.SHOW_ENTIRE_ROUTE;
    //Are we tracking ANY Run?
    private boolean mStarted = false;
    //Are we tracking THIS Run?
    private boolean mIsTrackingThisRun = false;
    //Map marker for the end of the Run; this marker's location will change, so we make it a member
    //variable we can access from multiple methods.
    private Marker mEndMarker;
    //Have we initialized our initial conditions for the map?
    private boolean mPrepared = false;
    //Are we bound to the BackgroundLocationService?
    private boolean mIsBound = false;

    private final ServiceConnection mLocationServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {

            mLocationService = new Messenger(service);
            mIsBound = true;
            try {
                //Register this fragment and its Messenger with the BackgroundLocationService
                Message msg = Message.obtain(null, Constants.MESSAGE_REGISTER_CLIENT, Constants.MESSENGER_COMBINEDFRAGMENT, 0);
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

    public CombinedFragment(){

    }

    public static CombinedFragment newInstance(long runId) {
        Bundle args = new Bundle();
        args.putLong(Constants.ARG_RUN_ID, runId);
        CombinedFragment cf = new CombinedFragment();
        cf.setArguments(args);
        return cf;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //setHasOptionsMenu set to "true" here, but invalidateFragmentsMenus() in RunPagerActivity
        //will set it to false if this Run isn't the one currently displayed in the Activity's ViewPager.
        setHasOptionsMenu(true);
        //It's easier to keep the connection to the BackgroundLocationService by retaining the fragment
        //instance than any other method I've found
        setRetainInstance(true);
        mRunManager = RunManager.get(getActivity());
        //Turn off DisplayHomeAsUpEnabled so that more of the ActionBar's subtitle will appear in portrait mode
        if (((AppCompatActivity) getActivity()).getSupportActionBar() != null) {
            //noinspection ConstantConditions
            ((AppCompatActivity) getActivity()).getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        }
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
            RunDatabaseHelper.LocationCursor locationCursor = mRunManager.queryLocationsForRun(mRunId);
            //If we've already stored values for mBounds and mPoints in the RunManager singletons,
            //retrieve them on a separate thread to speed initialization of the map
            LoadPointsAndBounds initTask = new LoadPointsAndBounds(locationCursor);
            initTask.execute();
        }
        //Set up Broadcast Receiver to get reports of results from TrackingLocationIntentService
        //First set up the IntentFilter for the Receiver so it will receive the Intents intended for it
        mResultsFilter = new IntentFilter(Constants.SEND_RESULT_ACTION);
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
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Resources r = getResources();
        Log.i(TAG, "Called onCreateView() for Run " + mRunId);
        View v = inflater.inflate(R.layout.combined_fragment, container, false);
        mStartedTextView = (TextView) v.findViewById(R.id.run_startedTextView);
        mStartingPointTextView = (TextView) v.findViewById(R.id.run_starting_pointTextView);
        mStartingAltitudeTextView = (TextView) v.findViewById(R.id.run__starting_altitudeTextView);
        mStartingAddressTextView = (TextView) v.findViewById(R.id.run_starting_addressTextView);

        mEndedTextView = (TextView) v.findViewById(R.id.run_endedTextView);
        mEndingPointTextView = (TextView) v.findViewById(R.id.ending_pointTextView);
        mEndingAltitudeTextView = (TextView) v.findViewById(R.id.run__ending_altitudeTextView);
        mEndingAddressTextView = (TextView) v.findViewById(R.id.run_ending_address_TextView);
        mDurationTextView = (TextView) v.findViewById(R.id.run_durationTextView);
        mDistanceCoveredTextView = (TextView) v.findViewById(R.id.distance_coveredTextView);

        mStartButton = (Button) v.findViewById(R.id.run_startButton);
        mStartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.i(TAG, " Pressed StartButton. Run Id is " + mRun.getId());
                //Start housekeeping for tracking a Run
                mRunManager.startTrackingRun(getActivity(), mRunId);
                //Send message to location service to start providing updates and tell it where the
                //request came from.
                Message msg = Message.obtain(null, Constants.MESSAGE_START_LOCATION_UPDATES);
                msg.replyTo = new Messenger(new IncomingHandler(CombinedFragment.this));
                try {
                    mLocationService.send(msg);
                } catch (RemoteException e) {
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
                mRunManager.stopRun();
                //Tell the BackgroundLocationService to stop location updates
                try {
                    mLocationService.send(Message.obtain(null, Constants.MESSAGE_STOP_LOCATION_UPDATES));
                } catch (RemoteException e) {
                    Log.i(TAG, "RemoteException thrown when trying to send MESSAGE_STOP_LOCATION_UPDATES");
                }
                //Now that we've stopped tracking, do one last update of the Ending Address to catch
                //any change from the updates done automatically during tracking of Run
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
        if ((mStartLocation = mRunManager.getStartLocationForRun(mRunId)) != null) {
            mStartedTextView.setText(Constants.DATE_FORMAT.format(mRun.getStartDate()));
            //Report latitude and longitude in degrees, minutes and seconds
            mStartingPointTextView.setText(r.getString(R.string.position,
                    (Location.convert(mStartLocation.getLatitude(), Location.FORMAT_SECONDS)),
                    (Location.convert(mStartLocation.getLongitude(), Location.FORMAT_SECONDS))));
            //Report altitude values in feet or meters, depending upon measurement units setting -
            //Imperial or metric
            mStartingAltitudeTextView.setText(mRunManager.formatAltitude(mStartLocation.getAltitude()));
            //Load what this Run has in the database for its Starting Address
            mStartingAddressTextView.setText(mRun.getStartAddress());
            //If what we're showing for the Starting Address is bad, try to get a good address from the
            ///geocoder and record it to the database
            if (mRunManager.addressBad(getActivity(), mStartingAddressTextView.getText().toString())) {
                TrackingLocationIntentService.startActionUpdateStartAddress(getActivity(), mRun, mStartLocation);
            }
        }
        //Now display what we have concerning the ending point.
        mLastLocation = mRunManager.getLastLocationForRun(mRunId);
        //If we have a last location that's different from the starting location, display the data
        //we have concerning it.
        if (mLastLocation != null && mLastLocation != mStartLocation) {
            mEndedTextView.setText(Constants.DATE_FORMAT.format(mLastLocation.getTime()));
            mEndingPointTextView.setText(r.getString(R.string.position,
                    (Location.convert(mLastLocation.getLatitude(), Location.FORMAT_SECONDS)),
                    (Location.convert(mLastLocation.getLongitude(), Location.FORMAT_SECONDS))));
            //Display altitude in feet or meters, depending upon measurement units setting.
            mEndingAltitudeTextView.setText(mRunManager.formatAltitude(mLastLocation.getAltitude()));
            mEndingAddressTextView.setText(mRun.getEndAddress());
            //If our Ending Address loaded from the database is bad, get a new value from the
            //geocoder and store it to the database,then display it
            if (mRunManager.addressBad(getActivity(), mEndingAddressTextView.getText().toString())) {
                TrackingLocationIntentService.startActionUpdateEndAddress(getActivity(), mRun, mLastLocation);
            }
            //Display duration of the Run in hours, minutes and seconds
            mDurationTextView.setText(Run.formatDuration((int) (mRun.getDuration() / 1000)));
            //Display Run's distance in meters and kilometers or feet and miles, depending upon
            //measurement units setting.
            mDistanceCoveredTextView.setText(mRunManager.formatDistance(mRun.getDistance()));
        }
        //Enable Start button only if we're not tracking ANY run at this time
        mStartButton.setEnabled(!mStarted);
        //Enable Stop button only if we're tracking and tracking THIS run
        mStopButton.setEnabled(mStarted && mIsTrackingThisRun);

        //Now initialize the map
        mMapView = (MapView) v.findViewById(R.id.mapViewContainer);
        mMapView.onCreate(savedInstanceState);
        mMapView.onResume(); //needed to get map to display immediately

        try {
            MapsInitializer.initialize(getActivity().getApplicationContext());
        } catch (Exception e) {
            e.printStackTrace();
        }

        mMapView.getMapAsync(googleMap -> {
            //Stash a reference to the GoogleMap
            mGoogleMap = googleMap;
            if (mGoogleMap != null) {
                //Rather than define our own zoom controls, just enable the UiSettings' zoom
                //controls and listen for changes in CameraPosition to update mZoomLevel
                mGoogleMap.getUiSettings().setZoomControlsEnabled(true);
                mGoogleMap.getUiSettings().setZoomGesturesEnabled(true);
                //Disable map scrolling so we can easily swipe from one map to another. This setting
                //can be changed in the Preferences menu.
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
                        //Set up vertical Linear Layout to hold TextViews for a marker's Title and
                        //Snippet fields.
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
                        //Add the TextViews to the Linear Layout and return the layout for addition
                        //to the Fragment's overall View hierarchy.
                        layout.addView(title);
                        layout.addView(snippet);
                        return layout;
                    }
                });
                //Set up a listener for when the user clicks on the End Marker. We update its
                //snippet while tracking this run only when the user clicks on it to avoid the
                //overhead of updating the EndAddress on every location update. If we're not
                //tracking the run,just load the end address from the database. The Start Marker's
                //data never changes, so the listener can ignore clicks on it and simply allow the
                //default behavior to occur.
                mGoogleMap.setOnMarkerClickListener(marker -> {
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
                    //Need to return "false" so the default action for clicking on a marker will
                    //also occur for the Start Marker and for the End Marker after we've updated its
                    //snippet.
                    return false;
                });
                mGoogleMap.setOnCameraMoveListener(new GoogleMap.OnCameraMoveListener() {
                    @Override
                    public void onCameraMove() {
                        mRunManager.mPrefs.edit().putFloat(Constants.ZOOM_LEVEL,
                                mGoogleMap.getCameraPosition().zoom).apply();
                    }
                });
            }
        });
        return v;
    }

    private static void doBindService(CombinedFragment fragment){
        fragment.getActivity().getApplicationContext()
                .bindService(new Intent(fragment.getActivity(), BackgroundLocationService.class),
                fragment.mLocationServiceConnection, Context.BIND_AUTO_CREATE);
        fragment.mIsBound = true;
    }

    private static void doUnbindService(CombinedFragment fragment){
        if (fragment.mIsBound){
            fragment.getActivity().getApplicationContext()
                    .unbindService(fragment.mLocationServiceConnection);
            fragment.mIsBound = false;
        }
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        //Set text of menuItem to select distance measurement units according to its current setting.
        if (mRunManager.mPrefs.getBoolean(Constants.MEASUREMENT_SYSTEM, Constants.IMPERIAL)) {
            Log.i(TAG, "In onPrepareOptionsMenu setting Measurements title to Imperial");
            menu.findItem(R.id.run_map_pager_activity_units).setTitle(R.string.imperial);
        } else {
            Log.i(TAG, "In onPrepareOptionsMenu setting Measurements title to Metric");
            menu.findItem(R.id.run_map_pager_activity_units).setTitle(R.string.metric);
        }
        //Set text of menuItem to turn scrolling on or off according to its current setting.
        if (mGoogleMap != null && mGoogleMap.getUiSettings() != null) {
            if (mGoogleMap.getUiSettings().isScrollGesturesEnabled()) {
                Log.i(TAG, "Scrolling is enabled for Run " + mRunId + " so display Scrolling Off " +
                        "in menu.");
                menu.findItem(R.id.run_map_pager_activity_scroll)
                        .setTitle(R.string.map_scrolling_off);
            } else {
                Log.i(TAG, "Scrolling not enabled for Run " + mRunId + "so display Scrolling On " +
                        "in menu.");
                menu.findItem(R.id.run_map_pager_activity_scroll)
                        .setTitle(R.string.map_scrolling_on);
            }
        }
        //If the Run's being tracked and the ViewMode is SHOW_ENTIRE_ROUTE or FOLLOW_END_POINT,
        //scrolling won't work because the map gets a new CameraUpdate with a LatLngBounds that
        //pulls the display back and undoes the scrolling with every location update
        if (mRunManager.isTrackingRun(mRunManager.getRun(mRunId)) &&
                !mRunManager.mPrefs.getBoolean(Constants.SCROLLABLE, false)){
            Log.i(TAG, "Tracking Run " + mRunId + " in ViewMode inconsistent with scrolling." +
                    "Turning scrolling off.");
            menu.findItem(R.id.run_map_pager_activity_scroll)
                    .setEnabled(false)
                    .setTitle(R.string.map_scrolling_on);
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater){
        super.onCreateOptionsMenu(menu, inflater);
        mOptionsMenu = menu;
    }

    /*
    *Settings that change how Runs' data are displayed are placed in the CombinedFragment's
    *OptionsMenu. This includes the selection of Imperial or Metric measurement units, turning map
    *scrolling on or off, and how to track Runs.
    *
    *The Options Menu allows the user to specify whether to track the whole route (the default),
    *track the ending point of the route, track the starting point of the route, or turn off
    *tracking. Actually changing the tracking mode is done in the separate method setTrackingMode()
    *so that the last previously selected tracking mode can be used when the map is reopened instead
    *of always starting with the default SHOW_ENTIRE_ROUTE.*/
    @Override
    public boolean onOptionsItemSelected(MenuItem item){
        Log.i(TAG, "Entered onOptionsItemSelected for Run #" + mRunId + ".");
        switch (item.getItemId()){
            case R.id.run_map_pager_activity_units:
                Log.i(TAG, "In CombinedFragment for Run " + mRunId + " entered " +
                        "onOptionsItemSelected case R.id.run_map_pager_activity_units:");
                //Swap distance measurement unit between imperial and metric
                mRunManager.mPrefs.edit().putBoolean(Constants.MEASUREMENT_SYSTEM,
                                !mRunManager.mPrefs.getBoolean(Constants.MEASUREMENT_SYSTEM,
                                Constants.IMPERIAL))
                                .apply();
                //Send a broadcast to all open CombinedFragments will update their displays to show
                //the newly-selected distance measurement units.
                Intent refreshIntent = new Intent(Constants.ACTION_REFRESH_UNITS);
                refreshIntent.putExtra(Constants.ARG_RUN_ID, mRunId);
                boolean receiver = LocalBroadcastManager.getInstance(getActivity()).sendBroadcast(refreshIntent);
                if(!receiver){
                    Log.i(TAG, "No receiver for CombinedRunFragment REFRESH broadcast!");
                }
                //Now force the OptionsMenu to be redrawn to switch the display of the measurement
                //units menuItem
                getActivity().invalidateOptionsMenu();
                updateUI();
                return true;
            case R.id.run_map_pager_activity_scroll:
                Log.i(TAG, "In CombinedFragment for Run " + mRunId + " entered " +
                        "onOptionsItemSelected case R.id.run_map_pager_activity_scroll:");
                //Change scroll setting for the currently displayed map only.
                if (mGoogleMap != null &&
                        mRunManager.mPrefs.getLong(Constants.ARG_RUN_ID, -1) == mRunId) {
                    Log.i(TAG, "Entered scroll menu. Is scrolling enabled? "
                            + mGoogleMap.getUiSettings().isScrollGesturesEnabled());
                    //Toggle the ability to scroll the map.
                    mGoogleMap.getUiSettings()
                            .setScrollGesturesEnabled(!mGoogleMap.getUiSettings()
                            .isScrollGesturesEnabled());
                    //Store current state of scrolling.
                    mRunManager.mPrefs.edit().putBoolean(Constants.SCROLL_ON,
                            !mRunManager.mPrefs.getBoolean(Constants.SCROLL_ON, false))
                            .apply();
                }
                assert mGoogleMap != null;
                Log.i(TAG, "After change to scrolling, is scrolling enabled? " +
                        mGoogleMap.getUiSettings().isScrollGesturesEnabled());
                //We want to change the menuItem title in onPrepareOptionsMenu, so we need to
                //invalidate the menu and recreate it.
                getActivity().invalidateOptionsMenu();
                updateUI();
                return true;
            //Select desired map updating mode, then call setTrackingMode() to act on it. We use a
            //separate function for setTrackingMode() so that it can be invoked when the fragment
            //restarts with the last previous tracking mode still in effect, rather than going with
            //the default of SHOW_ENTIRE_ROUTE
            case R.id.show_entire_route_menu_item:
                Log.i(TAG, "For Run " + mRunId + " entered case R.id.show_entire_route_menu_item:");
                mViewMode = Constants.SHOW_ENTIRE_ROUTE;
                mRunManager.mPrefs.edit()
                        .putInt(Constants.TRACKING_MODE, Constants.SHOW_ENTIRE_ROUTE).apply();
                //Scrolling won't work with this tracking mode, so turn it off with the next update
                //of the UI.
                mRunManager.mPrefs.edit().putBoolean(Constants.SCROLLABLE, false).apply();
                break;
            case R.id.track_end_point_menu_item:
                Log.i(TAG, "For Run " + mRunId + " entered case R.id.track_end_point_menu_item:");
                mViewMode = Constants.FOLLOW_END_POINT;
                mRunManager.mPrefs.edit()
                        .putInt(Constants.TRACKING_MODE, Constants.FOLLOW_END_POINT).apply();
                //Scrolling won't work with this tracking mode, so turn it off with the next update
                //of the UI.
                mRunManager.mPrefs.edit().putBoolean(Constants.SCROLLABLE, false).apply();
                break;
            case R.id.track_start_point_menu_item:
                Log.i(TAG, "For Run " + mRunId + " entered case R.id.track_start_point_menu_item:");
                mViewMode = Constants.FOLLOW_STARTING_POINT;
                mRunManager.mPrefs.edit()
                        .putInt(Constants.TRACKING_MODE, Constants.FOLLOW_STARTING_POINT).apply();
                //Scrolling won't work with this tracking mode, so turn it off with the next update
                //of the UI
                mRunManager.mPrefs.edit().putBoolean(Constants.SCROLLABLE, false).apply();
                break;
            case R.id.tracking_off_menu_item:
                Log.i(TAG, "For Run " + mRunId + " entered case R.id.tracking_off_menu_item:");
                mViewMode = Constants.NO_UPDATES;
                mRunManager.mPrefs.edit()
                        .putInt(Constants.TRACKING_MODE, Constants.NO_UPDATES).apply();
                //Scrolling will work with this tracking mode, so make sure we enable the scrolling
                //menuItem upon next update of UI.
                mRunManager.mPrefs.edit().putBoolean(Constants.SCROLLABLE, true).apply();
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        //Store and implement the new tracking mode and tell all the other open CombinedFragments to
        //switch to this mode
        mRunManager.mPrefs.edit().putInt(Constants.TRACKING_MODE, mViewMode).apply();
        setTrackingMode();
        Intent trackingModeIntent = new Intent(Constants.ACTION_REFRESH_MAPS)
                .putExtra(Constants.ARG_RUN_ID, mRunId);
        boolean receiver = mBroadcastManager.sendBroadcast(trackingModeIntent);
        if (!receiver){
            Log.i(TAG, "No receiver for trackingModeIntent!");
        }
        getActivity().invalidateOptionsMenu();
        updateUI();
        return true;
    }

    private void updateUI() {
        //It's possible for a Fragment to try to update its state when not attached to the under-
        //lying Activity - result then is crash!
        if (isAdded()) {
            if (mRun == null  )
            {
                return;
            }
            Resources r = getResources();
            //Are we tracking ANY run? We call the RunManager method because the PendingIntent that
            //the BackgroundLocationService uses to request and remove location updates is supplied
            //by RunManager's getLocationPendingIntent(boolean) method.
            mStarted = mRunManager.isTrackingRun();
            //Are we tracking THIS run?
            mIsTrackingThisRun = mRunManager.isTrackingRun(mRun);
            //Enable Start button only if we're not tracking ANY run at this time
            mStartButton.setEnabled(!mStarted);
            //Enable Stop button only if we're tracking and tracking THIS run
            mStopButton.setEnabled(mStarted && mIsTrackingThisRun);
            //If we haven't yet gotten a starting location for this run, try to get one. Once we've
            //gotten a starting location, no need to ask for it again.
            if (mRun != null && mStartLocation == null) {
                //Log.i(TAG, "For Run " + mRunId + "at beginning of updateUI() section re " +
                //        "mStartLocation mStartLocation is null");
                mStartLocation = mRunManager.getStartLocationForRun(mRunId);
                if (mStartLocation != null) {
                    //Now that we've gotten a Starting Location, record and display information
                    //about it. Change the start date to the timestamp of the first Location object
                    //received.
                    mRun.setStartDate(new Date(mStartLocation.getTime()));
                    //Now write the new start date to the database
                    TrackingLocationIntentService.startActionUpdateStartDate(mAppContext, mRun);
                    //Finally, display the new start date
                    mStartedTextView.setText(Constants.DATE_FORMAT.format(mRun.getStartDate()));
                    //Display in appropriate format the starting point for the Run
                    mStartingPointTextView.setText(r.getString(R.string.position,
                            (Location.convert(mStartLocation.getLatitude(), Location.FORMAT_SECONDS)),
                            (Location.convert(mStartLocation.getLongitude(), Location.FORMAT_SECONDS))));
                    //Now update display of the altitude of the starting point.
                    mStartingAltitudeTextView
                            .setText(mRunManager.formatAltitude(mStartLocation.getAltitude()));
                    //We won't have a Starting Address yet, so ask for one and record it.
                    TrackingLocationIntentService
                            .startActionUpdateStartAddress(mAppContext, mRun, mStartLocation);
                    mStartingAddressTextView.setText(mRun.getStartAddress());
                    //If we get here, mBounds should be null, but better to check. Put the starting
                    //location into the LatLngBounds Builder and later, when at least one additional
                    //location has also been included, build mBounds.
                    if (mBounds == null) {
                        mBuilder.include(new LatLng(mStartLocation.getLatitude(),
                                                    mStartLocation.getLongitude()));
                    }
                    //If we get here, mPoints should have zero elements, but better to check, then
                    //add the mStartLocation as the first element.
                    if (mPoints.size() == 0) {
                        mPoints.add(new LatLng(mStartLocation.getLatitude(),
                                               mStartLocation.getLongitude()));
                    }
                } else {
                    Log.i(TAG, "getStartLocationForRun returned null for Run " + mRun.getId());
                }
            }
            //If we have a starting location but don't yet have a starting address, get one and
            //update the Run Table with a new starting date equal to the time of the first location
            //and with the new starting address. Once we have a starting address,no need to reload
            //any data concerning the Start Location - it won't change as the Run goes on.
            if (mRun != null && mStartLocation != null) {
                mStartingAltitudeTextView
                        .setText(mRunManager.formatAltitude(mStartLocation.getAltitude()));
                if (mRunManager.addressBad(getActivity(),
                        mStartingAddressTextView.getText().toString())) {
                    Log.i(TAG, "mRun.getStartAddress() for Run " + mRun.getId() + " is bad; " +
                            "calling updateRunStartAddress().");
                    //Get the starting address from the geocoder and record that in the Run Table
                    TrackingLocationIntentService
                            .startActionUpdateStartAddress(mAppContext, mRun, mStartLocation);
                    mStartingAddressTextView.setText(mRun.getStartAddress());
                    Log.i(TAG, "After getting bad Start Address for Run " + mRunId + " and " +
                            "updating, Start Address is " + mRun.getStartAddress());
                }
            }
            //mLastLocation gets set by the LocationListLoader when mLocationCursor is returned
            //from the LocationListLoader. That generates changes to the data stored for the Run,
            //including updated Duration and Distance, so the RunCursorLoader will also produce a
            //new RunCursor. If we have a Run and a last location for it, we will have duration and
            //distance values for it in the Run Table, so retrieve and display them. This has to
            //be done every time a new location is recorded and, accordingly, the UI updates.
            if (mRun != null && mLastLocation != null && mLastLocation != mStartLocation) {
                //If we're tracking this Run and haven't started updating the ending address, start
                //doing so
                mDurationTextView.setText(Run.formatDuration((int) (mRun.getDuration() / 1000)));
                mDistanceCoveredTextView.setText(mRunManager.formatDistance(mRun.getDistance()));
                mEndingPointTextView.setText(r.getString(R.string.position,
                        (Location.convert(mLastLocation.getLatitude(), Location.FORMAT_SECONDS)),
                        (Location.convert(mLastLocation.getLongitude(), Location.FORMAT_SECONDS))));
                mEndingAltitudeTextView
                        .setText(mRunManager.formatAltitude(mLastLocation.getAltitude()));
                mEndedTextView.setText(Constants.DATE_FORMAT.format(mLastLocation.getTime()));
                mEndingAddressTextView.setText(mRun.getEndAddress());
                //We don't check for bad Ending Addresses because the Ending Address gets updated
                //every ten second while the Run is being tracked.
                //If mBounds hasn't been initialized yet, add this location to the Builder and
                //create mBounds. If mBounds has been created, simply add this point to it and save
                //the newly-updated mBounds to the RunManager singleton for use in recreating this
                //map more quickly upon a configuration change.
                LatLng latLng = new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude());
                if (mBounds == null) {
                    mBuilder.include(latLng);
                    mBounds = mBuilder.build();
                } else {
                    mBounds = mBounds.including(latLng);
                    mRunManager.saveBounds(mRunId, mBounds);
                }
                //Add this point to the collection of points for updating the polyline and save to
                //RunManager's singleton for use in recreating this map more quickly upon a
                //configuration change.
                mPoints.add(new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude()));
                mRunManager.savePoints(mRunId, mPoints);
            }

        }
        //If we've already initialized the map for this CombinedFragment, all we need to do is
        //update it with the latest location update's data
        if (mPrepared) {
            if (mLastLocation != null) {
                //Remember to check whether mLastLocation is null, which it could be if this
                //is a newly-opened run and no location data have been recorded to the database
                //before the map got opened.
                //LatLng latLng = new LatLng(mLastLocation.getLatitude(),
                //                           mLastLocation.getLongitude());
                //The last element of mPoints should be the same as recreating a LatLng from
                //mLastLocation's data.
                LatLng latLng = mPoints.get(mPoints.size() - 1);
                //Update mPolyline's set of points
                mPolyline.setPoints(mPoints);
                //Update the position of mEndMarker. To avoid the overhead of looking up an address
                //for every location update we don't update the snippet until the user clicks on it.
                if (mEndMarker != null) {
                    mEndMarker.setPosition(latLng);
                }
                //Update the map's camera position depending upon the desired map updating mode and
                //the last reported location. We shouldn't have to specify the display size because
                //the map has already been initialized and laid out in onCreateView().
                CameraUpdate movement = updateCamera(mViewMode, latLng);
                //If the user has chosen to turn tracking off, updateCamera() will return null and
                //the Camera will just stay where it was before.
                if (movement != null) {
                    Log.i(TAG, "Now animating camera on CameraUpdate movement");
                    //Animate the camera for the cool effect...
                    mGoogleMap.animateCamera(movement);
                }
            }
        } else {
            //If we haven't yet initialized the map with our starting conditions, do it now.
            prepareMap();
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
                //already been created, so we don't need to tell the CameraUpdate about the size of
                //the map.
                cameraUpdate = CameraUpdateFactory.newLatLngBounds(mBounds, 110);
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
                Log.i(TAG, "FOLLOW_STARTING_POINT zoom level: " +
                        mGoogleMap.getCameraPosition().zoom);
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

    @Override
    public void onResume() {
        super.onResume();
        restartLoaders();
        mMapView.onResume();
        mBroadcastManager.registerReceiver(mResultsReceiver, mResultsFilter);
        try {
            mPoints = new ArrayList<>(mRunManager.retrievePoints(mRunId));
            Log.i(TAG, "For Run #" + mRunId + " mPoints retrieved.");
        } catch (NullPointerException e){
            mPoints = new ArrayList<>();
            Log.i(TAG, "For Run #" + mRunId + " created new ArrayList<LatLng> for mPoints.");
        }
        mBounds = mRunManager.retrieveBounds(mRunId);
        Log.i(TAG, "In onCreate for Run #" + mRunId +" is mBounds null? " + (mBounds == null));
        mPrepared = false;
    }

    //Reload the location data for this run, thus forcing an update of the map display
    private void restartLoaders() {
        Bundle args = getArguments();
        if (args != null) {
            long runId = args.getLong(Constants.ARG_RUN_ID, -1);
            if (runId != -1) {
                mLoaderManager.restartLoader(Constants.LOAD_LOCATION, args, new LocationListCursorCallbacks());
                mLoaderManager.restartLoader(Constants.LOAD_RUN, args,  new RunCursorLoaderCallbacks());
            }
        }
    }

    private void updateOnStartingLocationUpdates(){
        updateUI();
        Log.i(TAG, "In updateOnStartingLocationUpdates(), is mStartButton enabled? " +
                mStartButton.isEnabled() + " mStopButton enabled? " + mStopButton.isEnabled());
    }

    private void updateOnStoppingLocationUpdates(){
        updateUI();
        Log.i(TAG, "In updateOnStoppingLocationUpdates(), is mStartButton enabled? " +
                mStartButton.isEnabled() + " mStopButton enabled? " + mStopButton.isEnabled());
    }

    @Override
    public void onPause(){
        mMapView.onPause();
        mBroadcastManager.unregisterReceiver(mResultsReceiver);
        super.onPause();
    }

    @Override
    public void onDestroy(){
        mMapView.onDestroy();
        doUnbindService(this);
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
        super.onStop();
    }

    //Method to initialize the map
    private void prepareMap() {
        Log.i(TAG, "Entered prepareMap() for Run #" + mRunId);
        //We can't prepare the map until we actually get a map and an open location cursor with at
        //least two locations.
        if (mGoogleMap == null || mLocationCursor == null ||
                mLocationCursor.isClosed() || mLocationCursor.getCount() < 2) {
            return;
        }
        Log.i(TAG, "mLocationCursor has " + mLocationCursor.getCount() + " entries.");
        //We need to use the LocationCursor for the map markers because we need time data, which the
        //LatLng objects in mPoints lack.
        mLocationCursor.moveToFirst();
        mStartLocation = mLocationCursor.getLocation();
        if (mBounds == null){
            mBuilder.include(new LatLng(mStartLocation.getLatitude(),
                                        mStartLocation.getLongitude()));
            mBounds = mBuilder.build();
        }
        if (mPoints.size() == 0){
            mPoints.add(new LatLng(mStartLocation.getLatitude(), mStartLocation.getLongitude()));
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
        //data if the number of locations in the cursor exceeds the number of locations stored
        //in mBounds and mPoints.
        if (mLocationCursor.getCount() > mPoints.size()){
            Log.i(TAG, "Cursor for Run #" + mRunId + " has " + mLocationCursor.getCount() + " and " +
                    "mPoints has " + mPoints.size() + " elements.");
            //Set the cursor to the first location after the locations already in memory
            mLocationCursor.moveToPosition(mPoints.size());
            LatLng latLng;
            //Iterate over the remaining location points in the cursor, updating mPoints, mPolyline,
            //and mBounds; fix position of mEndMarker when we get to the last entry in the cursor.
            while (!mLocationCursor.isAfterLast()){
                mLastLocation = mLocationCursor.getLocation();
                latLng = new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude());
                mPoints.add(latLng);
                mBounds = mBounds.including(latLng);
                if (mLocationCursor.isLast()){
                    endMarkerOptions.position(latLng);
                    endMarkerOptions.snippet(mRunManager.getAddress(getActivity(), latLng));
                }
                mLocationCursor.moveToNext();
            }
        } else {
            Log.i(TAG, "Cursor is equal in size to existing mPoints for Run #" + mRunId + ".");
        }
        PolylineOptions line = new PolylineOptions();
        //mPoints is the List of LatLngs used to create mPolyline created earlier in this
        //CombinedFragment and stored by the RunManager singleton. mPoints represents all the
        //location data collected for this run.
        line.addAll(mPoints);
        //Create mPolyline using the List of LatLngs stored for us in the singleton rather than
        //reading them in again from the database.
        mPolyline = mGoogleMap.addPolyline(line);
        //Now that we've fixed the position of mEndMarker, add it to the map.
        mEndMarker = mGoogleMap.addMarker(endMarkerOptions);
        //Now set up an initial CameraUpdate according to the tracking mode we're in.
        setTrackingMode();
        //The graphic elements of the map display have now all been configured, so clear the
        //mNeedToPrepare flag so that succeeding calls to onLoadFinished will merely update them as
        //new location data comes in.
        mPrepared = true;
    }

    /*Set up tracking depending up mViewMode. SHOW_ENTIRE_ROUTE is the default, but mViewMode may
    * be set to another value when the map is recreated. This method is also used when the user
    * affirmatively selects a different View Mode in the OptionsMenu*/
    private void setTrackingMode(){
        CameraUpdate movement;
        LatLng latLng;
        //Make sure that the zoom level for tracking the end or starting points is at least 17.0f
        //when you start tracking in one of those modes. Thereafter, zoom level is taken from
        //SharedPreferences.
        Log.i(TAG, "In setTrackingMode(), SharedPrefs zoom level is " + mRunManager.mPrefs.getFloat(Constants.ZOOM_LEVEL, 17.0f));
        float zoom = (mRunManager.mPrefs.getFloat(Constants.ZOOM_LEVEL, 17.0f) >= 17.0f) ?
                      mRunManager.mPrefs.getFloat(Constants.ZOOM_LEVEL, 17.0f) : 17.0f;
        Log.i(TAG, "In setTrackingMode(), zoom level after assignment is " + zoom);
        CameraPosition cameraPosition;
        switch (mViewMode){
            case Constants.FOLLOW_END_POINT:
                //Fix the camera on the last location in the run at the zoom level that was last used
                //for this or the FOLLOW_START_POINT view mode. Also make sure the End Marker is
                //placed at the same location so that the End Marker stays in sync with mLastLocation..
                //Move the camera to the end point at the specified zoom level
                latLng = mPoints.get(mPoints.size() - 1);
                Log.i(TAG, "In setTrackingMode() FOLLOW_END_POINT, zoom is " + zoom);
                cameraPosition = new CameraPosition.Builder()
                        .target(latLng)
                        .zoom(zoom)
                        .build();
                //movement = CameraUpdateFactory.newLatLngZoom(latLng, zoom);
                //mGoogleMap.animateCamera(movement);
                mGoogleMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
                Log.i(TAG, "In setTrackingMode FOLLOW_END_POINT after applying movement, zoom level" +
                        " is " + mGoogleMap.getCameraPosition().zoom);
                //Update the position of the end marker
                mEndMarker.setPosition(latLng);
                break;
            case Constants.FOLLOW_STARTING_POINT:
                //Fix the camera on the first location in the run at the designated zoom level.
                //The start marker never moves, so there's never any need to update it.
                latLng = mPoints.get(0);
                Log.i(TAG, "In setTrackingMode() FOLLOW_STARTING_POINT, zoom is " + zoom);
                //movement = CameraUpdateFactory.newLatLngZoom(latLng, zoom);
                cameraPosition = new CameraPosition.Builder()
                        .target(latLng)
                        .zoom(zoom)
                        .build();
                Log.i(TAG, "In setTrackingMode FOLLOW_STARTING_POINT after applying movement, zoom" +
                        "level is " + mGoogleMap.getCameraPosition().zoom);
                //mGoogleMap.animateCamera(movement);
                mGoogleMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
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
                Log.i(TAG, "In setTrackingMode() default, zoom is " + zoom);
                mGoogleMap.animateCamera(movement);
                Log.i(TAG, "In setTrackingMode default, after applying movement, zoom level is " +
                        mGoogleMap.getCameraPosition().zoom);
                break;
        }
    }

    private static void showErrorDialog(CombinedFragment fragment, int errorCode){
        ErrorDialogFragment dialogFragment = new ErrorDialogFragment();
        Bundle args = new Bundle();
        args.putInt(Constants.ARG_ERROR_CODE, errorCode);
        dialogFragment.setArguments(args);
        dialogFragment.show(fragment.getActivity().getSupportFragmentManager(), "errordialog");
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

    private class LocationListCursorCallbacks implements LoaderManager.LoaderCallbacks<Cursor>{
        //Return a loader with a cursor containing a the last location recorded for the designated
        //run.
        @Override
        public Loader<Cursor> onCreateLoader(int d, Bundle args){
            long runId = args.getLong(Constants.ARG_RUN_ID);
            return new MyLocationListCursorLoader(getActivity(), Constants.URI_TABLE_LOCATION, runId);
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor cursor){

            if (cursor != null && !cursor.isClosed()) {
                //Cast the superclass Cursor returned to us to the LocationCursor it really is so we
                ///can extract the Location from it
                mLocationCursor = (RunDatabaseHelper.LocationCursor) cursor;
                if (mLocationCursor.moveToLast()) {
                        mLastLocation = mLocationCursor.getLocation();
                        if (mLastLocation != null) {
                            updateUI();
                        } else {
                            Log.i(TAG, "LastLocationCursorLoader for Run " +mRunId + " returned a null Location");
                        }

                } else {
                    Log.i(TAG, "In LastLocationCursorLoader for Run " + mRunId + ", couldn't move to first position of cursor.");
                }
            } else {
                Log.i(TAG, "LastLocationCursorLoader for Run " + mRunId + " returned a null cursor");
            }
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader){
            //do nothing
        }
    }

    private static class IncomingHandler extends Handler {

        private final WeakReference<CombinedFragment> mFragment;

        IncomingHandler(CombinedFragment fragment){
            mFragment = new WeakReference<>(fragment);
        }
        @Override
        public void handleMessage(Message msg){

            CombinedFragment fragment = mFragment.get();
            switch (msg.what){
                case Constants.MESSAGE_GOOGLEAPICLIENT_CONNECTION_FAILED:
                    ConnectionResult connectionResult = (ConnectionResult) msg.obj;
                    try {
                        connectionResult.startResolutionForResult(fragment.getActivity(),
                                Constants.MESSAGE_PLAY_SERVICES_RESOLUTION_REQUEST);
                    } catch (IntentSender.SendIntentException e) {
                        Log.i(TAG, "Caught IntentSender.SentIntentException while trying to " +
                                "invoke startResolutionForResult with request code " +
                                "MESSAGE_PLAY_SERVICES_RESOLUTION_REQUEST");
                    }
                    break;
                case Constants.MESSAGE_PLAY_SERVICES_ERROR_DIALOG_REQUEST:
                    showErrorDialog(fragment, msg.arg1);
                    break;
                case Constants.MESSAGE_GOOGLEAPICLIENT_CONNECTION_SUSPENDED:
                    switch (msg.arg1) {
                        case GoogleApiClient.ConnectionCallbacks.CAUSE_NETWORK_LOST:
                            Toast.makeText(fragment.getActivity(),
                                    R.string.connection_suspended_network_lost,
                                    Toast.LENGTH_LONG).show();
                            break;
                        case GoogleApiClient.ConnectionCallbacks.CAUSE_SERVICE_DISCONNECTED:
                            Toast.makeText(fragment.getActivity(),
                                    R.string.connection_suspended_service_disconnected,
                                    Toast.LENGTH_LONG).show();
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
                        status.startResolutionForResult(fragment.getActivity(),
                                Constants.LOCATION_CHECK);
                    } catch (IntentSender.SendIntentException e) {
                        Log.i(TAG, "Caught IntentSender.SentIntentException while trying to " +
                                "invoke startResolutionForResult with request code LOCATION_CHECK");
                    }
                    break;
                case Constants.MESSAGE_LOCATION_SETTINGS_NOT_AVAILABLE:
                    Toast.makeText(fragment.getActivity(), "Location Settings not available. Can't" +
                            " track this run.", Toast.LENGTH_LONG).show();
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
                    if (fragment.mOptionsMenu != null){
                        if ((fragment.mViewMode == Constants.SHOW_ENTIRE_ROUTE ||
                                fragment.mViewMode == Constants.FOLLOW_END_POINT ||
                                fragment.mViewMode == Constants.FOLLOW_STARTING_POINT) &&
                                fragment.mRunManager
                                        .isTrackingRun(fragment.mRunManager.getRun(fragment.mRunId))){
                            fragment.mOptionsMenu.findItem(R.id.run_map_pager_activity_scroll)
                                    .setEnabled(false)
                                    .setTitle(R.string.map_scrolling_on);
                        } else {
                            fragment.mOptionsMenu.findItem(R.id.run_map_pager_activity_scroll)
                                    .setEnabled(true);
                        }
                        fragment.updateUI();
                    }
                    break;
                case Constants.MESSAGE_LOCATION_UPDATES_STOPPED:
                    Log.i(TAG, "Received MESSAGE_LOCATION_UPDATES_STOPPED");
                    fragment.updateOnStoppingLocationUpdates();
                    if (fragment.mOptionsMenu != null) {
                        fragment.mOptionsMenu.findItem(R.id.run_map_pager_activity_scroll)
                                .setEnabled(true);
                    }
                    fragment.updateUI();
            }
        }
    }

    //Simple AsyncTask to load the locations for this Run into a LatLngBounds and a List<LatLng> for
    //the use of the RunMapFragment for this RunId.
    private class LoadPointsAndBounds extends AsyncTask<Void, Void, Void> {
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

    private class ResultsReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent){

            String action = intent.getAction();
            if (action.equals(Constants.ACTION_REFRESH_UNITS)) {
                Log.i(TAG, "Received refresh broadcast - calling updateUI().");
                updateUI();
                return;
            }
            if (!mIsTrackingThisRun){
                Log.i(TAG, "Not tracking Run " + mRunId + " - ignoring broadcast");
                return;
            }
            switch(action) {
                case Constants.SEND_RESULT_ACTION:
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
                                //getActivity().stopService(new Intent(getActivity(), BackgroundLocationService.class));
                                try {
                                    Log.i(TAG, "For Run " + mRunId + ", sending MESSAGE_STOP_LOCATION_UPDATES after negative" +
                                            " CONTINUATION_LIMIT_RESULT");
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
                                Toast.makeText(CombinedFragment.this.getActivity(), toastTextRes, Toast.LENGTH_LONG).show();
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
                //If mViewMode is changed for any RunMapFragment, a broadcast is sent to all RunMapFragments
                //running will also change to the same view mode
                case Constants.ACTION_REFRESH_MAPS:
                    long runId = intent.getLongExtra(Constants.ARG_RUN_ID, -1);
                    Log.i(TAG, "Received ACTION_REFRESH_MAPS in Run " + mRunId + " from Run " + runId);
                    if (runId != mRunId) {
                        mViewMode = mRunManager.mPrefs.getInt(Constants.TRACKING_MODE, Constants.SHOW_ENTIRE_ROUTE);
                        setTrackingMode();
                    }
                    updateUI();
                    break;
                default:
                    Log.i(TAG, "How'd you get here!?! Not a defined ACTION!");
            }

        }
    }
}
