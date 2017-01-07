package se.oddbit.telolet;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.auth.ui.ResultCodes;
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
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import se.oddbit.telolet.util.OpenLocationCode;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static se.oddbit.telolet.util.Constants.Analytics.Events.LOCATION;
import static se.oddbit.telolet.util.Constants.Analytics.Param.OLC_10;
import static se.oddbit.telolet.util.Constants.Analytics.Param.OLC_8;
import static se.oddbit.telolet.util.Constants.Firebase.Database.USER_LOCATIONS;

public class MainActivity extends AppCompatActivity implements FirebaseAuth.AuthStateListener,
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {
    private static final String LOG_TAG = MainActivity.class.getSimpleName();
    private static final int RC_SIGN_IN = 42;
    private static final int RC_LOCATION_PERMISSIONS = 0x10c;

    private static final long LOCATION_UPDATE_INTERVAL = 10000;
    private static final long FASTEST_LOCATION_UPDATE_INTERVAL = LOCATION_UPDATE_INTERVAL / 2;

    private static final String REQUESTING_LOCATION_UPDATES_KEY = "telolet_is_requesting_location_updates";
    private static final String LOCATION_KEY = "telolet_current_location";

    private View mRootView;
    private boolean mRequestingLocationUpdates = false;

    private GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;
    private OpenLocationCode mCurrentLocation;
    private FirebaseUser mFirebaseUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mRootView = findViewById(R.id.activity_root_view);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        startSignInProcess();
        updateValuesFromBundle(savedInstanceState);
        buildGoogleApiClient();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    protected void onStop() {
        mGoogleApiClient.disconnect();
        super.onStop();
    }

    @Override
    protected void onPause() {
        FirebaseCrash.logcat(Log.VERBOSE, LOG_TAG, "onPause");
        FirebaseAuth.getInstance().removeAuthStateListener(this);
        stopLocationUpdates();
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        FirebaseCrash.logcat(Log.VERBOSE, LOG_TAG, "onResume");
        FirebaseAuth.getInstance().addAuthStateListener(this);
        startLocationUpdates();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                return true;
            case R.id.action_logout:
                final Context context = this;
                AuthUI.getInstance().signOut(this).addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull final Task<Void> task) {
                        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().apply();
                        startSignInProcess();
                    }
                });
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != RC_SIGN_IN) {
            return;
        }

        switch (resultCode) {
            case RESULT_OK:
                FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onActivityResult: Finished Firebase UI Auth Process Successfully");
                break;

            case RESULT_CANCELED:
                FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onActivityResult: Cancelled Firebase UI Auth Process");
                startSignInProcess();
                break;

            case ResultCodes.RESULT_NO_NETWORK:
                FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onActivityResult: Failed Firebase UI Auth Process: no network");
                Snackbar.make(mRootView, R.string.no_internet_connection, Snackbar.LENGTH_INDEFINITE).show();
                startSignInProcess();
                break;
        }
    }

    @Override
    protected void onSaveInstanceState(final Bundle savedInstanceState) {
        savedInstanceState.putBoolean(REQUESTING_LOCATION_UPDATES_KEY, mRequestingLocationUpdates);
        if (mCurrentLocation != null) {
            savedInstanceState.putString(LOCATION_KEY, mCurrentLocation.getCode());
        }

        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onAuthStateChanged(@NonNull final FirebaseAuth firebaseAuth) {
        if (firebaseAuth.getCurrentUser() == null) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onAuthStateChanged: NOT LOGGED IN");
            return;
        }

        mFirebaseUser = firebaseAuth.getCurrentUser();
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, String.format(Locale.getDefault(), "onAuthStateChanged: method: '%s', email: '%s', uid: '%s'",
                mFirebaseUser.getProviderId(), mFirebaseUser.getEmail(), mFirebaseUser.getUid()));

        final Bundle bundle = new Bundle();
        bundle.putString(FirebaseAnalytics.Param.CONTENT_TYPE, mFirebaseUser.getProviderId());
        FirebaseAnalytics.getInstance(this).logEvent(FirebaseAnalytics.Event.LOGIN, bundle);
    }

    @Override
    public void onConnected(@Nullable final Bundle bundle) {
        FirebaseCrash.logcat(Log.INFO, LOG_TAG, "onConnected: Connected to GoogleApiClient");

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, RC_LOCATION_PERMISSIONS);
            FirebaseCrash.logcat(Log.INFO, LOG_TAG, "onConnected: Requesting missing permissions");
            return;
        }

        if (mCurrentLocation == null) {
            final Location lastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
            mCurrentLocation = new OpenLocationCode(lastLocation.getLatitude(), lastLocation.getLongitude());
            FirebaseCrash.logcat(Log.INFO, LOG_TAG, "onConnected: No current location. Using last known location: " + mCurrentLocation.getCode());
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
        FirebaseCrash.logcat(Log.WARN, LOG_TAG, "Connection failed: ConnectionResult.getErrorCode() = " + connectionResult.getErrorCode());
    }

    @Override
    public void onLocationChanged(final Location location) {
        final OpenLocationCode newLocation = new OpenLocationCode(location.getLatitude(), location.getLongitude());
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onLocationChanged: " + newLocation.getCode());

        final Bundle bundle = new Bundle();
        bundle.putString(OLC_10, newLocation.getCode());
        bundle.putString(OLC_8, newLocation.getCode().substring(0, 8));
        FirebaseAnalytics.getInstance(this).logEvent(LOCATION, bundle);

        saveLocation(newLocation, mCurrentLocation);
        mCurrentLocation = newLocation;
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode, @NonNull final String[] permissions, @NonNull final int[] grantResults) {
        switch (requestCode) {
            case RC_LOCATION_PERMISSIONS:
                for (int i = 0; i < grantResults.length; i++) {
                    if (grantResults[i] != PERMISSION_GRANTED) {
                        FirebaseCrash.logcat(Log.WARN, LOG_TAG, "User didn't grant permission: " + permissions[i]);
                    }
                }

                return;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void startLocationUpdates() {
        if (mRequestingLocationUpdates) {
            return;
        }

        if (!mGoogleApiClient.isConnected()) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "startLocationUpdates: Google API client is not connected. Can't start.");
            return;
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, RC_LOCATION_PERMISSIONS);
            FirebaseCrash.logcat(Log.INFO, LOG_TAG, "startLocationUpdates: Requesting missing permissions");
            return;
        }

        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "startLocationUpdates: Requesting location updates");
        mRequestingLocationUpdates = true;
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
    }

    private void stopLocationUpdates() {
        if (mRequestingLocationUpdates) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "Stop location updates.");
            mRequestingLocationUpdates = false;
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
        }
    }

    private void updateValuesFromBundle(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            if (savedInstanceState.keySet().contains(REQUESTING_LOCATION_UPDATES_KEY)) {
                mRequestingLocationUpdates = savedInstanceState.getBoolean(
                        REQUESTING_LOCATION_UPDATES_KEY);
            }

            if (savedInstanceState.keySet().contains(LOCATION_KEY)) {
                mCurrentLocation = savedInstanceState.getParcelable(LOCATION_KEY);
            }
        }
    }

    private void startSignInProcess() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "Start Firebase UI Auth login process.");
            final List<AuthUI.IdpConfig> providers = Arrays.asList(
                    new AuthUI.IdpConfig.Builder(AuthUI.GOOGLE_PROVIDER).build(),
                    new AuthUI.IdpConfig.Builder(AuthUI.FACEBOOK_PROVIDER).build());

            final Intent loginIntent = AuthUI.getInstance()
                    .createSignInIntentBuilder()
                    .setProviders(providers)
                    .setIsSmartLockEnabled(!BuildConfig.DEBUG)
                    .setLogo(R.mipmap.ic_launcher)
                    .setTheme(R.style.AppTheme)
                    .build();

            startActivityForResult(loginIntent, RC_SIGN_IN);
        }
    }

    private synchronized void buildGoogleApiClient() {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "Building GoogleApiClient");
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        createLocationRequest();
    }

    protected void createLocationRequest() {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "createLocationRequest");
        mLocationRequest = new LocationRequest();

        mLocationRequest.setInterval(LOCATION_UPDATE_INTERVAL);
        mLocationRequest.setFastestInterval(FASTEST_LOCATION_UPDATE_INTERVAL);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        startLocationUpdates();
    }


    private void saveLocation(final OpenLocationCode newLocation, final OpenLocationCode oldLocation) {
        final String oldLocationBox = oldLocation.getCode().substring(0, 8).toUpperCase();
        final String newLocationBox = newLocation.getCode().substring(0, 8).toUpperCase();

        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, String.format("saveLocation: moved from '%s' to '%s'", oldLocation.getCode(), newLocation.getCode()));

        if (!oldLocationBox.equals(newLocationBox) && mFirebaseUser != null) {
            FirebaseCrash.logcat(Log.INFO, LOG_TAG, String.format("User '%s' moved from '%s' to '%s'",
                    mFirebaseUser.getUid(), oldLocation.getCode(), newLocation.getCode()));
            final DatabaseReference userLocationsRef = FirebaseDatabase.getInstance().getReference(USER_LOCATIONS);
            userLocationsRef.child(oldLocationBox).child(mFirebaseUser.getUid()).removeValue();
            userLocationsRef.child(newLocationBox).child(mFirebaseUser.getUid()).setValue(true);
        }
    }
}
