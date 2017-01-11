package se.oddbit.telolet;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
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
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.appinvite.AppInvite;
import com.google.android.gms.appinvite.AppInviteInvitationResult;
import com.google.android.gms.appinvite.AppInviteReferral;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.crash.FirebaseCrash;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;

import java.util.Arrays;
import java.util.List;

import se.oddbit.telolet.models.User;
import se.oddbit.telolet.services.CloudMessagingInstanceIdService;
import se.oddbit.telolet.services.CloudMessagingService;
import se.oddbit.telolet.services.LocationService;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static se.oddbit.telolet.R.string.progress_loading;
import static se.oddbit.telolet.util.Constants.Database.USERS;

public class MainActivity extends AppCompatActivity
        implements FirebaseAuth.AuthStateListener, GoogleApiClient.OnConnectionFailedListener, ResultCallback<AppInviteInvitationResult> {
    private static final String LOG_TAG = MainActivity.class.getSimpleName();
    private static final int RC_SIGN_IN = 42;
    private static final int RC_LOCATION_PERMISSIONS = 0x10c;

    private static final String[] LOCATION_PERMISSIONS = new String[]{
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
    };

    private View mRootView;
    private ProgressDialog mProgressDialog;
    private CurrentUserListener mCurrentUserListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mRootView = findViewById(R.id.activity_root_view);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // First make sure we listen to user change objects, then try to login and fetch them
        mCurrentUserListener = new CurrentUserListener(this);
        startSignInProcess();

        final GoogleApiClient googleApiClient = new GoogleApiClient.Builder(this)
                .addApi(AppInvite.API)
                .enableAutoManage(this, this)
                .build();
        AppInvite.AppInviteApi.getInvitation(googleApiClient, this, true).setResultCallback(this);
        MobileAds.initialize(getApplicationContext(), getString(R.string.banner_ad_unit_id));

        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "Starting all services necessary for the app to run.");
        startService(new Intent(this, CloudMessagingInstanceIdService.class));
        startService(new Intent(this, CloudMessagingService.class));
        startService(new Intent(this, LocationService.class));
    }

    @Override
    protected void onPause() {
        FirebaseCrash.logcat(Log.VERBOSE, LOG_TAG, "onPause");
        FirebaseAuth.getInstance().removeAuthStateListener(this);
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        FirebaseCrash.logcat(Log.VERBOSE, LOG_TAG, "onResume");
        checkLocationPermissions();
        FirebaseAuth.getInstance().addAuthStateListener(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        if (BuildConfig.DEBUG) {
            menu.findItem(R.id.action_fetch_remote_config).setVisible(true);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_fetch_remote_config:
                FirebaseCrash.logcat(Log.ERROR, LOG_TAG, "Fetching remote config.");
                final FirebaseRemoteConfig remoteConfig = FirebaseRemoteConfig.getInstance();
                remoteConfig.fetch(1).addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull final Task<Void> task) {
                        if (task.isSuccessful()) {
                            FirebaseCrash.logcat(Log.INFO, LOG_TAG, "Successfully fetched PATH remote configuration");
                            remoteConfig.activateFetched();
                        } else {
                            FirebaseCrash.logcat(Log.ERROR, LOG_TAG, "Could not fetch PATH remote config. Will go with defaults");
                        }
                    }
                });
                return true;

            case R.id.action_logout:
                final Context context = this;
                AuthUI.getInstance().signOut(this).addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull final Task<Void> task) {
                        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().apply();
                        stopService(new Intent(context, LocationService.class));
                        startSignInProcess();
                    }
                });
                return true;


            case R.id.action_invite_friends:
                startActivity(new Intent(this, InviteFriendsActivity.class));
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
                FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onActivityResult: Finished PATH UI Auth Process Successfully");
                break;

            case RESULT_CANCELED:
                FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onActivityResult: Cancelled PATH UI Auth Process");
                startSignInProcess();
                break;

            case ResultCodes.RESULT_NO_NETWORK:
                FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onActivityResult: Failed PATH UI Auth Process: no network");
                Snackbar.make(mRootView, R.string.no_internet_connection, Snackbar.LENGTH_INDEFINITE).show();
                startSignInProcess();
                break;
        }
    }

    @Override
    public void onAuthStateChanged(@NonNull final FirebaseAuth firebaseAuth) {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onAuthStateChanged");
        final FirebaseUser firebaseUser = firebaseAuth.getCurrentUser();
        if (firebaseUser == null) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onAuthStateChanged: NOT LOGGED IN");
            return;
        }

        showProgressDialog();
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG,
                String.format("onAuthStateChanged: %s, %s, %s", firebaseUser.getProviderId(), firebaseUser.getEmail(), firebaseUser.getUid()));

        final Bundle bundle = new Bundle();
        bundle.putString(FirebaseAnalytics.Param.CONTENT_TYPE, firebaseUser.getProviderId());
        FirebaseAnalytics.getInstance(this).logEvent(FirebaseAnalytics.Event.LOGIN, bundle);

        mCurrentUserListener.start();
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode, @NonNull final String[] permissions, @NonNull final int[] grantResults) {
        if (requestCode == RC_LOCATION_PERMISSIONS) {
            for (int i = 0; i < grantResults.length; i++) {
                if (grantResults[i] != PERMISSION_GRANTED) {
                    FirebaseCrash.logcat(Log.WARN, LOG_TAG, "User didn't grant permission: " + permissions[i]);
                }
            }

            return;
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onResult(@NonNull final AppInviteInvitationResult result) {
        FirebaseCrash.logcat(Log.INFO, LOG_TAG, "Open app invitation: " + result.getStatus());

        if (result.getStatus().isSuccess()) {
            final Intent intent = result.getInvitationIntent();
            final String deepLink = AppInviteReferral.getDeepLink(intent);
            final String invitationId = AppInviteReferral.getInvitationId(intent);

            FirebaseCrash.logcat(Log.INFO, LOG_TAG, String.format("Open app invitation (id: %s): %s", invitationId, deepLink));
        }
    }

    private void showProgressDialog() {
        if (mProgressDialog == null) {
            mProgressDialog = new ProgressDialog(this);
            mProgressDialog.setCancelable(false);
            mProgressDialog.setMessage(getString(progress_loading));
        }

        mProgressDialog.show();
    }

    private void hideProgressDialog() {
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            mProgressDialog.dismiss();
        }
    }

    private void startSignInProcess() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "Start PATH UI Auth login process.");
            final List<AuthUI.IdpConfig> providers = Arrays.asList(
                    new AuthUI.IdpConfig.Builder(AuthUI.GOOGLE_PROVIDER).build(),
                    new AuthUI.IdpConfig.Builder(AuthUI.FACEBOOK_PROVIDER).build());

            final Intent loginIntent = AuthUI.getInstance()
                    .createSignInIntentBuilder()
                    .setProviders(providers)
                    .setIsSmartLockEnabled(!BuildConfig.DEBUG)
                    .setLogo(R.drawable.ic_directions_bus_purple_128dp)
                    .setTheme(R.style.AppTheme)
                    .build();

            startActivityForResult(loginIntent, RC_SIGN_IN);
        }
    }

    private void checkLocationPermissions() {
        boolean hasPermissions = true;
        for (String permission : LOCATION_PERMISSIONS) {
            if (ActivityCompat.checkSelfPermission(this, permission) != PERMISSION_GRANTED) {
                FirebaseCrash.logcat(Log.INFO, LOG_TAG, "Missing permission: " + permission);
                hasPermissions = false;
            }
        }

        if (!hasPermissions) {
            ActivityCompat.requestPermissions(this, LOCATION_PERMISSIONS, RC_LOCATION_PERMISSIONS);
        }
    }

    @Override
    public void onConnectionFailed(@NonNull final ConnectionResult connectionResult) {

    }

    private class CurrentUserListener implements ValueEventListener {
        final Context mContext;
        String mUid;
        DatabaseReference mFirebaseUserDatabaseRef;

        CurrentUserListener(final Context context) {
            mContext = context;
        }

        public void start() {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "Starting current user data listener");
            final FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
            if (firebaseUser == null) {
                stop();
                return;
            }
            mUid = firebaseUser.getUid();

            if (mFirebaseUserDatabaseRef == null) {
                mFirebaseUserDatabaseRef = FirebaseDatabase.getInstance().getReference(USERS).child(mUid);
            }
            mFirebaseUserDatabaseRef.addValueEventListener(this);
        }

        public void stop() {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "Stopping current user data listener");
            if (mFirebaseUserDatabaseRef == null) {
                return;
            }
            mFirebaseUserDatabaseRef.removeEventListener(this);
        }

        @Override
        public void onDataChange(final DataSnapshot snapshot) {
            if (!snapshot.exists()) {
                final User user = User.createRandom(mContext, mUid);
                FirebaseCrash.logcat(Log.INFO, LOG_TAG, "Creating new user profile: " + user);
                mFirebaseUserDatabaseRef.setValue(user);
                return;
            }

            final User user = snapshot.getValue(User.class);
            FirebaseCrash.logcat(Log.INFO, LOG_TAG, "Got user profile from database: " + user);
            setTitle(user.getHandle());

            stop();
            hideProgressDialog();
        }

        @Override
        public void onCancelled(final DatabaseError error) {
            FirebaseCrash.logcat(Log.ERROR, LOG_TAG, "PATH error: " + error.getMessage());
            hideProgressDialog();
        }
    }
}
