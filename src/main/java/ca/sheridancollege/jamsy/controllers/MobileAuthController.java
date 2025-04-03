package ca.sheridancollege.jamsy.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import ca.sheridancollege.jamsy.beans.SpotifyAuthResponse;
import ca.sheridancollege.jamsy.services.FirebaseAuthServices;
import ca.sheridancollege.jamsy.services.SpotifyService;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class MobileAuthController {

    private final SpotifyService spotifyService;
    private final FirebaseAuthServices firebaseAuthService;

    @Autowired
    public MobileAuthController(SpotifyService spotifyService, FirebaseAuthServices firebaseAuthService) {
        this.spotifyService = spotifyService;
        this.firebaseAuthService = firebaseAuthService;
    }

    @PostMapping("/token")
    public ResponseEntity<?> exchangeCodeForToken(
            @RequestParam("code") String code,
            @RequestParam("redirect_uri") String redirectUri) {
        
        try {
            // Exchange code for Spotify token
            String spotifyAccessToken = spotifyService.getUserAccessToken(code, redirectUri);
            
            // Get Spotify user ID to use as Firebase UID
            String spotifyUserId = spotifyService.getSpotifyUserId(spotifyAccessToken);
            
            // Generate Firebase custom token if using Firebase Auth
            String firebaseToken = firebaseAuthService.generateCustomToken(spotifyUserId);
            
            // Return both tokens
            SpotifyAuthResponse response = new SpotifyAuthResponse(
                spotifyAccessToken,
                "Bearer",
                firebaseToken
            );
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Authentication error: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    
    @GetMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestParam("refresh_token") String refreshToken) {
        try {
            // This would need to be implemented in the SpotifyService
            Map<String, String> tokens = spotifyService.refreshAccessToken(refreshToken);
            return ResponseEntity.ok(tokens);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Token refresh error: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
}
