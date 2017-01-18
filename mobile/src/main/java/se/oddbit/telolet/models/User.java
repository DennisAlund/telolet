package se.oddbit.telolet.models;

import android.content.Context;

import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;
import com.google.firebase.iid.FirebaseInstanceId;

import java.util.Locale;
import java.util.Random;

import se.oddbit.telolet.R;

import static se.oddbit.telolet.models.User.DefaultValues.colors;

@IgnoreExtraProperties
public class User {
    public static final String ATTR_LAST_LOGIN = "lastLogin";
    public static final String ATTR_FCM_TOKEN = "fcmToken";
    public static final String ATTR_LOCATION = "location";

    private String mUid;
    private String mLocation;
    private Long mLastLogin;
    private String mImage;
    private String mHandle;
    private String mColor;
    private String mFcmToken;

    public User() {
    }

    public User(final String uid) {
        mUid = uid;
    }

    public String getUid() {
        return mUid;
    }

    public void setUid(final String uid) {
        mUid = uid;
    }

    public String getLocation() {
        return mLocation;
    }

    public void setLocation(final String location) {
        mLocation = location;
    }

    public Long getLastLogin() {
        return mLastLogin;
    }

    public void setLastLogin(final Long lastLogin) {
        mLastLogin = lastLogin;
    }

    public String getImage() {
        return mImage;
    }

    public void setImage(final String image) {
        mImage = image;
    }

    public String getHandle() {
        return mHandle;
    }

    public void setHandle(final String handle) {
        mHandle = handle;
    }

    public String getColor() {
        return mColor;
    }

    public void setColor(final String color) {
        mColor = color;
    }

    public String getFcmToken() {
        return mFcmToken;
    }

    public void setFcmToken(final String fcmToken) {
        mFcmToken = fcmToken;
    }

    @Override
    public String toString() {
        return String.format("%s{uid: %s}", User.class.getSimpleName(), mUid);
    }

    @Exclude
    public boolean isValid() {
        return mUid != null && !mUid.isEmpty();
    }

    public static User createRandom(final Context context, final String uid) {
        User randomCurrentUser = new User(uid);
        final Random rnd = new Random();

        randomCurrentUser.setFcmToken(FirebaseInstanceId.getInstance().getToken());
        randomCurrentUser.setColor(colors[rnd.nextInt(colors.length)]);
        randomCurrentUser.setImage(context.getString(R.string.bus_image_base_file_name) + rnd.nextInt(5));
        final String randomHandle = String.format(Locale.getDefault(), "%s %03d",
                context.getString(R.string.base_handle), (rnd.nextInt(998) + 1));
        randomCurrentUser.setHandle(randomHandle);

        return randomCurrentUser;
    }

    static class DefaultValues {
        static final String[] colors = new String[]{
                "#f44336", "#e91e63", "#9c27b0", "#673ab7", "#3f51b5", "#2196f3", "#03a9f4", "#00bcd4",
                "#009688", "#4caf50", "#8bc34a", "#cddc39", "#ffeb3b", "#ffc107", "#ff9800", "#ff5722"
        };
    }
}
