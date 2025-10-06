package ca.sheridancollege.jamsy.controllers;

import ca.sheridancollege.jamsy.beans.Track;
import ca.sheridancollege.jamsy.exceptions.AuthenticationRequiredException;
import ca.sheridancollege.jamsy.services.DiscoveryService;
import ca.sheridancollege.jamsy.services.spotify.SpotifyArtistService;
import ca.sheridancollege.jamsy.services.spotify.SpotifyUserService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/artists")
public class ArtistController {

    private final SpotifyUserService spotifyUserService;
    private final SpotifyArtistService spotifyArtistService;
    private final DiscoveryService discoveryService;
    private final ObjectMapper objectMapper;

    public ArtistController(SpotifyUserService spotifyUserService,
                            SpotifyArtistService spotifyArtistService,
                            DiscoveryService discoveryService,
                            ObjectMapper objectMapper) {
        this.spotifyUserService = spotifyUserService;
        this.spotifyArtistService = spotifyArtistService;
        this.discoveryService = discoveryService;
        this.objectMapper = objectMapper;
    }

    /**
     * Step 1 → Display more artists based on the selected workout & mood.
     * Retrieves user’s categorized artists stored in the session and filters
     * them by the selected workout type.
     */
    @GetMapping("/more")
    public String showMoreArtists(
            @RequestParam("workout") String workout,
            @RequestParam("mood") String mood,
            HttpSession session,
            Model model) {

        try {
            String accessToken = (String) session.getAttribute("accessToken");
            if (accessToken == null) throw new AuthenticationRequiredException("Spotify access token missing.");

            List<Map<String, Object>> categorizedArtists =
                    (List<Map<String, Object>>) session.getAttribute("categorizedArtists");

            if (categorizedArtists == null) {
                System.out.println("⚠️ No artists in session — redirecting to templates");
                return "redirect:/playlist-templates";
            }

            // Filter artists by selected workout category
            List<Map<String, Object>> workoutArtists = categorizedArtists.stream()
                    .filter(artist -> {
                        List<String> categories = (List<String>) artist.get("workoutCategories");
                        return categories != null && categories.contains(workout);
                    })
                    .collect(Collectors.toList());

            // Shuffle and pick 20
            Collections.shuffle(workoutArtists);
            List<Map<String, Object>> selectedArtists = workoutArtists.stream()
                    .limit(20)
                    .map(artist -> enhanceArtistWithImage(artist, accessToken))
                    .collect(Collectors.toList());

            model.addAttribute("workout", workout);
            model.addAttribute("mood", mood);
            model.addAttribute("artists", selectedArtists);

            return "select-artists";
        } catch (AuthenticationRequiredException e) {
            throw e;
        } catch (Exception e) {
            model.addAttribute("error", "Error loading artists: " + e.getMessage());
            return "redirect:/playlist-templates";
        }
    }

    /**
     * Step 2 → Handle user’s artist selections (Discover/Familiar)
     * Depending on the user’s action, calls DiscoveryService to get track recommendations.
     */
    @PostMapping("/select")
    public String handleArtistSelection(@RequestParam("selectedArtists") List<String> selectedArtistIds,
                                        @RequestParam("artistNames") String artistNamesJson,
                                        @RequestParam("workout") String workout,
                                        @RequestParam("mood") String mood,
                                        @RequestParam("action") String action,
                                        Model model,
                                        HttpSession session) {

        try {
            List<String> artistNames = objectMapper.readValue(
                    artistNamesJson, new TypeReference<List<String>>() {});
            System.out.println("🎧 Selected artists: " + artistNames);

            if ("discover".equals(action)) {
                List<Track> tracks = discoveryService.getDiscoveryTracks(artistNames, workout, 10);
                session.setAttribute("discoveryTracks", tracks);

                model.addAttribute("tracksJson", objectMapper.writeValueAsString(tracks));
                model.addAttribute("tracks", tracks);
                model.addAttribute("workout", workout);
                model.addAttribute("mood", mood);
                model.addAttribute("isFamiliar", false);

                return "tracks";
            }

            model.addAttribute("error", "Feature not implemented yet.");
            return "error";

        } catch (Exception e) {
            model.addAttribute("error", "Error processing artist selection: " + e.getMessage());
            return "error";
        }
    }

    /** ------------------ Helpers ------------------ **/

    private Map<String, Object> enhanceArtistWithImage(Map<String, Object> artist, String accessToken) {
        try {
            String artistId = (String) artist.get("id");
            if (artistId != null) {
                String imageUrl = spotifyArtistService.getArtistImageUrl(accessToken, artistId);
                if (imageUrl != null && !imageUrl.trim().isEmpty()) {
                    artist.put("imageUrl", imageUrl);
                    return artist;
                }
            }
        } catch (Exception e) {
            System.out.println("Error fetching artist image: " + e.getMessage());
        }
        return createPlaceholderImage(artist);
    }

    private Map<String, Object> createPlaceholderImage(Map<String, Object> artist) {
        String artistName = (String) artist.get("name");
        try {
            String initials = artistName.length() >= 2 ?
                    artistName.substring(0, 2).toUpperCase() : artistName.toUpperCase();
            int hash = Math.abs(artistName.hashCode());
            String color = String.format("%06x", hash & 0xFFFFFF);
            artist.put("imageUrl", "https://via.placeholder.com/100x100/" +
                    color + "/ffffff?text=" + URLEncoder.encode(initials, StandardCharsets.UTF_8));
        } catch (Exception e) {
            artist.put("imageUrl", "https://via.placeholder.com/100x100/cccccc/666666?text=🎵");
        }
        return artist;
    }
}
