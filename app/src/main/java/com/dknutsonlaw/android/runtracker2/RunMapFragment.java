package com.dknutsonlaw.android.runtracker2;

/**
 * Created by dck on 9/6/15. A Fragment to display the course of a Run in a MapView and update it
 * "live" if the Run is being tracked.
 */

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.ServiceConnection;
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
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
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

import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class RunMapFragment extends Fragment implements LoaderManager.LoaderCallbacks<Cursor>{
    private static final String TAG = "RunMapFragment";

    private final RunManager mRunManager = RunManager.get(getActivity());
    private long mRunId;
    GoogleMap mGoogleMap;
    private MapView mMapView;
    private LoaderManager mLoaderManager;
    private Messenger mLocationService;
    private final Messenger mMessenger = new Messenger(new IncomingHandler(this));
    private RunDatabaseHelper.LocationCursor mLocationCursor;
    private Polyline mPolyline;
    private ArrayList<LatLng> mPoints;
    private LatLngBounds mBounds;
    private final LatLngBounds.Builder mBuilder = new LatLngBounds.Builder();
    private final LocalBroadcastManager mBroadcastManager = LocalBroadcastManager.getInstance(getActivity());
    private IntentFilter mIntentFilter;
    private ResultsReceiver mResultsReceiver;
    private Marker mEndMarker;
    private Location mStartLocation, mLastLocation = null;
    private boolean mPrepared = false;
    private boolean mIsBound = false;
    private boolean mScroll_On;


    //Set default map tracking mode
    private int mViewMode = Constants.SHOW_ENTIRE_ROUTE;

    private final ServiceConnection mLocationServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mLocationService = new Messenger(service);
            mIsBound = true;
            try {
                Message msg = Message.obtain(null, Constants.MESSAGE_REGISTER_CLIENT, Constants.MESSENGER_RUNMAPFRAGMENT, 0);
                msg.replyTo = mMessenger;
                mLocationService.send(msg);
            } catch (RemoteException e){
                Log.i(TAG, "Caught RemoteException while trying to send MESSAGE_REGISTER_CLIENT");
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mLocationService = null;
            mIsBound = false;
        }
    };

    public RunMapFragment() {
        // Required empty public constructor
    }

    public static RunMapFragment newInstance(long runId) {

        Bundle args = new Bundle();
        args.putLong(Constants.ARG_RUN_ID, runId);
        RunMapFragment runMapFragment = new RunMapFragment();
        runMapFragment.setArguments(args);
        return runMapFragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //Retain this fragment across configuration changes so that we don't lose the threads
        //doing location and run updates.
        setRetainInstance(true);
        //Make sure the Options Menu gets created!
        setHasOptionsMenu(true);
        //Check for a Run ID as an argument, and find the run
        Bundle args = getArguments();
        if (args != null) {
            mRunId = args.getLong(Constants.ARG_RUN_ID, -1);
        }
        mIntentFilter = new IntentFilter(Constants.ACTION_REFRESH_MAPS);
        mResultsReceiver = new ResultsReceiver();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        doBindService(this);
        mLoaderManager = getLoaderManager();
        Bundle args = getArguments();
        //Bundle args = new Bundle();
        //args.putLong(Constants.ARG_RUN_ID, mRunId);
        mLoaderManager.initLoader(Constants.LOAD_LOCATION, args, this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){
        View v = inflater.inflate(R.layout.map_activity_fragment, container, false);
        mMapView = (MapView) v.findViewById(R.id.mapViewContainer);
        mMapView.onCreate(savedInstanceState);
        mMapView.onResume(); //needed to get map to display immediately

        try{
            MapsInitializer.initialize(getActivity().getApplicationContext());
        } catch (Exception e){
            e.printStackTrace();
        }

        mMapView.getMapAsync(googleMap -> {
            //Stash a reference to the GoogleMap
            mGoogleMap = googleMap;
            if (mGoogleMap != null) {
                Log.i(TAG, "In on ActivityCreated for Run #" + mRunId + " got a map.");
                //Rather than define our own zoom controls, just enable the UiSettings' zoom controls and
                //listen for changes in CameraPosition to update mZoomLevel
                mGoogleMap.getUiSettings().setZoomControlsEnabled(true);
                mGoogleMap.getUiSettings().setZoomGesturesEnabled(true);
                //Disable map scrolling so we can easily swipe from one map to another.
                mGoogleMap.getUiSettings().setScrollGesturesEnabled(mRunManager.mPrefs.getBoolean(Constants.SCROLL_ON, false));
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
                mGoogleMap.setOnMarkerClickListener(marker -> {
                    if (marker.equals(mEndMarker)) {
                        String endDate = "";
                        String snippetAddress;
                        if (mRunManager.getLastLocationForRun(mRunId) != null) {
                            endDate = Constants.DATE_FORMAT.format(
                                    mRunManager.getLastLocationForRun(mRunId).getTime());
                        }
                        if (mRunManager.isTrackingRun(mRunManager.getRun(mRunId))) {
                            snippetAddress = RunManager.getAddress(getActivity(), marker.getPosition());
                        } else {
                            snippetAddress = mRunManager.getRun(mRunId).getEndAddress();
                        }
                        marker.setSnippet(endDate + "\n" + snippetAddress);
                    }
                    //Need to return "false" so the default action for clicking on a marker will also occur
                    //for the Start Marker and for the End Marker after we've updated its snippet.
                    return false;
                });
            }
        });
        return v;
    }

    /*@Override
    public void onStart(){
        super.onStart();
        //doBindService(this);
    }*/

    private static void doBindService(RunMapFragment fragment){
        fragment.getActivity().getApplicationContext().bindService(new Intent(fragment.getActivity(), BackgroundLocationService.class),
                fragment.mLocationServiceConnection, Context.BIND_AUTO_CREATE);
        fragment.mIsBound = true;
    }

    private static void doUnbindService(RunMapFragment fragment){
        if (fragment.mIsBound){
            fragment.getActivity().getApplicationContext().unbindService(fragment.mLocationServiceConnection);
            fragment.mIsBound = false;
        }
    }
    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        //Set menuItem to select distance measurement units according to its current setting

        if (mRunManager.mPrefs.getBoolean(Constants.MEASUREMENT_SYSTEM, Constants.IMPERIAL)) {
            menu.findItem(R.id.run_map_pager_activity_units).setTitle(R.string.imperial);
        } else {
            menu.findItem(R.id.run_map_pager_activity_units).setTitle(R.string.metric);
        }
        if (mGoogleMap != null && mGoogleMap.getUiSettings() != null) {

            //if (mGoogleMap.getUiSettings().isScrollGesturesEnabled()) {
            if (mRunManager.mPrefs.getBoolean(Constants.SCROLL_ON, false)){
                Log.i(TAG, "Scrolling is enabled for Run " + mRunId + " so display Scrolling Off in menu.");

                menu.findItem(R.id.run_map_pager_activity_scroll).setTitle(R.string.map_scrolling_off);

            } else {
                Log.i(TAG, "Scrolling not enabled for Run " + mRunId + "so display Scrolling On in menu.");

                menu.findItem(R.id.run_map_pager_activity_scroll).setTitle(R.string.map_scrolling_on);

            }
        }
        //If the Run's being tracked and the ViewMode is SHOW_ENTIRE_ROUTE or FOLLOW_END_POINT,
        //scrolling won't work because the map gets a new CameraUpdate with every location update
        if (mRunManager.isTrackingRun(mRunManager.getRun(mRunId)) &&
                !mRunManager.mPrefs.getBoolean(Constants.SCROLLABLE, false)){
            Log.i(TAG, "Tracking Run " + mRunId + " in ViewMode inconsistent with scrolling." +
                    "Turning scrolling off.");

            menu.findItem(R.id.run_map_pager_activity_scroll)
                    .setEnabled(false)
                    .setTitle(R.string.map_scrolling_on);

            Log.i(TAG, "After attempting to turn off scrolling, is it off? " + !menu.findItem(R.id.run_map_pager_activity_scroll).isEnabled());
        }


    }


    /*The Options Menu allows the user to specify whether to track the whole route (the default),
     *track the ending point of the route, track the starting point of the route, or turn off
     *tracking. Actually changing the tracking mode is done in the separate method setTrackingMode()
     *so that the last previously selected tracking mode can be used when the map is reopened instead
     *of always starting with the default SHOW_ENTIRE_ROUTE. */
    @Override
    public boolean onOptionsItemSelected(MenuItem item){
        Log.i(TAG, "Entered onOptionsItemSelected for Run #" + mRunId + ".");
        switch (item.getItemId()){
            case R.id.run_map_pager_activity_units:
                //Swap distance measurement unit between imperial and metric
                mRunManager.mPrefs.edit().putBoolean(Constants.MEASUREMENT_SYSTEM,
                        !mRunManager.mPrefs.getBoolean(Constants.MEASUREMENT_SYSTEM, Constants.IMPERIAL)).apply();
                //Send a broadcast to all open RunFragments will update their displays to show the
                ///newly-selected distance measurement units.
                Intent refreshIntent = new Intent(Constants.ACTION_REFRESH_UNITS);
                refreshIntent.putExtra(Constants.ARG_RUN_ID, mRunId);
                boolean receiver = LocalBroadcastManager.getInstance(getActivity()).sendBroadcast(refreshIntent);
                if(!receiver){
                    Log.i(TAG, "No receiver for CombinedRunFragment REFRESH broadcast!");
                }

                getActivity().invalidateOptionsMenu();
                return true;
            case R.id.run_map_pager_activity_scroll:

                //This setting gets made in a RunMapFragment's MapView, but we want to put this together
                //with the measurement units menu item here in the Activity's OptionsMenu, so we first
                //have to retrieve the RunMapFragment that's currently displayed

                if (mGoogleMap != null) {
                    Log.i(TAG, "Entered scroll menu. Is scrolling enabled? " + mGoogleMap.getUiSettings().isScrollGesturesEnabled());
                    mGoogleMap.getUiSettings()
                            .setScrollGesturesEnabled(!mGoogleMap.getUiSettings().isScrollGesturesEnabled());
                    mRunManager.mPrefs.edit().putBoolean(Constants.SCROLL_ON, !mRunManager.mPrefs.getBoolean(Constants.SCROLL_ON, false)).apply();
                }
                assert mGoogleMap != null;
                Log.i(TAG, "After change to scrolling, is scrolling enabled? " + mGoogleMap.getUiSettings().isScrollGesturesEnabled());

                //We want to change the menuItem title in onPrepareOptionsMenu, so we need to invalidate
                //the menu and recreated it.
                getActivity().invalidateOptionsMenu();
                return true;
            //Select desired map updating mode, then call setTrackingMode() to act on it. We use a
            //separate function for setTrackingMode() so that it can be invoked when the fragment
            //restarts with the last previous tracking mode still in effect, rather than going with the
            //default of SHOW_ENTIRE_ROUTE
            case R.id.show_entire_route_menu_item:
                mViewMode = Constants.SHOW_ENTIRE_ROUTE;
                mRunManager.mPrefs.edit().putBoolean(Constants.SCROLLABLE, false).apply();
                break;
            case R.id.track_end_point_menu_item:
                mViewMode = Constants.FOLLOW_END_POINT;
                mRunManager.mPrefs.edit().putBoolean(Constants.SCROLLABLE, false).apply();
                break;
            case R.id.track_start_point_menu_item:
                mViewMode = Constants.FOLLOW_STARTING_POINT;
                mRunManager.mPrefs.edit().putBoolean(Constants.SCROLLABLE, true).apply();
                break;
            case R.id.tracking_off_menu_item:
                mViewMode = Constants.NO_UPDATES;
                mRunManager.mPrefs.edit().putBoolean(Constants.SCROLLABLE, true).apply();
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
        getActivity().invalidateOptionsMenu();
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
        restartLoader();
        Log.i(TAG, "onResume() called for Run #" + mRunId + ".");
        mMapView.onResume();
        mBroadcastManager.registerReceiver(mResultsReceiver, mIntentFilter);
        try {
            //noinspection ConstantConditions
            mPoints = new ArrayList<>(RunManager.retrievePoints(mRunId));
            Log.i(TAG, "For Run #" + mRunId + " mPoints retrieved.");
        } catch (NullPointerException e){
            mPoints = new ArrayList<>();
            Log.i(TAG, "For Run #" + mRunId + " created new ArrayList<LatLng> for mPoints.");
        }

        mBounds = RunManager.retrieveBounds(mRunId);
        Log.i(TAG, "In onCreate for Run #" + mRunId +" is mBounds null? " + (mBounds == null));
        mPrepared = false;
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
        //when the MapFragment resumes.
        if (mGoogleMap != null)
            mGoogleMap.clear();
        //Close the location cursor before shutting down
        if (mLocationCursor != null) {
            if (!mLocationCursor.isClosed())
                mLocationCursor.close();
        }
        doUnbindService(this);
        super.onStop();
    }

    @Override
    public Loader<Cursor> onCreateLoader(int d, Bundle args){
        long runId = args.getLong(Constants.ARG_RUN_ID, -1);
        //Create a loader than returns a cursor holding all the location data for this Run.
        return new MyLocationListCursorLoader(getActivity(), runId);
    }

    //All the "real work" to update this MapFragment in real time when the run is being tracked is
    //done in onLoadFinished() as new location updates come in.
    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        Log.i(TAG, "In onLoadFinished() for Run #" + mRunId);
        mLocationCursor = (RunDatabaseHelper.LocationCursor) cursor;
        //A cursor returned by a cursor loader supposedly is guaranteed to be open, but I've had
        //crashes on configuration changes claiming we're trying to open an object that's already
        //been closed. Checking here to see if the cursor is open seems to have cured the problem
        if (mLocationCursor.isClosed()) {
            return;
        }
        //Now that we've got a cursor holding all the location data for this run, we can process it.
        if (!mPrepared) {
            Log.i(TAG, "mPrepared is false for Run #" + mRunId + " - preparing map.");
            //If mPrepared is false, the map graphic elements are being newly-
            //created after the user presses the "Map" button, the MapFragment resumes after
            //the user comes back to the map, or the Activity has been destroyed in a configuration
            //change. Invoke the prepareMap() method to display adornments on the map
            //reflecting location data that's been previously recorded.
            prepareMap();
        } else {
            //If mPrepared is true, we've already prepared the map, so we just need to update
            //the Polyline, EndMarker, and mBounds based upon the latest location update. First,
            //move to the last location update.
            updateMap();
        }
    }
    private void updateMap(){
        Log.i(TAG, "mPrepared is true for Run #" + mRunId + ".");
        mLocationCursor.moveToLast();
        mLastLocation = mLocationCursor.getLocation();
        Log.i(TAG, "In onLoadFinished() for Run #" + mRunId +", is mLastLocation null? " + (mLastLocation == null));
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

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        //Stop using the data
        //mLocationCursor.close();
        //mLocationCursor = null;
    }

    //Reload the location data for this run, thus forcing an update of the map display
    private void restartLoader() {
        Bundle args = getArguments();
        if (args != null) {
            long runId = args.getLong(Constants.ARG_RUN_ID, -1);
            if (runId != -1) {
                //LoaderManager lm = getLoaderManager();
                //lm.restartLoader(Constants.LOAD_LOCATION, args, this);
                mLoaderManager.restartLoader(Constants.LOAD_LOCATION, args, this);
            }
        }
    }

    //Method to initialize the map
    private void prepareMap() {
        Log.i(TAG, "Entered prepareMap() for Run #" + mRunId);
        //We can't prepare the map until we actually get a map and a location cursor.
        if (mGoogleMap == null || mLocationCursor == null)
            return;
        Log.i(TAG, "mLocationCursor has " + mLocationCursor.getCount() + " entries.");
        //We need to use the LocationCursor for the map markers because we need time data, which the
        //LatLng objects in mPoints lack.
        mLocationCursor.moveToFirst();
        mStartLocation = mLocationCursor.getLocation();
        if (mBounds == null){
            mBuilder.include(new LatLng(mStartLocation.getLatitude(), mStartLocation.getLongitude()));
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
        if (RunManager.addressBad(getActivity(), snippetAddress)) {
            snippetAddress = RunManager.getAddress(getActivity(), mPoints.get(0));
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
        if (RunManager.addressBad(getActivity(), snippetAddress)) {
            snippetAddress = RunManager.getAddress(getActivity(), mPoints.get(mPoints.size() - 1));
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
        //data if the number of locations in the cursor exceeds the number of locations stored by the
        //RunFragment.
        if (mLocationCursor.getCount() > mPoints.size()){
            Log.i(TAG, "Cursor for Run #" + mRunId + " has " + mLocationCursor.getCount() + " and " +
                    "mPoints has " + mPoints.size() + " elements.");
            //Set the cursor to the first location after the locations already in memory
            mLocationCursor.moveToPosition(mPoints.size());
            LatLng latLng;
            //Iterate over the remaining location points in the cursor, updating mPoints, mPolyline,
            //and mBounds; fix position of mEndMarker when we get to the last entry in the cursor.
            while (!mLocationCursor.isAfterLast()){
                Log.i(TAG, "Entered reinstateMap loop for Run #" + mRunId);
                mLastLocation = mLocationCursor.getLocation();
                latLng = new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude());
                mPoints.add(latLng);
                mBounds = mBounds.including(latLng);
                if (mLocationCursor.isLast()){
                    endMarkerOptions.position(latLng);
                    endMarkerOptions.snippet(RunManager.getAddress(getActivity(), latLng));
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

    /*Set up tracking depending up mViewMode. SHOW_ENTIRE_ROUTE is the default, but mViewMode may
    * be set to another value when the map is recreated. This method is also used when the user
    * affirmatively selects a different View Mode in the OptionsMenu*/
    private void setTrackingMode(){
        CameraUpdate movement;
        LatLng latLng;
        switch (mViewMode){
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
                break;
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

    protected int getTrackingMode(){
        return mViewMode;
    }

    protected long getRunId(){
        return mRunId;
    }

    private static void showErrorDialog(RunMapFragment fragment, int errorCode){
        RunFragment.ErrorDialogFragment dialogFragment = new RunFragment.ErrorDialogFragment();
        Bundle args = new Bundle();
        args.putInt(Constants.ARG_ERROR_CODE, errorCode);
        dialogFragment.setArguments(args);
        dialogFragment.show(fragment.getActivity().getSupportFragmentManager(), "errordialog");
    }

    private static class IncomingHandler extends Handler {

        private final WeakReference<RunMapFragment> mFragment;

        IncomingHandler(RunMapFragment fragment){
            mFragment = new WeakReference<>(fragment);
        }
        @Override
        public void handleMessage(Message msg){

            RunMapFragment fragment = mFragment.get();
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

                }
        }
    }

    private class ResultsReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent){

            String action = intent.getAction();
            switch(action) {
                //If mViewMode is changed for any RunMapFragment, a broadcast is sent to all RunMapFragments
                //running will also change to the same view mode
                case Constants.ACTION_REFRESH_MAPS:
                    mViewMode = mRunManager.mPrefs.getInt(Constants.TRACKING_MODE, Constants.SHOW_ENTIRE_ROUTE);
                    setTrackingMode();
                default:
                    Log.i(TAG, "How'd you get here!?! Not ACTION_REFRESH_MAPS!");
            }

        }
    }
}
