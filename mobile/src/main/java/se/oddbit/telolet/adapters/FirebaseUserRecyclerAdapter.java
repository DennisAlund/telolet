package se.oddbit.telolet.adapters;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crash.FirebaseCrash;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import se.oddbit.telolet.R;
import se.oddbit.telolet.models.Telolet;
import se.oddbit.telolet.models.User;

import static se.oddbit.telolet.util.Constants.Analytics.Events.TELOLET_REQUEST;
import static se.oddbit.telolet.util.Constants.Analytics.Param.OLC;
import static se.oddbit.telolet.util.Constants.Database.TELOLETS_RECEIVED;
import static se.oddbit.telolet.util.Constants.Database.TELOLETS_SENT;
import static se.oddbit.telolet.util.Constants.Database.USERS;
import static se.oddbit.telolet.util.Constants.RemoteConfig.OLC_BOX_SIZE;

public class FirebaseUserRecyclerAdapter extends RecyclerView.Adapter<FirebaseUserRecyclerAdapter.UserViewHolder> implements ChildEventListener {
    private static final String LOG_TAG = FirebaseUserRecyclerAdapter.class.getSimpleName();
    private static final char UTF_MIN = '\u0000';
    private static final char UTF_MAX = '\uf8ff';

    private final Context mContext;
    private final User mCurrentUser;
    private final Map<String, User> mUserMap = Collections.synchronizedMap(new HashMap<String, User>());
    private final List<String> mUserIdList = Collections.synchronizedList(new ArrayList<String>());

    public FirebaseUserRecyclerAdapter(final Context context, final User user) {
        mContext = context;
        mCurrentUser = user;

        final int boxSize = (int) FirebaseRemoteConfig.getInstance().getLong(OLC_BOX_SIZE);
        final String olcBox = mCurrentUser.getLocation().substring(0, boxSize);
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, String.format(Locale.getDefault(),
                "updateAdapter: Setting %s query to OLC box %s (size %d)", mCurrentUser, olcBox, boxSize));

        FirebaseDatabase.getInstance().getReference(USERS)
                .orderByChild(User.ATTR_LOCATION)
                .startAt(olcBox + UTF_MIN)
                .endAt(olcBox + UTF_MAX)
                .addChildEventListener(this);
    }

    @Override
    public UserViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onCreateViewHolder");
        final LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        final View rootView = inflater.inflate(R.layout.list_item_public_user, parent, false);
        return new UserViewHolder(rootView);
    }

    @Override
    public void onBindViewHolder(final UserViewHolder viewHolder, final int position) {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onBindViewHolder: " + position);
        final User user = mUserMap.get(mUserIdList.get(position));
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, String.format(Locale.getDefault(), "onBindViewHolder %s at position %d", user, position));

        viewHolder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View memberView) {
                FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, String.format(Locale.getDefault(), "Clicking %s at position: %d", user, viewHolder.getAdapterPosition()));
                createNewTeloletRequest(user);
            }
        });

        viewHolder.bindToMember(user);
    }

    @Override
    public int getItemCount() {
        return mUserIdList.size();
    }

    @Override
    public void onChildAdded(final DataSnapshot snapshot, final String previousChildName) {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, String.format("onChildAdded key: %s, prev: %s", snapshot.getKey(), previousChildName));
        final User user = snapshot.getValue(User.class);
        if (snapshot.exists() && !user.getUid().equals(mCurrentUser.getUid())) {
            addUser(user);
            notifyDataSetChanged();
        }
    }

    @Override
    public void onChildChanged(final DataSnapshot snapshot, final String previousChildName) {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, String.format("onChildChanged key: %s, prev: %s", snapshot.getKey(), previousChildName));
        if (snapshot.exists() && mUserMap.containsKey(snapshot.getKey())) {
            mUserMap.put(snapshot.getKey(), snapshot.getValue(User.class));
            notifyDataSetChanged();
        }
    }

    @Override
    public void onChildRemoved(final DataSnapshot snapshot) {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, String.format("onChildRemoved key: %s", snapshot.getKey()));
        if (snapshot.exists() && mUserMap.containsKey(snapshot.getKey())) {
            removeUser(snapshot.getValue(User.class));
            notifyDataSetChanged();
        }
    }

    @Override
    public void onChildMoved(final DataSnapshot snapshot, final String previousChildName) {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, String.format("onChildMoved key: %s, prev: %s", snapshot.getKey(), previousChildName));
    }

    @Override
    public void onCancelled(final DatabaseError error) {

    }

    private synchronized void addUser(final User user) {
        mUserIdList.add(user.getUid());
        mUserMap.put(user.getUid(), user);
    }

    private synchronized void removeUser(final User user) {
        mUserIdList.remove(user.getUid());
        mUserMap.remove(user.getUid());
    }

    private void createNewTeloletRequest(final User user) {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "createNewTeloletRequest: " + user);
        final Telolet telolet = new Telolet();
        telolet.setRequesterUid(mCurrentUser.getUid());
        telolet.setReceiverUid(user.getUid());
        telolet.setRequestLocation(user.getLocation());

        final Map<String, Object> updatesMap = new HashMap<>();
        final Map<String, Object> valueMap = telolet.toNewRequestMap();
        final String pushKey = FirebaseDatabase.getInstance().getReference().push().getKey();

        updatesMap.put(String.format("/%s/%s/%s", TELOLETS_SENT, mCurrentUser.getUid(), pushKey), valueMap);
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

    static class UserViewHolder extends RecyclerView.ViewHolder {
        private ImageView mCurrentUserImageView;
        private TextView mCurrentUserHandleView;
        private CardView mCardView;
        private View mRootView;

        public UserViewHolder(final View rootItemView) {
            super(rootItemView);
            mRootView = rootItemView;
            mCardView = (CardView) rootItemView.findViewById(R.id.public_user_card);
            mCurrentUserImageView = (ImageView) rootItemView.findViewById(R.id.public_user_image);
            mCurrentUserHandleView = (TextView) rootItemView.findViewById(R.id.public_user_handle);
        }

        void bindToMember(final User user) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, String.format("Binding view holder to: %s", user));
            mRootView.setVisibility(View.VISIBLE);
            mCardView.setCardBackgroundColor(Color.parseColor("#" + user.getColor()));
            mCurrentUserHandleView.setText(user.getHandle());
        }
    }
}
