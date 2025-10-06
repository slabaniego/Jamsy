package ca.sheridancollege.jamsy.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;

import ca.sheridancollege.jamsy.beans.SpotifyAuthResponse;
import ca.sheridancollege.jamsy.services.FirebaseAuthServices;
import ca.sheridancollege.jamsy.services.spotify.SpotifyAuthService;
import ca.sheridancollege.jamsy.services.spotify.SpotifyUserService;

import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST})
public class MobileAuthController {

    private static final Logger logger = LoggerFactory.getLogger(MobileAuthController.class);

    private final SpotifyAuthService spotifyAuthService;
    private final SpotifyUserService spotifyUserService;
    private final FirebaseAuthServices firebaseAuthService;

    @Value("${spotify.mobile.redirect-uri}")
    private String mobileRedirectUri;

    @Autowired
    public MobileAuthController(
            SpotifyAuthService spotifyAuthService,
            SpotifyUserService spotifyUserService,
            FirebaseAuthServices firebaseAuthService
    ) {
        this.spotifyAuthService = spotifyAuthService;
        this.spotifyUserService = spotifyUserService;
        this.firebaseAuthService = firebaseAuthService;
    }

    /**
     * Exchange authorization code for Spotify + Firebase tokens (Mobile flow)
     */
    @PostMapping(
        value = "/token",
        consumes = {
            MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            MediaType.APPLICATION_JSON_VALUE,
            MediaType.ALL_VALUE
        }
    )
    public ResponseEntity<?> exchangeCodeForToken(
            @RequestParam("code") String code,
            @RequestParam(value = "redirect_uri", required = false) String redirectUri) {

        String effectiveRedirectUri = (redirectUri != null) ? redirectUri : mobileRedirectUri;
        logger.info("Received token exchange request with code: {} and redirect_uri: {}", code, effectiveRedirectUri);

        try {
            // Step 1: Exchange authorization code for Spotify access token
            logger.info("Attempting to exchange code for Spotify access token...");
            Map<String, Object> tokenData = spotifyAuthService.exchangeCodeForAccessToken(code, effectiveRedirectUri);
            String accessToken = (String) tokenData.get("access_token");
            String refreshToken = (String) tokenData.get("refresh_token");

            logger.info("Spotify token exchange successful");

            // Step 2: Retrieve Spotify user ID for Firebase mapping
            String spotifyUserId = spotifyUserService.getSpotifyUserId(accessToken);
            logger.info("Retrieved Spotify user ID: {}", spotifyUserId);

            // Step 3: Generate Firebase custom token (server-side authentication)
            String firebaseToken = firebaseAuthService.generateCustomToken(spotifyUserId);
            logger.info("Firebase token generated successfully");

            // Step 4: Construct unified response
            SpotifyAuthResponse response = new SpotifyAuthResponse(
                    accessToken,
                    "Bearer",
                    firebaseToken
            );

            // Optionally include refresh token for mobile app persistence
            Map<String, Object> payload = new HashMap<>();
            payload.put("spotify", response);
            payload.put("refresh_token", refreshToken);

            return ResponseEntity.ok(payload);

        } catch (Exception e) {
            logger.error("Error during token exchange: {}", e.getMessage(), e);
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Authentication error: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * Refresh Spotify access token (Mobile flow)
     */
    @GetMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestParam("refresh_token") String refreshToken) {
        try {
            Map<String, String> refreshedTokens = spotifyAuthService.refreshAccessToken(refreshToken);
            return ResponseEntity.ok(refreshedTokens);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Token refresh error: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

}
