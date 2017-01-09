package se.oddbit.telolet.services;

import android.util.Log;

import com.google.firebase.crash.FirebaseCrash;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class CloudMessagingService extends FirebaseMessagingService {
    private static final String LOG_TAG = CloudMessagingService.class.getSimpleName();

    public CloudMessagingService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onCreate");
    }

    @Override
    public void onMessageReceived(final RemoteMessage remoteMessage) {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "From: " + remoteMessage.getFrom());

        // Check if message contains a data payload.
        if (remoteMessage.getData().size() > 0) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "Message data payload: " + remoteMessage.getData());
        }

        // Check if message contains a notification payload.
        if (remoteMessage.getNotification() != null) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "Message Notification Body: " + remoteMessage.getNotification().getBody());
        }

        // Also if you intend on generating your own notifications as a result of a received FCM
        // message, here is where that should be initiated. See sendNotification method below.
    }

}
