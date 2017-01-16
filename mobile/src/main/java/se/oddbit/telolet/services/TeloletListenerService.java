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

import java.util.ArrayList;
import java.util.List;

import se.oddbit.telolet.MainActivity;
import se.oddbit.telolet.R;
import se.oddbit.telolet.TeloletListener;
import se.oddbit.telolet.models.Telolet;

public class TeloletListenerService extends Service implements FirebaseAuth.AuthStateListener, TeloletListener.OnTeloletEvent {
    private static final String LOG_TAG = TeloletListenerService.class.getSimpleName();
    private static final int REQUEST_NOTIFICATION_ID = 1;

    private TeloletListener mTeloletListener;
    private NotificationManager mNotificationManager;
    private final List<Telolet> mTeloletList = new ArrayList<>();

    public TeloletListenerService() {
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
        createTeloletListener();
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

    }

    @Override
    public void onDestroy() {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onDestroy");
        mTeloletListener.reset();
        super.onDestroy();
    }

    @Override
    public void onAuthStateChanged(@NonNull final FirebaseAuth firebaseAuth) {
        final FirebaseUser firebaseUser = firebaseAuth.getCurrentUser();
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onAuthStateChanged: firebaseUser=" + firebaseUser);
        if (firebaseUser == null) {
            stopSelf();
            return;
        }

        createTeloletListener();
    }

    @Override
    public void onTeloletRequest(final Telolet telolet) {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onTeloletRequest: " + telolet);

        final FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            FirebaseCrash.logcat(Log.INFO, LOG_TAG, "User was logged out while service was running. Stopping.");
            stopSelf();
            return;
        }

        if (telolet.isRequestedBy(firebaseUser.getUid())) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onTeloletRequest: own request is not notified " + telolet);
            return;
        }

        mTeloletList.add(telolet);

        final Intent intent = new Intent(this, MainActivity.class);
        final PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        final NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                .setContentTitle(getString(R.string.om_telolet_om))
                .setContentText(getString(R.string.notification_pending_telolet_requests))
                .setSmallIcon(R.drawable.ic_directions_bus_white_24dp)
                .setNumber(mTeloletList.size())
                .setContentIntent(pendingIntent);

        mNotificationManager.notify(REQUEST_NOTIFICATION_ID, builder.build());
    }

    @Override
    public void onTeloletResolved(final Telolet telolet) {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onTeloletResponse: " + telolet);
        FirebaseCrash.logcat(Log.WARN, LOG_TAG, "should show notification: " + telolet);
    }

    @Override
    public void onTeloletTimeout(final Telolet telolet) {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onTeloletTimeout: " + telolet);
    }

    private synchronized void createTeloletListener() {
        final FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (mTeloletListener == null && firebaseUser != null) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "createTeloletListener: Creating new listener");
            mTeloletListener = new TeloletListener(firebaseUser.getUid());
            mTeloletListener.addTeloletRequestListener(this);
            mTeloletListener.start();
        }
    }
}
