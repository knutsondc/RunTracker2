package com.dknutsonlaw.android.runtracker2;

/**
 * Created by dck on 9/6/15. A Fragment to display the course of a Run in a MapView and update it
 * "live" if the Run is being tracked.
 */

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
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
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
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
import java.util.Locale;

public class RunMapFragment extends Fragment implements LoaderManager.LoaderCallbacks<Cursor> {
    private static final String TAG = "RunMapFragment";

    private final RunManager mRunManager = RunManager.get(getActivity());
    private long mRunId;
    private GoogleMap mGoogleMap;
    private MapView mMapView;
    private LoaderManager mLoaderManager;
    private Messenger mLocationService;
    private final Messenger mMessenger = new Messenger(new IncomingHandler(this));
    private RunDatabaseHelper.LocationCursor mLocationCursor;
    private Polyline mPolyline;
    private ArrayList<LatLng> mPoints;
    private LatLngBounds mBounds;
    private final LatLngBounds.Builder mBuilder = new LatLngBounds.Builder();
    private Marker mEndMarker;
    private TextView mEndDateTextView;
    private TextView mDistanceTextView;
    private TextView mDurationTextView;
    private String mStartDate;
    private Location mStartLocation, mLastLocation = null;
    private double mDistanceTraveled = 0.0;
    private long mDurationMillis = 0;
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
        //If we got a legitimate RunId, start the loader for the location data for that Run
        //Query - why doesn't it work to initiate the loader in onActivityCreated()? That results
        //in a crash upon a configuration change because apparently the cursor is released before
        //the loader gets possession of it.
        /*if (mRunId != -1) {
            LoaderManager lm = getLoaderManager();
            lm.initLoader(Constants.LOAD_LOCATION, args, this);
        }*/

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
        View rootView = inflater.inflate(R.layout.map_activity_fragment, container, false);
        mMapView = (MapView) rootView.findViewById(R.id.mapViewContainer);
        mMapView.onCreate(savedInstanceState);
        mMapView.onResume(); //needed to get map to display immediately

        try{
            MapsInitializer.initialize(getActivity().getApplicationContext());
        } catch (Exception e){
            e.printStackTrace();
        }

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
                        //Need to return "false" so the default action for clicking on a marker will also occur
                        //for the Start Marker and for the End Marker after we've updated its snippet.
                        return false;
                    });
                }
                //Turn off the DisplayHomeAsUp - we might not return to the correct instance of RunFragment
                //noinspection ConstantConditions
                ((AppCompatActivity)getActivity()).getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            }
        });

        TextView runIdTextView = (TextView) rootView.findViewById(R.id.runIdTextView);
        runIdTextView.setText("Run #" + mRunId);
        TextView startDateTextView = (TextView) rootView.findViewById(R.id.startDateTextView);
        ///startDateTextView.setText(getString(R.string.started, mStartDate));
        startDateTextView.setText(getString(R.string.started,
                Constants.DATE_FORMAT.format(mRunManager.getRun(mRunId).getStartDate())));
        Log.i(TAG, "Set Start Date for Run #" + mRunId + " in setupWidgets as " + mRunManager.getRun(mRunId).getStartDate());
        mEndDateTextView = (TextView) rootView.findViewById(R.id.endDateTextView);
        //if (mLastLocation != null) {
            //mEndDateTextView.setText(getString(R.string.ended,
            //        Constants.DATE_FORMAT.format(mLastLocation.getTime())));
            mEndDateTextView.setText(getString(R.string.ended,
                    Constants.DATE_FORMAT.format(mRunManager.getLastLocationForRun(mRunId).getTime())));
            Log.i(TAG, "Set End Date for Run #" + mRunId + " in setupWidgets as " + Constants.DATE_FORMAT.format(mRunManager.getLastLocationForRun(mRunId).getTime()));
        //}
        mDistanceTextView = (TextView) rootView.findViewById(R.id.distanceTextView);
        mDistanceTraveled = mRunManager.getRun(mRunId).getDistance();
        //mDistanceTextView.setText(getString(R.string.distance_traveled_format,
        //        String.format(Locale.US, "%.2f", mDistanceTraveled * Constants.METERS_TO_MILES)));
        mDistanceTextView.setText(getString(R.string.distance_traveled_format,
                String.format(Locale.US, "%.2f", mRunManager.getRun(mRunId).getDistance() * Constants.METERS_TO_MILES)));
        Log.i(TAG, "Set Distance Traveled for Run #" + mRunId + " in setupWidgets as " + String.format(Locale.US, "%.2f", mRunManager.getRun(mRunId).getDistance() * Constants.METERS_TO_MILES));
        mDurationTextView = (TextView) rootView.findViewById(R.id.durationTextView);
        mDurationMillis = mRunManager.getRun(mRunId).getDuration();
        int durationSeconds = (int)mDurationMillis / 1000;
        mDurationTextView.setText(getString(R.string.run_duration_format, Run.formatDuration(durationSeconds)));
        Log.i(TAG, "Set Duration for Run #" + mRunId + " in setupWidgets as " + Run.formatDuration(durationSeconds));
        return rootView;
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
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater){
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.run_map_list_options, menu);
    }
    /*The Options Menu allows the user to specify whether to track the whole route (the default),
     *track the ending point of the route, track the starting point of the route, or turn off
     *tracking. Actually changing the tracking mode is done in the separate method setTrackingMode()
     *so that the last previously selected tracking mode can be used when the map is reopened instead
     *of always starting with the default SHOW_ENTIRE_ROUTE. */
    @Override
    public boolean onOptionsItemSelected(MenuItem item){
        Log.i(TAG, "Entered onOptionsItemSelected for Run #" + mRunId + ".");
        //Select desired map updating mode, then call setTrackingMode() to act on it. We use a
        //separate function for setTrackingMode() so that it can be invoked when the fragment
        //restarts with the last previous tracking mode still in effect, rather than going with the
        //default of SHOW_ENTIRE_ROUTE
        switch (item.getItemId()){
            case R.id.show_entire_route_menu_item:
                Log.i(TAG, "Entering show_entire_route_menu_item, getCameraPosition().zoom: " +
                        mGoogleMap.getCameraPosition().zoom);
                mViewMode = Constants.SHOW_ENTIRE_ROUTE;
                setTrackingMode();
                Log.i(TAG, "SHOW_ENTIRE_ROUTE getCameraPosition.zoom: " + mGoogleMap.getCameraPosition().zoom);
                return true;
            case R.id.track_end_point_menu_item:
                mViewMode = Constants.FOLLOW_END_POINT;
                setTrackingMode();
                return true;
            case R.id.track_start_point_menu_item:
                mViewMode = Constants.FOLLOW_STARTING_POINT;
                setTrackingMode();
                return true;
            case R.id.tracking_off_menu_item:
                mViewMode = Constants.NO_UPDATES;
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
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
        mPrepared = false;
    }

    @Override
    public void onPause(){
        mMapView.onPause();
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
        return new MyLocationListCursorLoader(getActivity(),
                Constants.URI_TABLE_LOCATION, runId);
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
        if (mLocationCursor.isClosed()){
            return;
        }
        //Now that we've got a cursor holding all the location data for this run, we can process it.
        if (!mPrepared) {
            Log.i(TAG, "mPrepared is false for Run #" + mRunId + " - preparing map.");
            //If mNeedToPrepare is true, the map graphic elements are being newly-
            //created after the user presses the "Map" button, the MapFragment resumes after
            //the user comes back to the map, or the Activity has been destroyed in a configuration
            //change. Invoke the prepareMap() method to display adornments on the map
            //reflecting location data that's been previously recorded.
            prepareMap();
        } else {
            Log.i(TAG, "mPrepared is true for Run #" + mRunId + ".");
            //If mNeedToPrepare is false, we've already prepared the map, so we just need to update
            //the Polyline, EndMarker, and mBounds based upon the latest location update. First,
            //move to the last location update.
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
                String endDate = Constants.DATE_FORMAT.format(mLastLocation.getTime());
                Log.i(TAG, "In onLoadFinished() for Run #" + mRunId + ", endDate is " + endDate);
                //Update mPolyline's set of points
                if (latLng != mPoints.get(mPoints.size() - 1)) {
                    Log.i(TAG, "In onLoadFinished() for Run #" + mRunId +", adding latLng to mPoints");
                    mPoints.add(latLng);
                }
                Log.i(TAG, "In onLoadFinished() for Run #" + mRunId + ", is mPoints null?" + (mPoints == null));
                Log.i(TAG, "In onLoadFinished() for Run #" + mRunId + " mPoints.size() is " + mPoints.size());
                Log.i(TAG, "In onLoadFinished() for Run #" + mRunId + ", is mPolyline null? " + (mPolyline == null));
                //Update mPolyline with the updated set of points.
                Log.i(TAG, "In onLoadFinished() for Run #" + mRunId + " mPolyline is null, so we're creating it here.");
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
                //Now update the TextViews
                Log.i(TAG, "In onLoadFinished() for Run #" + mRunId + ", is mEndDateTextView null? " + (mEndDateTextView == null));
                mEndDateTextView.setText(getString(R.string.ended, endDate));
                mDistanceTraveled = RunManager.get(getActivity()).getRun(mRunId).getDistance();
                mDistanceTextView.setText(getString(R.string.distance_traveled_format,
                        String.format(Locale.US, "%.2f", mDistanceTraveled * Constants.METERS_TO_MILES)));
                mDurationMillis = RunManager.get(getActivity()).getRun(mRunId).getDuration();
                int durationSeconds = (int) mDurationMillis / 1000;
                mDurationTextView.setText(getString(R.string.run_duration_format,
                        Run.formatDuration(durationSeconds)));
            }
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
        mStartDate = Constants.DATE_FORMAT.format(mStartLocation.getTime());
        Log.i(TAG, "mStartDate for Run #" + mRunId + " is " + mStartDate);
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
                .snippet(mStartDate + "\n" + snippetAddress)
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
        //data if the number of locations in the cursor exceeds the number of locations stored by the
        //RunFragment.

        Log.i(TAG, "In prepareMap() for Run #" + mRunId + " is mPoints null? " + (mPoints == null));
        Log.i(TAG, "In prepareMap() mPoints.size() is " + mPoints.size());
        Log.i(TAG, "In prepareMap(), is mBounds null? " + (mBounds == null));
        Log.i(TAG, "In prepareMap(), mBounds.toString() is " + mBounds.toString());
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

    /* Instantiate the TextViews displaying the Start Date, End Date, Distance, and Duration that
   * were defined in the *Activity's* xml layout file so they can "float" atop the View of the
   * MapFragment itself. The java code for them is here because their content is derived from
   * information developed in the MapFragment. The startDateTextview can be a local variable
   * because its content will never change; the other TextViews need to be member variables because
   * their content will get updated in the loader's onLoadFinished() method as each new location
   * comes in during live update. Also set up an overlay on the map for this run's prerecorded
   * locations.
   */

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
}
