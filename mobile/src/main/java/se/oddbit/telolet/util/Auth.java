package se.oddbit.telolet.util;

import android.util.Log;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.crash.FirebaseCrash;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Locale;

import se.oddbit.telolet.models.PublicUser;

import static se.oddbit.telolet.util.Constants.Firebase.Database.PUBLIC_USERS;

public class Auth {
    private static final String LOG_TAG = Auth.class.getSimpleName();
    private static Auth mInstance = new Auth();
    private FirebaseUser mFirebaseUser;
    private boolean mStarted;
    private PublicUserDataManager mPublicUserDataManager;


    public static Auth getInstance() {
        return mInstance;
    }

    private Auth() {
    }

    public void connect(final FirebaseUser firebaseUser) {
        FirebaseCrash.logcat(Log.INFO, LOG_TAG, String.format(Locale.getDefault(),
                "Connecting Firebase user method: '%s', email: '%s', uid: '%s'",
                firebaseUser.getProviderId(), firebaseUser.getEmail(), firebaseUser.getUid()));
        if (mFirebaseUser != null) {
            disconnect();
        }

        mFirebaseUser = firebaseUser;
        mPublicUserDataManager = new PublicUserDataManager(mFirebaseUser);
        start();
    }

    public void disconnect() {
        FirebaseCrash.logcat(Log.INFO, LOG_TAG, String.format(Locale.getDefault(),
                "Disconnecting Firebase user uid: '%s'", mFirebaseUser.getUid()));
        stop();
        mFirebaseUser = null;
        mPublicUserDataManager = null;
    }

    public void start() {
        if (mFirebaseUser == null) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "start: No Firebase user set. Cant't start yet.");
            return;
        }

        if (mStarted) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "start: Already started.");
            return;
        }

        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "start: Starting.");
        mStarted = true;
        mPublicUserDataManager.start();
    }

    public void stop() {
        if (!mStarted) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "stop: Not started.");
            return;
        }

        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "stop: Stopping.");
        mStarted = false;
        mPublicUserDataManager.stop();
    }

    public PublicUser getPublicUser() {
        return mPublicUserDataManager.mPublicUser;
    }

    private static class PublicUserDataManager implements ValueEventListener {

        PublicUser mPublicUser;
        final DatabaseReference mDatabaseRef;
        final FirebaseUser mFirebaseUser;


        PublicUserDataManager(final FirebaseUser firebaseUser) {
            mDatabaseRef = FirebaseDatabase.getInstance().getReference(PUBLIC_USERS).child(firebaseUser.getUid());
            mFirebaseUser = firebaseUser;
        }

        void start() {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "Starting " + PublicUserDataManager.class.getSimpleName());
            mDatabaseRef.addValueEventListener(this);
        }

        void stop() {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "Stopping " + PublicUserDataManager.class.getSimpleName());
            mDatabaseRef.removeEventListener(this);
        }

        @Override
        public void onDataChange(final DataSnapshot snapshot) {
            if (!snapshot.exists()) {
                FirebaseCrash.logcat(Log.INFO, LOG_TAG, String.format("No user for %s creating a random new one.", mFirebaseUser.getUid()));
                mDatabaseRef.setValue(PublicUser.createRandom(mFirebaseUser.getUid()));
                return;
            }

            mPublicUser = snapshot.getValue(PublicUser.class);
        }

        @Override
        public void onCancelled(final DatabaseError error) {
            FirebaseCrash.logcat(Log.WARN, LOG_TAG, String.format("Database error for %s: %s", PublicUserDataManager.class.getSimpleName(), error.getMessage()));
        }
    }

}
