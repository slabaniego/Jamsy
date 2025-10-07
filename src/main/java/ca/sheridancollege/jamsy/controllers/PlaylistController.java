package ca.sheridancollege.jamsy.controllers;

import ca.sheridancollege.jamsy.beans.Track;
import ca.sheridancollege.jamsy.models.SongAction;
import ca.sheridancollege.jamsy.repositories.SongActionRepository;
import ca.sheridancollege.jamsy.services.DiscoveryService;
import ca.sheridancollege.jamsy.services.playlist.PlaylistGeneratorService;
import ca.sheridancollege.jamsy.services.spotify.SpotifyAuthService;
import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * PlaylistController
 * -------------------
 * Handles the playlist preview and export flow.
 * Uses:
 *  - DiscoveryService to extend liked songs into a full playlist
 *  - PlaylistGeneratorService to create and populate Spotify playlists
 *  - SpotifyAuthService to validate or refresh Spotify tokens
 */
@Controller
public class PlaylistController {

    private final DiscoveryService discoveryService;
    private final PlaylistGeneratorService playlistGeneratorService;
    private final SpotifyAuthService spotifyAuthService;
    private final SongActionRepository songActionRepo;

    public PlaylistController(
            DiscoveryService discoveryService,
            PlaylistGeneratorService playlistGeneratorService,
            SpotifyAuthService spotifyAuthService,
            SongActionRepository songActionRepo
    ) {
        this.discoveryService = discoveryService;
        this.playlistGeneratorService = playlistGeneratorService;
        this.spotifyAuthService = spotifyAuthService;
        this.songActionRepo = songActionRepo;
    }
    
    @PostMapping("/handle-action")
    @ResponseBody
    public Map<String, Object> handleAction(
            @RequestBody Map<String, Object> requestBody,
            HttpSession session) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            String isrc = (String) requestBody.get("isrc");
            String songName = (String) requestBody.get("songName");
            String artist = (String) requestBody.get("artist");
            String action = (String) requestBody.get("action");
            List<String> genres = (List<String>) requestBody.get("genres");

            // Save to DB
            SongAction songAction = new SongAction();
            songAction.setIsrc(isrc);
            songAction.setSongName(songName);
            songAction.setArtist(artist);
            songAction.setGenres(genres);
            songAction.setAction(action);
            songActionRepo.save(songAction);
            System.out.println("Payload → songName=" + songName + ", artist=" + artist);

            // Store liked tracks in session
            if ("like".equals(action)) {
                List<Track> likedTracks = (List<Track>) session.getAttribute("likedTracks");
                if (likedTracks == null) {
                    likedTracks = new ArrayList<>();
                    session.setAttribute("likedTracks", likedTracks);
                }

                List<Track> discoveryTracks = (List<Track>) session.getAttribute("discoveryTracks");
                System.out.println("🟢 discoveryTracks in session: " + (discoveryTracks != null ? discoveryTracks.size() : 0));

                if (discoveryTracks != null) {
                    for (Track track : discoveryTracks) {
                        System.out.println("Checking " + track.getName() + " vs " + songName);
                        System.out.println("Artists in track: " + track.getArtists() + " vs payload artist: " + artist);

                        if (track.getName() != null && track.getName().equalsIgnoreCase(songName)) {
                            if (track.getArtists() == null || track.getArtists().isEmpty()) {
                                likedTracks.add(track);
                                System.out.println("✅ Added (name-only match): " + track.getName());
                            } else if (track.getArtists().stream()
                                      .anyMatch(a -> a.toLowerCase().contains(artist.toLowerCase()))) {
                                likedTracks.add(track);
                                System.out.println("✅ Added (name + artist match): " + track.getName() + " by " + track.getArtists());
                            }
                        }

                    }
                }

                System.out.println("❤️ likedTracks size after like: " + likedTracks.size());
            }


            response.put("success", true);
            response.put("message", "Action processed successfully");
            
        } catch (Exception e) {
            System.out.println("❌ Error in handleAction: " + e.getMessage());
            response.put("success", false);
            response.put("message", "Error processing action");
        }
        
        return response;
    }
    
    /**
     * Step 1 → Preview Playlist
     * 
     * This endpoint retrieves the user's liked songs from the session,
     * expands them into a one-hour playlist using {@link DiscoveryService#generateOneHourPlaylist(List, int)},
     * and prepares them for display in a preview page before export.
     */
    @GetMapping("/preview-playlist")
    public String previewPlaylist(HttpSession session, Model model) throws Exception {
        List<Track> likedTracks = (List<Track>) session.getAttribute("likedTracks");
        if (likedTracks == null || likedTracks.isEmpty()) {
        	System.out.println("liked songs in the method is zero = " + likedTracks);
            model.addAttribute("error", "No liked songs available.");
            return "liked";
        }

        // Generate playlist - this returns ONLY similar tracks (no liked tracks)
        List<Track> expandedTracks = discoveryService.generateOneHourPlaylist(likedTracks, 60);

        // Save to session for export step - ONLY the expanded tracks
        session.setAttribute("expandedPlaylist", expandedTracks);

        // Pass to frontend for rendering
        model.addAttribute("tracksJson", discoveryService.convertTracksForFrontend(expandedTracks));
        model.addAttribute("originalCount", likedTracks.size());
        model.addAttribute("expandedCount", expandedTracks.size());

        return "preview-liked";
    }

    /**
     * Step 2 → Create Playlist on Spotify
     *
     * After previewing, the user can export the playlist to their Spotify account.
     * This uses {@link PlaylistGeneratorService#createPlaylist(String, String, List)} to:
     *  1. Create an empty playlist in the user’s account
     *  2. Add all the songs (in 100-track batches)
     *  3. Return the final playlist link for confirmation
     */
    @GetMapping("/create-playlist")
    public String createPlaylist(HttpSession session, Model model) {
        List<Track> expandedPlaylist = (List<Track>) session.getAttribute("expandedPlaylist");
        if (expandedPlaylist == null || expandedPlaylist.isEmpty()) {
            model.addAttribute("error", "No playlist available to export.");
            return "liked";
        }

        try {
            // Validate or refresh the access token
            String accessToken = spotifyAuthService.ensureValidAccessToken(session);

            // Create playlist + populate with tracks
            String playlistUrl = playlistGeneratorService.createPlaylistWithTracks(accessToken, "My 1 Hour Mix", expandedPlaylist);

            model.addAttribute("playlistUrl", playlistUrl);
            return "playlist-success"; // HTML confirmation page with link
        } catch (Exception e) {
            model.addAttribute("error", "Error creating playlist: " + e.getMessage());
            return "liked";
        }
    }
}
