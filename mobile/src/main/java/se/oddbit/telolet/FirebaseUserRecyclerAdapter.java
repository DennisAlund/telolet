package se.oddbit.telolet;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.crash.FirebaseCrash;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.Query;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import se.oddbit.telolet.models.Telolet;
import se.oddbit.telolet.models.User;

import static se.oddbit.telolet.util.Constants.RemoteConfig.LIST_AD_FREQUENCY;

public class FirebaseUserRecyclerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> implements ChildEventListener {
    private static final String LOG_TAG = FirebaseUserRecyclerAdapter.class.getSimpleName();
    private static final String AD_PREFIX = "/AD/";
    private static final int VIEW_TYPE_AD = 0xAD;
    private static final int VIEW_TYPE_DEFAULT = 0xDEF;

    interface EmptyStateListener {
        void onEmptyState();

        void onNonEmptyState();
    }

    private int mAdFrequency;
    private Query mQuery;
    private User mUser;
    private final Context mContext;
    private final Object mListItemsLock = new Object();
    private final List<String> mItemIds = new ArrayList<>();
    private final Map<String, User> mUserMap = new HashMap<>();
    private final Map<String, Telolet> mSentTeloletsMap = new HashMap<>();
    private final Map<String, Telolet> mReceivedTeloletsMap = new HashMap<>();
    private final List<EmptyStateListener> mEmptyStateListeners = new ArrayList<>();

    FirebaseUserRecyclerAdapter(final Context context) {
        mContext = context;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onCreateViewHolder: " + viewType);
        final LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case VIEW_TYPE_AD:
                final View adListItemRootView = inflater.inflate(R.layout.list_item_ad, parent, false);
                return new AdViewHolder(mContext, parent, adListItemRootView);

            default:
                final View userListItemRootView = inflater.inflate(R.layout.list_item_public_user, parent, false);
                return new UserViewHolder(mContext, userListItemRootView, mUser);
        }
    }

    @Override
    public int getItemViewType(final int position) {
        return isAdPosition(position) ? VIEW_TYPE_AD : VIEW_TYPE_DEFAULT;
    }

    @Override
    public void onBindViewHolder(final RecyclerView.ViewHolder viewHolder, final int position) {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, String.format("onBindViewHolder: type=%s, pos=%s", viewHolder.getItemViewType(), position));

        if (viewHolder.getItemViewType() == VIEW_TYPE_AD) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "Putting in an advertisement at position: " + position);
            return;
        }

        final UserViewHolder userViewHolder = (UserViewHolder) viewHolder;
        final String listItemId = mItemIds.get(getOffsetPosition(position));
        final User user = mUserMap.get(listItemId);
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, String.format(Locale.getDefault(), "onBindViewHolder %s at position %d", user, position));

        if (mReceivedTeloletsMap.containsKey(user.getUid())) {
            userViewHolder.bind(user, mReceivedTeloletsMap.get(user.getUid()));
        } else if (mSentTeloletsMap.containsKey(user.getUid())) {
            userViewHolder.bind(user, mSentTeloletsMap.get(user.getUid()));
        } else {
            userViewHolder.bind(user);
        }
    }

    @Override
    public int getItemCount() {
        if (mAdFrequency <= 0) {
            return mItemIds.size();
        }

        return mItemIds.size() + mItemIds.size() / (mAdFrequency - 1);
    }


    @Override
    public void onChildAdded(final DataSnapshot snapshot, final String previousChildName) {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, String.format("onChildAdded key: %s, prev: %s", snapshot.getKey(), previousChildName));
        final User user = snapshot.getValue(User.class);
        final FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser != null && firebaseUser.getUid().equals(user.getUid())) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onChildAdded: setting own user: " + user);
            mUser = user;
        } else {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onChildAdded: adding user to list: " + user);
            addUser(user);
        }
    }

    @Override
    public void onChildChanged(final DataSnapshot snapshot, final String previousChildName) {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, String.format("onChildChanged key: %s, prev: %s", snapshot.getKey(), previousChildName));
        final User user = snapshot.getValue(User.class);
        final FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        assert firebaseUser != null;
        if (firebaseUser.getUid().equals(user.getUid())) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onChildChanged: updating own user: " + user);
            mUser = user;
        } else {
            updateUser(user);
        }
    }

    @Override
    public void onChildRemoved(final DataSnapshot snapshot) {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, String.format("onChildRemoved key: %s", snapshot.getKey()));
        if (snapshot.exists() && mUserMap.containsKey(snapshot.getKey())) {
            removeUser(snapshot.getValue(User.class));
        }
    }

    @Override
    public void onChildMoved(final DataSnapshot snapshot, final String previousChildName) {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, String.format("onChildMoved key: %s, prev: %s", snapshot.getKey(), previousChildName));
    }

    @Override
    public void onCancelled(final DatabaseError error) {
        FirebaseCrash.logcat(Log.ERROR, LOG_TAG, error.getMessage());
        FirebaseCrash.report(error.toException());
    }

    void setSentTelolet(final Telolet telolet) {
        mSentTeloletsMap.put(telolet.getReceiverUid(), telolet);
        final int receivingUserIndex = mItemIds.indexOf(telolet.getReceiverUid());
        if (receivingUserIndex >= 0) {
            // notifyItemChanged(receivingUserIndex);
            notifyDataSetChanged();
        }
    }

    void setReceivedTeloletRequest(final Telolet telolet) {
        final String uid = telolet.getRequesterUid();
        mReceivedTeloletsMap.put(uid, telolet);
        final int requestingUserIndex = mItemIds.indexOf(uid);
        if (requestingUserIndex >= 0) {
            moveToTop(uid);
        }
    }

    void removeTelolet(final Telolet telolet) {
        mSentTeloletsMap.remove(telolet.getReceiverUid());
        mReceivedTeloletsMap.remove(telolet.getRequesterUid());
        notifyDataSetChanged();
    }

    void setQuery(final Query query) {
        mAdFrequency = Integer.parseInt(FirebaseRemoteConfig.getInstance().getString(LIST_AD_FREQUENCY));
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG,
                String.format(Locale.getDefault(), "setQuery: new query, ads every %d list item", mAdFrequency));

        if (mQuery != null) {
            mQuery.removeEventListener(this);
            clear();
        }

        mQuery = query;
        mQuery.addChildEventListener(this);
    }

    void addEmptyStateListener(final EmptyStateListener emptyStateListener) {
        mEmptyStateListeners.add(emptyStateListener);
    }

    void removeEmptyStateListener(final EmptyStateListener emptyStateListener) {
        mEmptyStateListeners.remove(emptyStateListener);
    }

    private void clear() {
        synchronized (mListItemsLock) {
            mItemIds.clear();
            mUserMap.clear();
            notifyDataSetChanged();
            for (EmptyStateListener emptyStateListener : mEmptyStateListeners) {
                emptyStateListener.onEmptyState();
            }
        }
    }

    private void moveToTop(@NonNull final String uid) {
        synchronized (mListItemsLock) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "Adding moving user to the top: " + uid);
            mItemIds.remove(uid);
            mItemIds.add(0, uid);
            notifyDataSetChanged();
        }
    }

    private void updateUser(@NonNull final User user) {
        synchronized (mListItemsLock) {
            mUserMap.put(user.getUid(), user);
            final int changedUserIndex = mItemIds.indexOf(user.getUid());
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, String.format(Locale.getDefault(), "Updating %s at pos=%d", user, changedUserIndex));
            if (changedUserIndex >= 0) {
                notifyItemChanged(changedUserIndex);
            }
        }
    }

    private void addUser(@NonNull final User user) {
        synchronized (mListItemsLock) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "Adding user to internal records: " + user);
            if (mReceivedTeloletsMap.containsKey(user.getUid())) {
                FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "User has pending telolet request. Adding to the beginning of the list.");
                mItemIds.add(0, user.getUid());
            } else {
                FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "Adding user at index: " + mItemIds.size());
                mItemIds.add(user.getUid());
            }

            mUserMap.put(user.getUid(), user);
            if (mItemIds.size() == 1) {
                // Only do this if it goes from ZERO to ONE
                for (EmptyStateListener emptyStateListener : mEmptyStateListeners) {
                    emptyStateListener.onNonEmptyState();
                }
            }

            notifyDataSetChanged();
        }
    }

    private void removeUser(@NonNull final User user) {
        synchronized (mListItemsLock) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "Removing user from internal records: " + user);
            mItemIds.remove(user.getUid());
            mUserMap.remove(user.getUid());
            if (mItemIds.size() == 0) {
                for (EmptyStateListener emptyStateListener : mEmptyStateListeners) {
                    emptyStateListener.onEmptyState();
                }
            }

            notifyDataSetChanged();
        }
    }

    private boolean isAdPosition(final int position) {
        return mAdFrequency > 0 && (position % mAdFrequency) == (mAdFrequency - 1);
    }

    private int getOffsetPosition(final int position) {
        if (mAdFrequency <= 0) {
            return position;
        }
        return position - position / mAdFrequency;
    }
}
