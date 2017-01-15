package se.oddbit.telolet;

import android.content.Context;
import android.graphics.Color;
import android.support.annotation.NonNull;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.firebase.crash.FirebaseCrash;

import se.oddbit.telolet.models.Telolet;
import se.oddbit.telolet.models.User;
import se.oddbit.telolet.services.TeloletRequestService;

import static android.view.animation.AnimationUtils.loadAnimation;

class UserViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener{
    private static final String LOG_TAG = UserViewHolder.class.getSimpleName();
    private Context mContext;
    private User mCurrentUser;

    private final TextView mUserHandleView;
    private final ImageView mUserImageView;
    private final CardView mCardView;
    private User mOtherUser;
    private Telolet mTeloletRequest;

    UserViewHolder(final Context context, final View rootItemView, final User currentUser) {
        super(rootItemView);
        mContext = context;
        mCurrentUser = currentUser;
        mUserHandleView = (TextView) rootItemView.findViewById(R.id.user_handle);
        mUserImageView = (ImageView) rootItemView.findViewById(R.id.user_image);
        mCardView = (CardView) rootItemView.findViewById(R.id.user_card);
    }


    void bind(@NonNull final User user) {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, String.format("Binding view holder to: %s", user));
        stopAnimations();
        mOtherUser = user;
        mTeloletRequest = null;
        mCardView.setCardBackgroundColor(Color.parseColor(user.getColor()));
        mUserImageView.setImageResource(R.drawable.ic_directions_bus_black_24dp);
        mUserHandleView.setText(user.getHandle());
        mCardView.setOnClickListener(this);
    }

    void bind(@NonNull final User user, @NonNull final Telolet telolet) {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, String.format("Binding view holder to %s with telolet %s", user, telolet));
        bind(user);

        mTeloletRequest = telolet;

        if (telolet.isPendingRequestBy(user)) {
            setStatePendingRequestReceived();
        } else if (telolet.isPendingRequestFor(user)) {
            setStatePendingRequestSent();
        } else if (telolet.isAnsweredBy(user)){
            setStateShowingReceivedResponse();
        }
    }

    @Override
    public void onClick(final View view) {
        if (mTeloletRequest == null) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onClick: will create a telolet request to: " + mOtherUser);
            mCardView.setOnClickListener(null);
            mCardView.startAnimation(loadAnimation(mContext, R.anim.wiggle));
            TeloletRequestService.startActionRequest(mContext, mOtherUser.getUid(), mCurrentUser.getLocation());
        } else if (mTeloletRequest.isPendingRequestBy(mOtherUser)) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onClick: will be a reply to telolet request by: " + mOtherUser);
            stopAnimations();
            mCardView.setOnClickListener(null);
            TeloletRequestService.startActionResponse(mContext, mTeloletRequest.getId(), mOtherUser.getLocation());
        } else {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onClick: Already stuff going. Do nothing for: " + mOtherUser);
            mCardView.startAnimation(loadAnimation(mContext, R.anim.wiggle));
        }
    }

    private void setStatePendingRequestSent() {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, String.format("Requested telolet %s to %s. Shake a little.", mTeloletRequest, mOtherUser));
        mUserHandleView.setText(R.string.om_telolet_om);
        mUserImageView.setImageResource(R.drawable.ic_emoticon_excited);
        mCardView.startAnimation(loadAnimation(mContext, R.anim.wiggle));
    }

    private void setStatePendingRequestReceived() {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, String.format("Got telolet request %s from %s. Shake infinitely.", mTeloletRequest, mOtherUser));
        mUserHandleView.setText(R.string.om_telolet_om);
        mUserImageView.setImageResource(R.drawable.ic_human_handsup);
        mCardView.startAnimation(loadAnimation(mContext, R.anim.infinite_shake));
    }

    private void setStateShowingReceivedResponse() {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, String.format("Got telolet response %s from %s.", mTeloletRequest, mOtherUser));
        mUserImageView.setImageResource(R.drawable.ic_volume_up_black_24dp);
        mUserImageView.startAnimation(loadAnimation(mContext, R.anim.pulsate));
    }

    private void stopAnimations() {
        mCardView.clearAnimation();
        mUserImageView.clearAnimation();
    }
}
