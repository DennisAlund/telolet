package se.oddbit.telolet.services;

import android.app.Service;
import android.content.Intent;
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
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import se.oddbit.telolet.models.Telolet;

import static se.oddbit.telolet.util.Constants.Database.TELOLET_REQUESTS_RECEIVED;
import static se.oddbit.telolet.util.Constants.Database.TELOLET_REQUESTS_SENT;
import static se.oddbit.telolet.util.Constants.Database.USER_STATES;
import static se.oddbit.telolet.util.Constants.RemoteConfig.TELOLET_TIMEOUT_SECONDS;

public class TeloletSentRequestService extends Service implements FirebaseAuth.AuthStateListener, ChildEventListener {
    private static final String LOG_TAG = TeloletSentRequestService.class.getSimpleName();

    private final Map<String, TimerTask> mRequestTimeoutMap = new HashMap<>();
    private final Timer mRequestTimeoutHandler = new Timer();
    private Query mRequestsRef;

    public TeloletSentRequestService() {
    }


    @Override
    public IBinder onBind(final Intent intent) {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onBind");
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onCreate");

        final FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        assert firebaseUser != null;

        mRequestsRef = FirebaseDatabase.getInstance()
                .getReference(TELOLET_REQUESTS_SENT)
                .child(firebaseUser.getUid())
                .orderByChild(Telolet.ATTR_STATE)
                .startAt(Telolet.STATE_PENDING);

        mRequestsRef.addChildEventListener(this);
    }

    @Override
    public void onDestroy() {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onDestroy");
        mRequestsRef.removeEventListener(this);
        super.onDestroy();
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
        final Telolet telolet = snapshot.getValue(Telolet.class);
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onChildAdded: " + telolet);

        if (telolet.isState(Telolet.STATE_PENDING)) {
            final long timeout = FirebaseRemoteConfig.getInstance().getLong(TELOLET_TIMEOUT_SECONDS);
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, String.format(Locale.getDefault(),
                    "onChildAdded: setting a timeout of %d seconds for %s to be figured out", timeout, telolet));

            final TimerTask timerTask = makeTimeoutTaskFor(telolet);
            mRequestTimeoutHandler.schedule(timerTask, timeout * 1000);
            mRequestTimeoutMap.put(telolet.getId(), timerTask);
        }
    }

    @Override
    public void onChildChanged(final DataSnapshot snapshot, final String previousChildName) {
        final Telolet telolet = snapshot.getValue(Telolet.class);
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onChildChanged: " + telolet);
    }

    @Override
    public void onChildRemoved(final DataSnapshot snapshot) {
        final Telolet telolet = snapshot.getValue(Telolet.class);
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onChildRemoved: " + telolet);

        if (mRequestTimeoutMap.containsKey(telolet.getId())) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onChildRemoved: Telolet not reached timeout yet. Remove the timeout for: " + telolet);
            mRequestTimeoutMap.get(telolet.getId()).cancel();
            mRequestTimeoutMap.remove(telolet.getId());
        } else {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onChildRemoved: Telolet already resolved or timeout: " + telolet);
        }
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

    private TimerTask makeTimeoutTaskFor(final Telolet telolet) {
        return new TimerTask() {
            @Override
            public void run() {
                // Remove itself from the register
                mRequestTimeoutMap.remove(telolet.getId());

                // Create a timeout record
                final String teloletId = telolet.getId();
                final String otherUid = telolet.getReceiverUid();
                final String currUid = telolet.getRequesterUid();

                telolet.setState(Telolet.STATE_TIMEOUT);

                final Map<String, Object> updatesMap = new HashMap<>();
                updatesMap.put(String.format("/%s/%s/%s", TELOLET_REQUESTS_SENT, currUid, teloletId), telolet.toValueMap());
                updatesMap.put(String.format("/%s/%s/%s", TELOLET_REQUESTS_RECEIVED, otherUid, teloletId), telolet.toValueMap());
                updatesMap.put(String.format("/%s/%s/%s", USER_STATES, currUid, otherUid), null);
                updatesMap.put(String.format("/%s/%s/%s", USER_STATES, otherUid, currUid), null);
                FirebaseDatabase.getInstance().getReference().updateChildren(updatesMap);
            }
        };
    }
}
