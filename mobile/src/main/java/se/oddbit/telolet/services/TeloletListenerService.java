package se.oddbit.telolet.services;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.crash.FirebaseCrash;

import se.oddbit.telolet.TeloletListener;
import se.oddbit.telolet.models.Telolet;

public class TeloletListenerService extends Service implements FirebaseAuth.AuthStateListener, TeloletListener.OnTeloletEvent {
    private static final String LOG_TAG = TeloletListenerService.class.getSimpleName();

    private TeloletListener mTeloletListener;

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
        FirebaseCrash.logcat(Log.WARN, LOG_TAG, "should show notification: " + telolet);

        final FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        assert firebaseUser != null;
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
