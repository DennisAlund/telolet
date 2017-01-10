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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import se.oddbit.telolet.models.User;

public class FirebaseUserRecyclerAdapter extends RecyclerView.Adapter<UserViewHolder> implements ChildEventListener {
    private static final String LOG_TAG = FirebaseUserRecyclerAdapter.class.getSimpleName();

    public interface EmptyStateListener {
        void onEmptyState();
        void onNonEmptyState();
    }

    private final Context mContext;
    private Query mQuery;
    private final Map<String, User> mUserMap = Collections.synchronizedMap(new HashMap<String, User>());
    private final List<String> mUserIdList = Collections.synchronizedList(new ArrayList<String>());
    private final List<EmptyStateListener> mEmptyStateListeners = new ArrayList<>();

    public FirebaseUserRecyclerAdapter(final Context context) {
        mContext = context;
    }

    @Override
    public UserViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onCreateViewHolder");
        final LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        final View rootView = inflater.inflate(R.layout.list_item_public_user, parent, false);
        return new UserViewHolder(mContext, rootView);
    }

    @Override
    public void onBindViewHolder(final UserViewHolder viewHolder, final int position) {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onBindViewHolder: " + position);
        final User user = mUserMap.get(mUserIdList.get(position));
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, String.format(Locale.getDefault(), "onBindViewHolder %s at position %d", user, position));
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
        final FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser != null && firebaseUser.getUid().equals(user.getUid())) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onChildAdded: skipping own user: " + user);
        } else {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onChildAdded: adding user to list: " + user);
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
        FirebaseCrash.logcat(Log.ERROR, LOG_TAG, "Error fetching users: " + error.getMessage());
    }

    public void setQuery(final Query query) {
        if (mQuery != null) {
            mQuery.removeEventListener(this);
        }
        for (EmptyStateListener emptyStateListener : mEmptyStateListeners) {
            emptyStateListener.onEmptyState();
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

    private synchronized void addUser(final User user) {
        mUserIdList.add(user.getUid());
        mUserMap.put(user.getUid(), user);
        if (mUserIdList.size() == 1) {
            // Only do this if it goes from ZERO to ONE
            for (EmptyStateListener emptyStateListener : mEmptyStateListeners) {
                emptyStateListener.onNonEmptyState();
            }
        }
    }

    private synchronized void removeUser(final User user) {
        mUserIdList.remove(user.getUid());
        mUserMap.remove(user.getUid());
        if (mUserIdList.size() == 0) {
            for (EmptyStateListener emptyStateListener : mEmptyStateListeners) {
                emptyStateListener.onEmptyState();
            }
        }
    }
}
