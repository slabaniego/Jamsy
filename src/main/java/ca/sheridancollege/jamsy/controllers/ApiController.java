package ca.sheridancollege.jamsy.controllers;

import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import ca.sheridancollege.jamsy.beans.Track;
import ca.sheridancollege.jamsy.services.DeezerService;
import ca.sheridancollege.jamsy.services.LastFmService;
import ca.sheridancollege.jamsy.services.SpotifyService;

@RestController
@RequestMapping("/api")
public class ApiController {

    @Autowired
    private LastFmService lastFmService;
    
    @Autowired
    private DeezerService deezerService;
    
    @Autowired
    private SpotifyService spotifyService;

    /**
     * Returns recommended tracks for the Android app
     */
    @GetMapping("/tracks")
    public Map<String, Object> getTracks(
            @RequestParam(defaultValue = "true") boolean excludeExplicit,
            @RequestParam(defaultValue = "false") boolean excludeLoveSongs,
            @RequestParam(defaultValue = "false") boolean excludeFolk) {
        
        Map<String, Object> response = new HashMap<>();
        try {
            // Get recommended tracks from LastFM service
            List<Track> tracks = lastFmService.getFreshLastFmRecommendations();
            
            // Apply filtering if needed using SpotifyService (which has the filtering logic)
            if (excludeExplicit || excludeLoveSongs || excludeFolk) {
                tracks = spotifyService.applyFilters(tracks, excludeExplicit, excludeLoveSongs, excludeFolk);
            }
            
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
    }
    
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
}