package se.oddbit.telolet.models;

import com.google.firebase.database.IgnoreExtraProperties;

@IgnoreExtraProperties
public class User {
    private String uid;
    private String currentLocation;

    public User() {
    }

    public User(final String uid) {
        this.uid = uid;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(final String uid) {
        this.uid = uid;
    }

    public String getCurrentLocation() {
        return currentLocation;
    }

    public void setCurrentLocation(final String currentLocation) {
        this.currentLocation = currentLocation;
    }

    @Override
    public String toString() {
        return String.format("%s{uid: %s}", User.class.getSimpleName(), uid);
    }
}
