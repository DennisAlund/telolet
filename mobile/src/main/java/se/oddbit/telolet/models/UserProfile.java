package se.oddbit.telolet.models;

import android.content.Context;

import com.google.firebase.database.IgnoreExtraProperties;

import java.util.Locale;
import java.util.Random;

import se.oddbit.telolet.R;

import static se.oddbit.telolet.models.UserProfile.DefaultValues.colors;


@IgnoreExtraProperties
public class UserProfile {
    private String uid;
    private String image;
    private String handle;
    private String color;

    public UserProfile() {
    }

    public UserProfile(final String uid) {
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

    @Override
    public String toString() {
        return String.format("%s{uid: %s, handle: %s}", UserProfile.class.getSimpleName(), uid, handle);
    }

    public static UserProfile createRandom(final Context context, final String uid) {
        UserProfile randomUser = new UserProfile(uid);
        final Random rnd = new Random();

        randomUser.setColor(colors[rnd.nextInt(colors.length)]);
        randomUser.setImage(context.getString(R.string.bus_image_base_file_name) + rnd.nextInt(5));
        final String randomHandle = String.format(Locale.getDefault(), "%s %03d",
                context.getString(R.string.base_handle), (rnd.nextInt(998) + 1));
        randomUser.setHandle(randomHandle);

        return randomUser;
    }

    static class DefaultValues {
        static final String[] colors = new String[]{
                "f44336", "e91e63", "9c27b0", "673ab7", "3f51b5", "2196f3", "03a9f4", "00bcd4",
                "009688", "4caf50", "8bc34a", "cddc39", "ffeb3b", "ffc107", "ff9800", "ff5722"
        };
    }
}
