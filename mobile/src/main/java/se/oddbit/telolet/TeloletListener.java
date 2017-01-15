package se.oddbit.telolet;

import android.support.annotation.NonNull;
import android.util.Log;

import com.google.firebase.crash.FirebaseCrash;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;

import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import se.oddbit.telolet.models.Telolet;

import static se.oddbit.telolet.util.Constants.Database.TELOLET_REQUESTS_RECEIVED;
import static se.oddbit.telolet.util.Constants.Database.TELOLET_REQUESTS_SENT;
import static se.oddbit.telolet.util.Constants.RemoteConfig.RESPONSE_THRESHOLD_MILLISEC;


public class TeloletListener implements ChildEventListener {
    private static final String LOG_TAG = TeloletListener.class.getSimpleName();

    private final String mUid;
    private final Query mRequestsReceivedQuery;
    private final Query mRequestsSentQuery;
    private final Set<OnTeloletEvent> mTeloletEventListeners = new HashSet<>();

    public interface OnTeloletEvent {
        void onTeloletRequest(final Telolet telolet);

        void onTeloletResolved(final Telolet telolet);

        void onTeloletTimeout(final Telolet telolet);
    }

    public TeloletListener(@NonNull final String uid) {
        mUid = uid;

        // Query unresolved, received requests
        mRequestsReceivedQuery = FirebaseDatabase.getInstance()
                .getReference(TELOLET_REQUESTS_RECEIVED)
                .child(uid)
                .orderByChild(Telolet.ATTR_RESOLVED_AT)
                .equalTo(null);

        // Query unresolved, sent requests
        mRequestsSentQuery = FirebaseDatabase.getInstance()
                .getReference(TELOLET_REQUESTS_SENT)
                .child(uid)
                .orderByChild(Telolet.ATTR_RESOLVED_AT)
                .equalTo(null);
    }

    public void start() {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "start");
        mRequestsReceivedQuery.addChildEventListener(this);
        mRequestsSentQuery.addChildEventListener(this);
    }

    void stop() {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "stop");
        mRequestsReceivedQuery.removeEventListener(this);
        mRequestsSentQuery.removeEventListener(this);
    }

    public void reset() {
        stop();
        mTeloletEventListeners.clear();
    }

    public void addTeloletRequestListener(final OnTeloletEvent teloletEventListener) {
        mTeloletEventListeners.add(teloletEventListener);
    }

    @Override
    public void onChildAdded(final DataSnapshot snapshot, final String previousChildName) {
        final Telolet telolet = snapshot.getValue(Telolet.class);
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, String.format("onChildAdded: key=%s, val=%s ", snapshot.getKey(), telolet));

        notifyRequest(telolet);
    }

    @Override
    public void onChildChanged(final DataSnapshot snapshot, final String previousChildName) {
        final Telolet telolet = snapshot.getValue(Telolet.class);
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, String.format("onChildChanged: key=%s, val=%s ", snapshot.getKey(), telolet));
    }

    @Override
    public void onChildRemoved(final DataSnapshot snapshot) {
        final Telolet telolet = snapshot.getValue(Telolet.class);
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, String.format("onChildRemoved: key=%s, val=%s ", snapshot.getKey(), telolet));
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, String.format("Telolet request by current user '%s' was resolved: %s", mUid, telolet));

        if (isOverdue(telolet)) {
            notifyTimeout(telolet);
        } else {
            notifyResolve(telolet);
        }
    }

    @Override
    public void onChildMoved(final DataSnapshot snapshot, final String previousChildName) {
        final Telolet telolet = snapshot.getValue(Telolet.class);
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, String.format("onChildMoved: key=%s, val=%s ", snapshot.getKey(), telolet));
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onChildMoved: " + telolet);
    }

    @Override
    public void onCancelled(final DatabaseError error) {
        FirebaseCrash.logcat(Log.ERROR, LOG_TAG, error.getMessage());
        FirebaseCrash.report(error.toException());
    }

    private void notifyRequest(final Telolet telolet) {
        for (OnTeloletEvent teloletEventListener : mTeloletEventListeners) {
            teloletEventListener.onTeloletRequest(telolet);
        }
    }

    private void notifyResolve(final Telolet telolet) {
        for (OnTeloletEvent teloletEventListener : mTeloletEventListeners) {
            teloletEventListener.onTeloletResolved(telolet);
        }
    }

    private void notifyTimeout(final Telolet telolet) {
        for (OnTeloletEvent teloletEventListener : mTeloletEventListeners) {
            teloletEventListener.onTeloletTimeout(telolet);
        }
    }

    private boolean isOverdue(final Telolet telolet) {
        final long threshold = FirebaseRemoteConfig.getInstance().getLong(RESPONSE_THRESHOLD_MILLISEC);
        final long currentTimestamp = new Date().getTime();
        final long duration = currentTimestamp - telolet.getRequestedAt();
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG,
                String.format(Locale.getDefault(),
                        "isOverdue: threshold=%d, currentTimestamp=%d, duration=%d",
                        threshold, currentTimestamp, duration));
        return duration > threshold;
    }
}
