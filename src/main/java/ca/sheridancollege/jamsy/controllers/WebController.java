package ca.sheridancollege.jamsy.controllers;

import ca.sheridancollege.jamsy.beans.PlaylistTemplate;
import ca.sheridancollege.jamsy.beans.Track;
import ca.sheridancollege.jamsy.exceptions.AuthenticationRequiredException;
import ca.sheridancollege.jamsy.exceptions.InvalidRefreshTokenException;
import ca.sheridancollege.jamsy.models.SongAction;
import ca.sheridancollege.jamsy.repositories.SongActionRepository;
import ca.sheridancollege.jamsy.services.DeezerService;
import ca.sheridancollege.jamsy.services.DiscoveryService;
import ca.sheridancollege.jamsy.services.LastFmService;
import ca.sheridancollege.jamsy.services.MusicBrainzService;
import ca.sheridancollege.jamsy.services.PlaylistTemplateService;
import ca.sheridancollege.jamsy.services.SpotifyService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Controller
public class WebController {
    
    private static final String SPOTIFY_API_URL = "https://api.spotify.com/v1";

    private final SpotifyService spotifyService;
    private final MusicBrainzService musicBrainzService;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final SongActionRepository songActionRepo;
    private final LastFmService lastFmService;
    private final DeezerService deezerService;
    private final PlaylistTemplateService templateService;
    private final DiscoveryService discoveryService;
    private final ObjectMapper objectMapper;

    @Value("${spotify.client.id}")
    private String clientId;
    
    @Value("${spotify.web.redirect-uri}")
    private String redirectUri;
    
    public WebController(
            SpotifyService spotifyService, 
            OAuth2AuthorizedClientService authorizedClientService,
            SongActionRepository songActionRepo, 
            LastFmService lastFmService,
            DeezerService deezServ,
            MusicBrainzService musicBrainzService,
            PlaylistTemplateService templateService,
            DiscoveryService discoveryService,
            ObjectMapper objectMapper) {
        this.spotifyService = spotifyService;
        this.authorizedClientService = authorizedClientService;
        this.songActionRepo = songActionRepo;
        this.lastFmService = lastFmService;
        this.deezerService = deezServ;
        this.musicBrainzService = musicBrainzService;
        this.templateService = templateService;
        this.discoveryService = discoveryService;
        this.objectMapper = objectMapper;
    }
    
    @ExceptionHandler(AuthenticationRequiredException.class)
    public String handleAuthenticationRequired(AuthenticationRequiredException e, HttpSession session) {
        // Clear any existing session data
        session.invalidate();
        
        // Redirect to Spotify authentication
        String authUrl = "https://accounts.spotify.com/authorize?" +
                "client_id=" + clientId +
                "&response_type=code" +
                "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8) +
                "&scope=user-read-private user-read-email user-top-read user-read-recently-played playlist-modify-public playlist-modify-private" +
                "&state=" + UUID.randomUUID().toString();
        
        return "redirect:" + authUrl;
    }
    
    //user logins in and connects spotify
    @GetMapping("/")
    public String loginPage() {
        return "login"; 
    }

    @GetMapping("/login")
    public String login() {
        return "login"; 
    }

    @GetMapping("/playlist-templates")
    public String showPlaylistTemplates(@RegisteredOAuth2AuthorizedClient("spotify") OAuth2AuthorizedClient authorizedClient, 
                                      HttpSession session, Model model) {
        
        try {
            String accessToken = authorizedClient.getAccessToken().getTokenValue();
            
            // Store tokens in session for later use
            session.setAttribute("accessToken", accessToken);
            session.setAttribute("refreshToken", authorizedClient.getRefreshToken().getTokenValue());
            session.setAttribute("accessTokenExpiry", System.currentTimeMillis() + 3600000); // 1 hour
            
            // Retrieve 200 top artists with workout category associations
            List<Map<String, Object>> categorizedArtists = spotifyService.getUserTopArtistsWithWorkoutCategories(accessToken, 200);
            
            // Store in session for later use
            session.setAttribute("categorizedArtists", categorizedArtists);

            List<PlaylistTemplate> templates = templateService.getDefaultTemplates();
            model.addAttribute("templates", templates);

            return "playlist-template";
        } catch (Exception e) {
            if (e instanceof InvalidRefreshTokenException) {
                throw new AuthenticationRequiredException("Please re-authenticate with Spotify");
            }
            throw e;
        }
    }
    
    @GetMapping("/more-artists")
    public String showMoreArtists(
            @RequestParam("workout") String workout,
            @RequestParam("mood") String mood,
            HttpSession session,
            Model model) {

        try {
            String accessToken = spotifyService.ensureValidAccessToken(session);
            List<Map<String, Object>> categorizedArtists = (List<Map<String, Object>>) session.getAttribute("categorizedArtists");
            
            if (categorizedArtists == null) {
                System.out.println("No artists found in session, redirecting to templates");
                return "redirect:/playlist-templates";
            }

            // Filter artists by the selected workout category
            List<Map<String, Object>> workoutArtists = categorizedArtists.stream()
                .filter(artist -> {
                    List<String> categories = (List<String>) artist.get("workoutCategories");
                    return categories != null && categories.contains(workout);
                })
                .collect(Collectors.toList());

            System.out.println("Found " + workoutArtists.size() + " total artists for workout: " + workout);

            // Shuffle and select 20 random artists from the filtered list
            Collections.shuffle(workoutArtists);
            List<Map<String, Object>> selectedArtists = workoutArtists.stream()
                .limit(20)
                .map(artist -> enhanceArtistWithRealImage(artist, accessToken))
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

    private Map<String, Object> enhanceArtistWithRealImage(Map<String, Object> artist, String accessToken) {
        try {
            String artistId = (String) artist.get("id");
            if (artistId != null) {
                String imageUrl = spotifyService.getArtistImageUrl(accessToken, artistId);
                if (imageUrl != null && !imageUrl.trim().isEmpty()) {
                    artist.put("imageUrl", imageUrl);
                    System.out.println("✅ Got real image for: " + artist.get("name"));
                    return artist;
                }
            }
        } catch (Exception e) {
            System.out.println("Error in enhanceArtistWithRealImage for: " + artist.get("name"));
        }
        
        // Fallback to placeholder
        return createPlaceholderImage(artist);
    }
    
    private Map<String, Object> createPlaceholderImage(Map<String, Object> artist) {
        String artistName = (String) artist.get("name");
        if (artistName != null && !artistName.isEmpty()) {
            try {
                // Get first 2 letters for the placeholder
                String initials = artistName.length() >= 2 ? 
                    artistName.substring(0, 2).toUpperCase() : 
                    artistName.toUpperCase();
                
                // Create a deterministic color based on artist name hash
                int hash = Math.abs(artistName.hashCode());
                String color = String.format("%06x", hash & 0xFFFFFF); // Get RGB color from hash
                
                artist.put("imageUrl", "https://via.placeholder.com/100x100/" + color + "/ffffff?text=" + 
                    java.net.URLEncoder.encode(initials, "UTF-8"));
            } catch (Exception e) {
                // Fallback if URL encoding fails
                artist.put("imageUrl", "https://via.placeholder.com/100x100/4CAF50/ffffff?text=🎵");
            }
        } else {
            artist.put("imageUrl", "https://via.placeholder.com/100x100/cccccc/666666?text=🎵");
        }
        return artist;
    }

    @PostMapping("/select-artists/submit")
    public String handleArtistSelection(@RequestParam("selectedArtists") List<String> selectedArtistIds,
            @RequestParam("artistNames") String artistNamesJson,
            @RequestParam("workout") String workout,
            @RequestParam("mood") String mood,
            @RequestParam("action") String action,
            Model model,
            HttpSession session) {
        
    	try {
            ObjectMapper mapper = new ObjectMapper();
            List<String> artistNames = mapper.readValue(artistNamesJson, new TypeReference<List<String>>() {});
            
            System.out.println("=== SELECTED ARTISTS ===");
            System.out.println("IDs: " + selectedArtistIds);
            System.out.println("Names: " + artistNames);
            System.out.println("Workout: " + workout);
            System.out.println("Mood: " + mood);
            System.out.println("Action: " + action);
            
            if ("discover".equals(action)) {
                List<Track> tracks = discoveryService.getDiscoveryTracks(artistNames, workout, 10);
                System.out.println("Found " + tracks.size() + " tracks for discovery");
                
                // Convert tracks to the format expected by the frontend
                List<Map<String, Object>> frontendTracks = convertTracksForFrontend(tracks);
                
                model.addAttribute("tracksJson", mapper.writeValueAsString(frontendTracks));
                model.addAttribute("tracks", tracks); // Keep original for debugging
                model.addAttribute("workout", workout);
                model.addAttribute("mood", mood);
                model.addAttribute("seedArtists", artistNames);
                model.addAttribute("isFamiliar", false);
                
                // Store in session for later use
                session.setAttribute("discoveryTracks", tracks);
                
                return "tracks";
            } /*else if ("familiar".equals(action)) {
                // Handle familiar playlist creation
                List<Track> tracks = familiarService.getFamiliarTracks(artistNames, workout, 15);
                model.addAttribute("tracks", tracks);
                model.addAttribute("workout", workout);
                model.addAttribute("mood", mood);
                return "familiar-results";
            }*/
            
        } catch (Exception e) {
            System.err.println("Error processing artist selection: " + e.getMessage());
            e.printStackTrace();
            model.addAttribute("error", "Error processing your selection: " + e.getMessage());
            return "error";
        }
        
        model.addAttribute("error", "Invalid action specified");
        return "error";
    }
    
    private List<Map<String, Object>> convertTracksForFrontend(List<Track> tracks) {
        List<Map<String, Object>> frontendTracks = new ArrayList<>();

        for (Track track : tracks) {
            Map<String, Object> frontendTrack = new HashMap<>();
            frontendTrack.put("id", UUID.randomUUID().toString()); // Generate unique ID
            frontendTrack.put("name", track.getName());

            // ✅ Handle artists more robustly
            List<String> artistList = new ArrayList<>();
            if (track.getArtists() != null && !track.getArtists().isEmpty()) {
                artistList.addAll(track.getArtists());
            } else if (track.getArtistName() != null && !track.getArtistName().isEmpty()) {
                artistList.add(track.getArtistName());
            } else {
                artistList.add("Unknown Artist");
            }
            frontendTrack.put("artists", artistList);

            // Album cover fallback
            frontendTrack.put(
                "albumCover",
                track.getImageUrl() != null && !track.getImageUrl().isEmpty()
                    ? track.getImageUrl()
                    : "/images/default-cover.jpg"
            );

            // Preview and genres (not provided by Last.fm)
            frontendTrack.put("previewUrl", track.getPreviewUrl() != null ? track.getPreviewUrl() : null);
            frontendTrack.put("genres", track.getGenres() != null ? track.getGenres() : Collections.emptyList());

            frontendTracks.add(frontendTrack);
        }

        return frontendTracks;
    }

    
    // Song list 
    @GetMapping("/discover")
    public String discoverTracks(HttpSession session, Model model) {
        try {
            List<String> selectedArtists = (List<String>) session.getAttribute("selectedArtists");
            String workout = (String) session.getAttribute("selectedWorkout");
            String mood = (String) session.getAttribute("selectedMood");

            if (selectedArtists == null || selectedArtists.size() != 5) {
                model.addAttribute("error", "Please select exactly 5 artists first.");
                return "redirect:/select-artists";
            }

            System.out.println("Processing discovery for artists: " + selectedArtists);
            
            // Use enhanced discovery service
            List<Track> tracks = discoveryService.getDiscoveryTracks(selectedArtists, workout, 50);

            if (tracks.isEmpty()) {
                model.addAttribute("error", "No matching tracks found. Please try different artists.");
                // Return to select-artists with preserved context
                String workoutType = (String) session.getAttribute("selectedWorkout");
                String moodType = (String) session.getAttribute("selectedMood");
                model.addAttribute("workout", workoutType);
                model.addAttribute("mood", moodType);
                return "select-artists";
            }

            // Convert to JSON for Thymeleaf/JS
            ObjectMapper mapper = new ObjectMapper();
            try {
                String tracksJson = mapper.writeValueAsString(tracks);
                model.addAttribute("tracksJson", tracksJson);
            } catch (Exception e) {
                model.addAttribute("tracksJson", "[]");
                model.addAttribute("warning", "Could not load track data properly");
            }

            model.addAttribute("workout", workout);
            model.addAttribute("mood", mood);
            model.addAttribute("isFamiliar", false);

            System.out.println("Successfully found " + tracks.size() + " tracks");
            return "tracks";
            
        } catch (Exception e) {
            System.out.println("Error in discover: " + e.getMessage());
            e.printStackTrace();
            model.addAttribute("error", "Error discovering tracks: " + e.getMessage());
            return "redirect:/playlist-templates";
        }
    }

    @PostMapping("/track/like")
    @ResponseBody
    public String likeTrack(@RequestBody Map<String, String> request, HttpSession session) {
        String trackId = request.get("trackId");
        // Save liked track to session or database
        System.out.println("Liked track: " + trackId);
        return "Liked";
    }

    @PostMapping("/track/dislike")
    @ResponseBody
    public String dislikeTrack(@RequestBody Map<String, String> request) {
        String trackId = request.get("trackId");
        // Track dislike for future recommendations
        System.out.println("Disliked track: " + trackId);
        return "Disliked";
    }
    
    @PostMapping("/handle-action")
    @ResponseBody
    public Map<String, Object> handleAction(
            @RequestBody Map<String, Object> requestBody,
            HttpSession session) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
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
            System.out.println("Payload → songName=" + songName + ", artist=" + artist);

            // Store liked tracks in session
            if ("like".equals(action)) {
                List<Track> likedTracks = (List<Track>) session.getAttribute("likedTracks");
                if (likedTracks == null) {
                    likedTracks = new ArrayList<>();
                    session.setAttribute("likedTracks", likedTracks);
                }

                List<Track> discoveryTracks = (List<Track>) session.getAttribute("discoveryTracks");
                System.out.println("🟢 discoveryTracks in session: " + (discoveryTracks != null ? discoveryTracks.size() : 0));

                if (discoveryTracks != null) {
                    for (Track track : discoveryTracks) {
                        System.out.println("Checking " + track.getName() + " vs " + songName);
                        System.out.println("Artists in track: " + track.getArtists() + " vs payload artist: " + artist);

                        if (track.getName() != null && track.getName().equalsIgnoreCase(songName)) {
                            if (track.getArtists() == null || track.getArtists().isEmpty()) {
                                likedTracks.add(track);
                                System.out.println("✅ Added (name-only match): " + track.getName());
                            } else if (track.getArtists().stream()
                                      .anyMatch(a -> a.toLowerCase().contains(artist.toLowerCase()))) {
                                likedTracks.add(track);
                                System.out.println("✅ Added (name + artist match): " + track.getName() + " by " + track.getArtists());
                            }
                        }

                    }
                }

                System.out.println("❤️ likedTracks size after like: " + likedTracks.size());
            }


            response.put("success", true);
            response.put("message", "Action processed successfully");
            
        } catch (Exception e) {
            System.out.println("❌ Error in handleAction: " + e.getMessage());
            response.put("success", false);
            response.put("message", "Error processing action");
        }
        
        return response;
    }
    
    
	/* Users to see what they liked */
    @GetMapping("/liked")
    public String showLiked(HttpSession session, Model model) {
        List<Track> likedTracks = (List<Track>) session.getAttribute("likedTracks");
        if (likedTracks == null) likedTracks = new ArrayList<>();

        System.out.println("📀 likedTracks in session: " + likedTracks.size());

        model.addAttribute("tracksJson", new Gson().toJson(likedTracks));
        model.addAttribute("tracks", likedTracks);
        
        return "liked"; // goes to liked.html
    }
    
    @GetMapping("/familiar-playlist")
    public String createFamiliarPlaylist(HttpSession session, Model model) {
        
        try {
            String accessToken = spotifyService.ensureValidAccessToken(session);
            List<String> selectedArtists = (List<String>) session.getAttribute("selectedArtists");
            String workout = (String) session.getAttribute("selectedWorkout");
            
            if (selectedArtists == null || selectedArtists.isEmpty()) {
                return "redirect:/playlist-templates";
            }
            
            // Get top tracks from selected artists
            List<Track> familiarTracks = new ArrayList<>();
            for (String artistId : selectedArtists) {
                List<String> topTrackIds = spotifyService.getArtistTopTracks(artistId, accessToken, 4);
                for (String trackId : topTrackIds) {
                    Map<String, Object> trackData = getTrackDetailsFromSpotify(trackId, accessToken);
                    if (trackData != null) {
                        Track track = mapSpotifyTrack(trackData);
                        if (track.getPreviewUrl() != null) {
                            familiarTracks.add(track);
                        }
                    }
                }
            }
            
            // Shuffle and limit to 20 tracks
            Collections.shuffle(familiarTracks);
            familiarTracks = familiarTracks.stream().limit(20).collect(Collectors.toList());
            
            // Store in session for handle-action
            session.setAttribute("discoveryTracks", familiarTracks);
            
            if (familiarTracks.isEmpty()) {
                model.addAttribute("error", "No familiar tracks found");
                return "redirect:/select-artists";
            }
            
            String tracksJson = objectMapper.writeValueAsString(familiarTracks);
            
            session.setAttribute("familiarTracks", familiarTracks);
            session.setAttribute("likedTracks", new ArrayList<Track>());
            
            model.addAttribute("tracks", familiarTracks);
            model.addAttribute("tracksJson", tracksJson);
            model.addAttribute("workout", workout);
            model.addAttribute("isFamiliar", true);
            
            return "tracks";
            
        } catch (AuthenticationRequiredException e) {
            throw e;
        } catch (Exception e) {
            System.out.println("❌ Error creating familiar playlist: " + e.getMessage());
            model.addAttribute("error", "Error creating familiar playlist");
            return "redirect:/select-artists";
        }
    }
    
    // Helper method to get track details from Spotify
    private Map<String, Object> getTrackDetailsFromSpotify(String trackId, String accessToken) {
        try {
            String url = "https://api.spotify.com/v1/tracks/" + trackId;
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody();
            }
        } catch (Exception e) {
            System.out.println("❌ Error getting track details from Spotify: " + e.getMessage());
        }
        return null;
    }
    
    // Helper method to map Spotify track data
    private Track mapSpotifyTrack(Map<String, Object> trackData) {
        Track track = new Track();
        track.setId((String) trackData.get("id"));
        track.setName((String) trackData.get("name"));
        track.setPreviewUrl((String) trackData.get("preview_url"));
        track.setPopularity((Integer) trackData.get("popularity"));

        // Get artists
        List<Map<String, Object>> artists = (List<Map<String, Object>>) trackData.get("artists");
        List<String> artistNames = artists.stream()
            .map(artist -> (String) artist.get("name"))
            .collect(Collectors.toList());
        track.setArtists(artistNames);

        // Get album cover
        Map<String, Object> album = (Map<String, Object>) trackData.get("album");
        if (album != null) {
            List<Map<String, Object>> images = (List<Map<String, Object>>) album.get("images");
            if (images != null && !images.isEmpty()) {
                track.setAlbumCover((String) images.get(0).get("url"));
            }
        }

        return track;
    }

    @PostMapping("/recommend")
    public String recommendTracks(
            HttpSession session,
            Model model) {

        try {
            String accessToken = spotifyService.ensureValidAccessToken(session);

            // ✅ Use fresh merged+shuffled list
            List<Track> tracks = spotifyService.mergeAndShuffleTracks(accessToken);

            model.addAttribute("tracks", tracks);
            model.addAttribute("tracksJson", new Gson().toJson(tracks));

            return "tracks";
        } catch (AuthenticationRequiredException e) {
            throw e;
        } catch (Exception e) {
            model.addAttribute("error", "Error loading recommendations: " + e.getMessage());
            return "tracks";
        }
    }

    
    @GetMapping("/search")
    public String searchTracks(
            HttpSession session,
            @RequestParam String query,
            @RequestParam(defaultValue = "false") boolean excludeExplicit,
            @RequestParam(defaultValue = "false") boolean excludeLoveSongs,
            @RequestParam(defaultValue = "false") boolean excludeFolk,
            Model model) {

        try {
            String accessToken = spotifyService.ensureValidAccessToken(session);

            List<Track> tracks = spotifyService.searchTrack(query, accessToken, excludeExplicit, excludeLoveSongs, excludeFolk);
            model.addAttribute("tracks", tracks);
            return "tracks";
        } catch (AuthenticationRequiredException e) {
            throw e;
        } catch (Exception e) {
            model.addAttribute("error", "Error searching tracks: " + e.getMessage());
            return "tracks";
        }
    }
    
    @GetMapping("/tracks")
    public String tracks(
        @RegisteredOAuth2AuthorizedClient("spotify") OAuth2AuthorizedClient authorizedClient,
        HttpSession session,
        Model model
    ) {
        try {
            String accessToken = authorizedClient.getAccessToken().getTokenValue();
            session.setAttribute("accessToken", accessToken);
            session.setAttribute("refreshToken", authorizedClient.getRefreshToken().getTokenValue());
            session.setAttribute("accessTokenExpiry", System.currentTimeMillis() + 3600000);

            // ✅ This call ensures fresh results on every visit
            System.out.println("✅ Calling mergeAndShuffleTracks()");
            List<Track> mergedTracks = spotifyService.mergeAndShuffleTracks(accessToken);

            model.addAttribute("tracks", mergedTracks);
            model.addAttribute("tracksJson", new Gson().toJson(mergedTracks));
            System.out.println("From tracks --> tracks");
            return "tracks";
        } catch (Exception e) {
            model.addAttribute("error", "Error loading tracks: " + e.getMessage());
            return "tracks";
        }
    }
}