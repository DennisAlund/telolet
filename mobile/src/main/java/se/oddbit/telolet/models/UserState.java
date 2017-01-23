package se.oddbit.telolet.models;

import android.support.annotation.NonNull;

import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;

import java.util.HashMap;
import java.util.Map;

@IgnoreExtraProperties
public class UserState {
    public static final String ATTR_STATE = "state";
    public static final String ATTR_IS_FOCUS_STATE = "isFocusState";

    public static final String PENDING_SENT = "request_sent";
    public static final String PENDING_RECEIVED = "request_received";
    public static final String TELOLET_SENT = "telolelolet_sent";
    public static final String TELOLET_RECEIVED = "telolelolet_received";

    private Telolet mTelolet;
    private String mState;
    private boolean mIsFocusState;

    public UserState(final Telolet telolet, final String state) {
        mTelolet = telolet;
        mState = state;
        mIsFocusState = state.equals(PENDING_RECEIVED) || state.equals(TELOLET_RECEIVED);
    }

    public UserState(final Telolet telolet) {
        mTelolet = telolet;
    }

    public UserState() {
    }

    public Telolet getTelolet() {
        return mTelolet;
    }

    public void setTelolet(final Telolet telolet) {
        mTelolet = telolet;
    }

    public String getState() {
        return mState;
    }

    public void setState(final String state) {
        mState = state;
    }

    public boolean isFocusState() {
        return mIsFocusState;
    }

    public void setFocusState(final boolean focusState) {
        mIsFocusState = focusState;
    }

    @Exclude
    public boolean isState(@NonNull final String state) {
        return mState != null && state.equals(mState);
    }

    @Override
    public String toString() {
        return String.format("%s{telolet=%s, state=%s}", UserState.class.getSimpleName(), mTelolet, mState);
    }

    public Map<String, Object> toValueMap() {
        final Map<String, Object> map = new HashMap<>();

        map.put("state", mState);
        map.put("isFocusState", mIsFocusState);
        map.put("telolet", mTelolet.toValueMap());

        return map;
    }
}
