package se.oddbit.telolet.broadcast;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.util.Log;

import com.google.firebase.crash.FirebaseCrash;

import java.util.Locale;

public class CloudMessagesBroadcastReceiver extends BroadcastReceiver {
    private static final String LOG_TAG = CloudMessagesBroadcastReceiver.class.getSimpleName();
    private static final String ACTION_NOTIFICATION = "se.oddbit.telolet.broadcast.action.notification";
    private static final String EXTRA_TITLE = "se.oddbit.telolet.broadcast.extra.title";
    private static final String EXTRA_BODY = "se.oddbit.telolet.broadcast.extra.body";

    private final Context mContext;
    private final IntentFilter mIntentFilter;

    public CloudMessagesBroadcastReceiver(final Context context) {
        mContext = context;
        mIntentFilter = new IntentFilter(ACTION_NOTIFICATION);
    }

    public static void createMessage(final Context context, final String title, final String body) {
        final Intent intent = new Intent(ACTION_NOTIFICATION);
        intent.putExtra(EXTRA_TITLE, title);
        intent.putExtra(EXTRA_BODY, body);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    public void register() {
        LocalBroadcastManager.getInstance(mContext).registerReceiver(this, mIntentFilter);
    }

    public void unregister() {
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(this);
    }

    @Override
    public void onReceive(final Context context, final Intent intent) {
        if (intent == null) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onReceive: local broadcast intent was null");
            return;
        }
        if (intent.getAction().equals(ACTION_NOTIFICATION)) {
            final String title = intent.getStringExtra(EXTRA_TITLE);
            final String body = intent.getStringExtra(EXTRA_BODY);
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG,
                    String.format(Locale.getDefault(), "onReceive: show notification title='%s', body='%s'", title, body));
            final AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(mContext);
            if (title != null && !title.isEmpty()) {
                dialogBuilder.setTitle(title);
            }
            dialogBuilder.setMessage(body);
            dialogBuilder.create().show();
        }
    }
}

