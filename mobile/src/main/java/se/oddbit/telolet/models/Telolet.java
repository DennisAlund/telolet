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
    public static final String ATTR_RESOLVED_AT = "resolvedAt";

    private String mId;
    private String mRequesterUid;
    private String mReceiverUid;
    private String mRequestLocation;
    private String mResolveLocation;
    private Long mRequestedAt;
    private Long mResolvedAt;

    public Telolet() {
    }

    public Telolet(final String id) {
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

    public Long getRequestedAt() {
        return mRequestedAt;
    }

    public void setRequestedAt(final Long requestedAt) {
        mRequestedAt = requestedAt;
    }

    public Long getResolvedAt() {
        return mResolvedAt;
    }

    public void setResolvedAt(final Long resolvedAt) {
        mResolvedAt = resolvedAt;
    }

    @Exclude
    public boolean isPendingRequestFor(@NonNull final User user) {
        return isPendingRequestFor(user.getUid());
    }

    @Exclude
    public boolean isPendingRequestFor(@NonNull final String uid) {
        return mResolvedAt == null && uid.equals(mReceiverUid);
    }

    @Exclude
    public boolean isPendingRequestBy(@NonNull final String uid) {
        return mResolvedAt == null && uid.equals(mRequesterUid);
    }

    @Exclude
    public boolean isPendingRequestBy(@NonNull final User user) {
        return isPendingRequestBy(user.getUid());
    }

    @Exclude
    public boolean isRequestedBy(@NonNull final String uid) {
        return uid.equals(mRequesterUid);
    }

    @Exclude
    public boolean isAnsweredBy(@NonNull final User user) {
        return mResolvedAt != null && user.getUid().equals(mReceiverUid);
    }

    @Override
    public String toString() {
        return String.format(Locale.getDefault(), "%s{id: %s}", Telolet.class.getSimpleName(), mId);
    }

    public Map<String, Object> toNewRequestMap() {
        final Map<String, Object> map = new HashMap<>();

        map.put("id", mId);
        map.put("requesterUid", mRequesterUid);
        map.put("receiverUid", mReceiverUid);
        map.put("requestLocation", mRequestLocation);
        map.put("requestedAt", ServerValue.TIMESTAMP);

        return map;
    }

    public Map<String, Object> toNewReplyMap() {
        final Map<String, Object> map = new HashMap<>();

        map.put("id", mId);
        map.put("requesterUid", mRequesterUid);
        map.put("receiverUid", mReceiverUid);
        map.put("requestLocation", mRequestLocation);
        map.put("requestedAt", mRequestedAt);
        map.put("resolvedAt", ServerValue.TIMESTAMP);
        map.put("resolveLocation", mResolveLocation);

        return map;
    }
}
