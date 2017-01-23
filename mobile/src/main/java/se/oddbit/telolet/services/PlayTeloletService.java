package se.oddbit.telolet.services;

import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.crash.FirebaseCrash;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;

import java.util.HashMap;
import java.util.Map;

import se.oddbit.telolet.R;
import se.oddbit.telolet.models.Telolet;
import se.oddbit.telolet.models.UserState;

import static se.oddbit.telolet.util.Constants.Database.TELOLET_REQUESTS_RECEIVED;
import static se.oddbit.telolet.util.Constants.Database.TELOLET_REQUESTS_SENT;
import static se.oddbit.telolet.util.Constants.Database.USER_STATES;

public class PlayTeloletService extends Service implements FirebaseAuth.AuthStateListener, ChildEventListener, MediaPlayer.OnCompletionListener {
    private static final String LOG_TAG = PlayTeloletService.class.getSimpleName();

    private final Object mStateLock = new Object();
    private final Map<String, Object> mResolvedStatesMap = new HashMap<>();

    private MediaPlayer mMediaPlayer;
    private Query mUserStatesRef;
    private boolean mIsPlaying;

    public PlayTeloletService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onBind");
        return null;
    }

    @Override
    public void onCreate() {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onCreate");
        final FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        assert firebaseUser != null;

        // Only play on the device that receives the telolet (the one sending request)
        mUserStatesRef = FirebaseDatabase.getInstance()
                .getReference(USER_STATES)
                .child(firebaseUser.getUid())
                .orderByChild(UserState.ATTR_STATE)
                .equalTo(UserState.TELOLET_RECEIVED);

        super.onCreate();
    }

    @Override
    public void onDestroy() {
        mUserStatesRef.removeEventListener(this);
        if (mMediaPlayer != null) {
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
        super.onDestroy();
    }

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        super.onStartCommand(intent, flags, startId);
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onStartCommand");

        mUserStatesRef.addChildEventListener(this);
        return START_STICKY;
    }

    @Override
    public void onCompletion(final MediaPlayer mediaPlayer) {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onTeloletResolved: Finished playing telolet");
        synchronized (mStateLock) {
            mIsPlaying = false;

            // Update all the users whose states were updated during the time the song was playing
            FirebaseDatabase.getInstance().getReference().updateChildren(mResolvedStatesMap);
            mResolvedStatesMap.clear();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }

    }

    @Override
    public void onAuthStateChanged(@NonNull final FirebaseAuth firebaseAuth) {
        final FirebaseUser firebaseUser = firebaseAuth.getCurrentUser();
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onAuthStateChanged: firebaseUser=" + firebaseUser);
        if (firebaseUser == null) {
            stopSelf();
        }
    }

    @Override
    public void onChildAdded(final DataSnapshot snapshot, final String previousChildName) {
        final UserState userState = snapshot.getValue(UserState.class);
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onChildAdded: " + userState);
        final FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "User was logged out while service was running. Stopping.");
            stopSelf();
            return;
        }

        final String currUid = firebaseUser.getUid();
        final String otherUid = snapshot.getKey();
        final Telolet telolet = userState.getTelolet();
        telolet.setState(Telolet.STATE_RESOLVED);

        synchronized (mStateLock) {
            // Collect all user states that get resolved while playing the song. Update all at once afterwards.
            mResolvedStatesMap.put(String.format("/%s/%s/%s", TELOLET_REQUESTS_SENT, currUid, telolet.getId()), telolet.toValueMap());
            mResolvedStatesMap.put(String.format("/%s/%s/%s", TELOLET_REQUESTS_RECEIVED, otherUid, telolet.getId()), telolet.toValueMap());
            mResolvedStatesMap.put(String.format("/%s/%s/%s", USER_STATES, currUid, otherUid), null);
            mResolvedStatesMap.put(String.format("/%s/%s/%s", USER_STATES, otherUid, currUid), null);
            if (mIsPlaying) {
                return;
            }
            mIsPlaying = true;
        }

        FirebaseCrash.logcat(Log.INFO, LOG_TAG, "\n**********\n**********\n TELOLELOLET \n**********\n**********");
        mMediaPlayer = MediaPlayer.create(this, R.raw.klakson_telolet);
        mMediaPlayer.setOnCompletionListener(this);
        mMediaPlayer.start();
    }

    @Override
    public void onChildChanged(final DataSnapshot snapshot, final String previousChildName) {
        // N/A
    }

    @Override
    public void onChildRemoved(final DataSnapshot snapshot) {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onChildRemoved: " + snapshot.getKey());
    }

    @Override
    public void onChildMoved(final DataSnapshot snapshot, final String previousChildName) {
        // N/A
    }

    @Override
    public void onCancelled(final DatabaseError error) {
        FirebaseCrash.logcat(Log.ERROR, LOG_TAG, error.getMessage());
        FirebaseCrash.report(error.toException());
    }
}
