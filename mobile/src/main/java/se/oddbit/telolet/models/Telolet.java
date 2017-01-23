package se.oddbit.telolet.models;

import android.support.annotation.NonNull;

import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;
import com.google.firebase.database.ServerValue;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@IgnoreExtraProperties
public class Telolet {
    public static final String ATTR_STATE = "state";

    public static final String STATE_RESOLVED = "0:resolved";
    public static final String STATE_TIMEOUT = "0:timeout";
    public static final String STATE_PENDING = "1:pending";
    public static final String STATE_REPLIED = "2:replied";

    private String mId;
    private String mRequesterUid;
    private String mReceiverUid;
    private String mRequestLocation;
    private String mResolveLocation;
    private Long mCreated;
    private Long mModified;
    private String mState;

    public Telolet() {
        mState = STATE_PENDING;
    }

    public Telolet(final String id) {
        super();
        mId = id;
    }

    public String getId() {
        return mId;
    }

    public void setId(final String id) {
        mId = id;
    }

    public String getRequesterUid() {
        return mRequesterUid;
    }

    public void setRequesterUid(final String requesterUid) {
        mRequesterUid = requesterUid;
    }

    public String getReceiverUid() {
        return mReceiverUid;
    }

    public void setReceiverUid(final String receiverUid) {
        mReceiverUid = receiverUid;
    }

    public String getRequestLocation() {
        return mRequestLocation;
    }

    public void setRequestLocation(final String requestLocation) {
        mRequestLocation = requestLocation;
    }

    public String getResolveLocation() {
        return mResolveLocation;
    }

    public void setResolveLocation(final String resolveLocation) {
        mResolveLocation = resolveLocation;
    }

    public Long getCreated() {
        return mCreated;
    }

    public void setCreated(final Long created) {
        mCreated = created;
    }

    public Long getModified() {
        return mModified;
    }

    public void setModified(final Long modified) {
        mModified = modified;
    }

    public String getState() {
        return mState;
    }

    public void setState(final String state) {
        switch (state) {
            case STATE_RESOLVED:
            case STATE_TIMEOUT:
            case STATE_REPLIED:
            case STATE_PENDING:
                mState = state;
                break;
            default:
                mState = null;
        }
    }

    @Exclude
    public boolean isInProgress() {
        return mState != null && !mState.startsWith("0:");
    }

    @Exclude
    public boolean isState(@NonNull final String state) {
        return mState != null && mState.equals(state);
    }

    @Override
    public String toString() {
        return String.format(Locale.getDefault(), "%s{id: %s}", Telolet.class.getSimpleName(), mId);
    }

    public Map<String, Object> toValueMap() {
        final Map<String, Object> map = new HashMap<>();

        map.put("id", mId);
        map.put("state", mState);
        map.put("created", mCreated == null ? ServerValue.TIMESTAMP : mCreated);
        map.put("modified", ServerValue.TIMESTAMP);

        map.put("requesterUid", mRequesterUid);
        map.put("requestLocation", mRequestLocation);

        map.put("receiverUid", mReceiverUid);
        map.put("resolveLocation", mReceiverUid);

        return map;
    }
}
