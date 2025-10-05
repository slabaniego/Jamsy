package ca.sheridancollege.jamsy.controllers;

import ca.sheridancollege.jamsy.beans.PlaylistTemplate;
import ca.sheridancollege.jamsy.beans.SpotifyAuthResponse;
import ca.sheridancollege.jamsy.beans.Track;
import ca.sheridancollege.jamsy.services.FirebaseAuthServices;
import ca.sheridancollege.jamsy.services.PlaylistTemplateService;
import ca.sheridancollege.jamsy.services.spotify.SpotifyAuthService;
import ca.sheridancollege.jamsy.services.spotify.SpotifyUserService;
import ca.sheridancollege.jamsy.services.recommendation.*;
import com.google.firebase.auth.FirebaseAuthException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for mobile and external clients.
 * Handles Spotify authentication, Firebase linking,
 * and playlist recommendations via templates.
 */
@RestController
@RequestMapping("/api/spotify")
public class SpotifyApiController {

    private final SpotifyAuthService spotifyAuthService;
    private final SpotifyUserService spotifyUserService;
    private final FirebaseAuthServices firebaseAuthService;
    private final PlaylistTemplateService templateService;
    private final RecommendationService recommendationService;

    public SpotifyApiController(
            SpotifyAuthService spotifyAuthService,
            SpotifyUserService spotifyUserService,
            FirebaseAuthServices firebaseAuthService,
            PlaylistTemplateService templateService,
            RecommendationService recommendationService
    ) {
        this.spotifyAuthService = spotifyAuthService;
        this.spotifyUserService = spotifyUserService;
        this.firebaseAuthService = firebaseAuthService;
        this.templateService = templateService;
        this.recommendationService = recommendationService;
    }

    /**
     * Step 1 → Exchange authorization code for Spotify access token
     * and issue a custom Firebase token for the mobile app.
     */
    @PostMapping("/exchange")
    public ResponseEntity<?> exchangeCodeForToken(
            @RequestParam("code") String code,
            @RequestParam("redirect_uri") String redirectUri
    ) {
        try {
            // 1️⃣ Exchange authorization code for Spotify access + refresh tokens
            Map<String, Object> tokenData = spotifyAuthService.exchangeCodeForAccessToken(code, redirectUri);
            String spotifyAccessToken = (String) tokenData.get("access_token");
            String refreshToken = (String) tokenData.get("refresh_token");

            // 2️⃣ Get the Spotify user ID (used as Firebase UID)
            String spotifyUserId = spotifyUserService.getSpotifyUserId(spotifyAccessToken);

            // 3️⃣ Generate Firebase custom token for linking
            String firebaseToken = firebaseAuthService.generateCustomToken(spotifyUserId);

            // 4️⃣ Build combined response
            SpotifyAuthResponse response = new SpotifyAuthResponse(
                    spotifyAccessToken,
                    "Bearer",
                    firebaseToken
            );

            return ResponseEntity.ok(response);

        } catch (FirebaseAuthException e) {
            return ResponseEntity
                    .status(500)
                    .body(Map.of("error", "Firebase authentication error: " + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity
                    .status(500)
                    .body(Map.of("error", "Server error: " + e.getMessage()));
        }
    }


    /**
     * Step 2 → Fetch all available playlist templates.
     * Used by the mobile app to display mood/workout options.
     */
    @GetMapping("/templates")
    public List<PlaylistTemplate> getTemplates() {
        return templateService.getDefaultTemplates();
    }

    /**
     * Step 3 → Generate recommendations based on a selected template.
     * Uses {@link RecommendationService} to build a playlist that fits
     * the mood, tempo, and workout type defined in the template.
     */
    @GetMapping("/recommend/template/{name}")
    public ResponseEntity<?> getPlaylistByTemplate(
            @PathVariable String name,
            @RequestParam("accessToken") String accessToken
    ) {
        try {
            PlaylistTemplate template = templateService.getDefaultTemplates().stream()
                    .filter(t -> t.getName().equalsIgnoreCase(name))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Template not found"));

            List<Track> recommendations = recommendationService.getRecommendationsFromTemplate(template, accessToken);
            return ResponseEntity.ok(recommendations);

        } catch (Exception e) {
            return ResponseEntity
                    .status(500)
                    .body("{\"error\": \"Error getting playlist recommendations: " + e.getMessage() + "\"}");
        }
    }
}
