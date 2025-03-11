package ca.sheridancollege.jamsy.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.RequestParam;

import ca.sheridancollege.jamsy.services.LastFmService;
import ca.sheridancollege.jamsy.services.SpotifyService;
//import jakarta.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class WebController {

    private final SpotifyService spotifyService;

    public WebController(SpotifyService spotifyService) {
        this.spotifyService = spotifyService;
    }

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

        model.addAttribute("tracks", enrichedTracks);
        return "tracks";
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