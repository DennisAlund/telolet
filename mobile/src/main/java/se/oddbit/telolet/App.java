package se.oddbit.telolet;


import android.app.Application;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.crash.FirebaseCrash;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Logger;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;

public class App extends Application {

    private static final String LOG_TAG = "Application";

    @Override
    public void onCreate() {
        super.onCreate();
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "onCreate");

        // Database
        FirebaseDatabase.getInstance().setPersistenceEnabled(true);
        if (BuildConfig.DEBUG) {
            FirebaseDatabase.getInstance().setLogLevel(Logger.Level.DEBUG);
        }

        // Remote configuration
        final FirebaseRemoteConfig remoteConfig = FirebaseRemoteConfig.getInstance();
        FirebaseRemoteConfigSettings configSettings = new FirebaseRemoteConfigSettings.Builder()
                .setDeveloperModeEnabled(BuildConfig.DEBUG)
                .build();
        remoteConfig.setConfigSettings(configSettings);
        remoteConfig.setDefaults(R.xml.remote_config_defaults);
        remoteConfig.fetch().addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull final Task<Void> task) {
                if (!task.isSuccessful()) {
                    FirebaseCrash.logcat(Log.ERROR, LOG_TAG, "Could not fetch Firebase remote config. Will go with defaults");
                } else {
                    FirebaseCrash.logcat(Log.INFO, LOG_TAG, "Successfully fetched Firebase remote configuration");
                    remoteConfig.activateFetched();
                }
            }
        });
    }
}
