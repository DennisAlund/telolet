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

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.crash.FirebaseCrash;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import se.oddbit.telolet.adapters.FirebaseUserRecyclerAdapter;
import se.oddbit.telolet.models.User;

import static se.oddbit.telolet.util.Constants.Database.USERS;

/**
 * A placeholder fragment containing a simple view.
 */
public class MainActivityFragment extends Fragment implements FirebaseAuth.AuthStateListener, ValueEventListener {
    private static final String LOG_TAG = MainActivityFragment.class.getSimpleName();

    private RecyclerView mMemberList;
    private DatabaseReference mCurrentUserDatabaseRef;
    private User mCurrentUser;

    public MainActivityFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_main, container, false);
        mMemberList = (RecyclerView) rootView.findViewById(R.id.user_list);
        return rootView;
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        final LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getActivity());
        mMemberList.setLayoutManager(linearLayoutManager);
        updateAdapter();
    }

    @Override
    public void onPause() {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onPause");
        stopLocationListener();
        super.onPause();
    }

    @Override
    public void onResume() {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onResume");
        super.onResume();
        startLocationListener();
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
        startLocationListener();
    }

    @Override
    public void onDataChange(final DataSnapshot snapshot) {
        if (!snapshot.exists()) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onDataChange: No user object available");
            return;
        }

        mCurrentUser = snapshot.getValue(User.class);
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onDataChange: setting current user: " + mCurrentUser);
        updateAdapter();
    }

    @Override
    public void onCancelled(final DatabaseError error) {
        FirebaseCrash.logcat(Log.ERROR, LOG_TAG, "PATH error: " + error.getMessage());
    }

    private void startLocationListener() {
        final FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "startLocationListener: Not yet logged in. Can't start listing data.");
            return;
        }

        final String uid = firebaseUser.getUid();
        if (mCurrentUserDatabaseRef == null) {
            mCurrentUserDatabaseRef = FirebaseDatabase.getInstance().getReference(USERS).child(uid);
        }

        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "startLocationListener: starting to listen for user info: " + uid);
        mCurrentUserDatabaseRef.addValueEventListener(this);
    }

    private void stopLocationListener() {
        if (mCurrentUserDatabaseRef == null) {
            return;
        }
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "stopLocationListener");
        mCurrentUserDatabaseRef.removeEventListener(this);
    }

    public void updateAdapter() {
        if (mCurrentUser == null) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "updateAdapter: NO CURRENT USER");
            return;
        }

        if (mCurrentUser.getLocation() == null) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "updateAdapter: NO CURRENT LOCATION");
            return;
        }

        mMemberList.setAdapter(new FirebaseUserRecyclerAdapter(getContext(), mCurrentUser));
    }
}
