package se.oddbit.telolet.activities;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.auth.ui.ResultCodes;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.crash.FirebaseCrash;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.Arrays;
import java.util.List;

import se.oddbit.telolet.BuildConfig;
import se.oddbit.telolet.R;
import se.oddbit.telolet.models.User;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static se.oddbit.telolet.R.string.progress_loading;
import static se.oddbit.telolet.util.Constants.Database.USERS;

public class LaunchActivity extends AppCompatActivity
        implements View.OnClickListener, FirebaseAuth.AuthStateListener, ValueEventListener {
    private static final String LOG_TAG = LaunchActivity.class.getSimpleName();
    private static final int RC_SIGN_IN = 42;
    private static final int RC_LOCATION_PERMISSIONS = 0x10c;
    private static final String[] LOCATION_PERMISSIONS = new String[]{
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
    };

    private ProgressDialog mProgressDialog;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launch);

        MobileAds.initialize(getApplicationContext(), getString(R.string.banner_ad_unit_id));
        checkLocationPermissions();

        findViewById(R.id.anonymous_login_button).setVisibility(BuildConfig.DEBUG ? View.VISIBLE : View.GONE);
        if (!BuildConfig.DEBUG) {
            startSignInProcess();
        }
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
        FirebaseAuth.getInstance().addAuthStateListener(this);
    }


    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != RC_SIGN_IN) {
            return;
        }

        switch (resultCode) {
            case RESULT_OK:
                FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onActivityResult: Finished PATH UI Auth Process Successfully.");
                break;

            case RESULT_CANCELED:
                FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onActivityResult: Cancelled PATH UI Auth Process");
                startSignInProcess();
                break;

            case ResultCodes.RESULT_NO_NETWORK:
                FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onActivityResult: Failed PATH UI Auth Process: no network");
                Toast.makeText(this, R.string.no_internet_connection, Toast.LENGTH_LONG).show();
                startSignInProcess();
                break;
        }
    }

    @Override
    public void onClick(final View view) {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onClick: Sign in anonymously!");
        FirebaseAuth.getInstance().signInAnonymously();
    }

    @Override
    public void onAuthStateChanged(@NonNull final FirebaseAuth firebaseAuth) {
        final FirebaseUser firebaseUser = firebaseAuth.getCurrentUser();
        if (firebaseUser == null) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onAuthStateChanged: NOT LOGGED IN");
            return;
        }
        if (!BuildConfig.DEBUG && firebaseUser.isAnonymous()) {
            FirebaseCrash.logcat(Log.WARN, LOG_TAG, "Anonymous users are not allowed in production: " + firebaseUser.getUid());
            AuthUI.getInstance().signOut(this);
            return;
        }

        showProgressDialog();
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG,
                String.format("onAuthStateChanged: provider=%s, email=%s, uid=%s",
                        firebaseUser.getProviderId(), firebaseUser.getEmail(), firebaseUser.getUid()));

        final Bundle bundle = new Bundle();
        bundle.putString(FirebaseAnalytics.Param.CONTENT_TYPE, firebaseUser.getProviderId());
        FirebaseAnalytics.getInstance(this).logEvent(FirebaseAnalytics.Event.LOGIN, bundle);

        final String uid = firebaseUser.getUid();
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "Looking for user data for user id: " + uid);
        FirebaseDatabase.getInstance().getReference(USERS).child(uid).addValueEventListener(this);
    }

    @Override
    public void onDataChange(final DataSnapshot snapshot) {
        User user = snapshot.getValue(User.class);
        if (user == null) {
            // Make sure to check the initialization also, because location service might
            final FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
            assert firebaseUser != null;

            final String uid = firebaseUser.getUid();

            user = User.createRandom(this, uid);
            FirebaseCrash.logcat(Log.INFO, LOG_TAG, "Creating new user profile: " + user);
            FirebaseDatabase.getInstance().getReference(USERS).child(uid).setValue(user);
            return;
        }

        if (!user.isValid()) {
            FirebaseCrash.logcat(Log.ERROR, LOG_TAG, "User doesn't seem to be completely initialized.");
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "Perhaps services are still running while user logged out? Logging out and try to make services stop.");
            AuthUI.getInstance().signOut(LaunchActivity.this);
            return;
        }

        FirebaseDatabase.getInstance().getReference(USERS).child(user.getUid()).removeEventListener(this);
        FirebaseDatabase.getInstance()
                .getReference(USERS)
                .child(user.getUid())
                .child(User.ATTR_LAST_LOGIN)
                .setValue(ServerValue.TIMESTAMP)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull final Task<Void> task) {
                        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "Starting main activity");
                        hideProgressDialog();
                        startActivity(new Intent(LaunchActivity.this, MainActivity.class));
                        finish();
                    }
                });
    }

    @Override
    public void onCancelled(final DatabaseError error) {
        FirebaseCrash.logcat(Log.ERROR, LOG_TAG, "Error getting user data: " + error.getMessage());
        FirebaseCrash.report(error.toException());
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
}
