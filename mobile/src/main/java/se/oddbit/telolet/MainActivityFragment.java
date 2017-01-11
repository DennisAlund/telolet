package se.oddbit.telolet;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.crash.FirebaseCrash;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;

import java.util.Locale;

import se.oddbit.telolet.models.User;

import static se.oddbit.telolet.util.Constants.Database.USERS;
import static se.oddbit.telolet.util.Constants.RemoteConfig.OLC_BOX_SIZE;

/**
 * A placeholder fragment containing a simple view.
 */
public class MainActivityFragment extends Fragment
        implements FirebaseAuth.AuthStateListener, ValueEventListener, FirebaseUserRecyclerAdapter.EmptyStateListener {
    private static final String LOG_TAG = MainActivityFragment.class.getSimpleName();

    private View mEmptyStateView;
    private RecyclerView mMemberList;
    private FirebaseUserRecyclerAdapter mRecyclerAdapter;

    public MainActivityFragment() {
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_main, container, false);
        mMemberList = (RecyclerView) rootView.findViewById(R.id.user_list);
        mEmptyStateView = rootView.findViewById(R.id.on_empty_list_placeholder_view);

        final Button button = (Button) mEmptyStateView.findViewById(R.id.invite_friends_button);
        button.setOnClickListener(new FriendInvitationButtonClickHandler(getActivity(), LOG_TAG));

        return rootView;
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        final LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getActivity());
        mMemberList.setLayoutManager(linearLayoutManager);
        mRecyclerAdapter = new FirebaseUserRecyclerAdapter(getContext());
        mMemberList.setAdapter(mRecyclerAdapter);
    }

    @Override
    public void onPause() {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onPause");
        mRecyclerAdapter.removeEmptyStateListener(this);
        stopUserListListener();
        FirebaseAuth.getInstance().removeAuthStateListener(this);
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onResume");
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            FirebaseAuth.getInstance().addAuthStateListener(this);
        }
        mRecyclerAdapter.addEmptyStateListener(this);
        startUserListListener();
    }


    @Override
    public void onAuthStateChanged(@NonNull final FirebaseAuth firebaseAuth) {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onAuthStateChanged");
        final FirebaseUser firebaseUser = firebaseAuth.getCurrentUser();
        if (firebaseUser == null) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onAuthStateChanged: NOT LOGGED IN");
            return;
        }

        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG,
                String.format("onAuthStateChanged: %s, %s, %s", firebaseUser.getProviderId(), firebaseUser.getEmail(), firebaseUser.getUid()));

        FirebaseAuth.getInstance().removeAuthStateListener(this);
        // In case this happened *after* onResume
        startUserListListener();
    }


    private void startUserListListener() {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "startUserListListener");
        final FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "startUserListListener: Not yet logged in. Can't start listing data.");
            return;
        }
        final String uid = firebaseUser.getUid();
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "startUserListListener: starting to listen for user: " + uid);
        FirebaseDatabase.getInstance().getReference(USERS).child(uid).addValueEventListener(this);
    }

    private void stopUserListListener() {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "stopUserListListener");
        final FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "stopUserListListener: Not yet logged in. Can't stop listing data.");
            return;
        }

        final String uid = firebaseUser.getUid();
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "stopUserListListener: stop to listen for user: " + uid);
        FirebaseDatabase.getInstance().getReference(USERS).child(uid).removeEventListener(this);
    }

    @Override
    public void onDataChange(final DataSnapshot snapshot) {
        final User currentUser = snapshot.getValue(User.class);
        final int boxSize = (int) FirebaseRemoteConfig.getInstance().getLong(OLC_BOX_SIZE);
        final String olcBox = currentUser.getLocation().substring(0, boxSize);
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, String.format(Locale.getDefault(),
                "updateAdapter: Setting %s query to OLC box %s (size %d)", currentUser, olcBox, boxSize));

        final Query query = FirebaseDatabase.getInstance().getReference(USERS)
                .orderByChild(User.ATTR_LOCATION)
                .startAt(olcBox + '\u0000')
                .endAt(olcBox + '\uf8ff');

        mRecyclerAdapter.setQuery(query);
    }

    @Override
    public void onCancelled(final DatabaseError error) {
        final FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            FirebaseCrash.logcat(Log.ERROR, LOG_TAG, String.format("Error fetching own user (not logged in): %s", error.getMessage()));
        } else {
            final String uid = firebaseUser.getUid();
            FirebaseCrash.logcat(Log.ERROR, LOG_TAG, String.format("Error fetching own user %s: %s", uid, error.getMessage()));
        }
    }

    @Override
    public void onEmptyState() {
        mMemberList.setVisibility(View.GONE);
        mEmptyStateView.setVisibility(View.VISIBLE);
    }

    @Override
    public void onNonEmptyState() {
        mMemberList.setVisibility(View.VISIBLE);
        mEmptyStateView.setVisibility(View.GONE);
    }
}
