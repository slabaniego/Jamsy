package ca.sheridancollege.jamsy.controllers;

import ca.sheridancollege.jamsy.beans.PlaylistTemplate;
import ca.sheridancollege.jamsy.beans.SpotifyAuthResponse;
import ca.sheridancollege.jamsy.beans.Track;
import ca.sheridancollege.jamsy.services.FirebaseAuthServices;
import ca.sheridancollege.jamsy.services.PlaylistTemplateService;
import ca.sheridancollege.jamsy.services.playlist.PlaylistGeneratorService;
import ca.sheridancollege.jamsy.services.spotify.SpotifyArtistService;
import ca.sheridancollege.jamsy.services.spotify.SpotifyAuthService;
import ca.sheridancollege.jamsy.services.spotify.SpotifyUserService;
import ca.sheridancollege.jamsy.services.recommendation.RecommendationService;

import com.google.firebase.auth.FirebaseAuthException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * REST controller for mobile and external clients. Handles Spotify
 * authentication, Firebase linking, and playlist recommendations via templates.
 */
@RestController
@RequestMapping("/api/spotify")
@CrossOrigin(origins = "*")
public class SpotifyApiController {

	private final SpotifyAuthService spotifyAuthService;
	private final SpotifyUserService spotifyUserService;
	private final FirebaseAuthServices firebaseAuthService;
	private final PlaylistTemplateService templateService;
	private final RecommendationService recommendationService;
	private final PlaylistGeneratorService playlistGeneratorService;
	private final SpotifyArtistService spotifyArtistService;

	public SpotifyApiController(SpotifyAuthService spotifyAuthService, SpotifyUserService spotifyUserService,
			FirebaseAuthServices firebaseAuthService, PlaylistTemplateService templateService,
			RecommendationService recommendationService, PlaylistGeneratorService playlistGeneratorService,
			SpotifyArtistService spotifyArtistService) {
		this.spotifyAuthService = spotifyAuthService;
		this.spotifyUserService = spotifyUserService;
		this.firebaseAuthService = firebaseAuthService;
		this.templateService = templateService;
		this.recommendationService = recommendationService;
		this.playlistGeneratorService = playlistGeneratorService;
		this.spotifyArtistService = spotifyArtistService;
	}

	/**
	 * Step 1 → Exchange authorization code for Spotify access token and issue a
	 * custom Firebase token for the mobile app.
	 */
	@PostMapping("/exchange")
	public ResponseEntity<?> exchangeCodeForToken(@RequestParam("code") String code,
			@RequestParam("redirect_uri") String redirectUri) {
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
			SpotifyAuthResponse response = new SpotifyAuthResponse(spotifyAccessToken, "Bearer", firebaseToken);

			return ResponseEntity.ok(response);

		} catch (FirebaseAuthException e) {
			return ResponseEntity.status(500).body(Map.of("error", "Firebase authentication error: " + e.getMessage()));
		} catch (Exception e) {
			return ResponseEntity.status(500).body(Map.of("error", "Server error: " + e.getMessage()));
		}
	}

	/**
	 * Step 2 → Fetch all available playlist templates. Used by the mobile app to
	 * display mood/workout options.
	 */
	@GetMapping("/templates")
	public List<PlaylistTemplate> getTemplates() {
		return templateService.getDefaultTemplates();
	}

	/**
	 * Step 3 → Generate recommendations based on a selected template. Uses
	 * {@link RecommendationService} to build a playlist that fits the mood, tempo,
	 * and workout type defined in the template.
	 */
	@GetMapping("/recommend/template/{name}")
	public ResponseEntity<?> getPlaylistByTemplate(@PathVariable String name,
			@RequestParam("accessToken") String accessToken) {
		try {
			PlaylistTemplate template = templateService.getDefaultTemplates().stream()
					.filter(t -> t.getName().equalsIgnoreCase(name)).findFirst()
					.orElseThrow(() -> new RuntimeException("Template not found"));

			List<Track> recommendations = recommendationService.getRecommendationsFromTemplate(template, accessToken);
			return ResponseEntity.ok(recommendations);

		} catch (Exception e) {
			return ResponseEntity.status(500)
					.body(Map.of("error", "Error getting playlist recommendations: " + e.getMessage()));
		}
	}

	/**
	 * Step 4 → Get artists by workout and mood (for mobile use)
	 * ---------------------------------------------------------
	 * This mirrors the web flow where the user first chooses artists.
	 * The endpoint returns a curated list of artists related to the selected
	 * workout and mood, which the mobile app can display for user selection.
	 */
	@GetMapping("/artists/workout/{workout}/mood/{mood}")
	public ResponseEntity<Map<String, Object>> getArtistsByWorkoutAndMood(
	        @PathVariable String workout,
	        @PathVariable String mood,
	        @RequestHeader("Authorization") String authHeader) {

	    Map<String, Object> response = new HashMap<>();

	    try {
	        String accessToken = authHeader.replace("Bearer ", "").trim();
	        System.out.println("🎧 Mobile API → Getting artists for workout: " + workout + ", mood: " + mood);

	        // 1️⃣ Fetch user's top tracks (we no longer use SpotifyService, so use SpotifyUserService)
	        List<Track> userTopTracks = spotifyUserService.getTopTracks(accessToken);

	        if (userTopTracks == null || userTopTracks.isEmpty()) {
	            System.out.println("⚠️ No top tracks found for user. Returning empty artist list.");
	            response.put("artists", Collections.emptyList());
	            return ResponseEntity.ok(response);
	        }

	        // 2️⃣ Extract unique artist names from top tracks
	        List<String> artistNames = userTopTracks.stream()
	                .flatMap(track -> track.getArtists().stream())
	                .distinct()
	                .limit(50)
	                .toList();

	        System.out.println("✅ Extracted " + artistNames.size() + " unique artists from top tracks.");

	        // 3️⃣ Fetch artist details from SpotifyArtistService
	        List<Map<String, Object>> artistDetails = spotifyArtistService.getArtistNames(artistNames, accessToken);

	        // 4️⃣ Add light categorization for workouts (like the web version)
	        for (Map<String, Object> artist : artistDetails) {
	            @SuppressWarnings("unchecked")
	            List<String> genres = (List<String>) artist.get("genres");
	            if (genres == null) genres = new ArrayList<>();

	            String genreStr = String.join(",", genres).toLowerCase();
	            List<String> workoutCategories = new ArrayList<>();

	            if (genreStr.contains("pop") || genreStr.contains("dance") || genreStr.contains("edm")) {
	                workoutCategories.add("Cardio");
	            }
	            if (genreStr.contains("rock") || genreStr.contains("metal") || genreStr.contains("hip hop")) {
	                workoutCategories.add("Strength Training");
	            }
	            if (genreStr.contains("chill") || genreStr.contains("ambient") || genreStr.contains("acoustic")) {
	                workoutCategories.add("Yoga");
	            }

	            artist.put("workoutCategories", workoutCategories);
	        }

	        // 5️⃣ Filter by workout
	        List<Map<String, Object>> filteredArtists = artistDetails.stream()
	                .filter(a -> {
	                    @SuppressWarnings("unchecked")
	                    List<String> cats = (List<String>) a.get("workoutCategories");
	                    return cats != null && cats.contains(workout);
	                })
	                .toList();

	        // 6️⃣ Shuffle and limit to 20
	        Collections.shuffle(filteredArtists);
	        List<Map<String, Object>> selected = filteredArtists.stream().limit(20).toList();

	        System.out.println("✅ Returning " + selected.size() + " artists for workout: " + workout);

	        response.put("workout", workout);
	        response.put("mood", mood);
	        response.put("artists", selected);

	        return ResponseEntity.ok(response);

	    } catch (Exception e) {
	        System.err.println("❌ Error fetching artists for workout/mood: " + e.getMessage());
	        e.printStackTrace();
	        response.put("error", e.getMessage());
	        return ResponseEntity.status(500).body(response);
	    }
	}

	/**
	 * Helper → Adds real Spotify image or placeholder if missing.
	 */
	private Map<String, Object> enhanceArtistWithRealImage(Map<String, Object> artist) {
	    try {
	        Map<String, Object> imagesData = (Map<String, Object>) artist.get("images");
	        if (imagesData != null && !imagesData.isEmpty()) {
	            artist.put("imageUrl", ((List<Map<String, Object>>) imagesData).get(0).get("url"));
	        } else {
	            artist.put("imageUrl",
	                    "https://via.placeholder.com/300x300/1DB954/FFFFFF?text=" +
	                    java.net.URLEncoder.encode((String) artist.get("name"), java.nio.charset.StandardCharsets.UTF_8));
	        }
	    } catch (Exception e) {
	        artist.put("imageUrl",
	                "https://via.placeholder.com/300x300/1DB954/FFFFFF?text=" +
	                java.net.URLEncoder.encode((String) artist.get("name"), java.nio.charset.StandardCharsets.UTF_8));
	    }
	    return artist;
	}

	@PostMapping("/create-playlist")
	public ResponseEntity<?> createPlaylist(
	        @RequestHeader("Authorization") String authHeader,
	        @RequestBody Map<String, Object> body
	) {
	    try {
	        // 1️ Extract Spotify token from Authorization header
	        String accessToken = authHeader.replace("Bearer ", "").trim();

	        // 2️ Extract playlist name (optional) and tracks from request body
	        String playlistName = (String) body.getOrDefault("playlistName", "My Mobile Playlist");

	        @SuppressWarnings("unchecked")
	        List<Map<String, Object>> tracksData = (List<Map<String, Object>>) body.get("tracks");
	        if (tracksData == null || tracksData.isEmpty()) {
	            return ResponseEntity.badRequest().body(Map.of("error", "No tracks provided"));
	        }

	        // 3️ Convert raw JSON maps into Track objects
	        List<Track> tracks = new ArrayList<>();
	        for (Map<String, Object> t : tracksData) {
	            Track track = new Track();
	            track.setId((String) t.get("id"));
	            track.setName((String) t.get("name"));
	            track.setArtists((List<String>) t.get("artists"));
	            track.setAlbumCover((String) t.get("albumCover"));
	            track.setPreviewUrl((String) t.get("previewUrl"));
	            tracks.add(track);
	        }

	        // 4️ Create the playlist on Spotify
	        // (You’ll call your PlaylistGeneratorService here)
	        String playlistUrl = playlistGeneratorService.createPlaylistWithTracks(
	                accessToken, playlistName, tracks
	        );

	        // 5️⃣ Return success response with Spotify playlist URL
	        return ResponseEntity.ok(Map.of(
	                "status", "success",
	                "playlistUrl", playlistUrl
	        ));

	    } catch (Exception e) {
	        e.printStackTrace();
	        return ResponseEntity.status(500).body(Map.of(
	                "status", "error",
	                "message", "Error creating playlist: " + e.getMessage()
	        ));
	    }
	}
}
