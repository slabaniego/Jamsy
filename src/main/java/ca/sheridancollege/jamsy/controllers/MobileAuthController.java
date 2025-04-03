package ca.sheridancollege.jamsy.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;

import ca.sheridancollege.jamsy.beans.SpotifyAuthResponse;
import ca.sheridancollege.jamsy.services.FirebaseAuthServices;
import ca.sheridancollege.jamsy.services.SpotifyService;

import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST})
public class MobileAuthController {

    private static final Logger logger = LoggerFactory.getLogger(MobileAuthController.class);
    private final SpotifyService spotifyService;
    private final FirebaseAuthServices firebaseAuthService;

    @Value("${spotify.mobile.redirect-uri}")
    private String mobileRedirectUri;

    @Autowired
    public MobileAuthController(SpotifyService spotifyService, FirebaseAuthServices firebaseAuthService) {
        this.spotifyService = spotifyService;
        this.firebaseAuthService = firebaseAuthService;
    }

    @PostMapping(
        value = "/token", 
        consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.APPLICATION_JSON_VALUE, MediaType.ALL_VALUE}
    )
    public ResponseEntity<?> exchangeCodeForToken(
            @RequestParam(value = "code", required = true) String code,
            @RequestParam(value = "redirect_uri", required = false) String redirectUri) {
        
        // Use the provided redirect URI or fall back to the mobile app's default
        String effectiveRedirectUri = redirectUri != null ? redirectUri : mobileRedirectUri;
        logger.info("Received token exchange request with code: {} and redirect_uri: {}", code, effectiveRedirectUri);
        
        try {
            // Exchange code for Spotify token
            logger.info("Attempting to exchange code for Spotify token");
            String spotifyAccessToken = spotifyService.getUserAccessToken(code, effectiveRedirectUri);
            logger.info("Spotify token exchange successful");
            
            // Get Spotify user ID to use as Firebase UID
            logger.info("Fetching Spotify user ID");
            String spotifyUserId = spotifyService.getSpotifyUserId(spotifyAccessToken);
            logger.info("Got Spotify user ID: {}", spotifyUserId);
            
            // Generate Firebase custom token if using Firebase Auth
            logger.info("Generating Firebase token");
            String firebaseToken = firebaseAuthService.generateCustomToken(spotifyUserId);
            logger.info("Firebase token generated successfully");
            
            // Return both tokens
            SpotifyAuthResponse response = new SpotifyAuthResponse(
                spotifyAccessToken,
                "Bearer",
                firebaseToken
            );
            
            logger.info("Returning successful response with tokens");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error during token exchange: {}", e.getMessage(), e);
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
