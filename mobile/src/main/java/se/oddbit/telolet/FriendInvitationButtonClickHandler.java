package se.oddbit.telolet;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.google.android.gms.appinvite.AppInviteInvitation;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crash.FirebaseCrash;

import static se.oddbit.telolet.util.Constants.Analytics.Events.FRIEND_INVITE_SENT;
import static se.oddbit.telolet.util.Constants.RequestCodes.FRIEND_INVITATION_REQUEST;

public class FriendInvitationButtonClickHandler implements View.OnClickListener {
    private static final String LOG_TAG = FriendInvitationButtonClickHandler.class.getSimpleName();

    private final Activity mContext;
    private final String mScreen;

    public FriendInvitationButtonClickHandler(final Activity activity, final String screen) {
        mContext = activity;
        mScreen = screen;
    }

    @Override
    public void onClick(final View view) {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onClick: Invite friends button");
        final Intent intent = new AppInviteInvitation
                .IntentBuilder(mContext.getString(R.string.friend_invitation_title_v1))
                .setMessage(mContext.getString(R.string.friend_invitation_message_v1))
                .setDeepLink(Uri.parse(mContext.getString(R.string.invitation_deep_link)))
                .setCustomImage(Uri.parse(mContext.getString(R.string.friend_invitation_custom_image)))
                .setCallToActionText(mContext.getString(R.string.friend_invitation_cta_v1))
                .build();

        final Bundle bundle = new Bundle();
        bundle.putString(FirebaseAnalytics.Param.CONTENT_TYPE, mScreen);
        FirebaseAnalytics.getInstance(mContext).logEvent(FRIEND_INVITE_SENT, bundle);

        mContext.startActivityForResult(intent, FRIEND_INVITATION_REQUEST);
    }
}
