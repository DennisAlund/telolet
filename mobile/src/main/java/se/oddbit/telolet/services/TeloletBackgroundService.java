package se.oddbit.telolet.services;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.crash.FirebaseCrash;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ServerValue;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import se.oddbit.telolet.R;
import se.oddbit.telolet.activities.MainActivity;
import se.oddbit.telolet.models.Telolet;
import se.oddbit.telolet.models.UserState;

import static se.oddbit.telolet.models.UserState.RESOLVED;
import static se.oddbit.telolet.util.Constants.Database.TELOLET_REQUESTS_RECEIVED;
import static se.oddbit.telolet.util.Constants.Database.TELOLET_REQUESTS_SENT;
import static se.oddbit.telolet.util.Constants.Database.USER_STATES;
import static se.oddbit.telolet.util.Constants.RemoteConfig.RESPONSE_TIMEOUT_SECONDS;

public class TeloletBackgroundService extends Service implements FirebaseAuth.AuthStateListener, ChildEventListener {
    private static final String LOG_TAG = TeloletBackgroundService.class.getSimpleName();
    private static final int REQUEST_NOTIFICATION_ID = 1;

    private NotificationManager mNotificationManager;
    private int mPendingRequestCounter;
    private Query mPendingSentRequests;
    private Query mPendingReceivedRequests;
    private Map<String, TimerTask> mRequestTimeoutMap = new HashMap<>();
    private final Timer mRequestTimeoutHandler = new Timer();



    public TeloletBackgroundService() {
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
        mPendingRequestCounter = 0;
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        final FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        assert firebaseUser != null;

        // A query for pending SENT telolet requests. When remote user resolves it will trigger REMOVE
        mPendingSentRequests = FirebaseDatabase.getInstance()
                .getReference(TELOLET_REQUESTS_SENT)
                .child(firebaseUser.getUid())
                .orderByChild(Telolet.ATTR_RESOLVED_AT)
                .equalTo(null);
        mPendingSentRequests.addChildEventListener(this);

        // A query for pending RECEIVED requests. When current client resolve it will trigger REMOVE
        mPendingReceivedRequests = FirebaseDatabase.getInstance()
                .getReference(TELOLET_REQUESTS_RECEIVED)
                .child(firebaseUser.getUid())
                .orderByChild(Telolet.ATTR_RESOLVED_AT)
                .equalTo(null);
        mPendingReceivedRequests.addChildEventListener(this);
    }

    @Override
    public void onDestroy() {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onDestroy");
        mPendingSentRequests.removeEventListener(this);
        mPendingReceivedRequests.removeEventListener(this);
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

        final FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        assert firebaseUser != null;
        final String uid = firebaseUser.getUid();

        if (telolet.getReceiverUid().equals(uid)) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onChildAdded: pending RECEIVE request added: " + telolet);
            mPendingRequestCounter += 1;
            makeNotification();

            FirebaseDatabase.getInstance()
                    .getReference()
                    .child(USER_STATES)
                    .child(uid)
                    .child(telolet.getRequesterUid())
                    .setValue(new UserState(telolet.getId(), UserState.PENDING_RECEIVED));

        } else {
            final long timeout = FirebaseRemoteConfig.getInstance().getLong(RESPONSE_TIMEOUT_SECONDS);
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, String.format(Locale.getDefault(), "onChildAdded: pending request SENT. Timeout of %d seconds for %s", timeout, telolet));

            final TimerTask timerTask = makeTimeoutTaskFor(telolet);
            mRequestTimeoutHandler.schedule(timerTask, timeout * 1000);
            mRequestTimeoutMap.put(telolet.getId(), timerTask);
        }
    }

    @Override
    public void onChildChanged(final DataSnapshot snapshot, final String previousChildName) {
        // N/A
    }

    @Override
    public void onChildRemoved(final DataSnapshot snapshot) {
        final Telolet telolet = snapshot.getValue(Telolet.class);
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onChildRemoved: " + telolet);
        final FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        assert firebaseUser != null;
        final String currUid = firebaseUser.getUid();

        if (telolet.getReceiverUid().equals(currUid)) {
            mPendingRequestCounter -= 1;
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onChildRemoved: Pending request count reduced to: " + mPendingRequestCounter);
            makeNotification();
        } else if (mRequestTimeoutMap.containsKey(telolet.getId())) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onChildRemoved: Telolet not reached timeout yet. Resolve: " + telolet);
            mRequestTimeoutMap.get(telolet.getId()).cancel();
            mRequestTimeoutMap.remove(telolet.getId());

            final String otherUid = telolet.getReceiverUid();
            final UserState userState = new UserState(telolet.getId(), RESOLVED);
            FirebaseDatabase.getInstance().getReference(USER_STATES).child(currUid).child(otherUid).setValue(userState);
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
                final Map<String, Object> updatesMap = new HashMap<>();
                updatesMap.put(String.format("/%s/%s/%s/%s", TELOLET_REQUESTS_SENT, currUid, teloletId, Telolet.ATTR_RESOLVED_AT), ServerValue.TIMESTAMP);
                updatesMap.put(String.format("/%s/%s/%s/%s", TELOLET_REQUESTS_RECEIVED, otherUid, teloletId, Telolet.ATTR_RESOLVED_AT), ServerValue.TIMESTAMP);
                updatesMap.put(String.format("/%s/%s/%s", USER_STATES, currUid, otherUid), null); // Remove state from this user view
                FirebaseDatabase.getInstance().getReference().updateChildren(updatesMap);
            }
        };
    }

    private void makeNotification() {
        final Intent intent = new Intent(this, MainActivity.class);
        final PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        final NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                .setContentTitle(getString(R.string.om_telolet_om))
                .setContentText(getString(R.string.notification_pending_telolet_requests))
                .setSmallIcon(R.drawable.ic_directions_bus_white_24dp)
                .setNumber(mPendingRequestCounter)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);


        mNotificationManager.notify(REQUEST_NOTIFICATION_ID, builder.build());
    }
}
