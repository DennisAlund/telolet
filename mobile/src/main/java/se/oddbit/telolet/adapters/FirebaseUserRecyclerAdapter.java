package se.oddbit.telolet.adapters;

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

import se.oddbit.telolet.R;
import se.oddbit.telolet.models.User;
import se.oddbit.telolet.viewHolders.AdViewHolder;
import se.oddbit.telolet.viewHolders.UserViewHolder;

import static se.oddbit.telolet.util.Constants.RemoteConfig.LIST_AD_FREQUENCY;

public class FirebaseUserRecyclerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> implements ChildEventListener {
    private static final String LOG_TAG = FirebaseUserRecyclerAdapter.class.getSimpleName();
    private static final int VIEW_TYPE_AD = 0xAD;
    private static final int VIEW_TYPE_DEFAULT = 0xDEF;

    private int mAdFrequency;
    private Query mQuery;
    private User mUser;

    private final Context mContext;
    private final Object mListItemsLock = new Object();
    private final List<String> mItemIds = new ArrayList<>();
    private final List<String> mFocusItemIds = new ArrayList<>();
    private final Map<String, User> mUserMap = new HashMap<>();

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
        final String listItemId = mItemIds.get(getItemPositionWithoutAds(position));
        final User user = mUserMap.get(listItemId);
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, String.format(Locale.getDefault(), "onBindViewHolder %s at position %d", user, position));
        userViewHolder.bind(user);
    }

    @Override
    public void onViewRecycled(final RecyclerView.ViewHolder viewHolder) {
        if (viewHolder.getItemViewType() == VIEW_TYPE_AD) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onViewRecycled: Advertisement");
            return;
        }

        final UserViewHolder userViewHolder = (UserViewHolder) viewHolder;
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onViewRecycled: " + userViewHolder);
        userViewHolder.clear();

        super.onViewRecycled(viewHolder);
    }

    @Override
    public void onAttachedToRecyclerView(final RecyclerView recyclerView) {
        if (mQuery != null) {
            mQuery.addChildEventListener(this);
        }
    }

    /**
     * Remove user state listener if the adapter is removed from the recycler view
     *
     * @param recyclerView the recycler view that this adapter is applied to
     */
    @Override
    public void onDetachedFromRecyclerView(final RecyclerView recyclerView) {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onDetachedFromRecyclerView");

        final FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        assert firebaseUser != null;

        mQuery.removeEventListener(this);
    }

    /**
     * Get the total number of users and ads currently displaying in the list.
     * The amount of ads showing is depending on the frequency variable and calculated based on
     * the amount of members.
     *
     * @return total number of items in view
     */
    @Override
    public int getItemCount() {
        return getItemPositionWithAds(mItemIds.size());
    }


    @Override
    public void onChildAdded(final DataSnapshot snapshot, final String previousChildName) {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, String.format("onChildAdded key: %s, prev: %s", snapshot.getKey(), previousChildName));
        final User user = snapshot.getValue(User.class);
        final FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser != null && firebaseUser.getUid().equals(user.getUid())) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onChildAdded: setting own user: " + user);
            mUser = user;
            // Need to make sure that user views that potentially has been displayed are re-displayed
            notifyDataSetChanged();
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

            // Need to make sure that user views that potentially has been displayed are re-displayed
            notifyDataSetChanged();
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

    public void setQuery(final Query query) {
        mAdFrequency = Integer.parseInt(FirebaseRemoteConfig.getInstance().getString(LIST_AD_FREQUENCY));
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, String.format(Locale.getDefault(),
                "setQuery: new query, showing ads every %d list item", mAdFrequency));

        if (mQuery != null) {
            mQuery.removeEventListener(this);
            clear();
        }

        mQuery = query;
        mQuery.addChildEventListener(this);
    }

    public void addFocusUser(final String uid) {
        mFocusItemIds.add(uid);
        moveToTop(uid);
    }

    public void removeFocusUser(final String uid) {
        mFocusItemIds.remove(uid);
    }

    private void moveToTop(@NonNull final String uid) {
        synchronized (mListItemsLock) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "moveToTop: uid=" + uid);
            final int originalPosition = mItemIds.indexOf(uid);
            if (originalPosition > 0) {
                // Only move if the item is present and not already on top
                mItemIds.remove(uid);
                mItemIds.add(0, uid);
                notifyItemMoved(getItemPositionWithAds(originalPosition), 0);
            } else {
                FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "moveToTop: user not in list uid=" + uid);
            }
        }
    }

    public void clear() {
        synchronized (mListItemsLock) {
            final int numItems = getItemCount();
            mItemIds.clear();
            mUserMap.clear();
            notifyItemRangeRemoved(0, numItems);
        }
    }

    private void updateUser(@NonNull final User user) {
        synchronized (mListItemsLock) {
            mUserMap.put(user.getUid(), user);
            final int changedUserIndex = mItemIds.indexOf(user.getUid());
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, String.format(Locale.getDefault(), "Updating %s at pos=%d", user, changedUserIndex));
            if (changedUserIndex >= 0) {
                notifyItemChanged(getItemPositionWithAds(changedUserIndex));
            }
        }
    }

    private void addUser(@NonNull final User user) {
        synchronized (mListItemsLock) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "Adding user to internal records: " + user);
            if (mFocusItemIds.contains(user.getUid())) {
                mItemIds.add(0, user.getUid());
            } else {
                mItemIds.add(user.getUid());
            }

            mUserMap.put(user.getUid(), user);
            final int indexOfAddedItem = mItemIds.indexOf(user.getUid());
            notifyItemInserted(getItemPositionWithAds(indexOfAddedItem));
        }
    }

    private void removeUser(@NonNull final User user) {
        synchronized (mListItemsLock) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "Removing user from internal records: " + user);
            final int indexOfRemovedItem = mItemIds.indexOf(user.getUid());
            mItemIds.remove(user.getUid());
            mUserMap.remove(user.getUid());
            notifyItemRemoved(getItemPositionWithAds(indexOfRemovedItem));
        }
    }

    private boolean isAdPosition(final int position) {
        return mAdFrequency > 0 && (position % mAdFrequency) == (mAdFrequency - 1);
    }

    /**
     * Recalculate teh position of an item with respect to injected ads in the list
     *
     * @param position An item position including injected ads
     * @return the actual position of an item, not counting the ads.
     */
    private int getItemPositionWithoutAds(final int position) {
        if (mAdFrequency <= 0) {
            return position;
        }
        return position - position / mAdFrequency;
    }

    /**
     * Recalculate teh position of an item with respect to injected ads in the list
     *
     * @param position An item position excluding injected ads
     * @return the position of an item, including counting the ads.
     */
    private int getItemPositionWithAds(final int position) {
        if (mAdFrequency <= 0) {
            return position;
        }

        return position + position / (mAdFrequency - 1);
    }
}
