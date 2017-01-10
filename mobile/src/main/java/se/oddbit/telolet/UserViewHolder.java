package se.oddbit.telolet;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.crash.FirebaseCrash;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

import se.oddbit.telolet.models.Telolet;
import se.oddbit.telolet.models.User;

import static se.oddbit.telolet.util.Constants.Analytics.Events.TELOLET_REQUEST;
import static se.oddbit.telolet.util.Constants.Analytics.Param.OLC;
import static se.oddbit.telolet.util.Constants.Database.TELOLETS_RECEIVED;
import static se.oddbit.telolet.util.Constants.Database.TELOLETS_SENT;

public class UserViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener{
    private static final String LOG_TAG = UserViewHolder.class.getSimpleName();
    private Context mContext;
    private ImageView mCurrentUserImageView;
    private TextView mCurrentUserHandleView;
    private CardView mCardView;
    private View mRootView;
    private User mUser;

    public UserViewHolder(final Context context, final View rootItemView) {
        super(rootItemView);
        mContext = context;
        mRootView = rootItemView;
        mCurrentUserImageView = (ImageView) rootItemView.findViewById(R.id.public_user_image);
        mCurrentUserHandleView = (TextView) rootItemView.findViewById(R.id.public_user_handle);
        mCardView = (CardView) rootItemView.findViewById(R.id.public_user_card);
        mCardView.setOnClickListener(this);
    }

    void bindToMember(final User user) {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, String.format("Binding view holder to: %s", user));
        mUser = user;
        mRootView.setVisibility(View.VISIBLE);
        mCardView.setCardBackgroundColor(Color.parseColor(user.getColor()));
        mCurrentUserHandleView.setText(user.getHandle());
    }

    @Override
    public void onClick(final View view) {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "Clicking: " + mUser);
        createNewTeloletRequest(mUser);
    }

    private void createNewTeloletRequest(final User user) {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "createNewTeloletRequest: " + user);
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        final Telolet telolet = new Telolet();
        telolet.setRequesterUid(firebaseUser.getUid());
        telolet.setReceiverUid(user.getUid());
        telolet.setRequestLocation(user.getLocation());

        final Map<String, Object> updatesMap = new HashMap<>();
        final Map<String, Object> valueMap = telolet.toNewRequestMap();
        final String pushKey = FirebaseDatabase.getInstance().getReference().push().getKey();

        updatesMap.put(String.format("/%s/%s/%s", TELOLETS_SENT, firebaseUser.getUid(), pushKey), valueMap);
        updatesMap.put(String.format("/%s/%s/%s", TELOLETS_RECEIVED, user.getUid(), pushKey), valueMap);

        FirebaseDatabase.getInstance().getReference().updateChildren(updatesMap);

        final Bundle analyticsBundle = new Bundle();
        // The length 11 is for full length OLC that are actually 10 value characters
        for (int boxSize : new int[]{4, 6, 8, 11}) {
            final String olcBox = user.getLocation().substring(0, boxSize);
            // Put in location data for the box size.
            analyticsBundle.putString(OLC + (boxSize == 11 ? 10 : boxSize), olcBox);
        }

        FirebaseAnalytics.getInstance(mContext).logEvent(TELOLET_REQUEST, analyticsBundle);
    }
}
