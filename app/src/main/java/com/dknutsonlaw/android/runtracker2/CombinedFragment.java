package com.dknutsonlaw.android.runtracker2;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.Loader;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.Task;

import java.util.ArrayList;
import java.util.Date;

/**
 * Created by dck on 11/19/16. All the elements of the earlier RunFragment and RunMapFragment for a
 * given Run combined into a single Fragment
 */

public class CombinedFragment extends Fragment {
    private static final String TAG = "CombinedFragment";

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
    private BackgroundLocationService mService = null;
    private static final LocationRequest sLocationRequest = LocationRequest.create()
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
            .setInterval(1000);
    private static LocationSettingsRequest sLocationSettingsRequest;
    private LoaderManager mLoaderManager;
    private final LocalBroadcastManager mBroadcastManager = LocalBroadcastManager.getInstance(getActivity());
    //We load two data objects in this Fragment, the Run and a list of its locations, so we set up a
    //loader for a cursor of each of them so that the loading takes place on a different thread.
    private final RunCursorLoaderCallbacks mRunCursorLoaderCallbacks = new RunCursorLoaderCallbacks();
    private final LocationListCursorCallbacks mLocationListCursorCallbacks = new LocationListCursorCallbacks();
    private Cursor mLocationCursor;
    private GoogleMap mGoogleMap;
    private MapView mMapView;
    private Polyline mPolyline;
    //Data structures needed to select and receive local broadcast messages sent by the Intent
    //Service
    private IntentFilter mResultsFilter;
    private ResultsReceiver mResultsReceiver;
    //ArrayList to hold the LatLngs needed to build a Polyline to display the Run's route in the map.
    private ArrayList<LatLng> mPoints = null;
    //Bounds to define area a map for this run
    private LatLngBounds mBounds = null;
    private final LatLngBounds.Builder mBuilder = new LatLngBounds.Builder();
    //Designator for how to place the Camera on the map - default is to show the entire route as it's
    //generated
    private int mViewMode = Constants.SHOW_ENTIRE_ROUTE;
    private int mPadding;
    private float mZoom;
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
    private boolean mBound = false;
    public CombinedFragment(){

    }

    public static CombinedFragment newInstance(long runId) {
        Bundle args = new Bundle();
        args.putLong(Constants.ARG_RUN_ID, runId);
        CombinedFragment cf = new CombinedFragment();
        cf.setArguments(args);
        return cf;
    }

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
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //setHasOptionsMenu set to "true" here, but invalidateFragmentsMenus() in RunPagerActivity
        //will set it to false if this Run isn't the one currently displayed in the Activity's ViewPager.
        setHasOptionsMenu(true);
        //Turn off DisplayHomeAsUpEnabled so that more of the ActionBar's subtitle will appear in portrait mode
        if (((AppCompatActivity) getActivity()).getSupportActionBar() != null) {
            //noinspection ConstantConditions
            ((AppCompatActivity) getActivity()).getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        }
        Bundle args = getArguments();
        if (args != null) {
            long runId = args.getLong(Constants.ARG_RUN_ID, -1);
            //If the run already has an id, it will have database records associated with it that
            //need to be loaded using Loaders.
            if (runId != -1) {
                mRunId = runId;
                mRun = RunManager.getRun(mRunId);
            }
            mPadding = calculatePadding();
            mZoom = RunTracker2.getPrefs().getFloat(Constants.ZOOM_LEVEL, 17.0f);
            //If we've already stored values for mBounds and mPoints in the RunManager singletons,
            //retrieve them on a separate thread to speed initialization of the map
            try {
                //noinspection ConstantConditions
                mPoints = new ArrayList<>(RunManager.retrievePoints(mRunId));
                //Log.i(TAG, "For Run #" + mRunId + " mPoints retrieved.");
            } catch (NullPointerException e){
                mPoints = new ArrayList<>();
                //Log.i(TAG, "For Run #" + mRunId + " created new ArrayList<LatLng> for mPoints.");
            }
            mBounds = RunManager.retrieveBounds(mRunId);
            //Log.i(TAG, "In onCreate for Run #" + mRunId +" is mBounds null? " + (mBounds == null));
            //If this is not a newly-created Run, load all the location information available for
            //Run into a LocationCursor a spawn a separate thread to fill mBounds and  mPoints  with
            //it.
            Cursor locationCursor = getContext().getContentResolver().query(
                    Constants.URI_TABLE_LOCATION,
                    null,
                    Constants.COLUMN_LOCATION_RUN_ID + " = ?",
                    new String[]{String.valueOf(mRunId)},
                    Constants.COLUMN_LOCATION_TIMESTAMP + " desc"
            );
            LoadPointsAndBounds initTask = new LoadPointsAndBounds(locationCursor, mPoints,mBounds,
                    mBuilder);
            initTask.execute();
        }
        //Set up Broadcast Receiver to get reports of results from TrackingLocationIntentService
        //First set up the IntentFilter for the Receiver so it will receive the Intents intended for it
        mResultsFilter = new IntentFilter(Constants.SEND_RESULT_ACTION);
        mResultsFilter.addAction(Constants.ACTION_REFRESH_UNITS);
        mResultsFilter.addAction(Constants.ACTION_REFRESH_MAPS);
        //Now instantiate the Broadcast Receiver
        mResultsReceiver = new ResultsReceiver();
        sLocationSettingsRequest = getLocationSettingsRequest();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState){
        super.onActivityCreated(savedInstanceState);
        //Get and save the Application Context for use with some functions that might get called
        //when the Activity isn't around or this CombinedFragment isn't attached.
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
        if (RunManager.isTrackingRun() && mRun == null) {
            mRun = new Run();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Resources r = getResources();
        View v = inflater.inflate(R.layout.combined_fragment, container, false);
        mStartedTextView = v.findViewById(R.id.run_startedTextView);
        mStartingPointTextView = v.findViewById(R.id.run_starting_pointTextView);
        mStartingAltitudeTextView = v.findViewById(R.id.run__starting_altitudeTextView);
        mStartingAddressTextView = v.findViewById(R.id.run_starting_addressTextView);

        mEndedTextView = v.findViewById(R.id.run_endedTextView);
        mEndingPointTextView = v.findViewById(R.id.ending_pointTextView);
        mEndingAltitudeTextView = v.findViewById(R.id.run__ending_altitudeTextView);
        mEndingAddressTextView = v.findViewById(R.id.run_ending_address_TextView);
        mDurationTextView = v.findViewById(R.id.run_durationTextView);
        mDistanceCoveredTextView = v.findViewById(R.id.distance_coveredTextView);

        mStartButton = v.findViewById(R.id.run_startButton);
        mStartButton.setOnClickListener(view -> {
            Log.i(TAG, " Pressed StartButton. Run Id is " + mRun.getId());
            //Make sure Location Services is enabled and that we have permission to use it
            checkLocationSettings();
            if (!checkPermission()){
                requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                        Constants.REQUEST_LOCATION_PERMISSIONS);
            }
            //If we've got the necessary settings and permissions, start location updates and
            //perform housekeeping for tracking a Run
            if (RunTracker2.getLocationSettingsState() && checkPermission() && mBound){
                RunManager.startTrackingRun(getActivity(), mRunId);
                mService.startLocationUpdates(mRunId);
                //Tracking a Run disables the New Run menu item, so we have to invalidate the
                //Options Menu to update its appearance
                getActivity().invalidateOptionsMenu();
                updateUI();

            }
        });
        mStopButton = v.findViewById(R.id.run_stopButton);
        mStopButton.setOnClickListener(view -> {
            Log.i(TAG, "Stop Button Pressed. Run is " + mRunId);
            mService.stopForeground(true);
            //Tell the service that controls Location updates to stop updates
            mService.stopLocationUpdates();
            //Do housekeeping for stopping tracking a run.
            //The New Run menu item has been reenabled, so we must invalidate the Options Menu
            //to refresh its appearance
            getActivity().invalidateOptionsMenu();
            //Now that we've stopped tracking, do one last update of the Ending Address to catch
            //any change from the updates done automatically during tracking of Run
            if (mRun.getEndAddress() != null) {
                TrackingLocationIntentService.startActionUpdateEndAddress(getActivity(),
                        mRun, RunManager.getLastLocationForRun(mRunId));
            }
            updateUI();
        });

        //If this isn't a new run, we should immediately populate the textviews.
        //Start with information concerning the starting point.
        if ((mStartLocation = RunManager.getStartLocationForRun(mRunId)) != null) {
            mStartedTextView.setText(Constants.DATE_FORMAT.format(mRun.getStartDate()));
            //Report latitude and longitude in degrees, minutes and seconds
            /*mStartingPointTextView.setText(r.getString(R.string.position,
                    (Location.convert(mStartLocation.getLatitude(), Location.FORMAT_SECONDS)),
                    (Location.convert(mStartLocation.getLongitude(), Location.FORMAT_SECONDS))));*/
            mStartingPointTextView.setText(RunManager.convertLocation(mStartLocation.getLatitude(),
                                                                      mStartLocation.getLongitude()));
            //Report altitude values in feet or meters, depending upon measurement units setting -
            //Imperial or metric
            mStartingAltitudeTextView.setText(RunManager.formatAltitude(mStartLocation.getAltitude()));
            //Load what this Run has in the database for its Starting Address
            mStartingAddressTextView.setText(mRun.getStartAddress());
            //If what we're showing for the Starting Address is bad, try to get a good address from the
            ///geocoder and record it to the database
            if (RunManager.addressBad(getActivity(), mStartingAddressTextView.getText().toString())) {
                TrackingLocationIntentService.startActionUpdateStartAddress(getActivity(), mRun, mStartLocation);
            }
        }
        //Now display what we have concerning the ending point.
        mLastLocation = RunManager.getLastLocationForRun(mRunId);
        //If we have a last location that's different from the starting location, display the data
        //we have concerning it.
        if (mLastLocation != null && mLastLocation != mStartLocation) {
            mEndedTextView.setText(Constants.DATE_FORMAT.format(mLastLocation.getTime()));
            /*mEndingPointTextView.setText(r.getString(R.string.position,
                    (Location.convert(mLastLocation.getLatitude(), Location.FORMAT_SECONDS)),
                    (Location.convert(mLastLocation.getLongitude(), Location.FORMAT_SECONDS))));*/
            mEndedTextView.setText(RunManager.convertLocation(mLastLocation.getLatitude(),
                                                              mLastLocation.getLongitude()));
            //Display altitude in feet or meters, depending upon measurement units setting.
            mEndingAltitudeTextView.setText(RunManager.formatAltitude(mLastLocation.getAltitude()));
            mEndingAddressTextView.setText(mRun.getEndAddress());
            //If our Ending Address loaded from the database is bad, get a new value from the
            //geocoder and store it to the database,then display it
            if (RunManager.addressBad(getActivity(), mEndingAddressTextView.getText().toString())) {
                TrackingLocationIntentService.startActionUpdateEndAddress(getActivity(), mRun, mLastLocation);
            }
            //Display duration of the Run in hours, minutes and seconds
            mDurationTextView.setText(Run.formatDuration((int) (mRun.getDuration() / 1000)));
            //Display Run's distance in meters and kilometers or feet and miles, depending upon
            //measurement units setting.
            mDistanceCoveredTextView.setText(RunManager.formatDistance(mRun.getDistance()));
        }
        //Enable Start button only if we're not tracking ANY run at this time
        mStartButton.setEnabled(!mStarted);
        //Enable Stop button only if we're tracking and tracking THIS run
        mStopButton.setEnabled(mStarted && mIsTrackingThisRun);

        //Now initialize the map
        mMapView = v.findViewById(R.id.mapViewContainer);
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
                mZoom = RunTracker2.getPrefs().getFloat(Constants.ZOOM_LEVEL, 17.0f);
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
                        if (RunManager.getLastLocationForRun(mRunId) != null) {
                            endDate = Constants.DATE_FORMAT.format(
                                    RunManager.getLastLocationForRun(mRunId).getTime());
                        }
                        if (RunManager.isTrackingRun(RunManager.getRun(mRunId))) {
                            snippetAddress = RunManager.getAddress(getActivity(), marker.getPosition());
                        } else {
                            snippetAddress = RunManager.getRun(mRunId).getEndAddress();
                        }
                        marker.setSnippet(endDate + "\n" + snippetAddress);
                    }
                    //Need to return "false" so the default action for clicking on a marker will
                    //also occur for the Start Marker and for the End Marker after we've updated its
                    //snippet.
                    return false;
                });
                mGoogleMap.setOnCameraMoveListener(() -> {
                    if (mViewMode != Constants.SHOW_ENTIRE_ROUTE) {
                        mZoom = mGoogleMap.getCameraPosition().zoom;
                        RunTracker2.getPrefs().edit().putFloat(Constants.ZOOM_LEVEL,
                                mZoom).apply();
                    }
                });
            }
        });
        return v;
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        //Set text of menuItem to select distance measurement units according to its current setting.
        if (RunTracker2.getPrefs().getBoolean(Constants.MEASUREMENT_SYSTEM, Constants.IMPERIAL)) {
            menu.findItem(R.id.run_map_pager_activity_units).setTitle(R.string.imperial);
        } else {
            menu.findItem(R.id.run_map_pager_activity_units).setTitle(R.string.metric);
        }
        //Set text of menuItem to turn scrolling on or off according to its current setting.
        if (mGoogleMap != null && mGoogleMap.getUiSettings() != null) {
            if (mGoogleMap.getUiSettings().isScrollGesturesEnabled()) {
                menu.findItem(R.id.run_map_pager_activity_scroll)
                        .setTitle(R.string.map_scrolling_off);
            } else {
                menu.findItem(R.id.run_map_pager_activity_scroll)
                        .setTitle(R.string.map_scrolling_on);
            }
        }
        //If the Run's being tracked and the ViewMode is SHOW_ENTIRE_ROUTE or FOLLOW_END_POINT,
        //scrolling won't work because the map gets a new CameraUpdate with a LatLngBounds that
        //pulls the display back and undoes the scrolling with every location update
        if (RunManager.isTrackingRun(RunManager.getRun(mRunId)) &&
                !RunTracker2.getPrefs().getBoolean(Constants.SCROLLABLE, false)){
            Log.i(TAG, "Tracking Run " + mRunId + " in ViewMode inconsistent with scrolling." +
                    "Turning scrolling off.");
            menu.findItem(R.id.run_map_pager_activity_scroll)
                    .setEnabled(false)
                    .setTitle(R.string.map_scrolling_on);
        }
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
        switch (item.getItemId()){
            case R.id.run_map_pager_activity_units:
                //Swap distance measurement unit between imperial and metric
                RunTracker2.getPrefs().edit().putBoolean(Constants.MEASUREMENT_SYSTEM,
                                !RunTracker2.getPrefs().getBoolean(Constants.MEASUREMENT_SYSTEM,
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
                //Change scroll setting for the currently displayed map only.
                if (mGoogleMap != null &&
                        RunTracker2.getPrefs().getLong(Constants.ARG_RUN_ID, -1) == mRunId) {
                    //Toggle the ability to scroll the map.
                    mGoogleMap.getUiSettings()
                            .setScrollGesturesEnabled(!mGoogleMap.getUiSettings()
                            .isScrollGesturesEnabled());
                    //Store current state of scrolling.
                    RunTracker2.getPrefs().edit().putBoolean(Constants.SCROLL_ON,
                            !RunTracker2.getPrefs().getBoolean(Constants.SCROLL_ON, false))
                            .apply();
                }
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
                mViewMode = Constants.SHOW_ENTIRE_ROUTE;
                if (RunManager.isTrackingRun(mRun)){
                    //Scrolling won't work with this tracking mode while the run is actually being
                    //tracked, so turn it off with the next update of the UI.
                    RunTracker2.getPrefs().edit().putBoolean(Constants.SCROLLABLE, false).apply();
                }
                break;
            case R.id.track_end_point_menu_item:
                mViewMode = Constants.FOLLOW_END_POINT;
                mZoom = RunTracker2.getPrefs().getFloat(Constants.ZOOM_LEVEL, -17.0f) <
                        mGoogleMap.getCameraPosition().zoom ? mGoogleMap.getCameraPosition().zoom :
                        RunTracker2.getPrefs().getFloat(Constants.ZOOM_LEVEL, 17.0f);
                if (RunManager.isTrackingRun(mRun)){
                    //Scrolling won't work with this tracking mode while the run is actually being
                    //tracked, so turn it off with the next update of the UI.
                    RunTracker2.getPrefs().edit().putBoolean(Constants.SCROLLABLE, false).apply();
                }
                break;
            case R.id.track_start_point_menu_item:
                mViewMode = Constants.FOLLOW_STARTING_POINT;
                mZoom = RunTracker2.getPrefs().getFloat(Constants.ZOOM_LEVEL, -17.0f) <
                        mGoogleMap.getCameraPosition().zoom ? mGoogleMap.getCameraPosition().zoom :
                        RunTracker2.getPrefs().getFloat(Constants.ZOOM_LEVEL, 17.0f);
                if (RunManager.isTrackingRun(mRun)){
                    //Scrolling won't work with this tracking mode while the run is actually being
                    //tracked, so turn it off with the next update of the UI.
                    RunTracker2.getPrefs().edit().putBoolean(Constants.SCROLLABLE, false).apply();
                }
                break;
            case R.id.tracking_off_menu_item:
                mViewMode = Constants.NO_UPDATES;
                mZoom = RunTracker2.getPrefs().getFloat(Constants.ZOOM_LEVEL, -17.0f) <
                        mGoogleMap.getCameraPosition().zoom ? mGoogleMap.getCameraPosition().zoom :
                        RunTracker2.getPrefs().getFloat(Constants.ZOOM_LEVEL, 17.0f);
                //Scrolling will work with this tracking mode, so make sure we enable the scrolling
                //menuItem upon next update of UI.
                RunTracker2.getPrefs().edit().putBoolean(Constants.SCROLLABLE, true).apply();
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        //Store and implement the new tracking mode and tell all the other open CombinedFragments to
        //switch to this mode
        RunTracker2.getPrefs().edit().putInt(Constants.TRACKING_MODE, mViewMode).apply();
        setTrackingMode();
        mZoom = RunTracker2.getPrefs().getFloat(Constants.ZOOM_LEVEL, 17.0f);
        Intent trackingModeIntent = new Intent(Constants.SEND_RESULT_ACTION)
                .putExtra(Constants.ACTION_ATTEMPTED, Constants.ACTION_REFRESH_MAPS)
                .putExtra(Constants.ARG_RUN_ID, mRunId);
        boolean receiver = mBroadcastManager.sendBroadcast(trackingModeIntent);
        if (!receiver){
            Log.i(TAG, "No receiver for trackingModeIntent!");
        }
        //Need to update the menu's appearance after enabling.disabling Scroll menu item
        getActivity().invalidateOptionsMenu();
        updateUI();
        return true;
    }

    private void updateUI() {
        //It's possible for a Fragment to try to update its state when not attached to the under-
        //lying Activity - result then is crash!
        if (isAdded()) {
            //Are we tracking ANY run? We call the RunManager method because the PendingIntent that
            //the BackgroundLocationService uses to request and remove location updates is supplied
            //by RunManager's getLocationPendingIntent(boolean) method.
            mStarted = RunManager.isTrackingRun();
            //Are we tracking THIS run?
            mIsTrackingThisRun = RunManager.isTrackingRun(mRun);
            Log.i(TAG, "mIsTrackingThisRun for Run " + mRun.getId() + " is " + mIsTrackingThisRun);
            //Enable Start button only if we're not tracking ANY run at this time
            mStartButton.setEnabled(!mStarted);
            //Enable Stop button only if we're tracking and tracking THIS run
            mStopButton.setEnabled(mStarted && mIsTrackingThisRun);

            if (mRun == null) {
                return;
            }
            Resources r = getResources();
            //If we haven't yet gotten a starting location for this run, try to get one. Once we've
            //gotten a starting location, no need to ask for it again.
            if (mRun != null && mStartLocation == null) {
                //This is a  brand new Run with no locations yet picked up - get the Starting
                //Location, if it exists.
                mStartLocation = RunManager.getStartLocationForRun(mRunId);
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
                    mStartingPointTextView.setText(RunManager.convertLocation(mStartLocation.getLatitude(),
                                                                              mStartLocation.getLongitude()));
                    //Now update display of the altitude of the starting point.
                    mStartingAltitudeTextView
                            .setText(RunManager.formatAltitude(mStartLocation.getAltitude()));
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
                if (RunManager.addressBad(getActivity(),
                        mStartingAddressTextView.getText().toString())) {
                    //Get the starting address from the geocoder, record that in the Run Table and
                    //display it in the TextView
                    TrackingLocationIntentService
                            .startActionUpdateStartAddress(mAppContext, mRun, mStartLocation);
                    mStartingAddressTextView.setText(mRun.getStartAddress());
                }
                //Starting Altitude needs to get reset in case the measurement units get changed after
                //the user starts tracking the Run.
                mStartingAltitudeTextView.setText(RunManager.formatAltitude(mStartLocation.getAltitude()));
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
                mDistanceCoveredTextView.setText(RunManager.formatDistance(mRun.getDistance()));

                mEndingPointTextView.setText(RunManager.convertLocation(mLastLocation.getLatitude(),
                                                                        mLastLocation.getLongitude()));
                mEndingAltitudeTextView
                        .setText(RunManager.formatAltitude(mLastLocation.getAltitude()));
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
                    RunManager.saveBounds(mRunId, mBounds);
                } else {
                    if (!mBounds.contains(latLng)) {
                        mBounds = mBounds.including(latLng);
                        RunManager.saveBounds(mRunId, mBounds);
                    }
                }
                //Add this point to the collection of points for updating the polyline if this
                //location is not the same as the last previous one and thereafter save the
                //updated mPoints to RunManager's singleton for use in recreating this map more
                //quickly upon a configuration change.
                if (mPoints.size() >= 1) {
                    if (mLastLocation.getLatitude() != mPoints.get(mPoints.size() - 1).latitude ||
                            mLastLocation.getLongitude() != mPoints.get(mPoints.size() - 1).longitude) {

                        mPoints.add(new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude()));
                        RunManager.savePoints(mRunId, mPoints);
                    }
                }
            }
            //If we've already initialized the map for this CombinedFragment, all we need to do is
            //update it with the latest location update's data
            if (mPrepared) {
                //Remember to check whether mLastLocation is null, which it could be if this
                //is a newly-opened run and no location data have been recorded to the database
                //before the map got opened.
                if (mLastLocation != null) {
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
                        //Animate the camera for the cool effect...
                        mGoogleMap.animateCamera(movement);
                    }
                }
            } else {
                //If we haven't yet initialized the map with our starting conditions, do it now.
                prepareMap();
            }
        } else {
            Log.i(TAG, "Fragment is not Added() - cannot update UI");
        }
    }

    private CameraUpdate updateCamera(int mode, LatLng latLng) {
        //This method will be called after every location update and move the Camera location
        //according to the map updating mode selected by the user and the current location of
        //the run's end point.
        if (mRunId != RunManager.getCurrentRunId()){
            return null;
        }
        CameraUpdate cameraUpdate;
        switch (mode) {
            case Constants.SHOW_ENTIRE_ROUTE: {
                //To show the entire route, we supply the newly-updated mBounds containing all the
                //LatLngs in the route to the relevant CameraUpdateFactory method. The map has
                //already been created, so we don't need to tell the CameraUpdate about the size of
                //the map.
                cameraUpdate = CameraUpdateFactory.newLatLngBounds(mBounds, mPadding);
                break;
            }
            case Constants.FOLLOW_END_POINT: {
                //To track the end point of the Run, move the camera to the new end point at the
                //zoom level last used for this mode or FOLLOW_START_POINT mode.
                cameraUpdate = CameraUpdateFactory.newLatLngZoom(latLng, mZoom);
                break;
            }
            case Constants.FOLLOW_STARTING_POINT: {
                //To center on the start point of the Run, move the camera to the starting point at
                //the zoom level last set for this mode or FOLLOW_END_POINT mode.
                cameraUpdate = CameraUpdateFactory.newLatLngZoom(mPoints.get(0), mZoom);
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
    public void onStart(){
        super.onStart();
        //Bind to the BackgroundLocationService here and unBind in onStop()
        if (mService == null){
            mService = new BackgroundLocationService();
        }
        getContext().bindService(new Intent(getContext(), BackgroundLocationService.class), mServiceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onResume() {
        super.onResume();
        restartLoaders();
        mMapView.onResume();
        mBroadcastManager.registerReceiver(mResultsReceiver, mResultsFilter);
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

    @Override
    public void onPause(){
        mMapView.onPause();
        mBroadcastManager.unregisterReceiver(mResultsReceiver);
        super.onPause();
    }

    @Override
    public void onDestroy(){
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
        //when the map gets displayed again.
        if (mGoogleMap != null)
            mGoogleMap.clear();
        //Close the location cursor before shutting down
        if (mLocationCursor != null) {
            if (!mLocationCursor.isClosed())
                mLocationCursor.close();
        }
        //UnBind from the BackgroundLocationService here and Bind in onStart()
        getContext().unbindService(mServiceConnection);
        mBound = false;

        super.onStop();
    }

    private void fixMap(){

    }

    //Method to initialize the map
    private void prepareMap() {
        //We can't prepare the map until we actually get a map and an open location cursor with at
        //least two locations. We should also exit if the map has already been prepared,
        if (mGoogleMap == null || mLocationCursor == null ||
                mLocationCursor.isClosed() || mLocationCursor.getCount() < 2 ||
                mPrepared) {
            return;
        }
        //We need to use the LocationCursor for the map markers because we need time data, which the
        //LatLng objects in mPoints lack.
        mLocationCursor.moveToFirst();
        mStartLocation = RunDatabaseHelper.getLocation(mLocationCursor);
        if (mBounds == null){
            Log.i(TAG, "In prepareMap() for Run " + mRunId + ", mBounds is null");
            mBuilder.include(new LatLng(mStartLocation.getLatitude(),
                                        mStartLocation.getLongitude()));
            mBounds = mBuilder.build();
        }
        if (mPoints.size() == 0){
            mPoints.add(new LatLng(mStartLocation.getLatitude(), mStartLocation.getLongitude()));
        }
        String startDate = Constants.DATE_FORMAT.format(mStartLocation.getTime());
        Resources r = getActivity().getResources();
        //Now set up the EndMarker We use the default red icon for the EndMarker. We need to keep a
        //reference to it because it will be updated as the Run progresses.
        mLocationCursor.moveToLast();
        mLastLocation = RunDatabaseHelper.getLocation(mLocationCursor);
        String endDate  = "";
        try {
            endDate = Constants.DATE_FORMAT.format(mLastLocation != null ? mLastLocation.getTime() : 0);
        } catch (NullPointerException npe){
            Log.e(TAG, "Caught an NPE trying to get the time of a LastLocation");
        }
        //Get the address where the run ended from the database. If that address is bad, get a new
        //end address from the geocoder. The geocoder needs a LatLng, so we feed it the last element
        //in mPoints.
        String snippetAddress = RunManager.getRun(mRunId).getEndAddress();
        if (RunManager.addressBad(getActivity(), snippetAddress)) {
            snippetAddress = RunManager.getAddress(getActivity(), mPoints.get(mPoints.size() - 1));
        }
        MarkerOptions endMarkerOptions = new MarkerOptions()
                .position(new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude()))
                .title(r.getString(R.string.run_finish))
                .snippet(endDate + "\n" + snippetAddress)
                .flat(false);
        //Update mPoints, mPolyline, mBounds and the position of mEndMarker with additional location
        //data if the number of locations in the cursor exceeds the number of locations stored
        //in mBounds and mPoints.
        if (mLocationCursor.getCount() > mPoints.size()){
            //Set the cursor to the first location after the locations already in memory
            mLocationCursor.moveToPosition(mPoints.size());
            LatLng latLng = null;
            //Iterate over the remaining location points in the cursor, updating mPoints, mPolyline,
            //and mBounds; fix position of mEndMarker when we get to the last entry in the cursor.
            while (!mLocationCursor.isAfterLast()){
                mLastLocation = RunDatabaseHelper.getLocation(mLocationCursor);
                try {
                    latLng = new LatLng(mLastLocation != null ? mLastLocation.getLatitude() : 0,
                                        mLastLocation != null ? mLastLocation.getLongitude() : 0);
                    if (latLng.latitude != 0 && latLng.longitude !=0) {
                        mPoints.add(latLng);
                        mBounds = mBounds.including(latLng);
                    }
                } catch (NullPointerException NPE){
                    Log.e(TAG, "Caught an NPE trying to extract a LatLng from a LastLocation");
                }
                if (mLocationCursor.isLast()){
                    endMarkerOptions.position(latLng);
                    endMarkerOptions.snippet(RunManager.getAddress(getActivity(), latLng));
                }
                mLocationCursor.moveToNext();
            }
        }
        //Get the address where we started the Run from the database. If the database's StartAddress
        //is bad, get a new Starting Address from the geocoder. The geocoder needs a LatLng object,
        //so use the first element of the mPoints List.
        snippetAddress = RunManager.getRun(mRunId).getStartAddress();
        if (RunManager.addressBad(getContext(), snippetAddress)){
            snippetAddress = RunManager.getAddress(getContext(), mPoints.get(0));
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
        PolylineOptions line = new PolylineOptions();
        //mPoints is the List of LatLngs used to create mPolyline created earlier in this
        //CombinedFragment and stored by the RunManager singleton. mPoints represents all the
        //location data collected for this run.

        //Now set up an initial CameraUpdate according to the tracking mode we're in.
        if (mPoints.size() < 3){
            mViewMode = Constants.SHOW_ENTIRE_ROUTE;
        } else {
            mViewMode = RunTracker2.getPrefs().getInt(Constants.TRACKING_MODE, Constants.SHOW_ENTIRE_ROUTE);
        }
        line.addAll(mPoints);
        //Create mPolyline using the List of LatLngs stored for us in the singleton rather than
        //reading them in again from the database.
        mPolyline = mGoogleMap.addPolyline(line);
        //Now that we've fixed the position of mEndMarker, add it to the map.
        mEndMarker = mGoogleMap.addMarker(endMarkerOptions);
        if (mViewMode != Constants.SHOW_ENTIRE_ROUTE){
            //Set initial zoom level of at least 17.0 for all tracking modes other than SHOW_ENTIRE_ROUTE,
            //which always sets its own zoom level based upon the size of the entire route
            mZoom = RunTracker2.getPrefs().getFloat(Constants.ZOOM_LEVEL, 17.0f) <=
                    mGoogleMap.getCameraPosition().zoom ?
                    mGoogleMap.getCameraPosition().zoom :
                    RunTracker2.getPrefs().getFloat(Constants.ZOOM_LEVEL, 17.0f);
        }
        switch (mViewMode){
            case Constants.SHOW_ENTIRE_ROUTE:
                mGoogleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(mBounds, mPadding));
                break;
            case Constants.FOLLOW_END_POINT:
                mGoogleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(mPoints.get(mPoints.size()-1), mZoom));
                break;
            case Constants.FOLLOW_STARTING_POINT:
                mGoogleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(mPoints.get(0), mZoom));
                break;
            default:
                mGoogleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(mPoints.get(mPoints.size() - 1), mZoom));
                break;
        }
        //The graphic elements of the map display have now all been configured, so clear the
        //mNeedToPrepare flag so that succeeding calls to onLoadFinished will merely update them as
        //new location data comes in.
        mPrepared = true;
    }

    /*Set up tracking depending up mViewMode. SHOW_ENTIRE_ROUTE is the default, but mViewMode may
    * be set to another value when the map is recreated. This method is also used when the user
    * affirmatively selects a different View Mode in the OptionsMenu*/
    private void setTrackingMode(){
        mZoom = RunTracker2.getPrefs().getFloat(Constants.ZOOM_LEVEL, 17.0f) <=
                mGoogleMap.getCameraPosition().zoom ?
                mGoogleMap.getCameraPosition().zoom :
                RunTracker2.getPrefs().getFloat(Constants.ZOOM_LEVEL, 17.0f);
        LatLng latLng;
        //Make sure that the zoom level for tracking the end or starting points is at least 17.0f
        //when you start tracking in one of those modes. Thereafter, zoom level is taken from
        //SharedPreferences.
        switch (mViewMode){
            case Constants.FOLLOW_END_POINT:
                //Fix the camera on the last location in the run at the zoom level that was last used
                //for this or the FOLLOW_START_POINT view mode. Also make sure the End Marker is
                //placed at the same location so that the End Marker stays in sync with mLastLocation..
                //Move the camera to the end point at the specified zoom level
                latLng = mPoints.get(mPoints.size() - 1);
                mGoogleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, mZoom), 3000, null);
                //Update the position of the end marker
                mEndMarker.setPosition(latLng);
                break;
            case Constants.FOLLOW_STARTING_POINT:
                //Fix the camera on the first location in the run at the designated zoom level.
                //The start marker never moves, so there's never any need to update it.
                latLng = mPoints.get(0);
                mGoogleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, mZoom), 3000, null);
                break;
            default:
                /*This handles both SHOW_ENTIRE_ROUTE and NO_UPDATES.
                 *Set the camera at the center of the area defined by mBounds, again leaving a
                 *buffer zone at the edges of the screen set according to screen size. The map has
                 *already been created, so we don't need to tell the CameraUpdate about the  map's
                 *size. The zoom level is set according to the size of the area that needs to be
                 *displayed. We don't save the zoom level here, so that any switch to
                 *FOLLOW_END_POINT or FOLLOW_START_POINT mode will use the zoom level that was last
                 *used for one of those modes.*/
                mGoogleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(mBounds, mPadding), 3000, null);
        }
    }
    //Rather than set a fixed amount of padding for the map image, calculate an appropriate
    //amount based upon the size of the display in its current orientation
    private int calculatePadding(){
        final int width = getContext().getResources().getDisplayMetrics().widthPixels;
        final int height = getContext().getResources().getDisplayMetrics().heightPixels;
        final int minMetric = Math.min(width, height);
        //The figure of .075 was arrived at through trial and error. Is there a good way to calculate
        //an appropriate value?
        return (int)(minMetric * .075);
    }

    private boolean checkPermission(){
        int permissionState = ContextCompat.checkSelfPermission(getContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION);
        return permissionState == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermission(){
        requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                Constants.REQUEST_LOCATION_PERMISSIONS);
        }
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int grantResults[]){
        if (requestCode == Constants.REQUEST_LOCATION_PERMISSIONS) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED){
                Toast.makeText(getActivity(), "Location permissions granted.",
                        Toast.LENGTH_LONG).show();
                mService.startLocationUpdates(mRunId);
                RunManager.startTrackingRun(getActivity(), mRunId);
                //Tracking a Run disables the New Run menu item, so we have to invalidate the
                //Options Menu to update its appearance
                getActivity().invalidateOptionsMenu();
                updateUI();
            } else {
                Toast.makeText(getActivity(), "Location permissions denied. No tracking possible.",
                        Toast.LENGTH_LONG).show();
                if (mRun.getDuration() == 0){
                    Toast.makeText(getActivity(), "Never got any locations for this Run; deleting.",
                            Toast.LENGTH_LONG).show();
                    TrackingLocationIntentService.startActionDeleteRun(getActivity(), mRunId);
                }
            }
        } else {
            Log.i(TAG, "REQUEST_LOCATION_PERMISSIONS is the only requestCode used. How'd you get " +
                    "here!?!");
        }
    }

    private void checkLocationSettings(){
        Task<LocationSettingsResponse> result =
                LocationServices.getSettingsClient(getContext()).checkLocationSettings(sLocationSettingsRequest);
        result.addOnCompleteListener(task -> {
            try {
                task.getResult(ApiException.class);
                RunTracker2.setLocationSettingsState(true);
                if (!checkPermission()){
                    requestPermission();
                } else {
                    mService.startLocationUpdates(mRunId);
                }
            } catch (ApiException apie){
                switch(apie.getStatusCode()){
                    case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                        try {
                            ResolvableApiException resolvable = (ResolvableApiException)apie;
                            resolvable.startResolutionForResult(getActivity(),
                                    Constants.LOCATION_SETTINGS_CHECK);
                        } catch (android.content.IntentSender.SendIntentException sie){
                            Toast.makeText(RunTracker2.getInstance(),
                                    "SendIntentException caught when trying startResolutionResult for LocationSettings",
                                    Toast.LENGTH_LONG).show();
                        } catch(ClassCastException cce){
                            Log.e(TAG, "How'd you get a ClassCastException?");
                        }
                        break;
                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                        RunTracker2.setLocationSettingsState(false);
                        Toast.makeText(RunTracker2.getInstance(),
                                "Location settings are inadequate and cannot be fixed here. \n" +
                                        "Dialog not created.",
                                Toast.LENGTH_LONG)
                                .show();
                }
            }
        });
    }

    private class RunCursorLoaderCallbacks implements LoaderManager.LoaderCallbacks<Cursor> {

        @Override
        public Loader<Cursor> onCreateLoader(int d, Bundle args){
            long runId  = args.getLong(Constants.ARG_RUN_ID);
            //Return a Loader pointing to a cursor containing a Run with specified RunId
            return new RunCursorLoader(getActivity(), runId);
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor cursor){

            if (cursor != null) {
                Run run = null;
                if (cursor.moveToFirst()){
                    if (!cursor.isAfterLast()){
                        run = RunDatabaseHelper.getRun(cursor);
                    } else {
                        Log.i(TAG, "In RunCursorLoaderCallbacks, cursor went past last position");
                    }
                } else {
                    Log.i(TAG, "Couldn't move RunCursor to First Position in onLoadFinished");
                }
                if (run != null) {
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

    private class LocationListCursorCallbacks implements LoaderManager.LoaderCallbacks<Cursor>{
        //Return a loader with a cursor containing all the locations recorded for the designated
        //run. including the most recently added one.
        @Override
        public Loader<Cursor> onCreateLoader(int d, Bundle args){
            long runId = args.getLong(Constants.ARG_RUN_ID);
            return new MyLocationListCursorLoader(getActivity(), runId);
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor cursor){
            //Extract the latest location added to the location list and update the UI accordingly
            if (cursor != null && !cursor.isClosed()) {
                mLocationCursor = cursor;
                if (mLocationCursor.moveToLast()) {
                        mLastLocation = RunDatabaseHelper.getLocation(mLocationCursor);
                        if (mLastLocation != null) {
                            updateUI();
                        } else {
                            Log.i(TAG, "LastLocationCursorLoader for Run " +mRunId +
                                    " returned a null Location");
                        }

                } else {
                    Log.i(TAG, "In LastLocationCursorLoader for Run " + mRunId +
                            ", couldn't move to first position of cursor.");
                }
            } else {
                Log.i(TAG, "LastLocationCursorLoader for Run " + mRunId +
                        " returned a null cursor");
            }
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader){
            //do nothing
        }
    }

    private static LocationSettingsRequest getLocationSettingsRequest(){
        return new LocationSettingsRequest.Builder()
                .addLocationRequest(sLocationRequest)
                .build();
    }

    //Simple AsyncTask to load the locations for this Run into a LatLngBounds and a List<LatLng> for
    //the display of the MapView for this RunId.
    private static class LoadPointsAndBounds extends AsyncTask<Void, Void, Void> {
        private final Cursor mCursor;
        private final ArrayList<LatLng> mPoints;
        private LatLngBounds mBounds;
        final LatLngBounds.Builder mBuilder;

        LoadPointsAndBounds(Cursor cursor, ArrayList<LatLng> points,
                            LatLngBounds bounds, LatLngBounds.Builder builder){
            mCursor = cursor;
            mPoints = points;
            mBounds = bounds;
            mBuilder = builder;
        }
        @Override
        protected Void doInBackground(Void... params) {
            Location location;
            LatLng latLng;
            //If mPoints and mBounds already have some locations loaded into them, just load the
            //locations in the cursor that haven't yet been loaded into them.
            if (mPoints.size() > 0 && mBounds != null) {
                //Log.i(TAG, "In LoadPointsAndBounds for Run " + RunManager.getCurrentRunId() + ", mPoints.size() is " +
                //mPoints.size() + "and mBounds was not null.");
                mCursor.moveToPosition(mPoints.size());
                while (!mCursor.isAfterLast()) {
                    location = RunDatabaseHelper.getLocation(mCursor);
                    try {
                        latLng = new LatLng(location != null ? location.getLatitude() : 0, location != null ? location.getLongitude() : 0);
                        mPoints.add(latLng);
                        mBounds = mBounds.including(latLng);
                    } catch (NullPointerException npe){
                        Log.e(TAG, "Caught an NPE trying to extract a LatLng from a location in a cursor");
                    }
                    mCursor.moveToNext();
                }
            } else {
                //If mPoints has no members, mBounds should be null - start from the first location
                //in the cursor and load all location data into mPoints and a Builder for mBounds.
                mCursor.moveToFirst();
                while (!mCursor.isAfterLast()) {
                    location = RunDatabaseHelper.getLocation(mCursor);
                    try {
                        latLng = new LatLng(location != null ? location.getLatitude() : 0, location != null ? location.getLongitude() : 0);
                        mPoints.add(latLng);
                        mBuilder.include(latLng);
                    } catch (NullPointerException npe){
                        Log.e(TAG, "Caught an NPE while extracting a LastLng from a location in a cursor");
                    }
                    mCursor.moveToNext();
                }
                //If we got any locations from the cursor, we need to build mBounds.
                if (mPoints.size() > 0) {
                    mBounds = mBuilder.build();
                }
                mCursor.close();

            }
            return null;
        }
    }

    private class ResultsReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent){

            String action = intent.getAction();
            if (action == null)
                return;
            switch(action) {
                case Constants.ACTION_REFRESH_UNITS:
                    updateUI();
                    return;
                case Constants.SEND_RESULT_ACTION:
                    //Dispatch Intents for processing based upon the value passed in the
                    //ACTION_ATTEMPTED Extras key. Data specific to each different ACTION_ATTEMPTED
                    //value is carried with the EXTENDED_RESULTS_DATA key
                    String actionAttempted =
                            intent.getStringExtra(Constants.ACTION_ATTEMPTED);
                    switch (actionAttempted) {
                        case Constants.ACTION_UPDATE_START_DATE: {
                            int result =
                                    intent.getIntExtra(Constants.EXTENDED_RESULTS_DATA, -1);
                            int toastTextRes;
                            //The getWritableDatabase.update() method returns the number of rows
                            //affected; that value is returned to the IntentService and passed on to
                            //here. If no rows were affected or more than one row was affected,
                            //something went wrong! The IntentService no longer reports successful
                            //updates, so no need to check for those.
                            if (result == 1) {
                                toastTextRes = R.string.update_run_start_date_success;
                            } else if (result == 0) {
                                toastTextRes = R.string.update_run_start_date_failed;
                            } else if (result > 1) {
                                toastTextRes = R.string.multiple_runs_dates_updated;
                            } else {
                                toastTextRes = R.string.unknown_start_date_error;
                            }
                            //If an error occurred, put up a Toast advising the user of how things
                            //went wrong.
                            if (result != 1) {
                                if (isAdded()) {
                                    Toast.makeText(getActivity(),
                                            toastTextRes,
                                            Toast.LENGTH_LONG)
                                            .show();
                                }
                            }
                            break;
                        }
                        case Constants.ACTION_UPDATE_START_ADDRESS: {
                            int result = intent.getIntExtra(Constants.EXTENDED_RESULTS_DATA, -1);
                            int toastTextRes;
                            //The getWritableDatabase.update() method returns the number of rows
                            //affected; that value is returned to the IntentService and passed on to
                            //here. If no rows were affected or more than one row was affected,
                            //something went wrong! The IntentService no longer reports successful
                            //updates, so no need to check for those.
                            if (result == 1) {
                                toastTextRes = R.string.update_run_start_address_success;
                            } else if (result == 0) {
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
                            //The getWritableDatabase.update() method returns the number of rows
                            //affected; that value is returned to the IntentService and passed on to
                            //here. If no rows were affected or more than one row was affected,
                            //something went wrong! The IntentService no longer reports successful
                            //updates, so no need to check for those.
                            if (result == 1) {
                                toastTextRes = R.string.update_run_end_address_success;
                            } else if (result == 0) {
                                toastTextRes = R.string.update_end_address_failed;
                            } else if (result > 1) {
                                toastTextRes = R.string.multiple_runs_end_addresses_updated;
                            } else {
                                toastTextRes = R.string.unknown_end_address_update_error;
                            }
                            //If an error occurred, put up a Toast advising the user of how things
                            //went wrong.
                            if (result != 1) {
                                if (isAdded()) {
                                    Toast.makeText(getActivity(), toastTextRes, Toast.LENGTH_LONG).show();
                                }
                            }
                            break;
                        }
                        case Constants.ACTION_INSERT_LOCATION:
                            //Report any failure while inserting a new location. Successful insertions are not reported.
                            String resultsString = intent.getStringExtra(Constants.EXTENDED_RESULTS_DATA);
                            boolean shouldStop = intent.getBooleanExtra(Constants.SHOULD_STOP, false);
                            Toast.makeText(getContext(), resultsString, Toast.LENGTH_LONG).show();
                            if (shouldStop){
                                mService.stopLocationUpdates();
                            }

                        case Constants.ACTION_REFRESH_MAPS:
                            //If mViewMode is changed for any CombinedFragment, a broadcast is sent to the two
                            //other CombinedFragments currently running so they will also change to the same view
                            //mode and zoom level
                            long runId = intent.getLongExtra(Constants.ARG_RUN_ID, -1);
                            if (runId != -1 && runId != mRunId) {
                                mViewMode = RunTracker2.getPrefs().getInt(Constants.TRACKING_MODE,
                                        Constants.SHOW_ENTIRE_ROUTE);
                                mZoom = RunTracker2.getPrefs().getFloat(Constants.ZOOM_LEVEL, 17.0f);
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
}
