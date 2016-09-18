package com.dknutsonlaw.android.runtracker2;

/*
 * A Service intended to allow the user to start and stop location updates and guarantee that the
 * updates will continue without regard to the state of the UI elements of the program.
 */

import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;

import java.lang.ref.WeakReference;
import java.text.DateFormat;
import java.util.Date;

public class BackgroundLocationService extends Service implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener/*,
        ResultCallback<LocationSettingsResult>*/ {

    private static final String TAG = "location.service";

    /*public class LocalBinder extends Binder {
        public BackgroundLocationService getService() {
            return BackgroundLocationService.this;
        }

    }*/

    private final Messenger mMessenger = new Messenger(new IncomingHandler(this));
    private static Messenger sRunFragmentMessenger = null;
    private static Messenger sRecyclerFragmentMessenger = null;
    private static Messenger sRunMapFragmentMessenger = null;
    private static Messenger sRunPagerActivityMessenger = null;
    //private final IBinder mBinder = new LocalBinder();
    //private final RunManager mRunManager = RunManager.get(this);
    private final GoogleApiAvailability mGoogleApiAvailability = GoogleApiAvailability.getInstance();
    private static GoogleApiClient sClient;
    private static LocationRequest sLocationRequest;
    private static LocationSettingsRequest sLocationSettingsRequest;
    private static PendingIntent sPi;
    private boolean mInProgress;
    private static boolean sServicesAvailable = false;

    public BackgroundLocationService() {

    }

    @Override
    public void onCreate() {
        Log.i(TAG, "Reached onCreate()");
        super.onCreate();
        //mInProgress = false;


        sServicesAvailable = servicesConnected();

        /*
         * Create a new GoogleApiClient using the enclosing class to handle the callbacks
         */
        setUpGoogleApiClient();
        /*sClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .build();*/
        buildLocationRequest();
        buildLocationSettingsRequest();
    }

    private boolean servicesConnected() {
        //Check that Google Play Services is available
        int resultCode = mGoogleApiAvailability.isGooglePlayServicesAvailable(this);
        return resultCode == ConnectionResult.SUCCESS;
    }

   /* public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        Log.i(TAG, "Reached onStartCommand");

        if(!sServicesAvailable || sClient.isConnected() || mInProgress ){
        //if (sClient.isConnected()) {
        //if (RunTracker.sClient.isConnected()){
            //startLocationUpdates();
            //checkLocationSettings();
            return START_STICKY;
        } else if (!sServicesAvailable || mInProgress) {
            return START_STICKY;
        }

        //setUpGoogleApiClient();
        if (!sClient.isConnected() || sClient.isConnecting() && !mInProgress){
        //if (!sClient.isConnected() || sClient.isConnecting() && !mInProgress) {
            Log.i(TAG, "Started.");
            mInProgress = true;
            sClient.connect();
            //sClient.connect();
        }
        return START_STICKY;
    }*/

    private synchronized void setUpGoogleApiClient() {
        Log.i(TAG, "Reached setupGoogleApiClient().");
        sClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
    }

    private void buildLocationRequest(){
        //Construct the Location Request
        sLocationRequest = LocationRequest.create();
        //Use high accuracy
        sLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        sLocationRequest.setInterval(1000);
    }

    private void buildLocationSettingsRequest(){
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(sLocationRequest);
        sLocationSettingsRequest = builder.build();
    }

    private static void checkLocationSettings(BackgroundLocationService service){
        Log.i(TAG, "Reached checkLocationSettings()");
        Log.i(TAG, "sServicesAvailable() is " + sServicesAvailable);
        Log.i(TAG, "sClient null? " + (sClient == null));
        if (sClient != null) {
            Log.i(TAG, "sClient.isConnected() is " + sClient.isConnected());
        }
        PendingResult<LocationSettingsResult> result =
                LocationServices.SettingsApi.checkLocationSettings(
                        sClient,
                        sLocationSettingsRequest
                );
        result.setResultCallback(new ResultCallback<LocationSettingsResult>() {
            @Override
            public void onResult(@NonNull LocationSettingsResult locationSettingsResult){
                Log.i(TAG, "Reached onResult(LocationSettingsResult");
                final Status status = locationSettingsResult.getStatus();
                switch (status.getStatusCode()) {
                    case LocationSettingsStatusCodes.SUCCESS:
                        Log.i(TAG, "All Location Settings are satisfied.");
                        startLocationUpdates(service);
                        break;
                    case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                        Log.i(TAG, "Location settings are not satisfied. Show the user a dialog to " +
                                "update location settings.");
                        try {
                            sRunFragmentMessenger.send(Message.obtain(null, Constants.MESSAGE_LOCATION_SETTINGS_RESOLUTION_NEEDED, locationSettingsResult));
                        } catch (RemoteException e){
                            Log.i(TAG, "RemoteException thrown while trying to send MESSAGE_LOCATION_SETTINGS_RESOLUTION_NEEDED to RunFragment.");
                        }
                        break;
                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                        Log.i(TAG, "Location settings are inadequate and cannot be fixed here. Dialog not created.");
                        try {
                            sRunFragmentMessenger.send(Message.obtain(null, Constants.MESSAGE_LOCATION_SETTINGS_NOT_AVAILABLE));
                        } catch (RemoteException e){
                            Log.i(TAG, "RemoteException thrown while trying to send MESSAGE_LOCATION_SETTINGS_RNOT_AVAILABLE to RunFragment.");
                        }
                        break;
                }
            }
        });
    }

    @Override
    public IBinder onBind(Intent intent) {

        //return mBinder;
        return mMessenger.getBinder();
    }

    private static void checkPermissions(BackgroundLocationService service, Message msg){
        Log.i(TAG, "Checking Permissions.");
        if (Build.VERSION.SDK_INT >= 23 && ActivityCompat.checkSelfPermission(service,
                android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            try {
                msg.replyTo.send(Message.obtain(null, Constants.MESSAGE_PERMISSION_REQUEST_NEEDED));
            } catch (RemoteException e){
                Log.i(TAG, "RemoteException thrown while trying to send MESSAGE_PERMISSION_REQUEST_NEEDED to RunFragment.");
            }
        } else {
            checkLocationSettings(service);
            Log.i(TAG, "We already have permission to use location data.");
        }

    }

    private static void startLocationUpdates(BackgroundLocationService service) {
        Log.i(TAG, "Reached startLocationUpdates()");
        Log.i(TAG, "sServicesAvailable is " + sServicesAvailable);
        Log.i(TAG, "sClient null? " + (sClient == null));
        Log.i(TAG, "sClient.isConnected() is " + sClient.isConnected());
        if (sServicesAvailable && sClient != null && sClient.isConnected()) {
            sPi = RunManager.get(service).getLocationPendingIntent(service, true);
            try {
                PendingResult<Status> result = LocationServices.FusedLocationApi.requestLocationUpdates(
                        sClient, sLocationRequest, sPi);
                result.setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(@NonNull Status status) {
                        Log.i(TAG, "Reached OnResultCallback<Status> for location updates");
                        if (status.isSuccess()){
                            if (sRunPagerActivityMessenger != null && sRunFragmentMessenger != null){
                                try {
                                    sRunPagerActivityMessenger.send(Message.obtain(null, Constants.MESSAGE_LOCATION_UPDATES_STARTED));
                                    sRunFragmentMessenger.send(Message.obtain(null, Constants.MESSAGE_LOCATION_UPDATES_STARTED));
                                } catch (RemoteException e){
                                    Log.i(TAG, "Caught RemoteException trying to send MESSAGE_LOCATION_UPDATES_STARTED.");
                                }

                            } else {
                                Log.i(TAG, "Attempt to start location updates failed!");
                            }
                        }
                    }
                });

            //We should never catch an exception here because we've just gotten through checking permissions.
            } catch (SecurityException e){
                Log.i(TAG, "SecurityException thrown when trying to start location updates.");
            }
        }
    }

    private static void stopLocationUpdates(){
        if (sServicesAvailable && sClient != null && sClient.isConnected()){
            PendingResult<Status> result = LocationServices.FusedLocationApi.removeLocationUpdates(sClient, sPi);
            result.setResultCallback(new ResultCallback<Status>() {
                @Override
                public void onResult(@NonNull Status status) {
                    Log.i(TAG, "Reached onResult of removeLocationUpdates");
                    if (status.isSuccess()){
                        Log.i(TAG, "Location Updates off.");
                        if (sPi != null) {
                            sPi.cancel();
                        }
                        try {
                            sRunPagerActivityMessenger.send(Message.obtain(null, Constants.MESSAGE_LOCATION_UPDATES_STOPPED));
                            sRunFragmentMessenger.send(Message.obtain(null, Constants.MESSAGE_LOCATION_UPDATES_STOPPED));
                        } catch (RemoteException e){
                            Log.i(TAG, "Caught RemoteException trying to send MESSAGE_LOCATION_UPDATES_STOPPED");
                        }
                    }
                }
            });
        }
    }

    @Override
    public void onDestroy(){
        //Turn off the Request flag
        mInProgress = false;
        if(sServicesAvailable && sClient != null && sClient.isConnected() && sPi != null){
            LocationServices.FusedLocationApi.removeLocationUpdates(sClient, sPi);
            sPi.cancel();
            sClient.disconnect();
            //RunTracker.sClient = null;
        }
        Log.i(TAG, DateFormat.getDateTimeInstance().format(new Date()) + ": Stopped");
        super.onDestroy();
    }
    //Location Services calls this method when it's  connected. When this is called, you're free
    //to request location updates
    @Override
    public void onConnected(Bundle bundle){

        //checkLocationSettings();
        //startLocationUpdates();
        Log.i(TAG, "Connected");
    }
    //Called by Location Services if the connection to the client is suspended due to error.
    @Override
    public void onConnectionSuspended(int cause){

        //Turn off the request flag
        mInProgress = false;
        //Kill the GoogleApiClient
        sClient = null;
        //Display connection status
        if (cause == CAUSE_NETWORK_LOST){
            try {
                if (sRunFragmentMessenger != null) {
                    sRunFragmentMessenger.send(Message.obtain(null, Constants.MESSAGE_GOOGLEAPICLIENT_CONNECTION_SUSPENDED, CAUSE_NETWORK_LOST, 0));
                }
                if (sRecyclerFragmentMessenger != null) {
                    sRecyclerFragmentMessenger.send(Message.obtain(null, Constants.MESSAGE_GOOGLEAPICLIENT_CONNECTION_SUSPENDED, CAUSE_NETWORK_LOST, 0));
                }
                if (sRunMapFragmentMessenger != null) {
                    sRunMapFragmentMessenger.send(Message.obtain(null, Constants.MESSAGE_GOOGLEAPICLIENT_CONNECTION_SUSPENDED, CAUSE_NETWORK_LOST, 0));
                }
            } catch (RemoteException e){
                Log.i(TAG, "RemoteException thrown when trying to send MESSAGE_GOOGLEAPICLIENT_CONNECTION_SUSPENDED, CAUSE_NETWORK_LOST" +
                        "to RunFragment and RunRecyclerListFragment");
            }
            Log.i(TAG, "GoogleApiClient connection suspended - network connection lost!");
            //Toast.makeText(this, R.string.connection_suspended_network_lost, Toast.LENGTH_LONG).show();
        }
        if (cause == CAUSE_SERVICE_DISCONNECTED){
            Log.i(TAG, "GoogleApiClient connection suspended - remote service killed!");
            try {
                if (sRunFragmentMessenger != null) {
                    sRunFragmentMessenger.send(Message.obtain(null, Constants.MESSAGE_GOOGLEAPICLIENT_CONNECTION_SUSPENDED, CAUSE_SERVICE_DISCONNECTED, 0));
                }
                if (sRecyclerFragmentMessenger != null) {
                    sRecyclerFragmentMessenger.send(Message.obtain(null, Constants.MESSAGE_GOOGLEAPICLIENT_CONNECTION_SUSPENDED, CAUSE_SERVICE_DISCONNECTED, 0));
                }
                if (sRunMapFragmentMessenger != null) {
                    sRunMapFragmentMessenger.send(Message.obtain(null, Constants.MESSAGE_GOOGLEAPICLIENT_CONNECTION_SUSPENDED, CAUSE_SERVICE_DISCONNECTED, 0));
                }
            } catch (RemoteException e){
                Log.i(TAG, "RemoteException thrown when trying to send MESSAGE_GOOGLEAPICLIENT_CONNECTION_SUSPENDED, CAUSE_SERVICE_DISCONNECTED" +
                        "to RunFragment and RunRecyclerListFragment");
            }
            //Toast.makeText(this, R.string.connection_suspended_service_disconnected, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onConnectionFailed(@NonNull final ConnectionResult connectionResult){
        Log.i(TAG, "GoogleApi Connection failed.");
        mInProgress = false;
        if (connectionResult.hasResolution()) {

         /* Google Play services can resolve some errors it detects.
         * If the error has a resolution, try sending an Intent to
         * start an Activity that in turn will start a Google Play
         * services activity that can resolve the error.*/
            try {
                sRunFragmentMessenger.send(Message.obtain(null, Constants.MESSAGE_GOOGLEAPICLIENT_CONNECTION_FAILED, connectionResult));
                sRecyclerFragmentMessenger.send(Message.obtain(null, Constants.MESSAGE_GOOGLEAPICLIENT_CONNECTION_FAILED, connectionResult));
            } catch (RemoteException e) {
                Log.i(TAG, "RemoteException thrown when trying to send MESSAGE_GOOGLEAPICLIENT_CONNECTION_FAILED" +
                        "to RunFragment and RunRecyclerListFragment");
            }
        } else {
            try {
                sRunFragmentMessenger.send(Message.obtain(null, Constants.MESSAGE_PLAY_SERVICES_ERROR_DIALOG_REQUEST, connectionResult.getErrorCode(), 0));
                sRecyclerFragmentMessenger.send(Message.obtain(null, Constants.MESSAGE_PLAY_SERVICES_ERROR_DIALOG_REQUEST, connectionResult.getErrorCode(), 0));
            } catch (RemoteException e){
                Log.i(TAG, "RemoteException thrown when trying to send MESSAGE_PLAY_SERVICES_ERROR_DIALOG_REQUEST" +
                        "to RunFragment and RunRecyclerListFragment");
            }
        }
            // If no resolution is available, display an error dialog
    }

    /*public class LocalBinder extends Binder {
        BackgroundLocationService getService(){
            return BackgroundLocationService.this;
        }
    }*/

    /* Handler of incoming messages from RunFragments */
    private static class IncomingHandler extends Handler {

        private final WeakReference<BackgroundLocationService> mService;

        IncomingHandler(BackgroundLocationService service) {
            mService = new WeakReference<>(service);
        }
        @Override
        public void handleMessage(Message msg){
            BackgroundLocationService service = mService.get();
            if (service != null) {
                switch (msg.what) {
                    case Constants.MESSAGE_REGISTER_CLIENT:
                        switch (msg.arg1) {
                            case Constants.MESSENGER_RUNFRAGMENT:
                                sRunFragmentMessenger = msg.replyTo;
                                Log.i(TAG, "RunFragment bound");
                                if (!sClient.isConnected()) {
                                    sClient.connect();
                                }
                                break;
                            case Constants.MESSENGER_RECYCLERFRAGMENT:
                                sRecyclerFragmentMessenger = msg.replyTo;
                                Log.i(TAG, "RunRecyclerFragment bound.");
                                if (!sClient.isConnected()) {
                                    sClient.connect();
                                }
                                break;
                            case Constants.MESSENGER_RUNMAPFRAGMENT:
                                sRunMapFragmentMessenger = msg.replyTo;
                                Log.i(TAG, "RunMapFragment bound");
                                if (!sClient.isConnected()) {
                                    sClient.connect();
                                }
                                break;
                            case Constants.MESSENGER_RUNPAGERACTIVITY:
                                sRunPagerActivityMessenger = msg.replyTo;
                                Log.i(TAG, "RunPagerActivity bound");
                                if (!sClient.isConnected()) {
                                    sClient.connect();
                                }
                                break;
                        }
                        break;
                    case Constants.MESSAGE_START_LOCATION_UPDATES:
                        Log.i(TAG, "Arrived at IncomingHandler MESSAGE_START_LOCATION_UPDATES");
                        Log.i(TAG, "sClient connected? " + sClient.isConnected());
                        checkPermissions(service, msg);
                        //startLocationUpdates();
                        break;
                    case Constants.MESSAGE_TRY_GOOGLEAPICLIENT_RECONNECTION:
                        sClient.connect();
                        break;
                    case Constants.MESSAGE_PLAY_SERVICES_ERROR_DIALOG_REQUEST:
                        break;
                    case Constants.MESSAGE_LOCATION_SETTINGS_RESOLUTION_SUCCEEDED:
                        startLocationUpdates(mService.get());
                        break;
                    case Constants.MESSAGE_LOCATION_SETTINGS_RESOLUTION_FAILED:
                        Log.i(TAG, "Reached MESSAGE_LOCATION_SETTINGS_RESOLUTION_FAILED ini IncomingHandler.");
                        break;
                    case Constants.MESSAGE_PERMISSION_REQUEST_SUCCEEDED:
                        checkLocationSettings(service);
                        break;
                    case Constants.MESSAGE_STOP_LOCATION_UPDATES:
                        Log.i(TAG, "Reached MESSAGE_STOP_LOCATION_UPDATES");
                        stopLocationUpdates();
                        break;
                    default:
                        super.handleMessage(msg);
                }
            }
        }
    }
}
