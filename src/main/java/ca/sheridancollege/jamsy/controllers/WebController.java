package ca.sheridancollege.jamsy.controllers;

import ca.sheridancollege.jamsy.beans.Track;
import ca.sheridancollege.jamsy.exceptions.AuthenticationRequiredException;
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

    private final SpotifyAuthService spotifyAuthService;
    private final SpotifyUserService spotifyUserService;
    private final SpotifyTrackService spotifyTrackService;
    private final DiscoveryService discoveryService;
    private final ObjectMapper objectMapper;

    public WebController(
            SpotifyAuthService spotifyAuthService,
            SpotifyUserService spotifyUserService,
            SpotifyTrackService spotifyTrackService,
            DiscoveryService discoveryService,
            ObjectMapper objectMapper) {
        this.spotifyAuthService = spotifyAuthService;
        this.spotifyUserService = spotifyUserService;
        this.spotifyTrackService = spotifyTrackService;
        this.discoveryService = discoveryService;
        this.objectMapper = objectMapper;
    }

    /**
     * Step 1 → Display categorized artists for the selected workout and mood.
     */
    @GetMapping("/artists")
    public String showArtists(
            @RequestParam("workout") String workout,
            @RequestParam("mood") String mood,
            HttpSession session,
            Model model) {

        List<Map<String, Object>> categorizedArtists =
                (List<Map<String, Object>>) session.getAttribute("categorizedArtists");

        if (categorizedArtists == null) {
            return "redirect:/playlist-templates";
        }

        // Filter by workout
        List<Map<String, Object>> workoutArtists = categorizedArtists.stream()
                .filter(artist -> {
                    List<String> categories = (List<String>) artist.get("workoutCategories");
                    return categories != null && categories.contains(workout);
                })
                .toList();

        // Shuffle and limit
        Collections.shuffle(workoutArtists);
        List<Map<String, Object>> selected = workoutArtists.stream().limit(20).toList();

        model.addAttribute("workout", workout);
        model.addAttribute("mood", mood);
        model.addAttribute("artists", selected);

        return "select-artists";
    }

    /**
     * Step 2 → Handle user’s artist selection and generate discovery tracks.
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
            List<String> artistNames = objectMapper.readValue(artistNamesJson, new TypeReference<>() {});

            // Generate discovery tracks
            List<Track> discoveryTracks = discoveryService.getDiscoveryTracks(artistNames, workout, 20);
            List<Map<String, Object>> frontendTracks = discoveryService.convertTracksForFrontend(discoveryTracks);

            model.addAttribute("tracks", discoveryTracks);
            model.addAttribute("tracksJson", objectMapper.writeValueAsString(frontendTracks));
            model.addAttribute("workout", workout);
            model.addAttribute("mood", mood);
            model.addAttribute("seedArtists", artistNames);
            model.addAttribute("isFamiliar", false);

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
     */
    @GetMapping
    public String rediscover(HttpSession session, Model model) {
        try {
            List<String> seedArtists = (List<String>) session.getAttribute("selectedArtists");
            String workout = (String) session.getAttribute("selectedWorkout");

            if (seedArtists == null || seedArtists.isEmpty()) {
                return "redirect:/playlist-templates";
            }

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
}
