package se.oddbit.telolet.models;

import com.google.firebase.database.IgnoreExtraProperties;

@IgnoreExtraProperties
public class PublicUser {
    private String uid;
    private String image;
    private String handle;
    private String color;

    public PublicUser() {
    }

    public PublicUser(final String uid) {
        this.uid = uid;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(final String uid) {
        this.uid = uid;
    }

    public String getImage() {
        return image;
    }

    public void setImage(final String image) {
        this.image = image;
    }

    public String getHandle() {
        return handle;
    }

    public void setHandle(final String handle) {
        this.handle = handle;
    }

    public String getColor() {
        return color;
    }

    public void setColor(final String color) {
        this.color = color;
    }


    public static PublicUser createRandom(final String uid) {
        PublicUser randomUser = new PublicUser(uid);

        randomUser.setColor("e91e63");
        randomUser.setHandle("Bus 123");
        return randomUser;
    }
}
