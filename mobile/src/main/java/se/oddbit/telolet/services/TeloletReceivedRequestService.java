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
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import se.oddbit.telolet.R;
import se.oddbit.telolet.activities.MainActivity;
import se.oddbit.telolet.models.Telolet;

import static se.oddbit.telolet.util.Constants.Database.TELOLET_REQUESTS_RECEIVED;
import static se.oddbit.telolet.util.Constants.Database.TELOLET_REQUESTS_SENT;
import static se.oddbit.telolet.util.Constants.Database.USER_STATES;
import static se.oddbit.telolet.util.Constants.RemoteConfig.TELOLET_TIMEOUT_SECONDS;

public class TeloletReceivedRequestService extends Service implements FirebaseAuth.AuthStateListener, ChildEventListener {
    private static final String LOG_TAG = TeloletReceivedRequestService.class.getSimpleName();
    private static final int REQUEST_NOTIFICATION_ID = 1;

    private NotificationManager mNotificationManager;
    private int mPendingRequestCounter;
    private Query mPendingReceivedRequests;
    private final Map<String, TimerTask> mRequestTimeoutMap = new HashMap<>();
    private final Timer mRequestTimeoutHandler = new Timer();

    public TeloletReceivedRequestService() {
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

        mPendingReceivedRequests = FirebaseDatabase.getInstance()
                .getReference(TELOLET_REQUESTS_RECEIVED)
                .child(firebaseUser.getUid())
                .orderByChild(Telolet.ATTR_STATE)
                .startAt(Telolet.STATE_PENDING);

        mPendingReceivedRequests.addChildEventListener(this);
    }

    @Override
    public void onDestroy() {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onDestroy");
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

        if (telolet.isState(Telolet.STATE_PENDING)) {
            // Fetch remote config every time to adjust to new potential changes during service lifetime
            final long timeout = FirebaseRemoteConfig.getInstance().getLong(TELOLET_TIMEOUT_SECONDS);
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, String.format(Locale.getDefault(),
                    "onChildAdded: Timeout of %d seconds for received %s", timeout, telolet));

            final TimerTask timerTask = makeTimeoutTaskFor(telolet);
            mRequestTimeoutHandler.schedule(timerTask, timeout * 1000);
            mRequestTimeoutMap.put(telolet.getId(), timerTask);
            makeNotification();
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

        mPendingRequestCounter -= 1;
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onChildRemoved: Pending request count reduced to: " + mPendingRequestCounter);
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
                final String otherUid = telolet.getRequesterUid();
                final String currUid = telolet.getReceiverUid();

                telolet.setState(Telolet.STATE_TIMEOUT);
                final Map<String, Object> updatesMap = new HashMap<>();

                updatesMap.put(String.format("/%s/%s/%s", TELOLET_REQUESTS_RECEIVED, currUid, teloletId), telolet.toValueMap());
                updatesMap.put(String.format("/%s/%s/%s", TELOLET_REQUESTS_SENT, otherUid, teloletId), telolet.toValueMap());
                updatesMap.put(String.format("/%s/%s/%s", USER_STATES, currUid, otherUid), null);
                updatesMap.put(String.format("/%s/%s/%s", USER_STATES, otherUid, currUid), null);

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
