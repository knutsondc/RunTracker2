package com.dknutsonlaw.android.runtracker2;

/*
 * A Service intended to allow the user to start and stop location updates and guarantee that the
 * updates will continue without regard to the state of the UI elements of the program.
 */

import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.text.DateFormat;
import java.util.Date;

public class BackgroundLocationService extends Service implements
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener{
    private static final String TAG = "location.service";

    public class LocalBinder extends Binder {
        public BackgroundLocationService getService() {
            return BackgroundLocationService.this;
        }

    }

    private final IBinder mBinder = new LocalBinder();
    private final RunManager mRunManager = RunManager.get(this);
    private GoogleApiClient mClient;
    private LocationRequest mLocationRequest;
    private PendingIntent mPi;
    private boolean mInProgress;
    private boolean mServicesAvailable = false;

    public BackgroundLocationService() {

    }

    @Override
    public void onCreate(){
        super.onCreate();
        mInProgress = false;
        //Construct the Location Request
        mLocationRequest = LocationRequest.create();
        //Use high accuracy
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setInterval(1000);

        mServicesAvailable = servicesConnected();

        /*
         * Create a new GoogleApiClient using the enclosing class to handle the callbacks
         */
        mClient = new GoogleApiClient.Builder(this)
                        .addApi(LocationServices.API)
                        .addConnectionCallbacks(this)
                        .build();
    }

    private boolean servicesConnected() {
        //Check that Google Play Services is available
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        return resultCode == ConnectionResult.SUCCESS;
    }

    public int onStartCommand(Intent intent, int flags, int startId){
        super.onStartCommand(intent, flags, startId);
        Log.i(TAG, "Reached onStartCommand");

        //if(!mServicesAvailable || mClient.isConnected() || mInProgress ){
        if (mClient.isConnected()){
            startLocationUpdates();
            return START_STICKY;
        } else if (!mServicesAvailable || mInProgress){
            return START_STICKY;
        }

        setUpGoogleApiClientIfNeeded();
        if (!mClient.isConnected() || mClient.isConnecting() && !mInProgress){
            Log.i(TAG, "Started.");
            mInProgress = true;
            mClient.connect();
        }
        return START_STICKY;
    }

    private void setUpGoogleApiClientIfNeeded(){
        mClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .build();
    }

    @Override
    public IBinder onBind(Intent intent) {

        return mBinder;
    }

    private void startLocationUpdates(){
        if (mServicesAvailable && mClient != null && mClient.isConnected()) {
            mPi = mRunManager.getLocationPendingIntent(true);
            LocationServices.FusedLocationApi.requestLocationUpdates(
                    mClient, mLocationRequest, mPi);
        }
    }

    public void stopLocationUpdates(){
        if (mServicesAvailable && mClient != null && mClient.isConnected()){
            LocationServices.FusedLocationApi.removeLocationUpdates(mClient, mPi);
            mPi.cancel();
            Log.i(TAG, "Location Updates off.");
        }
    }

    @Override
    public void onDestroy(){
        //Turn off the Request flag
        mInProgress = false;
        if(mServicesAvailable && mClient != null && mClient.isConnected()){
            LocationServices.FusedLocationApi.removeLocationUpdates(mClient, mPi);
            mPi.cancel();
            mClient.disconnect();
            mClient = null;
        }
        Log.i(TAG, DateFormat.getDateTimeInstance().format(new Date()) + ": Stopped");
        super.onDestroy();
    }
    //Location Services calls this method when it's  connected. When this is called, you're free
    //to request location updates
    @Override
    public void onConnected(Bundle bundle){
        startLocationUpdates();
        Log.i(TAG, "Connected");
    }
    //Called by Location Services if the connection to the client is suspended due to error.
    @Override
    public void onConnectionSuspended(int cause){

        //Turn off the request flag
        mInProgress = false;
        //Kill the GoogleApiClient
        mClient = null;
        //Display connection status
        if (cause == CAUSE_NETWORK_LOST){
            Log.i(TAG, "GoogleApiClient connection suspended - network connection lost!");
            Toast.makeText(this, R.string.connection_suspended_network_lost, Toast.LENGTH_LONG).show();
        }
        if (cause == CAUSE_SERVICE_DISCONNECTED){
            Log.i(TAG, "GoogleApiClient connection suspended - remote service killed!");
            Toast.makeText(this, R.string.connection_suspended_service_disconnected, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult){
        mInProgress = false;

         /*
         * Google Play services can resolve some errors it detects.
         * If the error has a resolution, try sending an Intent to
         * start a Google Play services activity that can resolve
         * error.
         */
        //noinspection StatementWithEmptyBody
        if (connectionResult.hasResolution()) {

            // If no resolution is available, display an error dialog
        } else {

        }

    }
}
