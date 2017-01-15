package se.oddbit.telolet.util;

public final class Constants {
    public static final class RequestCodes {
        public static final int FRIEND_INVITATION_REQUEST = 0xf00;
    }

    public static final class Analytics {
        public static final class Events {
            public static final String FRIEND_INVITE_ACCEPTED = "invite_accepted";
            public static final String FRIEND_INVITE_SENT = "invite_sent";
            public static final String LOCATION = "location";
            public static final String TELOLET_REQUEST = "om_telolet_om";
            public static final String TELOLET_RESPONSE = "telolelolet";
        }
        public static final class Param {
            public static final String OLC = "olc";
        }

        public static final class UserProperty {
            public static final String GAME_LEVEL = "level";
            public static final String USER_LOCATION = "location";
        }
    }

    public static final class Database {
        public static final String TELOLET_REQUESTS_RECEIVED = "requestsReceived";
        public static final String TELOLET_REQUESTS_SENT = "requestsSent";
        public static final String USERS = "users";
    }

    public static final class RemoteConfig {
        public static final String RESPONSE_THRESHOLD_MILLISEC = "RESPONSE_THRESHOLD_MILLISEC";
        public static final String TEST_GROUP = "TEST_GROUP";
        public static final String LIST_AD_FREQUENCY = "LIST_AD_FREQUENCY";
        public static final String OLC_BOX_SIZE = "OLC_BOX_SIZE";
        public static final String LOCATION_UPDATES_THRESHOLD_METERS = "LOCATION_UPDATES_THRESHOLD_METERS";
        public static final String LOCATION_UPDATES_INTERVAL = "LOCATION_UPDATES_INTERVAL";
        public static final String FASTEST_LOCATION_UPDATE_INTERVAL = "FASTEST_LOCATION_UPDATE_INTERVAL";

    }
}
