package se.oddbit.telolet.util;

public final class Constants {
    public static final class Analytics {
        public static final class Events {
            public static final String LOCATION = "location";
            public static final String TELOLET_REQUEST = "om_telolet_om";
            public static final String TELOLET_RESPONSE = "telolelolet";
        }
        public static final class Param {
            public static final String OLC = "olc";
        }
    }

    public static final class Database {
        public static final String TELOLETS_RECEIVED = "teloletsReceived";
        public static final String TELOLETS_SENT = "teloletsSent";
        public static final String USERS = "users";
    }

    public static final class RemoteConfig {
        public static final String OLC_BOX_SIZE = "OLC_BOX_SIZE";
        public static final String LOCATION_UPDATES_THRESHOLD_METERS = "LOCATION_UPDATES_THRESHOLD_METERS";
        public static final String LOCATION_UPDATES_INTERVAL = "LOCATION_UPDATES_INTERVAL";
        public static final String FASTEST_LOCATION_UPDATE_INTERVAL = "FASTEST_LOCATION_UPDATE_INTERVAL";

    }
}
