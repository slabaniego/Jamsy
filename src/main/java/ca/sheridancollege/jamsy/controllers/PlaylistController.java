package ca.sheridancollege.jamsy.controllers;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import ca.sheridancollege.jamsy.beans.Track;
import ca.sheridancollege.jamsy.services.DiscoveryService;
import ca.sheridancollege.jamsy.services.SpotifyService;
import jakarta.servlet.http.HttpSession;

@Controller
public class PlaylistController {
	@Autowired
    private DiscoveryService discoveryService;

    @Autowired
    private SpotifyService spotifyService;
    
    @Autowired
    private ObjectMapper objectMapper;

 // Step 1 → Preview the playlist
    @GetMapping("/preview-playlist")
    public String previewPlaylist(HttpSession session, Model model) throws Exception {
        List<Track> likedTracks = (List<Track>) session.getAttribute("likedTracks");
        if (likedTracks == null || likedTracks.isEmpty()) {
            model.addAttribute("error", "No liked songs available.");
            return "liked";
        }

        // Expand to ~1hr
        List<Track> expanded = discoveryService.generateOneHourPlaylist(likedTracks, 60);

        List<Track> finalPlaylist = new ArrayList<>(likedTracks);
        finalPlaylist.addAll(expanded);

        // Save to session for export step
        session.setAttribute("expandedPlaylist", finalPlaylist);

        // Pass to frontend for rendering
        // Don’t stringify here — just pass the converted list
        model.addAttribute("tracksJson", discoveryService.convertTracksForFrontend(finalPlaylist));


        return "preview-liked"; // shows preview with confirm button
    }

    // Step 2 → Confirm & export to Spotify
    @GetMapping("/create-playlist")
    public String createPlaylist(HttpSession session, Model model) {
        List<Track> expandedPlaylist = (List<Track>) session.getAttribute("expandedPlaylist");
        if (expandedPlaylist == null || expandedPlaylist.isEmpty()) {
            model.addAttribute("error", "No playlist to export.");
            return "liked";
        }

        String accessToken = spotifyService.ensureValidAccessToken(session);
        String playlistUrl = spotifyService.createPlaylist(accessToken, "My 1 Hour Mix", expandedPlaylist);

        model.addAttribute("playlistUrl", playlistUrl);
        return "playlist-success"; // confirmation page
    }
}
