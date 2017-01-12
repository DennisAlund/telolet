package se.oddbit.telolet;

import android.support.annotation.NonNull;
import android.util.Log;

import com.google.firebase.crash.FirebaseCrash;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;

import java.util.HashSet;
import java.util.Set;

import se.oddbit.telolet.models.Telolet;

import static se.oddbit.telolet.util.Constants.Database.TELOLETS_RECEIVED;
import static se.oddbit.telolet.util.Constants.Database.TELOLETS_SENT;


public class TeloletListener implements ChildEventListener {
    private static final String LOG_TAG = TeloletListener.class.getSimpleName();

    private final Query mRequestsReceivedQuery;
    private final Query mRequestsSentQuery;
    private final Set<OnTeloletEvent> mTeloletEventListeners = new HashSet<>();

    public interface OnTeloletEvent {
        void onTeloletRequest(final Telolet telolet);

        void onTeloletResponse(final Telolet telolet);
    }

    public TeloletListener(@NonNull final String uid) {
        mRequestsReceivedQuery = FirebaseDatabase.getInstance()
                .getReference(TELOLETS_RECEIVED)
                .child(uid)
                .orderByChild(Telolet.ATTR_REPLIED_AT)
                .equalTo(null);

        mRequestsSentQuery = FirebaseDatabase.getInstance()
                .getReference(TELOLETS_SENT)
                .child(uid)
                .orderByChild(Telolet.ATTR_REPLIED_AT)
                .equalTo(null);

    }

    public void start() {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "start");
        mRequestsReceivedQuery.addChildEventListener(this);
        mRequestsSentQuery.addChildEventListener(this);
    }

    public void stop() {
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
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onChildAdded: " + telolet);
        handleTeloletData(telolet);
    }

    @Override
    public void onChildChanged(final DataSnapshot snapshot, final String previousChildName) {
        final Telolet telolet = snapshot.getValue(Telolet.class);
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onChildChanged: " + telolet);
        handleTeloletData(telolet);
    }

    @Override
    public void onChildRemoved(final DataSnapshot snapshot) {
        final Telolet telolet = snapshot.getValue(Telolet.class);
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onChildRemoved: " + telolet);
    }

    @Override
    public void onChildMoved(final DataSnapshot snapshot, final String previousChildName) {
        final Telolet telolet = snapshot.getValue(Telolet.class);
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onChildMoved: " + telolet);
    }

    @Override
    public void onCancelled(final DatabaseError error) {
        FirebaseCrash.logcat(Log.ERROR, LOG_TAG, error.getMessage());
        FirebaseCrash.report(error.toException());
    }

    private void handleTeloletData(final Telolet telolet) {
        if (telolet.getRepliedAt() == null) {
            FirebaseCrash.logcat(Log.VERBOSE, LOG_TAG, "Telolet request: " + telolet);
            for (OnTeloletEvent teloletEventListener : mTeloletEventListeners) {
                teloletEventListener.onTeloletRequest(telolet);
            }
        } else {
            FirebaseCrash.logcat(Log.VERBOSE, LOG_TAG, "Telolet response: " + telolet);
            for (OnTeloletEvent teloletEventListener : mTeloletEventListeners) {
                teloletEventListener.onTeloletResponse(telolet);
            }
        }
    }
}
