package com.dknutsonlaw.android.runtracker2;

/**
 * Created by dck on 9/6/15. A Fragment to display the course of a Run in a MapView and update it
 * "live" if the Run is being tracked.
 */

import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Typeface;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;

import com.google.android.gms.maps.model.PolylineOptions;

import java.util.ArrayList;
import java.util.List;

public class RunMapFragment extends SupportMapFragment implements LoaderManager.LoaderCallbacks<Cursor>{
    private static final String TAG = "RunMapFragment";

    private final RunManager mRunManager = RunManager.get(getActivity());
    private final BoundsHolder mBoundsHolder = BoundsHolder.get(getActivity());
    private final PointsHolder mPointsHolder = PointsHolder.get(getActivity());
    private long mRunId;
    //Instance variable to hold a reference to the fragment we can use to retrieve a retained
    //instance upon configuration change.
    private static RunMapFragment sRunMapFragment;
    private GoogleMap mGoogleMap;
    private RunDatabaseHelper.LocationCursor mLocationCursor;
    private PolylineOptions mLine;
    private Polyline mPolyline;
    private List<LatLng> mPoints;
    private LatLngBounds mBounds;
    private MarkerOptions mStartMarkerOptions, mEndMarkerOptions;
    private Marker mEndMarker;
    private TextView mStartDateTextView;
    private TextView mEndDateTextView;
    private TextView mDistanceTextView;
    private TextView mDurationTextView;
    private String mStartDate;
    private Location mStartLocation, mLastLocation = null;
    private double mDistanceTraveled = 0.0;
    private long mDurationMillis = 0;
    //private static boolean mCreated = false;
    private static boolean mNeedToPrepare = true;
    //Set default map tracking mode
    private int mViewMode = Constants.SHOW_ENTIRE_ROUTE;
    //Holder for the current zoom value for use when map is reconstituted
    private float mZoomLevel = 17.0f;

    public RunMapFragment() {
        // Required empty public constructor
    }

    public static RunMapFragment newInstance(long runId) {

        if(sRunMapFragment != null && sRunMapFragment.mRunId == runId) {
            Log.i(TAG, "Returned old RunMapFragment");
            //mCreated = true;
            return sRunMapFragment;
        } else {
            Log.i(TAG, "Creating new RunMapFragment");
            Bundle args = new Bundle();
            args.putLong(Constants.ARG_RUN_ID, runId);
            sRunMapFragment = new RunMapFragment();
            sRunMapFragment.setArguments(args);
            sRunMapFragment.setRetainInstance(true);
            return sRunMapFragment;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //Retain this fragment across configuration changes so that we don't lose the threads
        //doing location and run updates.
        setRetainInstance(true);
        //Make sure the Options Menu gets created!
        setHasOptionsMenu(true);
        //Turn off the DisplayHomeAsUp - we might not return to the correct instance of RunFragment
        //noinspection ConstantConditions
        ((AppCompatActivity)getActivity()).getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        //Check for a Run ID as an argument, and find the run
        Bundle args = getArguments();
        if (args != null) {
            mRunId = args.getLong(Constants.ARG_RUN_ID, -1);
            //If we got a legitimate RunId, start the loader for the location data for that Run
            if (mRunId != -1) {
                LoaderManager lm = getLoaderManager();
                lm.initLoader(Constants.LOAD_LOCATION, args, this);
            }
            mPoints = mPointsHolder.retrieve(mRunId);
            mBounds = mBoundsHolder.retrieve(mRunId);
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        //Developer docs say call getMapAsync() in onActivityCreated() because the map won't be
        //returned non-null until after onCreateView() returns.
        //First check to see if we already have a map we can use - do we have a non-null fragment?

        sRunMapFragment.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(GoogleMap googleMap) {
                //Stash a reference to the GoogleMap
                mGoogleMap = googleMap;
                if (mGoogleMap != null) {
                    Log.i(TAG, "In on ActivityCreated retrieved existing map.");
                }
            }
        });

        //If we can't get a valid map from a retained fragment, get a new one from Google.
        if (mGoogleMap == null) {
            getMapAsync(new OnMapReadyCallback() {
                @Override
                public void onMapReady(GoogleMap googleMap) {
                    //Stash a reference to the GoogleMap
                    mGoogleMap = googleMap;
                    Log.i(TAG, "In on ActivityCreated, creating new map.");
                }
            });
        } else {
            Log.i(TAG, "Using old mGoogleMap");
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater){
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.run_map_list_options, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item){
        CameraUpdate movement;
        LatLng latLng;
        //Select desired map updating mode.
        switch (item.getItemId()){
            case R.id.show_entire_route_menu_item:
                //Set the camera at the center of the area defined by mBounds, again leaving a 110
                //pixel buffer zone at the edges of the screen. The map is already created, so we
                //don't need to tell the CameraUpdate about the display size. The zoom level is
                //set according to the size of the area that needs to be displayed. We don't save
                //the zoom level here, so that any switch to FOLLOW_END_POINT or FOLLOW_START_POINT
                //mode will use the zoom level that was last used for one of those modes.
                mViewMode = Constants.SHOW_ENTIRE_ROUTE;
                movement = CameraUpdateFactory.newLatLngBounds(mBounds, 110);
                mGoogleMap.animateCamera(movement);
                Log.i(TAG, "SHOW_ENTIRE_ROUTE mZoomLevel: " + mGoogleMap.getCameraPosition().zoom);
                return true;
            case R.id.track_end_point_menu_item:
                mViewMode = Constants.FOLLOW_END_POINT;
                //Fix the camera on the last location in the run at the zoom level that was last used
                //for this or the FOLLOW_START_POINT view mode. Also make sure the End Marker is
                //placed at the same location so that the End Marker stays in sync with mLastLocation..
                latLng = new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude());
                //Make sure the zoom level for this mode is at least 17.0
                if (mZoomLevel >= 17.0f) {
                    movement = CameraUpdateFactory.newLatLngZoom(latLng, mZoomLevel);
                } else {
                    movement = CameraUpdateFactory.newLatLngZoom(latLng, 17.0f);
                }
                mGoogleMap.animateCamera(movement);
                Log.i(TAG, "FOLLOW_END_POINT mZoomLevel: " + mGoogleMap.getCameraPosition().zoom);
                //Update the position of the EndMarker
                mEndMarker.setPosition(latLng);
                return true;
            case R.id.track_start_point_menu_item:
                mViewMode = Constants.FOLLOW_STARTING_POINT;
                //Fix the camera on the first location in the run at the zoom level that was last used
                //for this or the FOLLOW_END_POINT view mode.
                latLng = new LatLng(mStartLocation.getLatitude(), mStartLocation.getLongitude());
                //Make sure the zoom level for this mode is at least 17.0
                if (mZoomLevel >= 17.0f) {
                    movement = CameraUpdateFactory.newLatLngZoom(latLng, mZoomLevel);
                } else {
                    movement = CameraUpdateFactory.newLatLngZoom(latLng, 17.0f);
                }
                mGoogleMap.animateCamera(movement);
                Log.i(TAG, "FOLLOW_STARTING_POINT mZoomLLevel: " + mGoogleMap.getCameraPosition().zoom);
                //No need to set position of starting marker - it never moves!
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
        Log.i(TAG, "onResume() called.");
        super.onResume();
        mNeedToPrepare = true;
        restartLoader();
    }

    @Override
    public void onStop() {
        //Clear map adornments to ensure that duplicate, stale end markers won't appear on the map
        //when the MapFragment resumes.
        if (mGoogleMap != null)
            mGoogleMap.clear();
        //Close the location cursor before shutting down
        if (!mLocationCursor.isClosed())
            mLocationCursor.close();
        super.onStop();
    }

    @Override
    public Loader<Cursor> onCreateLoader(int d, Bundle args){
        long runId = args.getLong(Constants.ARG_RUN_ID, -1);
        //Create a loader than returns a cursor holding all the location data for this Run.
        return new MyLocationListCursorLoader(getActivity(),
                Constants.URI_TABLE_LOCATION, runId);
    }

    //All the "real work" to update this MapFragment is done in onLoadFinished as new location updates
    //come in.
    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        Log.i(TAG, "In onLoadFinished()");
        mLocationCursor = (RunDatabaseHelper.LocationCursor) cursor;
        //Now that we've got a cursor holding all the location data for this run, we can process it.
        if (mNeedToPrepare) {

            Log.i(TAG, "mNeedToPrepare is true - preparing map.");

            //If mNeedToPrepare is true, the map graphic elements are being newly-
            //created after the user presses the "Map" button, the MapFragment resumes after
            //the user comes back to the map, or the Activity has been destroyed in a configuration
            ///change. Invoke the prepareMap() method to display adornments on the map
            //reflecting location data that's been previously recorded.
            prepareMap();
        } else {
            //If mNeedToPrepare is false, we've already prepared the map, so we just need to update
            //the Polyline, EndMarker, and mBounds based upon the latest location update. First,
            //move to the last location update.
            mLocationCursor.moveToLast();
            mLastLocation = mLocationCursor.getLocation();
            if (mLastLocation != null) {
                //Remember to check whether mLastLocation is null which it could be if this
                //is a newly-opened run and no location data have been recorded to the database
                //before the map got opened.
                LatLng latLng = new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude());
                //Get the time of the latest update into the correct format
                String endDate = Constants.DATE_FORMAT.format(mLastLocation.getTime());
                //Update mPolyline's set of points
                mPoints.add(latLng);
                //Update mPolyline with the updated set of points.
                mPolyline.setPoints(mPoints);
                //Update the position of mEndMarker
                //To avoid the overhead of looking up an address for every location update, we
                //don't update the snippet until the user clicks on it.
                if (mEndMarker != null) {
                    mEndMarker.setPosition(latLng);
                }
                //Make sure the bounds for the map are updated to include the new location
                if (mBounds != null) {
                    mBounds = mBounds.including(latLng);
                }
                //Update the map's camera position depending upon the desired map updating mode and
                //the last reported location. We shouldn't have to specify the display
                //size again because the map has already been initialized and laid out.
                CameraUpdate movement = updateCamera(mViewMode, latLng);
                //If the user has chosen to turn tracking off, updateCamera() will return null and
                //the Camera will just stay where it was before.
                if (movement != null)
                    //Animate the camera for the cool effect...
                    mGoogleMap.animateCamera(movement);
                //Now update the TextViews
                //Rather than incur the cost of additional database accesses, just update the
                //Textviews from the information calculated from the last location update
                mEndDateTextView.setText(getString(R.string.ended, endDate));
                mDistanceTraveled = RunManager.get(getActivity()).getRun(mRunId).getDistance();
                mDistanceTextView.setText(getString(R.string.distance_traveled_format,
                        String.format("%.2f", mDistanceTraveled * Constants.METERS_TO_MILES)));
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
        mLocationCursor.close();
        mLocationCursor = null;
        //mIsLoading = false;
    }

    //Reload the location data for this run, thus forcing an update of the map display
    private void restartLoader() {
        Bundle args = getArguments();
        if (args != null) {
            long runId = args.getLong(Constants.ARG_RUN_ID, -1);
            if (runId != -1) {
                LoaderManager lm = getLoaderManager();
                lm.restartLoader(Constants.LOAD_LOCATION, args, this);
                //Log.i(TAG, "restartLoader for LOAD_LOCATIONS called in restartLoader()");
            }
        }
    }

    //If were reusing an old map, we already have data in memory that can be used to reconstitute
    //some or all of the graphic elements of the map, thereby avoiding the cost of reloading all that
    //data from the database. This is called from prepareMap(), which is called when the map view
    //needs to be constructed from scratch upon initial opening, configuration change, etc.
    private void reinstateMap() {
        Log.i(TAG, "Entered reinstateMap()");
        mLine = new PolylineOptions();
        //mPoints is the List of LatLngs used to create mPolyline. If mPoints represents all the
        //location data we've collected, use the last element of mPoints to set the position of
        //mEndMarker.
        mLine.addAll(mPoints);
        //LatLng latLng = mPoints.get(mPoints.size() - 1);
        //Create mPolyline using the existing mPoints - if additional location points have been
        //recorded since the map was last created, we will add those points to mPoints and mPolyline
        //rather than going to the database for all the location data
        mPolyline = mGoogleMap.addPolyline(mLine);
        //The data needed to set up the StartMarker never change after it's initially determined, so
        //we can just reuse the existing mStartMarkerOptions to recreate the Marker.
        mLocationCursor.moveToFirst();
        mStartLocation = mLocationCursor.getLocation();
        mStartDate = Constants.DATE_FORMAT.format(mStartLocation.getTime());
        Log.i(TAG, "mStartDate is " + mStartDate);
        Resources r = getActivity().getResources();
        //Get the address where we started the Run from the geocoder
        String snippetAddress = mRunManager.getAddress(mPoints.get(0));
        //Now create a marker for the starting point and put it on the map.
        //The starting marker doesn't need to be updated, so we don't even need to keep
        //the return value from the call to mGoogleMap.addMarker().
        mStartMarkerOptions = new MarkerOptions()
                .position(mPoints.get(0))
                .title(r.getString(R.string.run_start))
                .snippet(mStartDate + "\n" + snippetAddress)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
                .flat(false);
        mGoogleMap.addMarker(mStartMarkerOptions);
        mLocationCursor.moveToLast();
        mLastLocation = mLocationCursor.getLocation();
        String endDate = Constants.DATE_FORMAT.format(mLastLocation.getTime());
        Log.i(TAG, "endDate is " + endDate);
        LatLng latLng = new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude());
        snippetAddress = mRunManager.getAddress(latLng);
        //We use the default red icon for the EndMarker
        mEndMarkerOptions = new MarkerOptions()
                .position(latLng)
                .title(r.getString(R.string.run_finish))
                .snippet(endDate + "\n" + snippetAddress)
                .flat(false);
        //mEndMarker = mGoogleMap.addMarker(mEndMarkerOptions);
        //Update mPoints, mPolyline, mBounds and the position of mEndMarker with additional location
        //data if the number of locations in the cursor exceeds the number of locations already in
        //memory.
        if (mLocationCursor.getCount() > mPoints.size()){
            Log.i(TAG, "Cursor is larger than existing mPoints");
            //Set the cursor to the first location after the locations already in memory
            mLocationCursor.moveToPosition(mPoints.size());
            //LatLng latLng;
            //Iterate over the remaining location points in the cursor, updating mPoints, mPolyline,
            //and mBounds; fix position of mEndMarker when we get to the last entry in the cursor.
            while (!mLocationCursor.isAfterLast()){
                Log.i(TAG, "Entered reinstateMap loop");
                mLastLocation = mLocationCursor.getLocation();
                latLng = new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude());
                mPoints.add(latLng);
                mBounds = mBounds.including(latLng);
                if (mLocationCursor.isLast()){
                    mEndMarkerOptions.position(latLng);
                }
                mLocationCursor.moveToNext();
            }
            mPolyline.setPoints(mPoints);
        } else {
            Log.i(TAG, "Cursor is equal in size to existing mPoints");
        }
        //Now that we've fixed the position of mEndMarker, add it to the map.
        mEndMarker = mGoogleMap.addMarker(mEndMarkerOptions);
        //Set the camera according to the View Mode selected and the zoom level last set for the
        //FOLLOW_END_POINT and FOLLOW_START_POINT modes.
        CameraUpdate movement;
        switch (mViewMode){
            case Constants.SHOW_ENTIRE_ROUTE:
                //Set the camera over the center of a map Bounds large enough to take in all the
                //points in mPolyline. First, get the size of the display
                Display display = getActivity().getWindowManager().getDefaultDisplay();
                Point point = new Point();
                display.getSize(point);
                //Place the Bounds so that it takes up the whole screen with a 110 pixel pad at the
                //edges. The map hasn't been rendered yet, so we need to tell it about the display
                //size.
                movement = CameraUpdateFactory.newLatLngBounds(mBounds,
                        point.x, point.y, 110);
                //Move the camera to set the map within the bounds we set.
                mGoogleMap.animateCamera(movement);
                //The zoom level is set at the level needed to fit the Bounds in the display area
                //set aside for it. When view mode switches to FOLLOW_END_POINT, the zoom level is
                //set to mZoomLevel, which may have been modified from its original value of 17.0 by
                //the zoom buttons.
                Log.i(TAG, "In ReinstateMap, ViewMode SHOW_ENTIRE_ROUTE mZoomLevel is " + mZoomLevel);
                break;
            case Constants.FOLLOW_END_POINT:
                //Center the camera over the last point in mPoints and mPolyline at the zoom level
                //last fixed by the zoom controls.
                movement = CameraUpdateFactory.newLatLngZoom(latLng, mZoomLevel);
                mGoogleMap.animateCamera(movement);
                Log.i(TAG, "In ReinstateMap, ViewMode FOLLOW_END_POINT mZoomLevel is " + mZoomLevel);
                break;
            case Constants.FOLLOW_STARTING_POINT:
                latLng = new LatLng(mStartLocation.getLatitude(), mStartLocation.getLongitude());
                movement = CameraUpdateFactory.newLatLngZoom(latLng, mZoomLevel);
                mGoogleMap.animateCamera(movement);
                Log.i(TAG, "In ReinstateMap, ViewMode FOLLOW_STARTING_POINT mZoomLevel is " + mZoomLevel);
            case Constants.NO_UPDATES:
                break;
            default:
                break;
        }
        setupWidgets();
    }

    //Method to initialize the map when we have to construct the map view from scratch upon initial
    //opening of the MapFragment, configuration change, etc.
    private void prepareMap() {
        Log.i(TAG, "Entered prepareMap()");

        //We can't prepare the map until we actually get a map and a location cursor.
        if (mGoogleMap == null || mLocationCursor == null)
            return;
        //Set up an overlay on the map for this run's prerecorded locations
        //We need a custom InfoWindowAdapter to allow multiline text snippets
        //in markers.
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
        //If we already have an instance of PolylineOptions, that means we already have in memory
        //at least some of the data needed to reconstitute the map, so we need merely "reinstate" the
        //map and update the data already in memory with location data subsequently recorded to the
        //database..
        //if (mLine != null) {
        if (mPoints != null && mPoints.size() > 0 && mBounds != null){
            reinstateMap();
            return;
        }
        //Create a polyline that will contain all the location update points.
        mLine = new PolylineOptions();
        //Also create a LatLngBounds so you can zoom to fit
        LatLngBounds.Builder latLngBuilder = new LatLngBounds.Builder();
        //Iterate over all the locations to build a Polyline, create a Bounds, and place the Markers
        //appropriately.
        mLocationCursor.moveToFirst();
        Resources r = getResources();
        LatLng latLng;
        while (!mLocationCursor.isAfterLast()) {
            Log.i(TAG, "Entered mLocationCursor loop in prepareMap()");
            //Fetch the location from the cursor
            Location loc = mLocationCursor.getLocation();
            //Construct a LatLng object to add to the list of them used in creating the map's
            //camera bounds
            latLng = new LatLng(loc.getLatitude(), loc.getLongitude());

            //If this is the first location, add a marker for it
            if (mLocationCursor.isFirst()) {
                Log.i(TAG, "Entered mLocationCursor.isFirst() if block in prepareMap");
                //The Start Date could also be retrieved from the database Run entry's StartDate
                //field, but we already have the same data here in the cursor - no need for another
                //database access
                mStartLocation = loc;
                mStartDate = Constants.DATE_FORMAT.format(loc.getTime());
                Log.i(TAG, "mStartDate is " + mStartDate);
                //Get the address where we started the Run from the geocoder
                String snippetAddress = mRunManager.getAddress(latLng);
                //Now create a marker for the starting point and put it on the map.
                //The starting marker doesn't need to be updated, so we don't even need to keep
                //the return value from the call to mGoogleMap.addMarker().
                mStartMarkerOptions = new MarkerOptions()
                        .position(latLng)
                        .title(r.getString(R.string.run_start))
                        .snippet(mStartDate + "\n" + snippetAddress)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
                        .flat(false);
                //We never change the StartMarker's properties, so no need to keep a handle for it.
                mGoogleMap.addMarker(mStartMarkerOptions);
            //Create a marker for the last point in the run.
            } else if (mLocationCursor.isLast()) {
                Log.i(TAG, "Entered mLocationCursor.isLast() block of prepareMap()");
                //If this is the last location and not also the first, add a marker
                //mEndMarker is an instance variable so we can update it live as we're moving.
                String endDate = Constants.DATE_FORMAT.format(loc.getTime());
                Log.i(TAG, "endDate is " + endDate);
                mLastLocation = loc;
                String snippetAddress = mRunManager.getAddress(latLng);
                //We use the default red icon for the EndMarker
                mEndMarkerOptions = new MarkerOptions()
                        .position(latLng)
                        .title(r.getString(R.string.run_finish))
                        .snippet(endDate + "\n" + snippetAddress)
                        .flat(false);
                mEndMarker = mGoogleMap.addMarker(mEndMarkerOptions);
            }
            //Update the data for the polyline and the map bounds with the LatLng we created from
            //this location
            mLine.add(latLng);
            latLngBuilder.include(latLng);
            mLocationCursor.moveToNext();
        }
        //Now that we've summed up all the data needed to create the polyline and the map bounds,
        //construct them, add them to the map, and initialize instance variables for them to
        //make them available for live update and for reuse when the map is reused in a subsequent
        //instance of the underlying Activity.
        mPolyline = mGoogleMap.addPolyline(mLine);
        //Initialize the list of LatLngs to use for updating or recreating the Polyline.
        mPoints = mPolyline.getPoints();
        //Make the map zoom to show the entire track, with some padding
        //Use the size of the current display in pixels to define a bounding box
        Display display = getActivity().getWindowManager().getDefaultDisplay();
        Point point = new Point();
        display.getSize(point);
        mBounds = latLngBuilder.build();
        //Construct a movement instruction for the map camera based upon the bounding box we set.
        //with 110 pixels of padding, enough to ensure that markers at the top of the map will be
        //displayed.
        CameraUpdate movement = CameraUpdateFactory.newLatLngBounds(mBounds,
                point.x, point.y, 110);
        //Move the camera to set the map within the bounds we set.
        mGoogleMap.animateCamera(movement);
        //Finally, initialize the widgets that appear on this fragment.
        setupWidgets();
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
    private void setupWidgets() {
        //Rather than define our own zoom controls, just enable the UiSettings' zoom controls and
        //listen for changes in CameraPosition to update mZoomLevel
        mGoogleMap.getUiSettings().setZoomControlsEnabled(true);
        mGoogleMap.getUiSettings().setZoomGesturesEnabled(true);
        //We need a custom InfoWindowAdapter to allow multiline text snippets in markers.
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
                //Define TextView with desired text attributes for the marker's Snippet. Using a
                //TextView for this purpose allows use of multi-line Snippets.
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
        //Listen for changes in CameraPosition and update mZoomLevel accordingly.
        mGoogleMap.setOnCameraChangeListener(new GoogleMap.OnCameraChangeListener() {
            @Override
            public void onCameraChange(CameraPosition cameraPosition) {
                if (mZoomLevel != cameraPosition.zoom){
                    mZoomLevel = cameraPosition.zoom;
                }
            }
        });

        mStartDateTextView = (TextView) getActivity().findViewById(R.id.startDateTextView);
        mStartDateTextView.setText(getString(R.string.started, mStartDate));
        mEndDateTextView = (TextView) getActivity().findViewById(R.id.endDateTextView);
        if (mLastLocation != null) {
            mEndDateTextView.setText(getString(R.string.ended,
                    Constants.DATE_FORMAT.format(mLastLocation.getTime())));
        }
        mDistanceTextView = (TextView) getActivity().findViewById(R.id.distanceTextView);
        mDistanceTraveled = RunManager.get(getActivity()).getRun(mRunId).getDistance();
        mDistanceTextView.setText(getString(R.string.distance_traveled_format,
                String.format("%.2f", mDistanceTraveled * Constants.METERS_TO_MILES)));
        mDurationTextView = (TextView) getActivity().findViewById(R.id.durationTextView);
        mDurationMillis = RunManager.get(getActivity()).getRun(mRunId).getDuration();
        int durationSeconds = (int)mDurationMillis / 1000;
        //Run.formatDuration is a static method, so we can call it here without loading from the
        //database the run instance itself. We do need the runId, though, to retrieve the location
        //data.
        mDurationTextView.setText(getString(R.string.run_duration_format, Run.formatDuration(durationSeconds)));

        //Set up a listener for when the user clicks on the End Marker. We update its snippet
        //only when the user clicks on it to avoid the overhead of updating the EndAddress on
        //every location update. The Start Marker's data never changes, so the listener can ignore
        //clicks on it and simply allow the default behavior to occur.
        mGoogleMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {
                if (marker.equals(mEndMarker)) {
                    Resources r = getResources();
                    String endDate = "";
                    if (mRunManager.getLastLocationForRun(mRunId) != null) {
                        endDate = Constants.DATE_FORMAT.format(
                                mRunManager.getLastLocationForRun(mRunId).getTime());
                    }
                    String snippetAddress = mRunManager.getAddress(marker.getPosition());
                    marker.setSnippet(endDate + "\n" + snippetAddress);
                }
                //Need to return "false" so the default action for clicking on a marker will also occur
                //for the Start Marker and for the End Marker after we've updated its snippet.
                return false;
            }
        });
        //The graphic elements of the map display have now all been configured, so clear the
        //mNeedToPrepare flag so that succeeding calls to onLoadFinished will merely update them as
        //new location data comes in.
        mNeedToPrepare = false;
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
                Log.i(TAG, "SHOW_ENTIRE_ROUTE mZoomLevel: " + mGoogleMap.getCameraPosition().zoom);
                break;
            }
            case Constants.FOLLOW_END_POINT: {
                //To track the end point of the Run, move the camera to the new end point at the
                //zoom level last used for this mode or FOLLOW_START_POINT mode.
                Log.i(TAG, "FOLLOW_END_POINT mZoomLevel: " + mZoomLevel);
                cameraUpdate = CameraUpdateFactory.newLatLngZoom(latLng, mZoomLevel);
                break;
            }
            case Constants.FOLLOW_STARTING_POINT: {
                //To center on the start point of the Run, move the camera to the starting point at
                //the zoom level last set for this mode or FOLLOW_END_POINT mode
                LatLng startLatLng = new LatLng(mStartLocation.getLatitude(), mStartLocation.getLongitude());
                cameraUpdate = CameraUpdateFactory.newLatLngZoom(startLatLng, mZoomLevel);
                Log.i(TAG, "FOLLOW_STARTING_POINT mZoomLevel: " + mZoomLevel);
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
}
