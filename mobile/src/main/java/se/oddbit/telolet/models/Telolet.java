package se.oddbit.telolet.models;

import com.google.firebase.database.IgnoreExtraProperties;
import com.google.firebase.database.ServerValue;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@IgnoreExtraProperties
public class Telolet {
    public static final String ATTR_REPLIED_AT = "repliedAt";

    private String mRequesterUid;
    private String mReceiverUid;
    private String mRequestLocation;
    private Long mRequestedAt;
    private Long mRepliedAt;

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

    public Long getRequestedAt() {
        return mRequestedAt;
    }

    public void setRequestedAt(final Long requestedAt) {
        mRequestedAt = requestedAt;
    }

    public Long getRepliedAt() {
        return mRepliedAt;
    }

    public void setRepliedAt(final Long repliedAt) {
        mRepliedAt = repliedAt;
    }

    @Override
    public String toString() {
        return String.format(Locale.getDefault(),
                "%s{req: %s, recv: %s, reqTS: %d}",
                Telolet.class.getSimpleName(), mRequesterUid, mReceiverUid, mRequestedAt);
    }

    public Map<String, Object> toNewRequestMap() {
        final Map<String, Object> map = new HashMap<>();

        map.put("requesterUid", mRequesterUid);
        map.put("receiverUid", mReceiverUid);
        map.put("requestLocation", mRequestLocation);
        map.put("requestedAt", ServerValue.TIMESTAMP);

        return map;
    }

    public Map<String, Object> toNewReplyMap() {
        final Map<String, Object> map = new HashMap<>();

        map.put("requesterUid", mRequesterUid);
        map.put("receiverUid", mReceiverUid);
        map.put("requestLocation", mRequestLocation);
        map.put("requestedAt", mRequestedAt);
        map.put("repliedAt", ServerValue.TIMESTAMP);

        return map;
    }}
