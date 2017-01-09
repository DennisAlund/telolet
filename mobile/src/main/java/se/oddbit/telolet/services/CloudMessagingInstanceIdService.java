package se.oddbit.telolet.services;

import android.util.Log;

import com.google.firebase.crash.FirebaseCrash;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.FirebaseInstanceIdService;

public class CloudMessagingInstanceIdService extends FirebaseInstanceIdService {
    private static final String LOG_TAG = CloudMessagingInstanceIdService.class.getSimpleName();
    public CloudMessagingInstanceIdService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onCreate");
    }

    @Override
    public void onTokenRefresh() {
        final String refreshedToken = FirebaseInstanceId.getInstance().getToken();
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onTokenRefresh: " + refreshedToken);
    }
}
