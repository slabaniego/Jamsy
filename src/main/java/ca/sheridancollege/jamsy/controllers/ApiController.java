package ca.sheridancollege.jamsy.controllers;

import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import ca.sheridancollege.jamsy.beans.Track;
import ca.sheridancollege.jamsy.models.SongAction;
import ca.sheridancollege.jamsy.repositories.SongActionRepository;
import ca.sheridancollege.jamsy.services.*;
import ca.sheridancollege.jamsy.services.playlist.PlaylistGeneratorService;
import ca.sheridancollege.jamsy.services.spotify.SpotifyTrackService;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*") // Allow requests from any origin (adjust for security later)
public class ApiController {

    @Autowired private DeezerService deezerService;
    @Autowired private SpotifyTrackService spotifyTrackService;
    @Autowired private DiscoveryService discoveryService;
    @Autowired private PlaylistGeneratorService playlistGeneratorService;
    @Autowired private SongActionRepository songActionRepo;

    /**
     * Handles like/unlike actions for tracks
     */
    @PostMapping("/track/action")
    public ResponseEntity<Map<String, String>> handleTrackAction(@RequestBody Map<String, Object> payload) {
        Map<String, String> response = new HashMap<>();
        try {
            String isrc = (String) payload.get("isrc");
            String songName = (String) payload.get("songName");
            String artist = (String) payload.get("artist");
            String genres = (String) payload.get("genres");
            String action = (String) payload.get("action");

            // Log the action
            System.out.println("Track " + action + ": " + songName + " by " + artist);

            // Save to database
            SongAction songAction = new SongAction();
            songAction.setIsrc(isrc);
            songAction.setSongName(songName);
            songAction.setArtist(artist);
            songAction.setAction(action);

            // Parse genres from comma-separated string
            if (genres != null && !genres.isEmpty()) {
                songAction.setGenres(Arrays.asList(genres.split(",")));
            } else {
                songAction.setGenres(new ArrayList<>());
            }

            songActionRepo.save(songAction);
            System.out.println("✅ Saved track action to database: " + songName + " by " + artist + " (" + action + ")");

            // Return success response
            response.put("status", "success");
            response.put("message", "Track " + action + " recorded successfully");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.out.println("❌ Error saving track action: " + e.getMessage());
            e.printStackTrace();
            response.put("status", "error");
            response.put("message", "Error processing request: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Search for tracks by query
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchTracks(
            @RequestParam String query,
            @RequestParam(defaultValue = "true") boolean excludeExplicit,
            @RequestParam(defaultValue = "false") boolean excludeLoveSongs,
            @RequestParam(defaultValue = "false") boolean excludeFolk,
            @RequestHeader("Authorization") String authHeader) {

        Map<String, Object> response = new HashMap<>();
        try {
            // Extract the token from the Authorization header
            String accessToken = authHeader.replace("Bearer ", "");

            // Search for tracks using SpotifyTrackService
            List<Track> tracks = spotifyTrackService.searchTrack(query, accessToken);

            // Apply filters for explicit/love/folk
            tracks.removeIf(track ->
                    (excludeExplicit && Boolean.TRUE.equals(track.isExplicit())) ||
                    (excludeLoveSongs && track.getName().toLowerCase().contains("love")) ||
                    (excludeFolk && track.getGenres() != null && track.getGenres().contains("folk"))
            );

            // Enrich tracks with preview URLs and album covers if needed
            tracks.forEach(track -> {
                if (track.getPreviewUrl() == null || track.getPreviewUrl().isEmpty()) {
                    String url = deezerService.getPreviewUrlFallback(track.getName(), track.getArtists());
                    track.setPreviewUrl(url);
                }

                if (track.getAlbumCover() == null || track.getAlbumCover().isEmpty()) {
                    String cover = deezerService.getAlbumCoverFallback(track.getName(), track.getArtists());
                    track.setAlbumCover(cover);
                }
            });

            response.put("tracks", tracks);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Mobile: Discover tracks (stateless JSON)
     * Accepts optional seed artist names and workout; otherwise uses generic seeds
     */
    @PostMapping("/discover")
    public ResponseEntity<Map<String, Object>> apiDiscover(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> response = new HashMap<>();
        try {
            @SuppressWarnings("unchecked")
            List<String> seedArtists = body != null && body.get("seedArtists") instanceof List
                    ? (List<String>) body.get("seedArtists")
                    : Arrays.asList("Drake", "Rihanna", "Eminem", "Adele", "Ed Sheeran");
            String workout = body != null && body.get("workout") instanceof String
                    ? (String) body.get("workout")
                    : "general";

            List<Track> tracks = discoveryService.getDiscoveryTracks(seedArtists, workout, 20);
            response.put("tracks", tracks);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Mobile: Get liked tracks (from DB SongAction where action == like)
     */
    @GetMapping("/liked")
    public ResponseEntity<Map<String, Object>> apiLiked() {
        Map<String, Object> response = new HashMap<>();
        try {
            // Basic implementation: this demo returns empty or constructs from actions if needed
            // If SongAction stored only metadata, return minimal Track list using songName/artist
            List<SongAction> likedActions = songActionRepo.findByAction("like");
            List<Track> likedTracks = new ArrayList<>();
            for (SongAction action : likedActions) {
                Track t = new Track();
                t.setName(action.getSongName());
                t.setArtists(Collections.singletonList(action.getArtist()));
                likedTracks.add(t);
            }
            response.put("tracks", likedTracks);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Mobile: Preview playlist from liked tracks (1 hour mix)
     */
    @GetMapping("/preview-playlist")
    public ResponseEntity<Map<String, Object>> apiPreviewPlaylist() {
        Map<String, Object> response = new HashMap<>();
        try {
            System.out.println("ApiController: ===== PREVIEW PLAYLIST API CALLED =====");

            List<SongAction> likedActions = songActionRepo.findByAction("like");
            System.out.println("ApiController: Found " + likedActions.size() + " liked actions in database");

            List<Track> likedTracks = new ArrayList<>();
            for (SongAction action : likedActions) {
                Track t = new Track();
                t.setName(action.getSongName());
                t.setArtists(Collections.singletonList(action.getArtist()));
                likedTracks.add(t);
            }

            System.out.println("ApiController: Converted to " + likedTracks.size() + " liked tracks");

            if (likedTracks.isEmpty()) {
                System.out.println("ApiController: WARNING - No liked tracks found, returning empty playlist");
                response.put("tracks", new ArrayList<>());
                response.put("message", "No liked tracks found. Please like some tracks first.");
                return ResponseEntity.ok(response);
            }

            List<Track> expanded = discoveryService.generateOneHourPlaylist(likedTracks, 60);
            System.out.println("ApiController: Generated " + expanded.size() + " expanded tracks");

            // Combine original liked tracks with expanded tracks (like web version)
            List<Track> finalPlaylist = new ArrayList<>(likedTracks);
            finalPlaylist.addAll(expanded);

            System.out.println("ApiController: Final playlist contains " + finalPlaylist.size() + " tracks");
            response.put("tracks", finalPlaylist);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.out.println("ApiController: Error in preview-playlist: " + e.getMessage());
            e.printStackTrace();
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Mobile: Create playlist in Spotify for the liked/expanded tracks
     * Requires Authorization: Bearer <spotify-access-token>
     */
    @PostMapping("/create-playlist")
    public ResponseEntity<Map<String, String>> apiCreatePlaylist(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, String> response = new HashMap<>();
        try {
            String accessToken = authHeader.replace("Bearer ", "").trim();
            // If client provided tracks payload, prefer that; else use preview expansion of likes
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> providedTracks = body != null
                    ? (List<Map<String, Object>>) body.get("tracks")
                    : null;

            List<Track> tracksToSave = new ArrayList<>();
            if (providedTracks != null) {
                for (Map<String, Object> m : providedTracks) {
                    Track t = new Track();
                    t.setName((String) m.get("name"));
                    Object artistsObj = m.get("artists");
                    if (artistsObj instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<String> artists = (List<String>) artistsObj;
                        t.setArtists(artists);
                    }
                    tracksToSave.add(t);
                }
            } else {
                List<SongAction> likedActions = songActionRepo.findByAction("like");
                for (SongAction action : likedActions) {
                    Track t = new Track();
                    t.setName(action.getSongName());
                    t.setArtists(Collections.singletonList(action.getArtist()));
                    tracksToSave.add(t);
                }
                tracksToSave = discoveryService.generateOneHourPlaylist(tracksToSave, 60);
            }

            // ✅ Use PlaylistGeneratorService for playlist creation
            String playlistUrl = playlistGeneratorService.createPlaylistWithTracks(accessToken, "My 1 Hour Mix", tracksToSave);
            response.put("playlistUrl", playlistUrl);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
