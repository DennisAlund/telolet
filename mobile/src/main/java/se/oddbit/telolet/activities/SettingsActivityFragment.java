package se.oddbit.telolet.activities;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.crash.FirebaseCrash;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Locale;

import se.oddbit.telolet.BuildConfig;
import se.oddbit.telolet.R;
import se.oddbit.telolet.models.User;

import static se.oddbit.telolet.util.Constants.Database.USERS;

/**
 * A placeholder fragment containing a simple view.
 */
public class SettingsActivityFragment extends PreferenceFragment
        implements Preference.OnPreferenceChangeListener, ValueEventListener {
    private static final String LOG_TAG = SettingsActivityFragment.class.getSimpleName();

    public SettingsActivityFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.pref_general);

        final Preference appVersionPref = findPreference(getString(R.string.pref_app_version));
        appVersionPref.setSummary(String.format(Locale.getDefault(), "%s (%d)", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE));

        findPreference(getString(R.string.pref_user_handle)).setOnPreferenceChangeListener(this);

        final FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        assert firebaseUser != null;

        FirebaseDatabase.getInstance().getReference(USERS).child(firebaseUser.getUid()).addValueEventListener(this);
    }

    @Override
    public boolean onPreferenceChange(final Preference preference, final Object value) {
        final FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        assert firebaseUser != null;

        if (preference.getKey().equals(getString(R.string.pref_user_handle))) {
            final String newName = (String) value;
            if (newName.length() < 1) {
                return false;
            }

            FirebaseDatabase.getInstance().getReference(USERS).child(firebaseUser.getUid()).child(User.ATTR_HANDLE).setValue(value);
            return true;
        }
        return false;
    }

    @Override
    public void onDataChange(final DataSnapshot snapshot) {
        if (!snapshot.exists()) {
            return;
        }

        final User user = snapshot.getValue(User.class);
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onDataChange: " + user);

        final SharedPreferences.Editor prefEditor = PreferenceManager.getDefaultSharedPreferences(getActivity()).edit();

        final Preference userHandlePref = findPreference(getString(R.string.pref_user_handle));
        userHandlePref.setSummary(user.getHandle());
        prefEditor.putString(getString(R.string.pref_user_handle), user.getHandle());

        prefEditor.apply();
    }

    @Override
    public void onCancelled(final DatabaseError error) {
        FirebaseCrash.logcat(Log.ERROR, LOG_TAG, "Error getting user data: " + error.getMessage());
        FirebaseCrash.report(error.toException());
    }
}
