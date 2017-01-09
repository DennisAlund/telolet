package se.oddbit.telolet.models;

import android.content.Context;

import com.google.firebase.database.IgnoreExtraProperties;

import java.util.Locale;
import java.util.Random;

import se.oddbit.telolet.R;

import static se.oddbit.telolet.models.User.UserProfile.DefaultValues.colors;

@IgnoreExtraProperties
public class User {
    private String mUid;
    private String mCurrentLocation;
    private UserProfile mProfile;

    public User() {
        mProfile = new UserProfile();
    }

    public User(final Context context, final String uid) {
        mUid = uid;
        mProfile = UserProfile.createRandom(context);
    }

    public String getUid() {
        return mUid;
    }

    public void setUid(final String uid) {
        mUid = uid;
        mProfile.setUid(uid);
    }

    public String getCurrentLocation() {
        return mCurrentLocation;
    }

    public void setCurrentLocation(final String currentLocation) {
        mCurrentLocation = currentLocation;
    }

    public UserProfile getProfile() {
        return mProfile;
    }

    public void setProfile(final UserProfile profile) {
        mProfile = profile;
    }


    @Override
    public String toString() {
        return String.format("%s{uid: %s}", User.class.getSimpleName(), mUid);
    }

    @IgnoreExtraProperties
    public static class UserProfile {
        private String mUid;
        private String mImage;
        private String mHandle;
        private String mColor;

        public UserProfile() {
        }

        public String getUid() {
            return mUid;
        }

        public void setUid(final String uid) {
            mUid = uid;
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

        @Override
        public String toString() {
            return String.format("%s{uid: %s, handle: %s}", UserProfile.class.getSimpleName(), mUid, mHandle);
        }

        public static UserProfile createRandom(final Context context) {
            UserProfile randomUser = new UserProfile();
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
}
