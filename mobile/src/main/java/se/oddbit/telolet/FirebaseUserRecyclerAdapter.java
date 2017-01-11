package se.oddbit.telolet;

import android.content.Context;
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
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import se.oddbit.telolet.models.User;

import static se.oddbit.telolet.util.Constants.RemoteConfig.LIST_AD_FREQUENCY;

public class FirebaseUserRecyclerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> implements ChildEventListener {
    private static final String LOG_TAG = FirebaseUserRecyclerAdapter.class.getSimpleName();
    private static final String AD_PREFIX = "/AD/";
    private static final int VIEW_TYPE_AD = 0xAD;
    private static final int VIEW_TYPE_DEFAULT = 0xDEF;

    public interface EmptyStateListener {
        void onEmptyState();

        void onNonEmptyState();
    }

    private int mAdFrequency;
    private Query mQuery;
    private final Context mContext;
    private final Object mListItemsLock = new Object();
    private final List<String> mItemIds = new ArrayList<>();
    private final Queue<String> mAdIds = new ConcurrentLinkedQueue<>();
    private final Map<String, User> mUserMap = new HashMap<>();
    private final List<EmptyStateListener> mEmptyStateListeners = new ArrayList<>();

    public FirebaseUserRecyclerAdapter(final Context context) {
        mContext = context;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onCreateViewHolder: " + viewType);
        final LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case VIEW_TYPE_AD:
                final View adListItemRootView = inflater.inflate(R.layout.list_item_ad, parent, false);
                return new AdViewHolder(adListItemRootView);

            default:
                final View userListItemRootView = inflater.inflate(R.layout.list_item_public_user, parent, false);
                return new UserViewHolder(mContext, userListItemRootView);
        }
    }

    @Override
    public int getItemViewType(final int position) {
        return mItemIds.get(position).startsWith(AD_PREFIX) ? VIEW_TYPE_AD : VIEW_TYPE_DEFAULT;
    }

    @Override
    public void onBindViewHolder(final RecyclerView.ViewHolder viewHolder, final int position) {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, String.format("onBindViewHolder: type=%s, pos=%s", viewHolder.getItemViewType(), position));

        if (viewHolder.getItemViewType() == VIEW_TYPE_AD) {
            ((AdViewHolder) viewHolder).makeAd();
            return;
        }

        final UserViewHolder userViewHolder = (UserViewHolder) viewHolder;
        final String listItemId = mItemIds.get(position);
        final User user = mUserMap.get(listItemId);
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, String.format(Locale.getDefault(), "onBindViewHolder %s at position %d", user, position));
        userViewHolder.bindToMember(user);
    }

    @Override
    public int getItemCount() {
        return mItemIds.size();
    }


    @Override
    public void onChildAdded(final DataSnapshot snapshot, final String previousChildName) {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, String.format("onChildAdded key: %s, prev: %s", snapshot.getKey(), previousChildName));
        final User user = snapshot.getValue(User.class);
        final FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser != null && firebaseUser.getUid().equals(user.getUid())) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onChildAdded: skipping own user: " + user);
        } else {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onChildAdded: adding user to list: " + user);
            addUser(user);
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
        }
    }

    @Override
    public void onChildMoved(final DataSnapshot snapshot, final String previousChildName) {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, String.format("onChildMoved key: %s, prev: %s", snapshot.getKey(), previousChildName));
    }

    @Override
    public void onCancelled(final DatabaseError error) {
        FirebaseCrash.report(error.toException());
    }

    public void setQuery(final Query query) {
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

    public void addEmptyStateListener(final EmptyStateListener emptyStateListener) {
        mEmptyStateListeners.add(emptyStateListener);
    }

    public void removeEmptyStateListener(final EmptyStateListener emptyStateListener) {
        mEmptyStateListeners.remove(emptyStateListener);
    }

    private void clear() {
        synchronized (mListItemsLock) {
            mAdIds.clear();
            mItemIds.clear();
            mUserMap.clear();
            notifyDataSetChanged();
            for (EmptyStateListener emptyStateListener : mEmptyStateListeners) {
                emptyStateListener.onEmptyState();
            }
        }
    }

    private void addUser(final User user) {
        synchronized (mListItemsLock) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "Adding user to internal records: " + user);
            mItemIds.add(user.getUid());
            mUserMap.put(user.getUid(), user);
            if (mItemIds.size() == 1) {
                // Only do this if it goes from ZERO to ONE
                for (EmptyStateListener emptyStateListener : mEmptyStateListeners) {
                    emptyStateListener.onNonEmptyState();
                }
            }

            if (mItemIds.size() > 0 && (mItemIds.size() % mAdFrequency) == 0) {
                // Put in an advertisement if the amount of list items has reached threshold
                final String adId = AD_PREFIX + UUID.randomUUID().toString();
                mAdIds.add(adId);
                mItemIds.add(adId);
                FirebaseCrash.logcat(Log.DEBUG, LOG_TAG,
                        String.format(Locale.getDefault(), "Adding ad '%s'. Now showing %d ads.", adId, mAdIds.size()));
            }

            notifyDataSetChanged();
        }
    }

    private void removeUser(final User user) {
        synchronized (mListItemsLock) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "Removing user from internal records: " + user);
            mItemIds.remove(user.getUid());
            mUserMap.remove(user.getUid());
            if (mItemIds.size() == 0) {
                for (EmptyStateListener emptyStateListener : mEmptyStateListeners) {
                    emptyStateListener.onEmptyState();
                }
            }

            if (!mAdIds.isEmpty() && (mItemIds.size() % mAdFrequency) == 0) {
                final String adToRemove = mAdIds.remove();
                FirebaseCrash.logcat(Log.DEBUG, LOG_TAG,
                        String.format(Locale.getDefault(), "Removing ad '%s'. Now showing %d ads.", adToRemove, mAdIds.size()));
                mItemIds.remove(adToRemove);
            }

            notifyDataSetChanged();
        }
    }
}
