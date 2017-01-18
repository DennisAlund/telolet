package se.oddbit.telolet.fragments;

import android.os.Bundle;
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
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;

import java.util.Locale;

import se.oddbit.telolet.interfaces.CurrentUserInterface;
import se.oddbit.telolet.adapters.FirebaseUserRecyclerAdapter;
import se.oddbit.telolet.eventHandlers.FriendInvitationButtonClickHandler;
import se.oddbit.telolet.R;
import se.oddbit.telolet.models.User;

import static se.oddbit.telolet.util.Constants.Database.USERS;
import static se.oddbit.telolet.util.Constants.RemoteConfig.OLC_BOX_SIZE;

/**
 * A placeholder fragment containing a simple view.
 */
public class MainActivityFragment extends Fragment implements CurrentUserInterface, FirebaseUserRecyclerAdapter.EmptyStateListener {
    private static final String LOG_TAG = MainActivityFragment.class.getSimpleName();

    private View mEmptyStateView;
    private RecyclerView mUserList;
    private FirebaseUserRecyclerAdapter mRecyclerAdapter;

    public MainActivityFragment() {
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        assert firebaseUser != null;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_main, container, false);
        mEmptyStateView = rootView.findViewById(R.id.on_empty_list_placeholder_view);

        final Button button = (Button) mEmptyStateView.findViewById(R.id.invite_friends_button);
        button.setOnClickListener(new FriendInvitationButtonClickHandler(getActivity(), LOG_TAG));

        mUserList = (RecyclerView) rootView.findViewById(R.id.user_list);

        return rootView;
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        final LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getActivity());
        mUserList.setLayoutManager(linearLayoutManager);
        mRecyclerAdapter = new FirebaseUserRecyclerAdapter(getContext());
        mUserList.setAdapter(mRecyclerAdapter);
    }


    @Override
    public void onPause() {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onPause");
        mRecyclerAdapter.removeEmptyStateListener(this);

        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onResume");
        mRecyclerAdapter.addEmptyStateListener(this);
    }

    @Override
    public void setCurrentUser(final User currentUser) {
        final int boxSize = (int) FirebaseRemoteConfig.getInstance().getLong(OLC_BOX_SIZE);
        if (currentUser.getLocation() == null) {
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, String.format(Locale.getDefault(),
                    "updateAdapter: User %s doesn't have any location yet. Waiting...", currentUser));
            return;
        }

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
    public void onEmptyState() {
        mUserList.setVisibility(View.GONE);
        mEmptyStateView.setVisibility(View.VISIBLE);
    }

    @Override
    public void onNonEmptyState() {
        mUserList.setVisibility(View.VISIBLE);
        mEmptyStateView.setVisibility(View.GONE);
    }
}
