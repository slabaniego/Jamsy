package ca.sheridancollege.jamsy.controllers;

import ca.sheridancollege.jamsy.beans.PlaylistTemplate;
import ca.sheridancollege.jamsy.beans.SpotifyAuthResponse;
import ca.sheridancollege.jamsy.beans.Track;
import ca.sheridancollege.jamsy.services.FirebaseAuthServices;
import ca.sheridancollege.jamsy.services.PlaylistTemplateService;
import ca.sheridancollege.jamsy.services.SpotifyService;
import com.google.firebase.auth.FirebaseAuthException;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/spotify")
public class SpotifyApiController {

    private final SpotifyService spotifyService;
    private final FirebaseAuthServices firebaseAuthService;
    private final PlaylistTemplateService templateService;

    @Autowired
    public SpotifyApiController(
    		SpotifyService spotifyService, 
    		FirebaseAuthServices firebaseAuthService,
    		PlaylistTemplateService templateService
	) {
        this.spotifyService = spotifyService;
        this.firebaseAuthService = firebaseAuthService;
        this.templateService = templateService;
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
    
    // PLAYLIST TEMPLATE ENDPOINTS	
    
    // Get all available tamplates
    @GetMapping("/templates")
    public List<PlaylistTemplate> getTemplates() {
        return templateService.getDefaultTemplates();
    }

    // Get recommended tracks based on a specific template
    @GetMapping("/recommend/template/{name}")
    public List<Track> getPlaylistByTemplate(
            @PathVariable String name,
            @RequestParam("accessToken") String accessToken
    ) {
        PlaylistTemplate template = templateService.getDefaultTemplates()
                .stream()
                .filter(t -> t.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Template not found"));

        return spotifyService.getRecommendationsFromTemplate(template, accessToken);
    }
    
    
}