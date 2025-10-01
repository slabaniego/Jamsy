package ca.sheridancollege.jamsy.controllers;

import ca.sheridancollege.jamsy.beans.PlaylistTemplate;
import ca.sheridancollege.jamsy.beans.SpotifyAuthResponse;
import ca.sheridancollege.jamsy.beans.Track;
import ca.sheridancollege.jamsy.services.FirebaseAuthServices;
import ca.sheridancollege.jamsy.services.PlaylistTemplateService;
import ca.sheridancollege.jamsy.services.SpotifyService;
import com.google.firebase.auth.FirebaseAuthException;

import java.util.List;
import java.util.Map;

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
    
    // Get artists by workout and mood - API endpoint for mobile
    @GetMapping("/artists/workout/{workout}/mood/{mood}")
    public ResponseEntity<List<Map<String, Object>>> getArtistsByWorkout(
            @PathVariable String workout,
            @PathVariable String mood,
            @RequestHeader("Authorization") String authHeader
    ) {
        try {
            String accessToken = authHeader.replace("Bearer ", "").trim();
            System.out.println("Getting artists for workout: " + workout + ", mood: " + mood);
            
            // Get categorized artists from Spotify
            List<Map<String, Object>> categorizedArtists = spotifyService.getUserTopArtistsWithWorkoutCategories(accessToken, 200);
            
            // Filter artists by the selected workout category
            List<Map<String, Object>> workoutArtists = categorizedArtists.stream()
                .filter(artist -> {
                    @SuppressWarnings("unchecked")
                    List<String> categories = (List<String>) artist.get("workoutCategories");
                    return categories != null && categories.contains(workout);
                })
                .collect(java.util.stream.Collectors.toList());

            System.out.println("Found " + workoutArtists.size() + " total artists for workout: " + workout);

            // Shuffle and select 20 random artists from the filtered list
            java.util.Collections.shuffle(workoutArtists);
            List<Map<String, Object>> selectedArtists = workoutArtists.stream()
                .limit(20)
                .map(artist -> enhanceArtistWithRealImage(artist, accessToken))
                .collect(java.util.stream.Collectors.toList());

            System.out.println("Returning " + selectedArtists.size() + " artists for workout: " + workout);
            return ResponseEntity.ok(selectedArtists);
        } catch (Exception e) {
            System.err.println("Error getting artists by workout: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(java.util.Collections.emptyList());
        }
    }
    
    private Map<String, Object> enhanceArtistWithRealImage(Map<String, Object> artist, String accessToken) {
        try {
            String artistId = (String) artist.get("id");
            if (artistId != null) {
                String imageUrl = spotifyService.getArtistImageUrl(accessToken, artistId);
                if (imageUrl != null && !imageUrl.trim().isEmpty()) {
                    artist.put("imageUrl", imageUrl);
                    System.out.println("✅ Got real image for: " + artist.get("name"));
                    return artist;
                }
            }
        } catch (Exception e) {
            System.out.println("Error in enhanceArtistWithRealImage for: " + artist.get("name"));
        }
        
        // Fallback to placeholder
        return createPlaceholderImage(artist);
    }
    
    private Map<String, Object> createPlaceholderImage(Map<String, Object> artist) {
        artist.put("imageUrl", "https://via.placeholder.com/300x300/1DB954/FFFFFF?text=" + 
                  java.net.URLEncoder.encode((String) artist.get("name"), java.nio.charset.StandardCharsets.UTF_8));
        return artist;
    }
    
}