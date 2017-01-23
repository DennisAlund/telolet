package se.oddbit.telolet.services;

import android.support.annotation.NonNull;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.crash.FirebaseCrash;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import se.oddbit.telolet.broadcast.CloudMessagesBroadcastReceiver;

public class CloudMessagingService extends FirebaseMessagingService implements FirebaseAuth.AuthStateListener {
    private static final String LOG_TAG = CloudMessagingService.class.getSimpleName();

    public CloudMessagingService() {
    }

    @Override
    public void onCreate() {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onCreate");
        super.onCreate();
    }

    @Override
    public void onMessageReceived(final RemoteMessage remoteMessage) {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onMessageReceived: from=" + remoteMessage.getFrom());
        final FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onMessageReceived: User has been logged out. Stop service.");
            stopSelf();
            return;
        }

        if (remoteMessage.getData().size() > 0) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onMessageReceived: data=" + remoteMessage.getData());
        }

        if (remoteMessage.getNotification() == null) {
            return;
        }
        final String title = remoteMessage.getNotification().getTitle();
        final String body = remoteMessage.getNotification().getBody();
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, String.format("Message Notification title=%s, body=%s", title, body));
        CloudMessagesBroadcastReceiver.createMessage(this, title, body);
    }

    @Override
    public void onAuthStateChanged(@NonNull final FirebaseAuth firebaseAuth) {
        final FirebaseUser firebaseUser = firebaseAuth.getCurrentUser();
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onAuthStateChanged: firebaseUser=" + firebaseUser);
        if (firebaseUser == null) {
            stopSelf();
        }
    }
}
