package ca.sheridancollege.jamsy.controllers;

import ca.sheridancollege.jamsy.beans.Track;
import java.util.LinkedHashMap;
import ca.sheridancollege.jamsy.models.SongAction;
import ca.sheridancollege.jamsy.repositories.SongActionRepository;
import ca.sheridancollege.jamsy.services.DeezerService;
import ca.sheridancollege.jamsy.services.LastFmService;
import ca.sheridancollege.jamsy.services.MusicBrainzService;
import ca.sheridancollege.jamsy.services.SpotifyService;
import jakarta.servlet.http.HttpSession;

import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import com.google.gson.Gson;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Controller
public class WebController {

    private final SpotifyService spotifyService;
    private final MusicBrainzService musicBrainzService;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final SongActionRepository songActionRepo;
    private final LastFmService lastFmService;
    private final DeezerService deezerService;
    
    
    public WebController(
    		SpotifyService spotifyService, 
    		OAuth2AuthorizedClientService authorizedClientService,
    		SongActionRepository songActionRepo, 
    		LastFmService lastFmService,
    		DeezerService deezServ,
    		MusicBrainzService musicBrainzService) {
        this.spotifyService = spotifyService;
        this.authorizedClientService = authorizedClientService;
        this.songActionRepo = songActionRepo;
        this.lastFmService = lastFmService;
        this.deezerService = deezServ;
        this.musicBrainzService = musicBrainzService;
    }
    
    @GetMapping("/")
    public String home() {
        return "redirect:/login";
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login"; // make sure login.html exists in /templates
    }

    @GetMapping("/login/oauth2/code/spotify")
    public String handleSpotifyRedirect(@RequestParam String code, HttpSession session) {
        String accessToken = spotifyService.getUserAccessToken(code);
        System.out.println("✅ Access token received: " + accessToken);
        session.setAttribute("accessToken", accessToken);
        return "redirect:/filters";
    }
    
    @GetMapping("/test-session")
    public String testSession(HttpSession session) {
        System.out.println("Access token test = " + session.getAttribute("accessToken"));
        return "filters";
    }


    @GetMapping("/filters")
    public String showFiltersPage(OAuth2AuthenticationToken authentication, HttpSession session) {
        OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                authentication.getAuthorizedClientRegistrationId(),
                authentication.getName()
        );

        if (client == null || client.getAccessToken() == null) {
            System.out.println("⚠️ Couldn't get access token from authorized client");
            return "redirect:/login";
        }

        String accessToken = client.getAccessToken().getTokenValue();
        session.setAttribute("accessToken", accessToken);

        return "filters"; // return your filters page
    }
    
    @GetMapping("/recommend")
    public String mixedTracks(@RegisteredOAuth2AuthorizedClient("spotify") OAuth2AuthorizedClient client,
                              HttpSession session, Model model) {
        String accessToken = client.getAccessToken().getTokenValue();

        boolean excludeExplicit = Boolean.TRUE.equals(session.getAttribute("excludeExplicit"));
        boolean excludeLove = Boolean.TRUE.equals(session.getAttribute("excludeLoveSongs"));
        boolean excludeFolk = Boolean.TRUE.equals(session.getAttribute("excludeFolk"));

        // Step 1: Get random Spotify track
        Track randomTrack = spotifyService.getRandomSpotifyTrack(accessToken);
        
        if (randomTrack == null) return "error";

        String trackName = randomTrack.getName();
        String artistName = randomTrack.getArtists() != null && !randomTrack.getArtists().isEmpty()
                            ? randomTrack.getArtists().get(0)
                            : "";

        // Step 2: Get Last.fm similar tracks
        List<Track> lastFmSimilar = lastFmService.getSimilarTracksFromRandomSpotifyTrack(
            trackName, artistName, excludeExplicit, excludeLove, excludeFolk, deezerService
        );

        // Step 3: Add to model
        model.addAttribute("tracks", lastFmSimilar);
        model.addAttribute("tracksJson", new Gson().toJson(lastFmSimilar));
        System.out.println("From recommend --> mixedTracks");
        return "tracks";
    }

    
    @PostMapping("/recommend")
    public String recommendTracks(
            HttpSession session,
            @RequestParam(defaultValue = "false") boolean excludeExplicit,
            @RequestParam(defaultValue = "false") boolean excludeLoveSongs,
            @RequestParam(defaultValue = "false") boolean excludeFolk,
            Model model) {

        String accessToken = (String) session.getAttribute("accessToken");
        if (accessToken == null || accessToken.isBlank()) {
            System.out.println("⚠️ No access token in session");
            return "redirect:/login";
        }

        System.out.println("🔁 POST /recommend called with filters: " +
            "explicit=" + excludeExplicit +
            ", love=" + excludeLoveSongs +
            ", folk=" + excludeFolk);

        // ✅ Use fresh merged+shuffled list
        List<Track> tracks = spotifyService.mergeAndShuffleTracks(
            accessToken, excludeExplicit, excludeLoveSongs, excludeFolk);

        model.addAttribute("tracks", tracks);
        model.addAttribute("tracksJson", new Gson().toJson(tracks));

        return "tracks";
    }

    
    @GetMapping("/search")
    public String searchTracks(
            HttpSession session,
            @RequestParam String query,
            @RequestParam(defaultValue = "false") boolean excludeExplicit,
            @RequestParam(defaultValue = "false") boolean excludeLoveSongs,
            @RequestParam(defaultValue = "false") boolean excludeFolk,
            Model model) {

        String accessToken = (String) session.getAttribute("accessToken");
        if (accessToken == null || accessToken.isBlank()) {
            return "redirect:/login";
        }

        List<Track> tracks = spotifyService.searchTrack(query, accessToken, excludeExplicit, excludeLoveSongs, excludeFolk);
        model.addAttribute("tracks", tracks);
        return "tracks";
    }
    
    @GetMapping("/tracks")
    public String tracks(
        @RegisteredOAuth2AuthorizedClient("spotify") OAuth2AuthorizedClient authorizedClient,
        HttpSession session,
        Model model
    ) {
        String accessToken = authorizedClient.getAccessToken().getTokenValue();

        // Get filters (stored earlier from form)
        boolean excludeExplicit = Boolean.TRUE.equals(session.getAttribute("excludeExplicit"));
        boolean excludeLoveSongs = Boolean.TRUE.equals(session.getAttribute("excludeLoveSongs"));
        boolean excludeFolk = Boolean.TRUE.equals(session.getAttribute("excludeFolk"));

        // ✅ This call ensures fresh results on every visit
        System.out.println("✅ Calling mergeAndShuffleTracks()");
        List<Track> mergedTracks = spotifyService.mergeAndShuffleTracks(
            accessToken, excludeExplicit, excludeLoveSongs, excludeFolk
        );

        model.addAttribute("tracks", mergedTracks);
        model.addAttribute("tracksJson", new Gson().toJson(mergedTracks));
        System.out.println("From tracks --> tracks");
        return "tracks";
    }
    

    
    @PostMapping("/handle-action")
    @ResponseBody
    public Map<String, Object> handleAction(
            @RequestBody Map<String, Object> requestBody,
            HttpSession session) {
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

        return Map.of("success", true);
    }

    @GetMapping("/recommendations")
    public String showRecommendationsPage(Model model) {
        List<SongAction> likedSongs = songActionRepo.findByAction("like");
        
        if (likedSongs.isEmpty()) {
            model.addAttribute("error", "No liked songs found to base recommendations on");
            return "recommendations";
        }
        
        Map<SongAction, List<Track>> allRecommendations = new LinkedHashMap<>();
        int maxPopularity = 40; // Only show tracks with popularity < 40
        
        for (SongAction likedSong : likedSongs) {
            try {
                // Get recommendations from both services
                List<Track> lastFmRecs = lastFmService.getSimilarTracksWithPopularity(
                    likedSong.getSongName(), 
                    likedSong.getArtist(),
                    maxPopularity
                );
                
                List<Track> musicBrainzRecs = musicBrainzService.getObscureSimilarTracks(
                    likedSong.getSongName(),
                    likedSong.getArtist(),
                    maxPopularity
                );
                
                // Combine and deduplicate
                List<Track> combined = Stream.concat(lastFmRecs.stream(), musicBrainzRecs.stream())
                    .distinct()
                    .sorted(Comparator.comparingInt(Track::getPopularity))
                    .limit(10)
                    .collect(Collectors.toList());
                
                // Ensure album covers are set
                combined.forEach(track -> {
                    if (track.getAlbumCover() == null || track.getAlbumCover().isEmpty()) {
                        String cover = deezerService.getAlbumCoverFallback(
                            track.getName(), 
                            track.getArtists()
                        );
                        track.setAlbumCover(cover);
                    }
                });
                
                if (!combined.isEmpty()) {
                    allRecommendations.put(likedSong, combined);
                }
                
            } catch (Exception e) {
                System.err.println("Error processing song: " + likedSong.getSongName());
                e.printStackTrace();
            }
        }
        
        model.addAttribute("allRecommendations", allRecommendations);
        return "recommendations";
    }


}
