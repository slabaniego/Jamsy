package ca.sheridancollege.jamsy.controllers;

import ca.sheridancollege.jamsy.beans.Track;
import ca.sheridancollege.jamsy.services.DiscoveryService;
import ca.sheridancollege.jamsy.services.spotify.SpotifyAuthService;
import ca.sheridancollege.jamsy.services.spotify.SpotifyUserService;
import ca.sheridancollege.jamsy.services.spotify.SpotifyTrackService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * WebController (refactored)
 * --------------------------
 * Handles discovery-related views such as artist selection and track discovery.
 * Uses modular Spotify services instead of the old monolithic SpotifyService.
 */
@Controller
@RequestMapping("/discover")
public class WebController {

   
    private final DiscoveryService discoveryService;
    private final ObjectMapper objectMapper;

    public WebController(
            SpotifyAuthService spotifyAuthService,
            SpotifyUserService spotifyUserService,
            SpotifyTrackService spotifyTrackService,
            DiscoveryService discoveryService,
            ObjectMapper objectMapper) {
        this.discoveryService = discoveryService;
        this.objectMapper = objectMapper;
    }

    /**
     * Step 1 → Display categorized artists for the selected workout and mood.
     * Populates the select-artists.html page with 20 random artists
     * belonging to the chosen workout type.
     */
    @GetMapping("/artists")
    public String showArtists(
            @RequestParam("workout") String workout,
            @RequestParam("mood") String mood,
            HttpSession session,
            Model model) {

        // Retrieve categorized artists from session (set earlier in flow)
        List<Map<String, Object>> categorizedArtists =
                (List<Map<String, Object>>) session.getAttribute("categorizedArtists");

        if (categorizedArtists == null) {
            // Redirect back if no artist data found in session
            return "redirect:/playlist-templates";
        }

        // Filter artists that belong to the selected workout category
        List<Map<String, Object>> workoutArtists = categorizedArtists.stream()
                .filter(artist -> {
                    List<String> categories = (List<String>) artist.get("workoutCategories");
                    return categories != null && categories.contains(workout);
                })
                .toList();

        // Randomize and limit to 20 artists
        Collections.shuffle(workoutArtists);
        List<Map<String, Object>> selected = workoutArtists.stream().limit(20).toList();

        model.addAttribute("workout", workout);
        model.addAttribute("mood", mood);
        model.addAttribute("artists", selected);

        return "select-artists";
    }

    /**
     * Step 2 → Handle user’s artist selection and generate discovery tracks.
     * Called from the select-artists form on submit.
     */
    @PostMapping("/artists/submit")
    public String handleArtistSelection(
            @RequestParam("selectedArtists") List<String> selectedArtistIds,
            @RequestParam("artistNames") String artistNamesJson,
            @RequestParam("workout") String workout,
            @RequestParam("mood") String mood,
            Model model,
            HttpSession session) {

        try {
            // Parse JSON string of artist names
            List<String> artistNames = objectMapper.readValue(artistNamesJson, new TypeReference<>() {});

            // Generate new discovery tracks based on selected artists
            List<Track> discoveryTracks = discoveryService.getDiscoveryTracks(artistNames, workout, 20);
            List<Map<String, Object>> frontendTracks = discoveryService.convertTracksForFrontend(discoveryTracks);

            // Add attributes for rendering in Thymeleaf
            model.addAttribute("tracks", discoveryTracks);
            model.addAttribute("tracksJson", objectMapper.writeValueAsString(frontendTracks));
            model.addAttribute("workout", workout);
            model.addAttribute("mood", mood);
            model.addAttribute("seedArtists", artistNames);
            model.addAttribute("isFamiliar", false);

            // Store session state
            session.setAttribute("discoveryTracks", discoveryTracks);
            session.setAttribute("selectedArtists", artistNames);
            session.setAttribute("selectedWorkout", workout);
            session.setAttribute("selectedMood", mood);

            return "tracks";

        } catch (Exception e) {
            model.addAttribute("error", "Error generating discovery tracks: " + e.getMessage());
            return "error";
        }
    }

    /**
     * Step 3 → Revisit or refresh discovery results.
     * Used when the user reloads or navigates back to /discover.
     */
    @GetMapping
    public String rediscover(HttpSession session, Model model) {
        try {
            List<String> seedArtists = (List<String>) session.getAttribute("selectedArtists");
            String workout = (String) session.getAttribute("selectedWorkout");

            if (seedArtists == null || seedArtists.isEmpty()) {
                // Redirect user to start if session expired or missing data
                return "redirect:/playlist-templates";
            }

            // Re-generate discovery tracks
            List<Track> tracks = discoveryService.getDiscoveryTracks(seedArtists, workout, 20);
            List<Map<String, Object>> frontendTracks = discoveryService.convertTracksForFrontend(tracks);

            model.addAttribute("tracks", tracks);
            model.addAttribute("tracksJson", objectMapper.writeValueAsString(frontendTracks));
            model.addAttribute("workout", workout);
            model.addAttribute("isFamiliar", false);

            return "tracks";

        } catch (Exception e) {
            model.addAttribute("error", "Error refreshing discovery view: " + e.getMessage());
            return "error";
        }
    }
    
    /*
     * Restart flow - button to restart the the process if
     * user doesn't like the results
     */
    @GetMapping("/restart")
    public String restartDiscovery(HttpSession session) {
        session.removeAttribute("selectedArtists");
        session.removeAttribute("selectedWorkout");
        session.removeAttribute("selectedMood");
        session.removeAttribute("discoveryTracks");
        return "redirect:/playlist-templates";
    }
}