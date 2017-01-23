package se.oddbit.telolet.services;

import android.support.annotation.NonNull;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.crash.FirebaseCrash;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.FirebaseInstanceIdService;

import se.oddbit.telolet.models.User;

import static se.oddbit.telolet.util.Constants.Database.USERS;

public class CloudMessagingInstanceIdService extends FirebaseInstanceIdService implements FirebaseAuth.AuthStateListener {
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

    @Override
    public void onAuthStateChanged(@NonNull final FirebaseAuth firebaseAuth) {
        final FirebaseUser firebaseUser = firebaseAuth.getCurrentUser();
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onAuthStateChanged: firebaseUser=" + firebaseUser);
        if (firebaseUser == null) {
            stopSelf();
        }
    }

    private void saveFcmToken() {
        final String fcmToken = FirebaseInstanceId.getInstance().getToken();
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "FCM Token: " + fcmToken);

        final FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "saveFcmToken: User has been logged out. Stop service.");
            stopSelf();
            return;
        }

        final String uid = firebaseUser.getUid();
        FirebaseDatabase
                .getInstance()
                .getReference(USERS)
                .child(uid)
                .runTransaction(new Transaction.Handler() {
                    @Override
                    public Transaction.Result doTransaction(final MutableData mutableData) {
                        final User user = mutableData.getValue(User.class);
                        if (user == null) {
                            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, String.format("saveFcmToken:doTransaction: will retry, user=%s is not yet set", uid));
                            return Transaction.success(mutableData);
                        }

                        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, String.format("saveFcmToken:doTransaction: set new fcmToken=%s for user=%s", fcmToken, uid));
                        user.setFcmToken(fcmToken);
                        mutableData.setValue(user);
                        return Transaction.success(mutableData);
                    }

                    @Override
                    public void onComplete(final DatabaseError error, final boolean committed, final DataSnapshot snapshot) {
                        if (error != null) {
                            FirebaseCrash.logcat(Log.ERROR, LOG_TAG, error.getMessage());
                            FirebaseCrash.report(error.toException());
                            return;
                        }

                        if (committed && snapshot.exists()) {
                            final User user = snapshot.getValue(User.class);
                            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, String.format("saveFcmToken: transaction committed new fcmToken %s for %s", user.getFcmToken(), user));
                        }
                    }
                });
    }
}
