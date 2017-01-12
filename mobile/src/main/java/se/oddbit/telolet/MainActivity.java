package se.oddbit.telolet;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
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
import com.google.firebase.crash.FirebaseCrash;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;

import se.oddbit.telolet.models.User;
import se.oddbit.telolet.services.CloudMessagingInstanceIdService;
import se.oddbit.telolet.services.CloudMessagingService;
import se.oddbit.telolet.services.LocationService;
import se.oddbit.telolet.services.TeloletListenerService;

public class MainActivity extends AppCompatActivity
        implements ValueEventListener, FirebaseAuth.AuthStateListener, GoogleApiClient.OnConnectionFailedListener, ResultCallback<AppInviteInvitationResult> {
    private static final String LOG_TAG = MainActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

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
                        killActivity();
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
    public void onAuthStateChanged(@NonNull final FirebaseAuth firebaseAuth) {
        if (firebaseAuth.getCurrentUser() == null) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onAuthStateChanged: NOT LOGGED IN");
            killActivity();
        }
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
        killActivity();
    }

    @Override
    public void onDataChange(final DataSnapshot snapshot) {
        if (!snapshot.exists()) {
            FirebaseCrash.logcat(Log.WARN, LOG_TAG, "No user profile in database... yet?");
            return;
        }

        final User user = snapshot.getValue(User.class);
        FirebaseCrash.logcat(Log.INFO, LOG_TAG, "Got user profile from database: " + user);
        setTitle(user.getHandle());
    }

    @Override
    public void onCancelled(final DatabaseError error) {
        FirebaseCrash.logcat(Log.ERROR, LOG_TAG, "Error getting user data: " + error.getMessage());
        FirebaseCrash.report(error.toException());
    }

    private void killActivity() {
        stopService(new Intent(this, CloudMessagingInstanceIdService.class));
        stopService(new Intent(this, CloudMessagingService.class));
        stopService(new Intent(this, LocationService.class));
        stopService(new Intent(this, TeloletListenerService.class));
    }
}
