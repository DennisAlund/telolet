package se.oddbit.telolet.viewHolders;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.AnimationDrawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crash.FirebaseCrash;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

import se.oddbit.telolet.R;
import se.oddbit.telolet.models.Telolet;
import se.oddbit.telolet.models.User;
import se.oddbit.telolet.models.UserState;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.view.animation.AnimationUtils.loadAnimation;
import static se.oddbit.telolet.models.UserState.PENDING_RECEIVED;
import static se.oddbit.telolet.models.UserState.PENDING_SENT;
import static se.oddbit.telolet.models.UserState.TELOLET_RECEIVED;
import static se.oddbit.telolet.models.UserState.TELOLET_SENT;
import static se.oddbit.telolet.util.Constants.Analytics.Events.TELOLET_REQUEST;
import static se.oddbit.telolet.util.Constants.Analytics.Events.TELOLET_RESPONSE;
import static se.oddbit.telolet.util.Constants.Analytics.Param.OLC;
import static se.oddbit.telolet.util.Constants.Database.TELOLET_REQUESTS_RECEIVED;
import static se.oddbit.telolet.util.Constants.Database.TELOLET_REQUESTS_SENT;
import static se.oddbit.telolet.util.Constants.Database.USER_STATES;

public class UserViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, ValueEventListener {
    private static final String LOG_TAG = UserViewHolder.class.getSimpleName();
    private Context mContext;
    private User mCurrentUser;

    private final LinearLayout mTeloletTextContainer;
    private final TextView mUserHandleView;
    private final ImageView mUserImageView;
    private final CardView mCardView;
    private User mOtherUser;
    private DatabaseReference mUserStateRef;
    private UserState mUserState;


    public UserViewHolder(final Context context, final View rootItemView, final User currentUser) {
        super(rootItemView);
        mContext = context;
        mCurrentUser = currentUser;
        mUserHandleView = (TextView) rootItemView.findViewById(R.id.user_handle);
        mUserImageView = (ImageView) rootItemView.findViewById(R.id.user_image);
        mCardView = (CardView) rootItemView.findViewById(R.id.user_card);
        mTeloletTextContainer = (LinearLayout) rootItemView.findViewById(R.id.telolet_text_container);
    }

    @Override
    public void onClick(final View view) {
        if (mUserState == null) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onClick: will create a telolet request to: " + mOtherUser);
            sendRequest();
        } else if (mUserState.isState(PENDING_RECEIVED)) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onClick: will be a reply to telolet request by: " + mOtherUser);
            replyToIncomingRequest();
        } else {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onClick: Already stuff going on. Do nothing for: " + mOtherUser);
            mCardView.startAnimation(loadAnimation(mContext, R.anim.wiggle));
        }
    }

    @Override
    public void onDataChange(final DataSnapshot snapshot) {
        final UserState newUserState = snapshot.getValue(UserState.class);
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, String.format("onDataChange: %s %s => %s", mOtherUser, mUserState, newUserState));
        setDefaultState();
        mUserState = newUserState;
        final String state = newUserState == null ? "?" : newUserState.getState();
        switch (state) {
            case PENDING_SENT:
                setStatePendingRequestSent();
                break;
            case PENDING_RECEIVED:
                setStatePendingRequestReceived();
                break;
            case TELOLET_RECEIVED:
            case TELOLET_SENT:
                setStatePlayingTelolet();
                break;
            default:
                setDefaultState();
        }
    }

    @Override
    public void onCancelled(final DatabaseError error) {
        FirebaseCrash.logcat(Log.ERROR, LOG_TAG, error.getMessage());
        FirebaseCrash.report(error.toException());
    }

    @Override
    public String toString() {
        return String.format("%s{user=%s}", UserViewHolder.class.getSimpleName(), mOtherUser);
    }

    public void bind(@NonNull final User user) {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, String.format("Binding view holder to: %s", user));
        mOtherUser = user;
        setDefaultState();
        if (mCurrentUser != null) {
            // There's a chance that the user view get bound before the current user data has been set
            mUserStateRef = FirebaseDatabase
                    .getInstance()
                    .getReference()
                    .child(USER_STATES)
                    .child(mCurrentUser.getUid())
                    .child(user.getUid());

            mUserStateRef.addValueEventListener(this);
        }
    }

    public void clear() {
        setDefaultState();
        mUserState = null;
        mOtherUser = null;
        if (mUserStateRef != null) {
            mUserStateRef.removeEventListener(this);
        }
    }

    private void setDefaultState() {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, String.format("setDefaultState: %s", mOtherUser));
        stopAnimations();

        mCardView.setCardBackgroundColor(Color.parseColor(mOtherUser.getColor()));
        mUserImageView.setImageResource(R.drawable.ic_directions_bus_black_24dp);
        mUserImageView.setBackground(null);
        mUserHandleView.setText(mOtherUser.getHandle());

        mUserHandleView.setVisibility(VISIBLE);
        mTeloletTextContainer.setVisibility(GONE);

        // Only add click listener if current user is set
        mCardView.setOnClickListener(mCurrentUser == null ? null : this);
    }

    private void setStatePendingRequestSent() {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, String.format("setStatePendingRequestSent: %s: Shake a little.", mOtherUser));
        mCardView.setOnClickListener(null);
        mUserHandleView.setText(R.string.om_telolet_om);
        mUserImageView.setImageResource(R.drawable.ic_emoticon_excited);
        mCardView.startAnimation(loadAnimation(mContext, R.anim.wiggle));
    }

    private void setStatePendingRequestReceived() {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, String.format("setStatePendingRequestReceived: %s: Shake infinitely.", mOtherUser));
        mUserHandleView.setText(R.string.om_telolet_om);
        mUserImageView.setImageResource(R.drawable.ic_human_handsup);
        mCardView.startAnimation(loadAnimation(mContext, R.anim.infinite_shake));
    }

    private void setStatePlayingTelolet() {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, String.format("setStatePlayingTelolet: %s: pulsate a speaker", mOtherUser));

        stopAnimations();
        mCardView.setOnClickListener(null);

        // Replace the image with a pulsating speaker
        /*
        mUserImageView.setImageDrawable(null);
        mUserImageView.setBackgroundResource(R.drawable.ic_animated_speaker);
        final AnimationDrawable animationDrawable = (AnimationDrawable) mUserImageView.getBackground();
        animationDrawable.start();
        */
        mUserImageView.setImageResource(R.drawable.ic_animated_speaker);
        final AnimationDrawable animationDrawable = (AnimationDrawable) mUserImageView.getDrawable();
        animationDrawable.start();

        // Replace the user handle with a wiggly text saying TELOLET
        mUserHandleView.setVisibility(GONE);
        mTeloletTextContainer.setVisibility(VISIBLE);
        for (int i = 0; i < mTeloletTextContainer.getChildCount(); i++) {
            final View letterView = mTeloletTextContainer.getChildAt(i);
            final Animation animation = loadAnimation(mContext, R.anim.pulsate);
            animation.setDuration(150 + ((i % 3) * 100)); // Wiggle letters at different duration offset
            letterView.startAnimation(animation);
        }
    }

    private void stopAnimations() {
        mCardView.clearAnimation();
        mUserImageView.clearAnimation();
    }

    private void replyToIncomingRequest() {
        makeAnalyticsEventReport(TELOLET_RESPONSE);

        final Telolet telolet = mUserState.getTelolet();
        telolet.setResolveLocation(mCurrentUser.getLocation());
        telolet.setState(Telolet.STATE_REPLIED);

        final String otherUid = telolet.getRequesterUid();
        final String currUid = telolet.getReceiverUid();

        final Map<String, Object> updatesMap = new HashMap<>();

        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "replyToIncomingRequest: " + telolet);
        updatesMap.put(String.format("/%s/%s/%s", TELOLET_REQUESTS_SENT, otherUid, telolet.getId()), telolet.toValueMap());
        updatesMap.put(String.format("/%s/%s/%s", TELOLET_REQUESTS_RECEIVED, currUid, telolet.getId()), telolet.toValueMap());
        updatesMap.put(String.format("/%s/%s/%s", USER_STATES, currUid, otherUid), new UserState(telolet, UserState.TELOLET_SENT).toValueMap());
        updatesMap.put(String.format("/%s/%s/%s", USER_STATES, otherUid, currUid), new UserState(telolet, UserState.TELOLET_RECEIVED).toValueMap());

        FirebaseDatabase.getInstance().getReference().updateChildren(updatesMap);
    }

    private void sendRequest() {
        makeAnalyticsEventReport(TELOLET_REQUEST);

        final Telolet telolet = new Telolet(FirebaseDatabase.getInstance().getReference().push().getKey());
        telolet.setRequesterUid(mCurrentUser.getUid());
        telolet.setReceiverUid(mOtherUser.getUid());
        telolet.setRequestLocation(mCurrentUser.getLocation());
        telolet.setState(Telolet.STATE_PENDING);

        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "sendRequest: " + telolet);

        final Map<String, Object> updatesMap = new HashMap<>();
        updatesMap.put(String.format("/%s/%s/%s", TELOLET_REQUESTS_SENT, mCurrentUser.getUid(), telolet.getId()), telolet.toValueMap());
        updatesMap.put(String.format("/%s/%s/%s", TELOLET_REQUESTS_RECEIVED, mOtherUser.getUid(), telolet.getId()), telolet.toValueMap());
        updatesMap.put(String.format("/%s/%s/%s", USER_STATES, mCurrentUser.getUid(), mOtherUser.getUid()), new UserState(telolet, UserState.PENDING_SENT).toValueMap());
        updatesMap.put(String.format("/%s/%s/%s", USER_STATES, mOtherUser.getUid(), mCurrentUser.getUid()), new UserState(telolet, UserState.PENDING_RECEIVED).toValueMap());

        FirebaseDatabase.getInstance().getReference().updateChildren(updatesMap);
    }

    private void makeAnalyticsEventReport(final String event) {
        final Bundle analyticsBundle = new Bundle();
        // The length 11 is for full length OLC that are actually 10 value characters
        for (int boxSize : new int[]{4, 6, 8, 11}) {
            final String olcBox = mCurrentUser.getLocation().substring(0, boxSize);
            // Put in location data for the box size.
            analyticsBundle.putString(OLC + (boxSize == 11 ? 10 : boxSize), olcBox);
        }

        FirebaseAnalytics.getInstance(mContext).logEvent(event, analyticsBundle);
    }
}
