/* Implementation of a ContentProvider that will allow the app to be used with Android Oreo, which
 * will not run using the former technique of directly addressing SQL Tables.
 */

package com.dknutsonlaw.android.runtracker2;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.util.Log;

public class MyContentProvider extends ContentProvider {

    private static final String TAG = "MyContentProvider";

    private static RunDatabaseHelper sHelper;

    public MyContentProvider() {
    }

    private static final UriMatcher uriMatcher;
        static {
            uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
            uriMatcher.addURI(Constants.AUTHORITY, "run", Constants.RUN_LIST);
            uriMatcher.addURI(Constants.AUTHORITY, "run/#", Constants.SINGLE_RUN);
            uriMatcher.addURI(Constants.AUTHORITY, "location", Constants.LOCATION_LIST);
            uriMatcher.addURI(Constants.AUTHORITY, "location/#", Constants.SINGLE_LOCATION);
        }

    @Override
    public int delete(@NonNull Uri uri, String selection, String[] selectionArgs) {
        /*Individual Runs and their associated Location Lists are all we ever delete, so those are
         *the only two cases we need deal with.
         */
        // Implement this to handle requests to delete one or more rows.
        int result;
        if (uriMatcher.match(uri) == Constants.LOCATION_LIST){
            result = sHelper.getWritableDatabase().delete(
                    Constants.TABLE_LOCATION,
                    selection,
                    selectionArgs
            );
            try {
                //noinspection ConstantConditions
                getContext().getContentResolver().notifyChange(Constants.URI_TABLE_LOCATION, null);
            } catch (NullPointerException npe){
                Log.e(TAG, "Caught an NPE while trying to get the ContentResolver");
            }
        } else if (uriMatcher.match(uri) == Constants.SINGLE_RUN){

           result = sHelper.getWritableDatabase().delete(
                   Constants.TABLE_RUN,
                   selection,
                   selectionArgs
           );
           try {
               //noinspection ConstantConditions
               getContext().getContentResolver().notifyChange(Constants.URI_TABLE_RUN, null);
           } catch (NullPointerException npe) {
               Log.e(TAG, "Caught an NPE while trying to get the ContentResolver");
           }
        } else {
            Log.e (TAG, "You must have goofed up somewhere - bad URI in call to ContentProvider.delete()");
            result = -1;
        }
        return result;
    }

    @Override
    public String getType(@NonNull Uri uri) {

        switch(uriMatcher.match(uri)){
            case Constants.RUN_LIST:
                return "vnd.android.cursor.dir/vnd.runtracker2.run";
            case Constants.SINGLE_RUN:
                return "vnd.android.cursor.item/vnd.runtracker2.run";
            case Constants.LOCATION_LIST:
                return "vnd.android.cursor.dir/vnd.runtracker2.location";
            case Constants.SINGLE_LOCATION:
                return "vnd.android.cursor.item/vnd.runtracker2.location";
            default:
                Log.e(TAG, "Error in attempt to getType()");
                return null;
        }
    }

    @Override
    public Uri insert(@NonNull Uri uri, ContentValues values) {
        long results = -1;
        switch(uriMatcher.match(uri)){
            case Constants.RUN_LIST:
                results = sHelper.getWritableDatabase().insert(
                        Constants.TABLE_RUN,
                        null,
                        values
                );
                try {
                    //noinspection ConstantConditions
                    getContext().getContentResolver().notifyChange(Constants.URI_TABLE_RUN, null);
                } catch (NullPointerException npe){
                    Log.e(TAG, "Caught an NPE while trying to get the ContentResolver");
                }
                break;
            case Constants.LOCATION_LIST:
                results = sHelper.getWritableDatabase().insert(
                        Constants.TABLE_LOCATION,
                        null,
                        values
                );
                try {
                    //noinspection ConstantConditions
                    getContext().getContentResolver().notifyChange(Constants.URI_TABLE_LOCATION, null);
                } catch (NullPointerException npe){
                    Log.e(TAG, "Caught an NPE while trying to get the ContentResolver");
                }
                break;
        }
        return ContentUris.withAppendedId(uri, results);
    }

    @Override
    public boolean onCreate() {

        sHelper = new RunDatabaseHelper(getContext());
        return true;
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        Cursor cursor;

        switch (uriMatcher.match(uri)){

            case Constants.RUN_LIST:
                int numericSortOrder = Integer.parseInt(sortOrder);
                switch (numericSortOrder) {
                    case Constants.SORT_BY_DATE_ASC:
                        cursor = sHelper.getReadableDatabase().query(
                                Constants.TABLE_RUN,
                                null,
                                null,
                                null,
                                null,
                                null,
                                Constants.COLUMN_RUN_START_DATE + " asc"
                        );
                        break;
                    case Constants.SORT_BY_DATE_DESC:
                        cursor = sHelper.getReadableDatabase().query(
                                Constants.TABLE_RUN,
                                null,
                                null,
                                null,
                                null,
                                null,
                                Constants.COLUMN_RUN_START_DATE + " desc"
                        );
                        break;
                    case Constants.SORT_BY_DISTANCE_ASC:
                        cursor = sHelper.getReadableDatabase().query(
                                Constants.TABLE_RUN,
                                null,
                                null,
                                null,
                                null,
                                null,
                                Constants.COLUMN_RUN_DISTANCE + " asc"
                        );
                        break;
                    case Constants.SORT_BY_DISTANCE_DESC:
                        cursor = sHelper.getReadableDatabase().query(
                                Constants.TABLE_RUN,
                                null,
                                null,
                                null,
                                null,
                                null,
                                Constants.COLUMN_RUN_DISTANCE + " desc"
                        );
                        break;
                    case Constants.SORT_BY_DURATION_ASC:
                        cursor = sHelper.getReadableDatabase().query(
                                Constants.TABLE_RUN,
                                null,
                                null,
                                null,
                                null,
                                null,
                                Constants.COLUMN_RUN_DURATION + " asc"
                        );
                        break;
                    case Constants.SORT_BY_DURATION_DESC:
                        cursor = sHelper.getReadableDatabase().query(
                                Constants.TABLE_RUN,
                                null,
                                null,
                                null,
                                null,
                                null,
                                Constants.COLUMN_RUN_DURATION + " desc"
                        );
                        break;
                    case Constants.SORT_NO_RUNS:
                        cursor = sHelper.getReadableDatabase().query(
                                Constants.TABLE_RUN,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null
                        );
                        break;
                    default:
                        cursor = null;
                        Log.e(TAG, "Sort Order Error");
                }
                try {
                    //noinspection ConstantConditions
                    cursor.setNotificationUri(getContext().getContentResolver(),
                                                Constants.URI_TABLE_RUN);
                } catch (NullPointerException npe) {
                    Log.e(TAG, "Caught an npe while trying to setNotificationUri");
                }
                break;
            case Constants.SINGLE_RUN:
                cursor = sHelper.getReadableDatabase().query(
                        Constants.TABLE_RUN,
                        null,
                        selection,
                        selectionArgs,
                        null,
                        null,
                        null
                );
                //noinspection ConstantConditions
                cursor.setNotificationUri(getContext().getContentResolver(),
                                                        Constants.URI_TABLE_RUN);
                break;
            case Constants.LOCATION_LIST:
                cursor = sHelper.getReadableDatabase().query(
                        Constants.TABLE_LOCATION,
                        null,
                        selection,
                        selectionArgs,
                        null,
                        null,
                        Constants.COLUMN_LOCATION_TIMESTAMP + " asc"
                );
                try {
                    //noinspection ConstantConditions
                    cursor.setNotificationUri(getContext().getContentResolver(),
                                                            Constants.URI_TABLE_LOCATION);
                } catch (NullPointerException npe){
                    Log.e(TAG, "Caught an NPE while trying to set Notification Uri");
                }
                break;
            case Constants.SINGLE_LOCATION:
                cursor = sHelper.getReadableDatabase().query(
                        Constants.TABLE_LOCATION,
                        null,
                        selection,
                        selectionArgs,
                        null,
                        null,
                        sortOrder,
                        "1"
                );
                //noinspection ConstantConditions
                cursor.setNotificationUri(getContext().getContentResolver(),
                                                        Constants.URI_TABLE_LOCATION);
                break;
            default:
                cursor = null;
                Log.i(TAG, "Invalid Uri passed to ContentProvider");
        }
        return cursor;
    }

    @Override
    public int update(@NonNull Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        int results;

        switch(uriMatcher.match(uri)){
            case Constants.SINGLE_RUN:
                results = sHelper.getWritableDatabase().update(
                        Constants.TABLE_RUN,
                        values,
                        selection,
                        selectionArgs
                );
                try {
                    //noinspection ConstantConditions
                    getContext().getContentResolver().notifyChange(Constants.URI_TABLE_RUN,
                                                                    null);
                } catch (NullPointerException npe) {
                    Log.e(TAG, "Caught an NPE while trying to get the ContentResolver");
                }
                break;
            default:
                results = -1;
                Log.e(TAG, "Updates should only happen to Single Runs");
        }
        return results;
    }
}
