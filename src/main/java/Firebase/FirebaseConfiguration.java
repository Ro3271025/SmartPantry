package Firebase;

import com.google.cloud.firestore.*;
import com.google.firebase.FirebaseOptions;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.cloud.FirestoreClient;

import java.io.InputStream;

public class FirebaseConfiguration {

    public static Firestore initialize() {
        try{
            InputStream serviceAccount = FirebaseConfiguration.class.getResourceAsStream("/key.json");
            if (serviceAccount == null) {
                throw new RuntimeException("Key file not found");
            }

            FirebaseOptions options = FirebaseOptions.builder().setCredentials(GoogleCredentials.fromStream(serviceAccount)).build();

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
                System.out.println("Firebase App initialized");
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return FirestoreClient.getFirestore();
    }
    public static Firestore getDatabase() {
        return FirestoreClient.getFirestore();
    }

    

}
