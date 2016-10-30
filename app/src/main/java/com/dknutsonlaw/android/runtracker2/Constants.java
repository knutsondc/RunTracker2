package com.dknutsonlaw.android.runtracker2;

import android.net.Uri;

import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * Created by dck on 11/4/15.
 *
 * Constant values used throughout the program, arranged alphabetically.
 */
final class Constants {
    static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("E, MMM dd, yyyy, hh:mm:ss a z", Locale.US);

    //Conversion factors to convert meters (the unit by which the Android Location API calculates
    //distances) into the units we wish to display to the user
    static final double METERS_TO_FEET = 3.28084;
    static final double METERS_TO_MILES = .0006214;

    //Identifies member of array of ints reporting results of attempt to insert a location signifying
    //whether the location was too far from last previous location to be considered a continuation of
    //the same run.
    static final int CONTINUATION_LIMIT_RESULT = 2;
    //Error result when attempting to delete a run or a location
    static final int DELETION_ERROR = -1;
    //MapView Menu selection items in RunMapFragment
    static final int FOLLOW_END_POINT = 1;
    static final int FOLLOW_STARTING_POINT = 2;
    //Sort direction upon restarting a RunRecyclerListFragment or RunPagerActivity
    static final int KEEP_EXISTING_SORT = -1;
    //Identifiers for the two different types of loaders we use in RunFragment
    static final int LOAD_LOCATION = 1;
    static final int LOAD_RUN = 0;
    static final int LOCATION_CHECK = 8000;
    //Label for member of array of ints used to report results of deletion operations
    static final int LOCATION_DELETIONS = 0;
    ///Label for result of location insertion operation
    static final int LOCATION_INSERTION_RESULT = 0;
    //Labels for Messages between UI Fragments and BackgroundLocationService
    static final int MESSAGE_GOOGLEAPICLIENT_CONNECTION_SUSPENDED = 0;
    static final int MESSAGE_GOOGLEAPICLIENT_CONNECTION_FAILED = 1;
    static final int MESSAGE_TRY_GOOGLEAPICLIENT_RECONNECTION = 2;
    static final int MESSAGE_PLAY_SERVICES_RESOLUTION_REQUEST = 3;
    static final int MESSAGE_PLAY_SERVICES_ERROR_DIALOG_REQUEST = 4;
    static final int MESSAGE_LOCATION_SETTINGS_RESOLUTION_NEEDED = 5;
    static final int MESSAGE_LOCATION_SETTINGS_NOT_AVAILABLE = 6;
    static final int MESSAGE_LOCATION_SETTINGS_RESOLUTION_SUCCEEDED = 7;
    static final int MESSAGE_LOCATION_SETTINGS_RESOLUTION_FAILED = 8;
    static final int MESSAGE_PERMISSION_REQUEST_NEEDED = 9;
    static final int MESSAGE_PERMISSION_REQUEST_SUCCEEDED = 10;
    static final int MESSAGE_PERMISSION_REQUEST_CANCELED = 11;
    static final int MESSAGE_START_LOCATION_UPDATES = 12;
    static final int MESSAGE_LOCATION_UPDATES_STARTED = 13;
    static final int MESSAGE_STOP_LOCATION_UPDATES = 14;
    static final int MESSAGE_LOCATION_UPDATES_STOPPED = 15;
    static final int MESSAGE_REGISTER_CLIENT = 16;
    static final int MESSENGER_RUNFRAGMENT = 0;
    static final int MESSENGER_RECYCLERFRAGMENT = 1;
    static final int MESSENGER_RUNMAPFRAGMENT = 2;
    static final int MESSENGER_RUNPAGERACTIVITY = 3;
    static final int MESSENGER_RUNMAPPAGERACTIVITY = 4;
    //MapView Menu selection item in RunMapFragment
    static final int NO_UPDATES = 3;
    //Label for BackgroundLocationService to tell the UI Fragment that Location Permissions needed
    static final int REQUEST_LOCATION_PERMISSIONS = 1;
    //Label for member of array of ints used to report results of deletion operation
    static final int RUN_DELETIONS = 1;
    //Label for loader used in RunRecyclerListFragment
    static final int RUN_LIST_LOADER = 0;
    //Label for member of array of ints used to report results of update operations
    static final int RUN_UPDATE_RESULT = 1;
    //MapView Menu selection item in RunMapFragment
    static final int SHOW_ENTIRE_ROUTE = 0;
    //Sort order values used in RunRecyclerListFragment and RunPagerActivity
    static final int SORT_BY_DATE_ASC = 0;
    static final int SORT_BY_DATE_DESC = 1;
    static final int SORT_BY_DISTANCE_ASC = 2;
    static final int SORT_BY_DISTANCE_DESC = 3;
    static final int SORT_BY_DURATION_ASC = 4;
    static final int SORT_BY_DURATION_DESC = 5;
    //Identifier of version of db schema
    static final int VERSION = 1;

    //Values preventing insertion of locations that are too far away from the last previous location
    //to be considered a "continuation" of a run
    static final long CONTINUATION_DISTANCE_LIMIT = 100; //100 meters
    static final long CONTINUATION_TIME_LIMIT = 30000; //30 seconds

    //Labels used in creating Intents used to invoke TrackingLocationIntentService and to report the
    //results of the operations to UI elements
    static final String ACTION_ATTEMPTED =
            "com.dknutsonlaw.android.runtracker.action.attempted";
    static final String ACTION_CHECK_END_ADDRESS =
            "com.dknutsonlaw.android.runtracker.action.check.end.address";
    static final String ACTION_CHECK_START_ADDRESS =
            "com.dknutsonlaw.android.runtracker.action.check.start.address";
    static final String ACTION_DELETE_RUN =
            "com.dknutsonlaw.android.runtracker.action.delete.run";
    static final String ACTION_DELETE_RUNS =
            "com.dknutsonlaw.android.runtracker.action.delete.runs";
    static final String ACTION_INSERT_LOCATION =
            "com.dknutsonlaw.android.runtracker.action.insert.location";
    static final String ACTION_INSERT_RUN =
            "com.dknutsonlaw.android.runtracker.action.insert.run";
    static final String ACTION_LOCATION =
            "com.dknutsonlaw.android.runtracker2.ACTION_LOCATION";
    static final String ACTION_UPDATE_END_ADDRESS =
            "com.dknutsonlaw.android.runtracker.action.update.end.address";
    static final String ACTION_UPDATE_START_ADDRESS =
            "com.dknutsonlaw.android.runtracker2.action.update.start.address";
    static final String ACTION_UPDATE_START_DATE =
            "com.dknutsonlaw.android.runtracker.action.update.start.date";
    //Label for saving adapter item count to SharedPreferences and in savedInstanceState
    //Bundles
    static final String ADAPTER_ITEM_COUNT =
            "com.dknutsonlaw.android.runtracker2.adapter.item.count";
    //Label used to pass ID of run to use in a new instance of RunFragment
    static final String ARG_RUN_ID = "RUN_ID";
    static final String ARG_ERROR_CODE = "error_code";
    static final String MAP_BOUNDS = "map_bounds";
    //Labels for columns in the Location table
    static final String COLUMN_LOCATION_ALTITUDE = "altitude";
    static final String COLUMN_LOCATION_LATITUDE = "latitude";
    static final String COLUMN_LOCATION_LONGITUDE = "longitude";
    static final String COLUMN_LOCATION_PROVIDER = "provider";
    static final String COLUMN_LOCATION_RUN_ID = "run_id";
    static final String COLUMN_LOCATION_TIMESTAMP = "timestamp";
    //Labels for column in the Run table
    static final String COLUMN_RUN_DISTANCE = "distance";
    static final String COLUMN_RUN_DURATION = "duration";
    static final String COLUMN_RUN_END_ADDRESS = "end_address";
    static final String COLUMN_RUN_ID = "_id";
    static final String COLUMN_RUN_START_ADDRESS = "start_address";
    static final String COLUMN_RUN_START_DATE = "start_date";
    //Label for name of database
    static final String DB_NAME = "runs.sqlite";
    //Label used to pass along extra info about results of TrackingLocationIntentService operation
    static final String EXTENDED_RESULTS_DATA =
            "com.dknutsonlaw.android.runtracker.extended.results.data";
    //Label used to pass along run IDs in Intents
    static final String EXTRA_RUN_ID =
            "com.dknutsonlaw.android.runtracker.run_id";
    //Label used to pass along the existing sort order from RunRecyclerListFragment to RunPagerActivity
    //and vice-versa
    static final String EXTRA_SORT_ORDER = "com.dknutsonlaw.android.runtracker2.sort_order";
    static final String IS_BOUND = "is_bound";
    static final String LAST_LOCATION = "last_location";
    static final String LATLNG_LIST = "latlng_list";
    static final String LOCATION_SERVICE = "location_service";
    static final String LOCAL_MESSENGER = "local_messenger";
    //Label used to communicate a location parameter in an Intent or a Bundle
    static final String PARAM_LOCATION =
            "com.dknutsonlaw.android.runtracker.param.location";
    //Label used to communicate a run parameter in an Intent or a Bundle
    static final String PARAM_RUN =
            "com.dknutsonlaw.android.runtracker.param.run";
    static final String PARAM_RUN_IDS =
            "com.dknutsonlaw.android.runtracker.param.runids";
    //Label used to identify the current run id into SystemPreferences
    static final String PREF_CURRENT_RUN_ID = "prefs.currentRunId";
    //Label used to identify file used in SystemPreferences operations
    static final String PREFS_FILE = "runs";
    //Label used to identify run id retrieved from SystemPreferences
    static final String SAVED_RUN_ID = "com.dknutsonlaw.com.android.runtracker2.saved_run_id";
    //Label used to communicate results of TrackingLocationIntentService operations in response Intents
    static final String SEND_RESULT_ACTION =
            "com.dknutsonlaw.android.runtracker.send.result.action";
    static final String STARTING_LOCATION = "starting_location";
    //Label used to store and retrieve the run sort order in Intents and Bundles
    static final String SORT_ORDER = "sort";
    //Label used to store and retrieve fragment subtitles in Intents and Bundles
    static final String SUBTITLE = "subtitle";
    //Labels for the two data tables in the database
    static final String TABLE_LOCATION = "location";
    static final String TABLE_RUN = "run";
    static final String TRACKING = "tracking";
    static final String TRACKING_THIS_RUN = "tracking_this_run";
    static final String UPDATED_ADDRESS_RESULT = "addressResult";

    //Labels for the Uris for the two data tables used for observing and reporting changes in them to
    ///trigger appropriate actions in loaders
    static final Uri URI_TABLE_LOCATION =
            Uri.parse("sqlite://com.dknutsonlaw.android.runtracker/location");
    static final Uri URI_TABLE_RUN =
            Uri.parse("sqlite://com.dknutsonlaw.android.runtracker/run");
}
