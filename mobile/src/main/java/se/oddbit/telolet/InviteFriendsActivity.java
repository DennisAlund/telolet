package se.oddbit.telolet;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.google.android.gms.appinvite.AppInviteInvitation;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crash.FirebaseCrash;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;

import java.util.HashMap;
import java.util.Map;

import static se.oddbit.telolet.util.Constants.Analytics.Events.FRIEND_INVITE_SENT;
import static se.oddbit.telolet.util.Constants.RemoteConfig.TEST_GROUP;
import static se.oddbit.telolet.util.Constants.RequestCodes.FRIEND_INVITATION_REQUEST;

public class InviteFriendsActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String LOG_TAG = InviteFriendsActivity.class.getSimpleName();

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_invite_friends);
        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));

        final ActionBar supportActionBar = getSupportActionBar();
        if (supportActionBar != null) {
            supportActionBar.setDisplayHomeAsUpEnabled(true);
        }

        final Button button = (Button) findViewById(R.id.invite_friends_button);
        button.setOnClickListener(new FriendInvitationButtonClickHandler(this, LOG_TAG));
    }

    @Override
    public void onClick(final View view) {
        if (view.getId() == R.id.invite_friends_button) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onClick: Invite friends button");

            final Bundle bundle = new Bundle();
            bundle.putString(FirebaseAnalytics.Param.CONTENT_TYPE, LOG_TAG);
            FirebaseAnalytics.getInstance(this).logEvent(FRIEND_INVITE_SENT, bundle);

            if (FirebaseRemoteConfig.getInstance().getString(TEST_GROUP).equals("A")) {
                startActivityForResult(getTypeAIntent(), FRIEND_INVITATION_REQUEST);
            } else {
                startActivityForResult(getTypeBIntent(), FRIEND_INVITATION_REQUEST);
            }
        }
    }

    private Intent getTypeAIntent() {
        final Map<String, String> referralParams = new HashMap<>();
        referralParams.put(TEST_GROUP, "A");
        return new AppInviteInvitation
                .IntentBuilder(getString(R.string.friend_invitation_title_v1))
                .setMessage(getString(R.string.friend_invitation_message_v1))
                .setCallToActionText(getString(R.string.friend_invitation_cta_v1))
                .setAdditionalReferralParameters(referralParams)
                .setDeepLink(Uri.parse(getString(R.string.invitation_deep_link)))
                .setCustomImage(Uri.parse(getString(R.string.friend_invitation_custom_image)))
                .build();
    }

    private Intent getTypeBIntent() {
        final Map<String, String> referralParams = new HashMap<>();
        referralParams.put(TEST_GROUP, "A");
        return new AppInviteInvitation
                .IntentBuilder(getString(R.string.friend_invitation_title_v2))
                .setMessage(getString(R.string.friend_invitation_message_v2))
                .setCallToActionText(getString(R.string.friend_invitation_cta_v2))
                .setAdditionalReferralParameters(referralParams)
                .setDeepLink(Uri.parse(getString(R.string.invitation_deep_link)))
                .setCustomImage(Uri.parse(getString(R.string.friend_invitation_custom_image)))
                .build();
    }
}
