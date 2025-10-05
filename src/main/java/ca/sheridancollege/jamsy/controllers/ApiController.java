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
import ca.sheridancollege.jamsy.services.spotify.SpotifyTrackService;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*") // Allow all origins (adjust for security later)
public class ApiController {

    private final LastFmService lastFmService;
    private final DeezerService deezerService;
    private final MusicBrainzService musicBrainzService;
    private final SpotifyTrackService spotifyTrackService;
    private final SongActionRepository songActionRepo;

    @Autowired
    public ApiController(
            LastFmService lastFmService,
            DeezerService deezerService,
            MusicBrainzService musicBrainzService,
            SpotifyTrackService spotifyTrackService,
            SongActionRepository songActionRepo
    ) {
        this.lastFmService = lastFmService;
        this.deezerService = deezerService;
        this.musicBrainzService = musicBrainzService;
        this.spotifyTrackService = spotifyTrackService;
        this.songActionRepo = songActionRepo;
    }

    /**
     * Handles like/unlike actions for tracks.
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

            // Log and save the song action
            SongAction songAction = new SongAction();
            songAction.setIsrc(isrc);
            songAction.setSongName(songName);
            songAction.setArtist(artist);
            songAction.setGenres(Collections.singletonList(genres));
            songAction.setAction(action);
            songActionRepo.save(songAction);

            System.out.println("🎵 Track " + action + ": " + songName + " by " + artist);

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
     * Search for tracks by query (Spotify + Deezer fallback).
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
            // Extract Spotify access token
            String accessToken = authHeader.replace("Bearer ", "");

            // 🔍 Search tracks via Spotify API
            List<Track> tracks = spotifyTrackService.searchTrack(query, accessToken);

            // Apply simple filters if needed
            tracks.removeIf(track ->
                    (excludeExplicit && Boolean.TRUE.equals(track.isExplicit())) ||
                    (excludeLoveSongs && track.getName().toLowerCase().contains("love")) ||
                    (excludeFolk && track.getGenres() != null && track.getGenres().contains("folk"))
            );

            // Fallbacks from Deezer if missing info
            tracks.forEach(track -> {
                if (track.getPreviewUrl() == null || track.getPreviewUrl().isEmpty()) {
                    track.setPreviewUrl(
                            deezerService.getPreviewUrlFallback(track.getName(), track.getArtists())
                    );
                }
                if (track.getImageUrl() == null || track.getImageUrl().isEmpty()) {
                    track.setImageUrl(
                            deezerService.getAlbumCoverFallback(track.getName(), track.getArtists())
                    );
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
