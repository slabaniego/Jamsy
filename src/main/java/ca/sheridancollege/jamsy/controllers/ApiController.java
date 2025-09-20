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
    
}