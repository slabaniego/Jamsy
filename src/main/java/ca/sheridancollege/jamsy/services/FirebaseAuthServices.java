package ca.sheridancollege.jamsy.services;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import org.springframework.stereotype.Service;

@Service
public class FirebaseAuthServices {

    /**
     * Generate a custom Firebase token for the given user ID.
     * In this case, we're using the Spotify user ID as the Firebase user ID.
     */
    public String generateCustomToken(String spotifyUserId) throws FirebaseAuthException {
        return FirebaseAuth.getInstance().createCustomToken(spotifyUserId);
    }
}