package se.oddbit.telolet.models;

import android.support.annotation.NonNull;

import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;

@IgnoreExtraProperties
public class UserState {
    public static final String ATTR_STATE = "state";

    public static final String PENDING_SENT = "pending_sent";
    public static final String PENDING_RECEIVED = "pending_received";
    public static final String RESOLVED = "telolelolet";

    private String mTeloletId;
    private String mState;

    public UserState(final String teloletId, final String state) {
        mTeloletId = teloletId;
        mState = state;
    }

    public UserState() {

    }

    public String getTeloletId() {
        return mTeloletId;
    }

    public void setTeloletId(final String teloletId) {
        mTeloletId = teloletId;
    }

    public String getState() {
        return mState;
    }

    public void setState(final String state) {
        mState = state;
    }

    @Exclude
    public boolean isState(@NonNull final String state) {
        return mState != null && state.equals(mState);
    }

    @Override
    public String toString() {
        return String.format("%s{telolet=%s, state=%s}", UserState.class.getSimpleName(), mTeloletId, mState);
    }
}
