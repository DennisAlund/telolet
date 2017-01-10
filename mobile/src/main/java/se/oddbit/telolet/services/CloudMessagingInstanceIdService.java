package se.oddbit.telolet.services;

import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.crash.FirebaseCrash;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.FirebaseInstanceIdService;

import se.oddbit.telolet.models.User;

import static se.oddbit.telolet.util.Constants.Database.USERS;

public class CloudMessagingInstanceIdService extends FirebaseInstanceIdService {
    private static final String LOG_TAG = CloudMessagingInstanceIdService.class.getSimpleName();

    public CloudMessagingInstanceIdService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onCreate");
        saveFcmToken();
    }

    @Override
    public void onTokenRefresh() {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onTokenRefresh");
        saveFcmToken();
    }

    private void saveFcmToken() {
        final String fcmToken = FirebaseInstanceId.getInstance().getToken();
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "FCM Token: " + fcmToken);

        final FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "saveFcmToken: not yet logged in");
            return;
        }

        FirebaseDatabase
                .getInstance()
                .getReference(USERS)
                .child(firebaseUser.getUid())
                .child(User.ATTR_FCM_TOKEN)
                .setValue(fcmToken);
    }
}
