package ca.sheridancollege.jamsy.controllers;

import ca.sheridancollege.jamsy.beans.Track;
import ca.sheridancollege.jamsy.services.DiscoveryService;
import ca.sheridancollege.jamsy.services.playlist.PlaylistGeneratorService;
import ca.sheridancollege.jamsy.services.spotify.SpotifyAuthService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.ArrayList;
import java.util.List;

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

    public PlaylistController(
            DiscoveryService discoveryService,
            PlaylistGeneratorService playlistGeneratorService,
            SpotifyAuthService spotifyAuthService
    ) {
        this.discoveryService = discoveryService;
        this.playlistGeneratorService = playlistGeneratorService;
        this.spotifyAuthService = spotifyAuthService;
    }

    /**
     * Step 1 → Preview Playlist
     * 
     * This endpoint retrieves the user's liked songs from the session,
     * expands them into a one-hour playlist using {@link DiscoveryService#generateOneHourPlaylist(List, int)},
     * and prepares them for display in a preview page before export.
     */
    @GetMapping("/preview-playlist")
    public String previewPlaylist(HttpSession session, Model model) {
    	List<Track> likedTracks = (List<Track>) session.getAttribute("discoveryTracks");
        if (likedTracks == null || likedTracks.isEmpty()) {
            model.addAttribute("error", "No liked songs available to create a playlist.");
            return "liked";
        }

        // Expand liked tracks into roughly 1-hour playlist
        List<Track> expandedPlaylist = discoveryService.generateOneHourPlaylist(likedTracks, 60);
        List<Track> finalPlaylist = new ArrayList<>(likedTracks);
        finalPlaylist.addAll(expandedPlaylist);

        // Save expanded playlist in session for the next step
        session.setAttribute("expandedPlaylist", finalPlaylist);

        // Convert to frontend format (album cover, preview, artist names, etc.)
        model.addAttribute("tracksJson", discoveryService.convertTracksForFrontend(finalPlaylist));

        return "preview-liked"; // HTML page showing preview + confirm button
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
