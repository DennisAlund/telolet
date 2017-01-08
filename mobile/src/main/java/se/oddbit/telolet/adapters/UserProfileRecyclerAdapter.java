package se.oddbit.telolet.adapters;

import android.content.Context;
import android.graphics.Color;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.google.firebase.crash.FirebaseCrash;
import com.google.firebase.database.Query;

import java.util.Locale;

import se.oddbit.telolet.R;
import se.oddbit.telolet.models.UserProfile;

public class UserProfileRecyclerAdapter extends FirebaseRecyclerAdapter<UserProfile, UserProfileRecyclerAdapter.UserProfileViewHolder> {
    private static final String LOG_TAG = UserProfileRecyclerAdapter.class.getSimpleName();
    private final Context mContext;

    public UserProfileRecyclerAdapter(final Context context, final Query memberQuery) {
        super(UserProfile.class, R.layout.list_item_public_user, UserProfileViewHolder.class, memberQuery);
        mContext = context;
    }

    @Override
    protected void populateViewHolder(final UserProfileViewHolder viewHolder, final UserProfile user, final int position) {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, String.format(Locale.getDefault(), "populateViewHolder \"%s\" at position %d", user, position));

        final String userId = getRef(position).getKey();

        viewHolder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View memberView) {
                Toast.makeText(mContext, "Telolet: " + userId, Toast.LENGTH_LONG).show();
            }
        });

        viewHolder.bindToMember(user);
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

        void bindToMember(final UserProfile user) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, String.format("Binding view holder to: %s", user));
            mCardView.setCardBackgroundColor(Color.parseColor("#" + user.getColor()));
            mUserHandleView.setText(user.getHandle());
        }
    }
}
