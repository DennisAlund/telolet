package se.oddbit.telolet.adapters;

import android.content.Context;
import android.graphics.Color;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.google.firebase.crash.FirebaseCrash;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;

import java.util.Locale;

import se.oddbit.telolet.R;
import se.oddbit.telolet.models.Telolet;
import se.oddbit.telolet.models.User;

import static se.oddbit.telolet.util.Constants.Firebase.Database.TELOLETS;

public class UserProfileRecyclerAdapter extends FirebaseRecyclerAdapter<User.UserProfile, UserProfileRecyclerAdapter.UserProfileViewHolder> {
    private static final String LOG_TAG = UserProfileRecyclerAdapter.class.getSimpleName();
    private final Context mContext;
    private final User mUser;

    public UserProfileRecyclerAdapter(final Context context, final User user, final Query query) {
        super(User.UserProfile.class, R.layout.list_item_public_user, UserProfileViewHolder.class, query);
        mContext = context;
        mUser = user;
    }

    @Override
    protected void populateViewHolder(final UserProfileViewHolder viewHolder, final User.UserProfile userProfile, final int position) {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, String.format(Locale.getDefault(), "populateViewHolder \"%s\" at position %d", userProfile, position));

        viewHolder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View memberView) {
                FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, String.format(Locale.getDefault(), "Clicking %s at position: %d", userProfile, position));
                makeTeloletRequest(userProfile);
            }
        });

        viewHolder.bindToMember(userProfile);

        if (mUser.getUid().equals(userProfile.getUid())) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "Hiding own list item at position: " + position);
            viewHolder.mCardView.setVisibility(View.GONE);
        }
    }

    private void makeTeloletRequest(final User.UserProfile userProfile) {
        final Telolet telolet = new Telolet();
        telolet.setRequesterUid(mUser.getUid());
        telolet.setReceiverUid(userProfile.getUid());
        telolet.setRequestLocation(mUser.getCurrentLocation());

        final DatabaseReference teloletsRef = FirebaseDatabase.getInstance().getReference(TELOLETS);
        teloletsRef.child(userProfile.getUid()).push().setValue(telolet.toNewRequestMap());
    }

    static class UserProfileViewHolder extends RecyclerView.ViewHolder {
        private ImageView mUserImageView;
        private TextView mUserHandleView;
        private CardView mCardView;

        public UserProfileViewHolder(final View rootItemView) {
            super(rootItemView);
            mCardView = (CardView) rootItemView.findViewById(R.id.public_user_card);
            mUserImageView = (ImageView) rootItemView.findViewById(R.id.public_user_image);
            mUserHandleView = (TextView) rootItemView.findViewById(R.id.public_user_handle);
        }

        void bindToMember(final User.UserProfile user) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, String.format("Binding view holder to: %s", user));
            mCardView.setCardBackgroundColor(Color.parseColor("#" + user.getColor()));
            mUserHandleView.setText(user.getHandle());
        }
    }
}
