package ca.sheridancollege.jamsy.controllers;

import ca.sheridancollege.jamsy.beans.Track;
import ca.sheridancollege.jamsy.exceptions.AuthenticationRequiredException;
import ca.sheridancollege.jamsy.services.DiscoveryService;
import ca.sheridancollege.jamsy.services.spotify.SpotifyUserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

/**
 * Handles the "Discovery" feature of the app.
 * Allows users to discover new tracks based on selected artists, moods, and workouts.
 */
@Controller
@RequestMapping("/discovery")
public class DiscoveryController {

    private final DiscoveryService discoveryService;
    private final SpotifyUserService spotifyUserService;
    private final ObjectMapper objectMapper;

    public DiscoveryController(DiscoveryService discoveryService,
                               SpotifyUserService spotifyUserService,
                               ObjectMapper objectMapper) {
        this.discoveryService = discoveryService;
        this.spotifyUserService = spotifyUserService;
        this.objectMapper = objectMapper;
    }

    /**
     * Step 1 → Show discovery results based on selected artists and workout type.
     * <p>
     * Pulls artist data from the session and generates new recommended tracks
     * using {@link DiscoveryService#getDiscoveryTracks(List, String, int)}.
     * </p>
     */
    @GetMapping
    public String discoverTracks(HttpSession session, Model model) {
        try {
            // Get selections from session
            List<String> selectedArtists = (List<String>) session.getAttribute("selectedArtists");
            String workout = (String) session.getAttribute("selectedWorkout");
            String mood = (String) session.getAttribute("selectedMood");

            if (selectedArtists == null || selectedArtists.isEmpty()) {
                model.addAttribute("error", "Please select some artists first.");
                return "redirect:/playlist-templates";
            }

            System.out.println("🎵 Discovering tracks for artists: " + selectedArtists);

            // Fetch discovery results
            List<Track> tracks = discoveryService.getDiscoveryTracks(selectedArtists, workout, 50);

            if (tracks.isEmpty()) {
                model.addAttribute("error", "No tracks found for your selection.");
                return "redirect:/artists/more?workout=" + workout + "&mood=" + mood;
            }

            // Convert tracks to JSON for frontend rendering
            String tracksJson = objectMapper.writeValueAsString(tracks);
            model.addAttribute("tracksJson", tracksJson);
            model.addAttribute("tracks", tracks);
            model.addAttribute("workout", workout);
            model.addAttribute("mood", mood);
            model.addAttribute("isFamiliar", false);

            // Save in session for later use (likes, playlist export)
            session.setAttribute("discoveryTracks", tracks);

            return "tracks";

        } catch (AuthenticationRequiredException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("❌ Error in discovery: " + e.getMessage());
            model.addAttribute("error", "Error discovering tracks: " + e.getMessage());
            return "error";
        }
    }

    /**
     * Step 2 → Generate familiar playlist recommendations (optional feature).
     * <p>
     * This placeholder route could later use a dedicated "FamiliarService"
     * to suggest tracks the user already listens to.
     * </p>
     */
    @GetMapping("/familiar")
    public String familiarTracks(HttpSession session, Model model) {
        model.addAttribute("message", "Familiar playlist generation not implemented yet.");
        return "tracks";
    }

    /**
     * Step 3 → Refresh and shuffle track recommendations.
     * <p>
     * Calls SpotifyUserService to merge, shuffle, and limit recommended tracks.
     * </p>
     */
    @PostMapping("/refresh")
    public String refreshRecommendations(HttpSession session, Model model) {
        try {
            String accessToken = spotifyUserService.getSpotifyUserId(
                    (String) session.getAttribute("accessToken"));
            if (accessToken == null) throw new AuthenticationRequiredException("Access token missing");

            List<Track> refreshedTracks = spotifyUserService.mergeAndShuffleTracks(accessToken);

            model.addAttribute("tracks", refreshedTracks);
            model.addAttribute("tracksJson", new ObjectMapper().writeValueAsString(refreshedTracks));
            model.addAttribute("isFamiliar", false);

            return "tracks";
        } catch (Exception e) {
            model.addAttribute("error", "Error refreshing recommendations: " + e.getMessage());
            return "tracks";
        }
    }
}
