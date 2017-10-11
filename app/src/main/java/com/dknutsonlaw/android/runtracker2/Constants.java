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
    //Value for use of Imperial system for distance and altitude measurements
    static final boolean IMPERIAL = true;
    //Value for use of Metric system for distance and altitude measurements
    static final boolean METRIC = false;
    //Conversion factors to convert meters (the unit by which the Android Location API calculates
    //distances) into the units we wish to display to the user
    static final double METERS_TO_FEET = 3.28083989501;
    static final double METERS_TO_MILES = .000621371192237;

    //MapView Menu selection items
    static final int SHOW_ENTIRE_ROUTE = 0;
    static final int FOLLOW_END_POINT = 1;
    static final int FOLLOW_STARTING_POINT = 2;
    static final int NO_UPDATES = 3;
    //Sort direction upon restarting a RunRecyclerListFragment or RunPagerActivity
    static final int KEEP_EXISTING_SORT = -1;
    //Identifiers for the two different types of loaders we use in RunFragment
    static final int LOAD_LOCATION = 1;
    static final int LOAD_RUN = 0;
    static final int LOCATION_SETTINGS_CHECK = 8000;
    static final int MESSAGE_PLAY_SERVICES_RESOLUTION_REQUEST = 3;
    //Labels for Messages between UI Fragments and BackgroundLocationService

    static final int NOTIFICATION_ID = 1;
    //Label for BackgroundLocationService to tell the UI Fragment that Location Permissions needed
    static final int REQUEST_LOCATION_PERMISSIONS = 1;
    //Values to pass back from DeleteRunsDialog so that correct Fragment will act on the result
    static final int RUN_LIST_RECYCLER_FRAGMENT = 0;
    static final int COMBINED_FRAGMENT = 1;
    //Label for loader used in RunRecyclerListFragment
    static final int RUN_LIST_LOADER = 0;
    //Sort order values used in RunRecyclerListFragment and RunPagerActivity
    static final int SORT_BY_DATE_ASC = 0;
    static final int SORT_BY_DATE_DESC = 1;
    static final int SORT_BY_DISTANCE_ASC = 2;
    static final int SORT_BY_DISTANCE_DESC = 3;
    static final int SORT_BY_DURATION_ASC = 4;
    static final int SORT_BY_DURATION_DESC = 5;
    static final int SORT_NO_RUNS = 6;

    static final int RUN_LIST = 0;
    static final int SINGLE_RUN = 1;
    static final int LOCATION_LIST = 2;
    static final int SINGLE_LOCATION = 3;
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
    static final String ACTION_REFRESH_MAPS =
            "com.dknutsonlaw.android.runtracker2.action.refresh.maps";
    static final String ACTION_REFRESH_UNITS =
            "com.dknutsonlaw.android.runtracker2.action.refresh.units";
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
    //Label for saving adapter position of Run currently being displayed in RunPagerActivity
    static final String ADAPTER_POSITION =
            "com.dknutsonlaw.com.android.runtracker2.adapter.position";
    //Label used to pass ID of run to use in a new instance of RunFragment
    static final String ARG_RUN_ID = "RUN_ID";
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
    static final String EXTRA_ERROR_CODE =
            "com.dknutsonlaw.android.runtracker.error_code";
    static final String EXTRA_RUN_ID =
            "com.dknutsonlaw.android.runtracker.run_id";
    //Label used to pass along the existing sort order from RunRecyclerListFragment to RunPagerActivity
    //and vice-versa
    static final String EXTRA_SORT_ORDER = "com.dknutsonlaw.android.runtracker2.sort_order";
    static final String EXTRA_VIEW_HASHMAP = "com.dknutsonlaw.android.runtracker2.view_hash_map";
    //Label to identify type of fragment that called DeleteRunsDialog
    static final String FRAGMENT = "com.dknutsonlaw.android.runtracker2.fragment";
    //Label for distance/altitude measurement system
    static final String MEASUREMENT_SYSTEM =
            "com.dknutsonlaw.android.runtracker2.measurement_system";
    //Label for number of Runs to delete argument to pass to DeleteRunsDialog
    static final String NUMBER_OF_RUNS =
            "com.dknutsonlaw.android.runtracker2.number_of_runs";
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
    static final String PRIMARY_CHANNEL = "primary.channel";
    //Label used to identify run id retrieved from SystemPreferences
    static final String SAVED_RUN_ID = "com.dknutsonlaw.android.runtracker2.saved_run_id";
    //Label to store boolean in shared preferences for whether a mapview should be scrollable
    static final String SCROLLABLE = "com.dknutsonlaw.android.runtracker2.scrollable";
    static final String SCROLL_ON = "com.dknutsonlaw.android.runtracker2.scroll_on";
    //Label used to communicate results of TrackingLocationIntentService operations in response Intents
    static final String SEND_RESULT_ACTION =
            "com.dknutsonlaw.android.runtracker.send.result.action";
    static final String SHOULD_STOP =
            "com.dknutsonlaw.android.runtracker2.should.stop";
    //Label used to store and retrieve the run sort order in Intents and Bundles
    static final String SORT_ORDER = "sort";
    //Label used to store and retrieve fragment subtitles in Intents and Bundles
    static final String SUBTITLE = "subtitle";
    //Labels for the two data tables in the database
    static final String TABLE_LOCATION = "location";
    static final String TABLE_RUN = "run";
    static final String TRACKING_MODE = "tracking_mode";
    static final String UPDATED_ADDRESS_RESULT = "addressResult";
    static final String VIEWS_TO_DELETE = "views_to_delete";
    static final String ZOOM_LEVEL = "zoom_level";

    //Labels for the Uris for the two data tables used for observing and reporting changes in them to
    ///trigger appropriate actions in loaders
    static final String AUTHORITY = "com.dknutsonlaw.android.runtracker2";
    private static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY);
    static final Uri URI_TABLE_LOCATION = Uri.withAppendedPath(CONTENT_URI, "location");
    static final Uri URI_TABLE_RUN = Uri.withAppendedPath(CONTENT_URI, "run");
}
