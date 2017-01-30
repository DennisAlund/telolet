package se.oddbit.telolet.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.firebase.ui.auth.AuthUI;
import com.google.android.gms.appinvite.AppInvite;
import com.google.android.gms.appinvite.AppInviteInvitationResult;
import com.google.android.gms.appinvite.AppInviteReferral;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.crash.FirebaseCrash;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;

import se.oddbit.telolet.BuildConfig;
import se.oddbit.telolet.R;
import se.oddbit.telolet.broadcast.CloudMessagesBroadcastReceiver;
import se.oddbit.telolet.fragments.MainActivityFragment;
import se.oddbit.telolet.models.User;
import se.oddbit.telolet.services.CloudMessagingInstanceIdService;
import se.oddbit.telolet.services.CloudMessagingService;
import se.oddbit.telolet.services.LocationService;
import se.oddbit.telolet.services.PlayTeloletService;
import se.oddbit.telolet.services.TeloletReceivedRequestService;
import se.oddbit.telolet.services.TeloletSentRequestService;

import static se.oddbit.telolet.util.Constants.Database.USERS;

public class MainActivity extends AppCompatActivity
        implements ValueEventListener, FirebaseAuth.AuthStateListener, GoogleApiClient.OnConnectionFailedListener, ResultCallback<AppInviteInvitationResult> {
    private static final String LOG_TAG = MainActivity.class.getSimpleName();

    private CloudMessagesBroadcastReceiver mBroadcastReceiver;
    private User mCurrentUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mBroadcastReceiver = new CloudMessagesBroadcastReceiver(this);

        // First make sure we listen to user change objects, then try to login and fetch them
        final GoogleApiClient googleApiClient = new GoogleApiClient.Builder(this)
                .addApi(AppInvite.API)
                .enableAutoManage(this, this)
                .build();
        AppInvite.AppInviteApi.getInvitation(googleApiClient, this, true).setResultCallback(this);

        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "Starting all services necessary for the app to run.");
        startService(new Intent(this, CloudMessagingInstanceIdService.class));
        startService(new Intent(this, CloudMessagingService.class));
        startService(new Intent(this, LocationService.class));
        startService(new Intent(this, TeloletReceivedRequestService.class));
        startService(new Intent(this, TeloletSentRequestService.class));
        startService(new Intent(this, PlayTeloletService.class));
    }

    @Override
    protected void onPause() {
        FirebaseCrash.logcat(Log.VERBOSE, LOG_TAG, "onPause");
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);
        mBroadcastReceiver.unregister();
        FirebaseAuth.getInstance().removeAuthStateListener(this);
        stopCurrentUserListener();
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        FirebaseCrash.logcat(Log.VERBOSE, LOG_TAG, "onResume");
        startCurrentUserListener();
        FirebaseAuth.getInstance().addAuthStateListener(this);
        mBroadcastReceiver.register();
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
                        startActivity(new Intent(MainActivity.this, LaunchActivity.class));
                        finish();
                    }
                });
                return true;

            case R.id.action_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;

            case R.id.action_invite_friends:
                startActivity(new Intent(this, InviteFriendsActivity.class));
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onAuthStateChanged(@NonNull final FirebaseAuth firebaseAuth) {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onAuthStateChanged");
        final FirebaseUser firebaseUser = firebaseAuth.getCurrentUser();
        if (firebaseUser == null) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onAuthStateChanged: NOT LOGGED IN");
            startActivity(new Intent(this, LaunchActivity.class));
            finish();
            return;
        }

        FirebaseAuth.getInstance().removeAuthStateListener(this);
        // In case this happened *after* onResume
        startCurrentUserListener();
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

    @Override
    public void onConnectionFailed(@NonNull final ConnectionResult connectionResult) {
        FirebaseCrash.logcat(Log.ERROR, LOG_TAG, connectionResult.getErrorMessage());
        finish();
    }

    @Override
    public void onDataChange(final DataSnapshot snapshot) {
        if (!snapshot.exists()) {
            FirebaseCrash.logcat(Log.WARN, LOG_TAG, "No user profile in database... yet?");
            return;
        }

        mCurrentUser = snapshot.getValue(User.class);
        FirebaseCrash.logcat(Log.INFO, LOG_TAG, "Got user profile from database: " + mCurrentUser);
        updateUiForCurrentUser();
    }

    @Override
    public void onCancelled(final DatabaseError error) {
        FirebaseCrash.logcat(Log.ERROR, LOG_TAG, "Error getting user data: " + error.getMessage());
        FirebaseCrash.report(error.toException());
    }

    @Override
    public void onAttachFragment(final Fragment fragment) {
        super.onAttachFragment(fragment);
        if (fragment instanceof MainActivityFragment) {
            updateUiForCurrentUser();
        }
    }

    private void startCurrentUserListener() {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "startCurrentUserListener");
        final FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "startCurrentUserListener: Not yet logged in. Can't start listing data.");
            return;
        }
        final String uid = firebaseUser.getUid();
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "startCurrentUserListener: starting to listen for user: " + uid);
        FirebaseDatabase.getInstance().getReference(USERS).child(uid).addValueEventListener(this);
    }

    private void stopCurrentUserListener() {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "stopCurrentUserListener");
        final FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "stopCurrentUserListener: Not yet logged in. Can't stop listing data.");
            return;
        }

        final String uid = firebaseUser.getUid();
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "stopCurrentUserListener: stop to listen for user: " + uid);
        FirebaseDatabase.getInstance().getReference(USERS).child(uid).removeEventListener(this);
    }

    private void updateUiForCurrentUser() {
        if (mCurrentUser == null) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "updateUiForCurrentUser: No user data yet.");
            return;
        }

        setTitle(mCurrentUser.getHandle());
        MainActivityFragment mainActivityFragment = (MainActivityFragment) getSupportFragmentManager().findFragmentById(R.id.main_content_fragment);
        if (mainActivityFragment == null) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "updateUiForCurrentUser: fragment not ready yet: " + MainActivityFragment.class.getSimpleName());
            return;
        }

        mainActivityFragment.setCurrentUser(mCurrentUser);
    }
}
