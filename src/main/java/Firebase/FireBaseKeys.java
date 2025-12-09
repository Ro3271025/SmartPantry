package Firebase;

import java.io.IOException;
import java.util.Properties;

public class FireBaseKeys {
    private static final Properties props = new Properties();
    static {
        try {
            props.load(FireBaseKeys.class.getResourceAsStream("/configuration.properties"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public static final String WEB_API_KEY = props.getProperty("FIREBASE_API_KEY");
}
