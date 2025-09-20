package ca.sheridancollege.jamsy.controllers;

import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import ca.sheridancollege.jamsy.beans.Track;
import ca.sheridancollege.jamsy.models.SongAction;
import ca.sheridancollege.jamsy.repositories.SongActionRepository;
import ca.sheridancollege.jamsy.services.DeezerService;
import ca.sheridancollege.jamsy.services.LastFmService;
import ca.sheridancollege.jamsy.services.MusicBrainzService;
import ca.sheridancollege.jamsy.services.SpotifyService;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*") // Allow requests from any origin, adjust as needed
public class ApiController {

    @Autowired
    private LastFmService lastFmService;
    
    @Autowired
    private DeezerService deezerService;
    
    @Autowired
    private SpotifyService spotifyService;

@Autowired
    private MusicBrainzService musicBrainzService;
    
    @Autowired
    private SongActionRepository songActionRepo;

    /**
     * Returns recommended tracks for the Android app
     */
    /*
    @GetMapping("/tracks")
    public Map<String, Object> getTracks(
            @RequestParam(defaultValue = "true") boolean excludeExplicit,
            @RequestParam(defaultValue = "false") boolean excludeLoveSongs,
            @RequestParam(defaultValue = "false") boolean excludeFolk) {
        
        Map<String, Object> response = new HashMap<>();
        try {
            // Get recommended tracks from LastFM service
            List<Track> tracks = lastFmService.getFreshLastFmRecommendations();
            
            
            // Enrich tracks with preview URLs and album covers
            for (Track track : tracks) {
                if (track.getPreviewUrl() == null || track.getPreviewUrl().isEmpty()) {
                    String url = deezerService.getPreviewUrlFallback(track.getName(), track.getArtists());
                    track.setPreviewUrl(url);
                }
                
                if (track.getAlbumCover() == null || track.getAlbumCover().isEmpty()) {
                    String cover = deezerService.getAlbumCoverFallback(track.getName(), track.getArtists());
                    track.setAlbumCover(cover);
                }
            }
            
            response.put("tracks", tracks);
        } catch (Exception e) {
            response.put("error", e.getMessage());
        }
        return response;
    }*/
    
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
            
            // Return success response
            response.put("status", "success");
            response.put("message", "Track " + action + " recorded successfully");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
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
            
            // Search for tracks using SpotifyService
            List<Track> tracks = spotifyService.searchTrack(query, accessToken, 
                excludeExplicit, excludeLoveSongs, excludeFolk);
            
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
     * Get currently playing track for a user
     */
    @GetMapping("/current-track")
    public ResponseEntity<Map<String, Object>> getCurrentTrack(
            @RequestHeader("Authorization") String authHeader) {
        
        Map<String, Object> response = new HashMap<>();
        try {
            // Extract the token from the Authorization header
            String accessToken = authHeader.replace("Bearer ", "");
            
            // Get currently playing track
            Optional<Track> currentTrackOpt = spotifyService.getCurrentlyPlayingTrack(accessToken);
            
            if (currentTrackOpt.isPresent()) {
                Track track = currentTrackOpt.get();
                response.put("track", track);
                return ResponseEntity.ok(response);
            } else {
                response.put("message", "No track currently playing");
                return ResponseEntity.ok(response);
            }
        } catch (Exception e) {
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    
    /**
     * Get personalized recommendations
     */
    /*
    @GetMapping("/recommendations")
    public ResponseEntity<Map<String, Object>> getRecommendations() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            List<SongAction> likedSongs = songActionRepo.findByAction("like");
            
            if (likedSongs.isEmpty()) {
                response.put("message", "No liked songs found to base recommendations on");
                return ResponseEntity.ok(response);
            }
            
            Map<String, List<Track>> recommendations = new HashMap<>();
            int maxPopularity = 40; // Only show tracks with popularity < 40
            
            for (SongAction likedSong : likedSongs) {
                try {
                    // Get recommendations from both services
                    List<Track> lastFmRecs = lastFmService.getSimilarTracksWithPopularity(
                        likedSong.getSongName(), 
                        likedSong.getArtist(),
                        maxPopularity
                    );
                    
                    List<Track> musicBrainzRecs = musicBrainzService.getObscureSimilarTracks(
                        likedSong.getSongName(),
                        likedSong.getArtist(),
                        maxPopularity
                    );
                    
                    // Combine and deduplicate
                    List<Track> combined = new ArrayList<>();
                    combined.addAll(lastFmRecs);
                    combined.addAll(musicBrainzRecs);
                    
                    // Remove duplicates, sort by popularity, and limit
                    List<Track> uniqueTracks = combined.stream()
                        .distinct()
                        .sorted(Comparator.comparingInt(Track::getPopularity))
                        .limit(10)
                        .toList();
                    
                    // Ensure album covers are set
                    for (Track track : uniqueTracks) {
                        if (track.getAlbumCover() == null || track.getAlbumCover().isEmpty()) {
                            String cover = deezerService.getAlbumCoverFallback(
                                track.getName(), 
                                track.getArtists()
                            );
                            track.setAlbumCover(cover);
                        }
                    }
                    
                    if (!uniqueTracks.isEmpty()) {
                        recommendations.put(likedSong.getSongName() + " by " + likedSong.getArtist(), uniqueTracks);
                    }
                } catch (Exception e) {
                    // Log and continue with next liked song
                    System.err.println("Error processing song: " + likedSong.getSongName());
                    e.printStackTrace();
                }
            }
            
            response.put("recommendations", recommendations);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    */
    /**
     * Get random track to start recommendations
     */
    @GetMapping("/random-track")
    public ResponseEntity<Map<String, Object>> getRandomTrack(
            @RequestHeader("Authorization") String authHeader) {
        
        Map<String, Object> response = new HashMap<>();
        try {
            // Extract the token from the Authorization header
            String accessToken = authHeader.replace("Bearer ", "");
            
            // Get random track from Spotify
            Track randomTrack = spotifyService.getRandomSpotifyTrack(accessToken);
            
            if (randomTrack != null) {
                response.put("track", randomTrack);
                return ResponseEntity.ok(response);
            } else {
                response.put("error", "Could not get a random track");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
        } catch (Exception e) {
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Get similar tracks based on a track
     */
    /*
    @GetMapping("/similar-tracks")
    public ResponseEntity<Map<String, Object>> getSimilarTracks(
            @RequestParam String trackName,
            @RequestParam String artistName,
            @RequestParam(defaultValue = "true") boolean excludeExplicit,
            @RequestParam(defaultValue = "false") boolean excludeLoveSongs,
            @RequestParam(defaultValue = "false") boolean excludeFolk) {
        
        Map<String, Object> response = new HashMap<>();
        try {
            List<Track> similarTracks = lastFmService.getSimilarTracksFromRandomSpotifyTrack(
                trackName, artistName, excludeExplicit, excludeLoveSongs, excludeFolk, deezerService
            );
            
            response.put("tracks", similarTracks);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }*/
}