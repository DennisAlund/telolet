package se.oddbit.telolet.services;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.crash.FirebaseCrash;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import se.oddbit.telolet.models.Telolet;

import static se.oddbit.telolet.util.Constants.Analytics.Events.TELOLET_REQUEST;
import static se.oddbit.telolet.util.Constants.Analytics.Events.TELOLET_RESPONSE;
import static se.oddbit.telolet.util.Constants.Analytics.Param.OLC;
import static se.oddbit.telolet.util.Constants.Database.TELOLET_REQUESTS_RECEIVED;
import static se.oddbit.telolet.util.Constants.Database.TELOLET_REQUESTS_SENT;

public class TeloletRequestService extends IntentService {
    private static final String LOG_TAG = TeloletRequestService.class.getSimpleName();

    private static final String ACTION_REQUEST = "se.oddbit.telolet.services.action.Request";
    private static final String ACTION_REPLY = "se.oddbit.telolet.services.action.Reply";
    private static final String ACTION_TIMEOUT = "se.oddbit.telolet.services.action.Timeout";

    private static final String EXTRA_LOCATION = "se.oddbit.telolet.services.extra.location";
    private static final String EXTRA_RECEIVER_UID = "se.oddbit.telolet.services.extra.receiver_uid";
    private static final String EXTRA_TELOLET_ID = "se.oddbit.telolet.services.extra.telolet_id";

    public TeloletRequestService() {
        super(TeloletRequestService.class.getSimpleName());
    }

    public static void startActionRequest(final Context context, final String receiverUid, final String senderLocation) {
        final Intent intent = new Intent(context, TeloletRequestService.class);
        intent.setAction(ACTION_REQUEST);
        intent.putExtra(EXTRA_RECEIVER_UID, receiverUid);
        intent.putExtra(EXTRA_LOCATION, senderLocation);
        context.startService(intent);
    }

    public static void startActionResponse(final Context context, final String teloletId, final String senderLocation) {
        final Intent intent = new Intent(context, TeloletRequestService.class);
        intent.setAction(ACTION_REPLY);
        intent.putExtra(EXTRA_TELOLET_ID, teloletId);
        intent.putExtra(EXTRA_LOCATION, senderLocation);
        context.startService(intent);
    }

    public static void startActionTimeout(final Context context, final String teloletId) {
        final Intent intent = new Intent(context, TeloletRequestService.class);
        intent.setAction(ACTION_TIMEOUT);
        intent.putExtra(EXTRA_TELOLET_ID, teloletId);
        context.startService(intent);
    }

    @Override
    protected void onHandleIntent(final Intent intent) {
        if (intent == null) {
            return;
        }

        final String action = intent.getAction();
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onHandleIntent: action=" + action);
        if (ACTION_REQUEST.equals(action)) {
            final String receiverUid = intent.getStringExtra(EXTRA_RECEIVER_UID);
            final String senderLocation = intent.getStringExtra(EXTRA_LOCATION);
            handleActionTeloletRequest(receiverUid, senderLocation);
        } else if (ACTION_REPLY.equals(action)) {
            final String teloletId = intent.getStringExtra(EXTRA_TELOLET_ID);
            final String senderLocation = intent.getStringExtra(EXTRA_LOCATION);
            handleActionTeloletResponse(teloletId, senderLocation);
        } else if (ACTION_TIMEOUT.equals(action)) {
            final String teloletId = intent.getStringExtra(EXTRA_TELOLET_ID);
            handleActionTeloletResponse(teloletId, null);
        }
    }

    private void handleActionTeloletRequest(final String receiverUid, final String senderLocation) {
        final FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            FirebaseCrash.logcat(Log.ERROR, LOG_TAG, "User was logged out while creating telolet request... strange");
            return;
        }

        final String senderUid = firebaseUser.getUid();
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG,
                String.format(Locale.getDefault(), "handleActionTeloletRequest: from=%s, to=%s, at=%s", senderUid, receiverUid, senderLocation));

        final String pushKey = FirebaseDatabase.getInstance().getReference().push().getKey();
        final Telolet telolet = new Telolet(pushKey);
        telolet.setRequesterUid(senderUid);
        telolet.setReceiverUid(receiverUid);
        telolet.setRequestLocation(senderLocation);

        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "handleActionTeloletRequest: " + telolet);

        final Map<String, Object> updatesMap = new HashMap<>();
        final Map<String, Object> valueMap = telolet.toNewRequestMap();

        updatesMap.put(makeRequestSenderPath(telolet), valueMap);
        updatesMap.put(makeRequestReceiverPath(telolet), valueMap);
        FirebaseDatabase.getInstance().getReference().updateChildren(updatesMap);

        makeAnalyticsEventReport(TELOLET_REQUEST, senderLocation);
    }

    private void handleActionTeloletResponse(final String teloletId, final String senderLocation) {
        final FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            FirebaseCrash.logcat(Log.ERROR, LOG_TAG, "User was logged out while creating telolet response... strange");
            return;
        }

        final String senderUid = firebaseUser.getUid();
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG,
                String.format(Locale.getDefault(), "New telolet response from=%s, telolet=%s, at=%s", senderUid, teloletId, senderLocation));

        FirebaseDatabase.getInstance()
                .getReference(TELOLET_REQUESTS_RECEIVED)
                .child(firebaseUser.getUid())
                .child(teloletId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(final DataSnapshot snapshot) {
                        if (!snapshot.exists()) {
                            FirebaseCrash.logcat(Log.ERROR, LOG_TAG,
                                    String.format("Telolet received by '%s' with id '%s' doesn't exist. It was supposed to.",
                                            firebaseUser.getUid(), teloletId));
                            return;
                        }

                        final Telolet telolet = snapshot.getValue(Telolet.class);
                        telolet.setResolveLocation(senderLocation);

                        final Map<String, Object> updatesMap = new HashMap<>();
                        final Map<String, Object> valueMap = telolet.toNewReplyMap();

                        updatesMap.put(makeRequestSenderPath(telolet), valueMap);
                        updatesMap.put(makeRequestReceiverPath(telolet), valueMap);
                        FirebaseDatabase.getInstance().getReference().updateChildren(updatesMap);
                    }

                    @Override
                    public void onCancelled(final DatabaseError error) {
                        FirebaseCrash.logcat(Log.ERROR, LOG_TAG, error.getMessage());
                        FirebaseCrash.report(error.toException());
                    }
                });

        if (senderLocation != null) {
            makeAnalyticsEventReport(TELOLET_RESPONSE, senderLocation);
        }
    }

    private void makeAnalyticsEventReport(final String event, final String location) {
        final Bundle analyticsBundle = new Bundle();
        // The length 11 is for full length OLC that are actually 10 value characters
        for (int boxSize : new int[]{4, 6, 8, 11}) {
            final String olcBox = location.substring(0, boxSize);
            // Put in location data for the box size.
            analyticsBundle.putString(OLC + (boxSize == 11 ? 10 : boxSize), olcBox);
        }

        FirebaseAnalytics.getInstance(this).logEvent(event, analyticsBundle);
    }

    private String makeRequestSenderPath(final Telolet telolet) {
        return String.format("/%s/%s/%s", TELOLET_REQUESTS_SENT, telolet.getRequesterUid(), telolet.getId());
    }

    private String makeRequestReceiverPath(final Telolet telolet) {
        return String.format("/%s/%s/%s", TELOLET_REQUESTS_RECEIVED, telolet.getReceiverUid(), telolet.getId());
    }
}
