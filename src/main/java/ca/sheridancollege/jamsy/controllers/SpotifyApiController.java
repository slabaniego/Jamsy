package ca.sheridancollege.jamsy.controllers;

import ca.sheridancollege.jamsy.beans.SpotifyAuthResponse;
import ca.sheridancollege.jamsy.services.FirebaseAuthServices;
import ca.sheridancollege.jamsy.services.SpotifyService;
import com.google.firebase.auth.FirebaseAuthException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/spotify")
public class SpotifyApiController {

    private final SpotifyService spotifyService;
    private final FirebaseAuthServices firebaseAuthService;

    @Autowired
    public SpotifyApiController(SpotifyService spotifyService, FirebaseAuthServices firebaseAuthService) {
        this.spotifyService = spotifyService;
        this.firebaseAuthService = firebaseAuthService;
    }

    @PostMapping("/exchange")
    public ResponseEntity<?> exchangeCodeForToken(
            @RequestParam("code") String code,
            @RequestParam("redirect_uri") String redirectUri) {
        
        try {
            // Exchange code for Spotify token
            String spotifyAccessToken = spotifyService.getUserAccessToken(code, redirectUri);
            
            // Get Spotify user ID to use as Firebase UID
            String spotifyUserId = spotifyService.getSpotifyUserId(spotifyAccessToken);
            
            // Generate Firebase custom token
            String firebaseToken = firebaseAuthService.generateCustomToken(spotifyUserId);
            
            // Return both tokens
            SpotifyAuthResponse response = new SpotifyAuthResponse(
                spotifyAccessToken,
                "Bearer",
                firebaseToken
            );
            
            return ResponseEntity.ok(response);
            
        } catch (FirebaseAuthException e) {
            return ResponseEntity
                .status(500)
                .body("{\"error\": \"Firebase authentication error: " + e.getMessage() + "\"}");
        } catch (Exception e) {
            return ResponseEntity
                .status(500)
                .body("{\"error\": \"Server error: " + e.getMessage() + "\"}");
        }
    }
}