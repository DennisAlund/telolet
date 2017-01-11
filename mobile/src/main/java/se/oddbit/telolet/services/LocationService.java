package se.oddbit.telolet.services;

import android.Manifest;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.crash.FirebaseCrash;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;

import java.util.Locale;

import se.oddbit.telolet.models.User;
import se.oddbit.telolet.util.OpenLocationCode;

import static se.oddbit.telolet.util.Constants.Analytics.Events.LOCATION;
import static se.oddbit.telolet.util.Constants.Analytics.Param.OLC;
import static se.oddbit.telolet.util.Constants.Analytics.UserProperty.GAME_LEVEL;
import static se.oddbit.telolet.util.Constants.Analytics.UserProperty.USER_LOCATION;
import static se.oddbit.telolet.util.Constants.Database.USERS;
import static se.oddbit.telolet.util.Constants.RemoteConfig.FASTEST_LOCATION_UPDATE_INTERVAL;
import static se.oddbit.telolet.util.Constants.RemoteConfig.LOCATION_UPDATES_INTERVAL;
import static se.oddbit.telolet.util.Constants.RemoteConfig.LOCATION_UPDATES_THRESHOLD_METERS;
import static se.oddbit.telolet.util.Constants.RemoteConfig.OLC_BOX_SIZE;

public class LocationService extends Service implements
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {

    private static final String LOG_TAG = LocationService.class.getSimpleName();

    private boolean mRequestingLocationUpdates = false;
    private GoogleApiClient mGoogleApiClient;
    private OpenLocationCode mCurrentLocation;
    FirebaseRemoteConfig mRemoteConfig;

    public LocationService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onCreate");
        buildGoogleApiClient();
    }

    @Override
    public void onDestroy() {
        stopLocationUpdates();
        mGoogleApiClient = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onBind");
        return null;
    }

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        super.onStartCommand(intent, flags, startId);
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onStartCommand");
        mRemoteConfig = FirebaseRemoteConfig.getInstance();
        mRemoteConfig.fetch().addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull final Task<Void> task) {
                if (!task.isSuccessful()) {
                    FirebaseCrash.logcat(Log.ERROR, LOG_TAG, "Could not fetch PATH remote config. Will go with defaults");
                } else {
                    FirebaseCrash.logcat(Log.INFO, LOG_TAG, "Successfully fetched PATH remote configuration");
                    restartLocationUpdates();
                }
            }
        });

        return START_STICKY;
    }

    @Override
    public void onConnected(@Nullable final Bundle bundle) {
        FirebaseCrash.logcat(Log.INFO, LOG_TAG, "onConnected: Connected to GoogleApiClient");

        if (checkCallingOrSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                || checkCallingOrSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            FirebaseCrash.logcat(Log.ERROR, LOG_TAG, "startLocationUpdates: Missing required permissions to run!");
            return;
        }

        if (mCurrentLocation == null) {
            final Location lastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
            mCurrentLocation = new OpenLocationCode(lastLocation.getLatitude(), lastLocation.getLongitude());
            FirebaseCrash.logcat(Log.INFO, LOG_TAG, "onConnected: No current location. Using last known location: " + mCurrentLocation.getCode());
            saveCurrentLocation();
        }

        startLocationUpdates();
    }

    @Override
    public void onConnectionSuspended(final int i) {
        FirebaseCrash.logcat(Log.INFO, LOG_TAG, "Connection suspended");
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnectionFailed(@NonNull final ConnectionResult connectionResult) {
        FirebaseCrash.logcat(Log.ERROR, LOG_TAG, "Connection failed: ConnectionResult.getErrorCode() = " + connectionResult.getErrorCode());
        stopSelf();
    }

    @Override
    public void onLocationChanged(final Location location) {
        final OpenLocationCode newLocation = new OpenLocationCode(location.getLatitude(), location.getLongitude());

        if (newLocation.equals(mCurrentLocation)) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG,
                    String.format("onLocationChanged: Location notification (still at %s)", mCurrentLocation.getCode()));
        }

        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG,
                String.format("onLocationChanged: Location updated (%s => %s)", mCurrentLocation.getCode(), newLocation.getCode()));
        mCurrentLocation = newLocation;
        saveCurrentLocation();

        final Bundle analyticsBundle = new Bundle();
        // The length 11 is for full length OLC that are actually 10 value characters
        for (int boxSize : new int[]{4, 6, 8, 11}) {
            final String olcBox = newLocation.getCode().substring(0, boxSize);
            // Put in location data for the box size.
            analyticsBundle.putString(OLC + (boxSize == 11 ? 10 : boxSize), olcBox);
        }

        final Integer boxSize = Integer.valueOf(mRemoteConfig.getString(OLC_BOX_SIZE));
        final FirebaseAnalytics analytics = FirebaseAnalytics.getInstance(this);
        analytics.setUserProperty(GAME_LEVEL, boxSize.toString());
        analytics.setUserProperty(USER_LOCATION, newLocation.getCode().substring(0, boxSize));
        analytics.logEvent(LOCATION, analyticsBundle);
    }

    private void restartLocationUpdates() {
        if (mGoogleApiClient.isConnected()) {
            stopLocationUpdates();
            startLocationUpdates();
        }
    }

    private void startLocationUpdates() {
        if (mRequestingLocationUpdates) {
            return;
        }

        if (!mGoogleApiClient.isConnected()) {
            FirebaseCrash.logcat(Log.ERROR, LOG_TAG, "Tried to start location updates, but Google API client is not connected!!!");
            return;
        }

        if (checkCallingOrSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                || checkCallingOrSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            FirebaseCrash.logcat(Log.ERROR, LOG_TAG, "startLocationUpdates: Missing required permissions to run!");
            return;
        }

        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "startLocationUpdates: Requesting location updates");
        mRequestingLocationUpdates = true;

        final LocationRequest locationRequest = new LocationRequest();
        final float thresholdMeters = (float) mRemoteConfig.getDouble(LOCATION_UPDATES_THRESHOLD_METERS);
        final long updatesInterval = mRemoteConfig.getLong(LOCATION_UPDATES_INTERVAL);
        final long fastestUpdatesInterval = mRemoteConfig.getLong(FASTEST_LOCATION_UPDATE_INTERVAL);

        FirebaseCrash.logcat(Log.INFO, LOG_TAG, String.format(Locale.getDefault(),
                "Starting location updates with a threshold of %.2f meters or about %d seconds interval (no faster than %d seconds)",
                thresholdMeters, updatesInterval / 1000, fastestUpdatesInterval / 1000));

        locationRequest.setSmallestDisplacement(thresholdMeters);
        locationRequest.setInterval(updatesInterval);
        locationRequest.setFastestInterval(fastestUpdatesInterval);
        locationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);

        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, locationRequest, this);
    }

    private void stopLocationUpdates() {
        if (mRequestingLocationUpdates) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "Stop location updates.");
            mRequestingLocationUpdates = false;
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
        }
    }

    private synchronized void buildGoogleApiClient() {
        if (mGoogleApiClient != null) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "GoogleApiClient already built");
            return;
        }
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "Building GoogleApiClient");
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();

        mGoogleApiClient.connect();
    }


    private void saveCurrentLocation() {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG,
                String.format("saveCurrentLocation: %s", mCurrentLocation.getCode()));

        final FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "saveCurrentLocation: PATH user not yet set. Can't save.");
            return;
        }

        FirebaseDatabase
                .getInstance()
                .getReference(USERS)
                .child(firebaseUser.getUid())
                .child(User.ATTR_LOCATION)
                .setValue(mCurrentLocation.getCode());
    }
}
