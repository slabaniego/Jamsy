package ca.sheridancollege.jamsy.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.google.gson.Gson;

import ca.sheridancollege.jamsy.services.LastFmService;
import ca.sheridancollege.jamsy.services.SpotifyService;
//import jakarta.servlet.http.HttpSession;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class WebController {

    private final SpotifyService spotifyService;

    public WebController(SpotifyService spotifyService) {
        this.spotifyService = spotifyService;
    }

    
    private final List<Map<String, String>> songActions = new ArrayList<>();
    
    @GetMapping("/")
    public String home() {
        return "login";
    }

    // @GetMapping("/callback")
    // public String callback(@RequestParam String code, HttpSession session) {
    // String accessToken = spotifyService.getUserAccessToken(code);
    // session.setAttribute("accessToken", accessToken);
    // return "redirect:/tracks";
    // }

    // @GetMapping("/tracks")
    // public String tracks(HttpSession session, Model model) {
    // String accessToken = (String) session.getAttribute("accessToken");

    // // Fetch non-popular tracks (popularity < 50) and limit to 20
    // List<Map<String, Object>> nonPopularTracks =
    // spotifyService.getNonPopularTracks(accessToken, 50, 20);

    // // Enrich track data
    // List<Map<String, Object>> enrichedTracks =
    // spotifyService.enrichTrackData(nonPopularTracks, accessToken);

    // // Log the tracks being added to the model
    // System.out.println("Tracks to display: " + enrichedTracks);

    // model.addAttribute("tracks", enrichedTracks);
    // return "tracks";
    // }

    @GetMapping("/tracks")
    public String tracks(@RegisteredOAuth2AuthorizedClient("spotify") OAuth2AuthorizedClient authorizedClient,
                         Model model) {
        String accessToken = authorizedClient.getAccessToken().getTokenValue();

        // Fetch non-popular tracks (popularity < 50) and limit to 20
        List<Map<String, Object>> nonPopularTracks = spotifyService.getNonPopularTracks(accessToken, 50, 20);

        // Enrich track data
        List<Map<String, Object>> enrichedTracks = spotifyService.enrichTrackData(nonPopularTracks, accessToken);

        // Add tracks to the model as JSON
        model.addAttribute("tracks", enrichedTracks);
        model.addAttribute("tracksJson", new Gson().toJson(enrichedTracks)); // Convert tracks to JSON

        // Add the first track to the model
        if (!enrichedTracks.isEmpty()) {
            model.addAttribute("track", enrichedTracks.get(0));
        }

        return "tracks";
    }
    
    @PostMapping("/handle-action")
    @ResponseBody
    public Map<String, Object> handleAction(
            @RequestBody Map<String, String> requestBody // Use @RequestBody to parse JSON
    ) {
        String isrc = requestBody.get("isrc");
        String songName = requestBody.get("songName");
        String action = requestBody.get("action");

        // Log the action for debugging
        System.out.println("Action received: " + action + " for song " + songName + " (ISRC: " + isrc + ")");

        // Save the action (like/unlike) in memory
        Map<String, String> actionData = new HashMap<>();
        actionData.put("isrc", isrc);
        actionData.put("songName", songName);
        actionData.put("action", action);
        songActions.add(actionData);

        // Return success response
        return Map.of("success", true);
    }

    @GetMapping("/song-actions")
    public String songActions(Model model) {
        // Separate liked and unliked songs
        List<Map<String, String>> likedSongs = new ArrayList<>();
        List<Map<String, String>> unlikedSongs = new ArrayList<>();

        for (Map<String, String> action : songActions) {
            if ("like".equals(action.get("action"))) {
                likedSongs.add(action);
            } else if ("unlike".equals(action.get("action"))) {
                unlikedSongs.add(action);
            }
        }

        // Add to the model
        model.addAttribute("likedSongs", likedSongs);
        model.addAttribute("unlikedSongs", unlikedSongs);

        return "song-actions"; // Return the Thymeleaf template name
    }

    // @GetMapping("/test")
    // public String test() {
    // return "This is a test!";
    // }

    @GetMapping("/test-lastfm")
    public ResponseEntity<Map<String, Object>> testLastFm() {
        LastFmService lastFmService = new LastFmService();

        // Test getting genres for a known track
        List<String> genres = lastFmService.getGenresForTrack("Bohemian Rhapsody", "Queen");

        // Test getting similar genres for "rock"
        List<String> similarGenres = lastFmService.getSimilarGenres("rock");

        Map<String, Object> response = new HashMap<>();
        response.put("trackGenres", genres);
        response.put("similarGenres", similarGenres);

        return ResponseEntity.ok(response);
    }
}