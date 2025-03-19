package ca.sheridancollege.jamsy.controllers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import com.google.gson.Gson;

import ca.sheridancollege.jamsy.models.SongAction;
import ca.sheridancollege.jamsy.repositories.SongActionRepository;
import ca.sheridancollege.jamsy.services.LastFmService;
import ca.sheridancollege.jamsy.services.SpotifyService;
//import jakarta.servlet.http.HttpSession;

@Controller
public class WebController {

    private final SpotifyService spotifyService;
    private final SongActionRepository songActionRepo;

    public WebController(SpotifyService spotifyService, SongActionRepository songActionRepo) {
        this.spotifyService = spotifyService;
        this.songActionRepo = songActionRepo;
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
            @RequestBody Map<String, Object> requestBody // Use @RequestBody to parse JSON
    ) {
    	String isrc = (String) requestBody.get("isrc");
        String songName = (String) requestBody.get("songName");
        String artist = (String) requestBody.get("artist");
        String action = (String) requestBody.get("action");
    
     // Genres comes as a List from JSON
        List<String> genres = (List<String>) requestBody.get("genres");

        // Log the action for debugging
        System.out.println("Action received: " + action + " for song " + songName + " (ISRC: " + isrc + ")");

        // Save to DB
        SongAction songAction = new SongAction();
        songAction.setIsrc(isrc);
        songAction.setSongName(songName);
        songAction.setArtist(artist);
        songAction.setGenres(genres);
        songAction.setAction(action);
        songActionRepo.save(songAction);
        
        // Return success response
        return Map.of("success", true);
    }

    @GetMapping("/song-actions")
    public String songActions(Model model) {
    	 List<SongAction> allActions = songActionRepo.findAll();

         List<SongAction> likedSongs = allActions.stream()
                 .filter(action -> "like".equalsIgnoreCase(action.getAction()))
                 .collect(Collectors.toList());

         List<SongAction> unlikedSongs = allActions.stream()
                 .filter(action -> "unlike".equalsIgnoreCase(action.getAction()))
                 .collect(Collectors.toList());

         model.addAttribute("likedSongs", likedSongs);
         model.addAttribute("unlikedSongs", unlikedSongs);

         return "song-actions"; // Thymeleaf template
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