package ca.sheridancollege.jamsy.services;

import ca.sheridancollege.jamsy.beans.PlaylistTemplate;
import ca.sheridancollege.jamsy.beans.Track;
import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;


import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class SpotifyService {

    @Value("${spotify.client.id}")
    private String clientId;

    @Value("${spotify.client.secret}")
    private String clientSecret;

    @Value("${spotify.mobile.redirect-uri}")
    private String mobileRedirectUri;

    private static final String SPOTIFY_API_URL = "https://api.spotify.com/v1";

    private final RestTemplate restTemplate = new RestTemplate();
    private final DeezerService deezerService;
    private final LastFmService lastFmService;
    private final MusicBrainzService musicBrainzService;
    
    private final Map<String, List<Map<String, Object>>> artistCache = new ConcurrentHashMap<>();

    public SpotifyService(LastFmService last, DeezerService deezerService) {
        this.deezerService = deezerService;
        this.lastFmService = last;
		this.musicBrainzService = null;
    }
    
    private static final int MAX_REQUESTS_PER_MINUTE = 50;
    private static final Map<String, RateLimitInfo> rateLimitMap = new ConcurrentHashMap<>();
    
    // Add this inner class
    private static class RateLimitInfo {
        private int requestCount = 0;
        private long lastResetTime = System.currentTimeMillis();
    }
    
    // Modify your methods to include rate limiting
    private synchronized void checkRateLimit(String endpoint) {
        RateLimitInfo info = rateLimitMap.computeIfAbsent(endpoint, k -> new RateLimitInfo());
        
        long currentTime = System.currentTimeMillis();
        if (currentTime - info.lastResetTime > 60000) { // 1 minute
            info.requestCount = 0;
            info.lastResetTime = currentTime;
        }
        
        if (info.requestCount >= MAX_REQUESTS_PER_MINUTE) {
            try {
                Thread.sleep(1000); // Wait 1 second
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            info.requestCount = 0;
            info.lastResetTime = System.currentTimeMillis();
        }
        
        info.requestCount++;
    }

    public String getSpotifyUserId(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        HttpEntity<String> entity = new HttpEntity<>("parameters", headers);
        ResponseEntity<Map> response = restTemplate.exchange(
                "https://api.spotify.com/v1/me",
                HttpMethod.GET,
                entity,
                Map.class);

        return (String) response.getBody().get("id");
    }

    public String getUserAccessToken(String code, String redirectUri) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        String body = "grant_type=authorization_code&code=" + code +
                "&redirect_uri=" + redirectUri +
                "&client_id=" + clientId + "&client_secret=" + clientSecret;

        HttpEntity<String> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.exchange(
                "https://accounts.spotify.com/api/token",
                HttpMethod.POST, entity, Map.class);

        return (String) response.getBody().get("access_token");
    }

    public String getUserAccessToken(String code) {
        return getUserAccessToken(code, mobileRedirectUri);
    }

    public Map<String, String> refreshAccessToken(String refreshToken) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setBasicAuth(clientId, clientSecret);

            String body = "grant_type=refresh_token&refresh_token=" + refreshToken;

            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    "https://accounts.spotify.com/api/token",
                    HttpMethod.POST, entity, Map.class);

            Map<String, String> tokens = new HashMap<>();
            tokens.put("access_token", (String) response.getBody().get("access_token"));
            if (response.getBody().containsKey("refresh_token")) {
                tokens.put("refresh_token", (String) response.getBody().get("refresh_token"));
            } else {
                tokens.put("refresh_token", refreshToken); // Return the same refresh token if not provided
            }
            tokens.put("token_type", (String) response.getBody().get("token_type"));
            tokens.put("expires_in", String.valueOf(response.getBody().get("expires_in")));

            return tokens;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.BAD_REQUEST && 
                e.getResponseBodyAsString().contains("invalid_grant")) {
                // Refresh token is invalid, we need to re-authenticate
                throw new InvalidRefreshTokenException("Refresh token is invalid, user needs to re-authenticate");
            }
            throw e; // Re-throw other exceptions
        }
    }
    
    public class InvalidRefreshTokenException extends RuntimeException {
        public InvalidRefreshTokenException(String message) {
            super(message);
        }
    }
    
    public String ensureValidAccessToken(HttpSession session) {
        String accessToken = (String) session.getAttribute("accessToken");
        String refreshToken = (String) session.getAttribute("refreshToken");
        Long expiryTime = (Long) session.getAttribute("accessTokenExpiry");

        // If token is missing or expired
        if (accessToken == null || expiryTime == null || System.currentTimeMillis() > expiryTime) {
            try {
                Map<String, String> tokens = refreshAccessToken(refreshToken);
                accessToken = tokens.get("access_token");

                // Save updated values in session
                session.setAttribute("accessToken", accessToken);
                session.setAttribute("refreshToken", tokens.get("refresh_token"));
                session.setAttribute("accessTokenExpiry",
                        System.currentTimeMillis() + (Long.parseLong(tokens.get("expires_in")) * 1000));
            } catch (InvalidRefreshTokenException e) {
                // Clear the session and force re-authentication
                session.removeAttribute("accessToken");
                session.removeAttribute("refreshToken");
                session.removeAttribute("accessTokenExpiry");
                throw new AuthenticationRequiredException("Please re-authenticate with Spotify");
            }
        }

        return accessToken;
    }
    
    public class AuthenticationRequiredException extends RuntimeException {
        public AuthenticationRequiredException(String message) {
            super(message);
        }
    }

    public String getArtistName(String artistId, String accessToken) {
        try {
            String url = "https://api.spotify.com/v1/artists/" + artistId;
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return (String) response.getBody().get("name");
            }
        } catch (Exception e) {
            System.out.println("Error getting artist name for " + artistId + ": " + e.getMessage());
        }
        return null;
    }
    
    public List<Map<String, Object>> getUserTopArtistsWithWorkoutCategories(String accessToken, int limit) {
        String cacheKey = accessToken + "_categorized_artists_" + limit;
        
        if (artistCache.containsKey(cacheKey)) {
            return artistCache.get(cacheKey);
        }
        
        List<Map<String, Object>> categorizedArtists = new ArrayList<>();
        int offset = 0;
        int retrieved = 0;
        
        try {
            while (retrieved < limit) {
                int batchSize = Math.min(50, limit - retrieved);
                String url = SPOTIFY_API_URL + "/me/top/artists?limit=" + batchSize + "&offset=" + offset + "&time_range=long_term";
                
                HttpHeaders headers = new HttpHeaders();
                headers.set("Authorization", "Bearer " + accessToken);
                HttpEntity<String> entity = new HttpEntity<>(headers);
                
                ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
                
                if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                    Map<String, Object> responseBody = response.getBody();
                    List<Map<String, Object>> items = (List<Map<String, Object>>) responseBody.get("items");
                    
                    if (items == null || items.isEmpty()) break;
                    
                    for (Map<String, Object> artist : items) {
                        String artistId = (String) artist.get("id");
                        String artistName = (String) artist.get("name");
                        
                        Map<String, Object> artistInfo = new HashMap<>();
                        artistInfo.put("id", artistId);
                        artistInfo.put("name", artistName);
                        artistInfo.put("genres", artist.get("genres"));
                        artistInfo.put("popularity", artist.get("popularity"));
                        
                        // Try audio features first, fallback to genre analysis if 403
                        List<String> workoutCategories;
                        try {
                            Map<String, Double> features = getArtistAudioFeatures(accessToken, artistId);
                            workoutCategories = analyzeAudioFeaturesForWorkout(features, artistName);
                        } catch (Exception e) {
                            // Fallback to genre-based analysis
                            System.out.println("🔄 Falling back to genre analysis for: " + artistName);
                            workoutCategories = determineWorkoutCategoriesFromGenres(artistName);
                        }
                        
                        artistInfo.put("workoutCategories", workoutCategories);
                        categorizedArtists.add(artistInfo);
                        retrieved++;
                    }
                    
                    offset += items.size();
                    if (items.size() < batchSize) break;
                } else {
                    break;
                }
            }
            
            categorizedArtists = ensureCategoryBalance(categorizedArtists);
            artistCache.put(cacheKey, categorizedArtists);
            printCategoryDistribution(categorizedArtists);
            
        } catch (Exception e) {
            System.out.println("❌ Error fetching categorized artists: " + e.getMessage());
        }
        
        return categorizedArtists;
    }

    private Map<String, Double> getArtistAudioFeatures(String accessToken, String artistId) {
        try {
            // Get artist's top tracks
            String topTracksUrl = SPOTIFY_API_URL + "/artists/" + artistId + "/top-tracks?market=US";
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(topTracksUrl, HttpMethod.GET, entity, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                List<Map<String, Object>> tracks = (List<Map<String, Object>>) responseBody.get("tracks");
                
                if (tracks != null && !tracks.isEmpty()) {
                    // Get track IDs for audio features
                    List<String> trackIds = tracks.stream()
                        .map(track -> (String) track.get("id"))
                        .limit(3) // Analyze top 3 tracks
                        .collect(Collectors.toList());
                    
                    // Get audio features for these tracks
                    return getAverageAudioFeatures(accessToken, trackIds);
                }
            }
        } catch (Exception e) {
            System.out.println("❌ Error getting audio features for artist " + artistId + ": " + e.getMessage());
            throw e; // Re-throw to trigger fallback
        }
        
        throw new RuntimeException("No audio features available");
    }

    private Map<String, Double> getAverageAudioFeatures(String accessToken, List<String> trackIds) {
        if (trackIds.isEmpty()) {
            throw new RuntimeException("No track IDs provided");
        }
        
        try {
            String trackIdsParam = String.join(",", trackIds);
            String audioFeaturesUrl = SPOTIFY_API_URL + "/audio-features?ids=" + trackIdsParam;
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(audioFeaturesUrl, HttpMethod.GET, entity, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                List<Map<String, Object>> audioFeaturesList = (List<Map<String, Object>>) responseBody.get("audio_features");
                
                if (audioFeaturesList != null && !audioFeaturesList.isEmpty()) {
                    return calculateAverageFeatures(audioFeaturesList);
                }
            } else if (response.getStatusCode() == HttpStatus.FORBIDDEN) {
                System.out.println("⚠️ Audio features access forbidden - using genre fallback");
                throw new RuntimeException("403 Forbidden - Audio features not accessible");
            }
            
        } catch (Exception e) {
            System.out.println("❌ Error in getAverageAudioFeatures: " + e.getMessage());
            throw new RuntimeException("Audio features unavailable", e);
        }
        
        throw new RuntimeException("No audio features data received");
    }

    private Map<String, Map<String, Double>> getArtistsAudioFeatures(String accessToken, List<String> artistIds) {
        Map<String, Map<String, Double>> artistFeatures = new HashMap<>();
        
        try {
            for (String artistId : artistIds) {
                // Get artist's top tracks
                String topTracksUrl = SPOTIFY_API_URL + "/artists/" + artistId + "/top-tracks?market=US";
                
                HttpHeaders headers = new HttpHeaders();
                headers.set("Authorization", "Bearer " + accessToken);
                HttpEntity<String> entity = new HttpEntity<>(headers);
                
                ResponseEntity<Map> response = restTemplate.exchange(topTracksUrl, HttpMethod.GET, entity, Map.class);
                
                if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                    Map<String, Object> responseBody = response.getBody();
                    List<Map<String, Object>> tracks = (List<Map<String, Object>>) responseBody.get("tracks");
                    
                    if (tracks != null && !tracks.isEmpty()) {
                        // Get track IDs for audio features
                        List<String> trackIds = tracks.stream()
                            .map(track -> (String) track.get("id"))
                            .limit(5) // Analyze top 5 tracks
                            .collect(Collectors.toList());
                        
                        // Get audio features for these tracks
                        Map<String, Double> avgFeatures = getAverageAudioFeatures(accessToken, trackIds);
                        artistFeatures.put(artistId, avgFeatures);
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("❌ Error getting audio features: " + e.getMessage());
        }
        
        return artistFeatures;
    }

    

    private Map<String, Double> calculateAverageFeatures(List<Map<String, Object>> audioFeaturesList) {
        Map<String, Double> sums = new HashMap<>();
        Map<String, Integer> counts = new HashMap<>();
        
        String[] featureKeys = {"danceability", "energy", "valence", "tempo", "acousticness", "loudness"};
        
        for (Map<String, Object> features : audioFeaturesList) {
            if (features != null) {
                for (String key : featureKeys) {
                    Object value = features.get(key);
                    if (value instanceof Number) {
                        sums.put(key, sums.getOrDefault(key, 0.0) + ((Number) value).doubleValue());
                        counts.put(key, counts.getOrDefault(key, 0) + 1);
                    }
                }
            }
        }
        
        Map<String, Double> averages = new HashMap<>();
        for (String key : featureKeys) {
            if (counts.containsKey(key) && counts.get(key) > 0) {
                averages.put(key, sums.get(key) / counts.get(key));
            }
        }
        
        return averages;
    }

    private List<String> analyzeAudioFeaturesForWorkout(Map<String, Double> features, String artistName) {
        if (features == null || features.isEmpty()) {
            // Fallback to genre-based analysis if audio features unavailable
            return determineWorkoutCategoriesFromGenres(artistName);
        }
        
        List<String> categories = new ArrayList<>();
        
        // Analyze based on Spotify's audio features
        double danceability = features.getOrDefault("danceability", 0.5);
        double energy = features.getOrDefault("energy", 0.5);
        double valence = features.getOrDefault("valence", 0.5); // Positivity
        double tempo = features.getOrDefault("tempo", 120.0);
        double acousticness = features.getOrDefault("acousticness", 0.5);
        
        // Cardio: High energy, high danceability, fast tempo
        if (energy > 0.7 && danceability > 0.6 && tempo > 120) {
            categories.add("Cardio");
        }
        
        // Strength Training: High energy, lower danceability, powerful
        if (energy > 0.7 && danceability < 0.5 && tempo > 100) {
            categories.add("Strength Training");
        }
        
        // HIIT: Very high energy, high tempo, intense
        if (energy > 0.8 && tempo > 130) {
            categories.add("HIIT");
        }
        
        // Yoga: Lower energy, higher acousticness, calm
        if (energy < 0.5 && acousticness > 0.6 && tempo < 100) {
            categories.add("Yoga");
        }
        
        // If no strong matches, use weighted scoring
        if (categories.isEmpty()) {
            Map<String, Double> categoryScores = new HashMap<>();
            
            categoryScores.put("Cardio", (energy * 0.4 + danceability * 0.3 + (tempo/200) * 0.3) * 100);
            categoryScores.put("Strength Training", (energy * 0.5 + (1-danceability) * 0.3 + (tempo/180) * 0.2) * 100);
            categoryScores.put("HIIT", (energy * 0.6 + (tempo/200) * 0.4) * 100);
            categoryScores.put("Yoga", ((1-energy) * 0.4 + acousticness * 0.4 + (1-tempo/200) * 0.2) * 100);
            
            String topCategory = categoryScores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .get()
                .getKey();
            
            categories.add(topCategory);
        }
        
        return categories;
    }

    private List<String> determineWorkoutCategoriesFromGenres(String artistName) {
        // Try multiple sources for genre data
        List<String> allGenres = new ArrayList<>();
        
        // 1. Try Last.fm
        try {
            List<String> lastFmGenres = lastFmService.getArtistGenres(artistName);
            if (lastFmGenres != null && !lastFmGenres.isEmpty()) {
                allGenres.addAll(lastFmGenres);
                System.out.println("✅ Found Last.fm genres for " + artistName + ": " + lastFmGenres);
            }
        } catch (Exception e) {
            System.out.println("⚠️ Last.fm genre lookup failed for " + artistName);
        }
        
        // 2. Try MusicBrainz (if available)
        if (musicBrainzService != null) {
            try {
                List<String> musicBrainzGenres = musicBrainzService.getArtistGenres(artistName);
                if (musicBrainzGenres != null && !musicBrainzGenres.isEmpty()) {
                    allGenres.addAll(musicBrainzGenres);
                    System.out.println("✅ Found MusicBrainz genres for " + artistName + ": " + musicBrainzGenres);
                }
            } catch (Exception e) {
                System.out.println("⚠️ MusicBrainz genre lookup failed for " + artistName);
            }
        } else {
            System.out.println("⚠️ MusicBrainzService is not available");
        }
        
        // 3. Use categorizeGenres if we have data
        if (!allGenres.isEmpty()) {
            List<String> categories = categorizeGenres(allGenres);
            if (!categories.isEmpty()) {
                return categories;
            }
        }
        
        // 4. Final fallback based on artist name
        System.out.println("🔄 Using name analysis fallback for: " + artistName);
        return Arrays.asList(getFallbackCategoryBasedOnPopularity(artistName));
    }
    
    private List<String> categorizeGenres(List<String> genres) {
        if (genres == null || genres.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<String> categories = new ArrayList<>();
        Set<String> genreSet = new HashSet<>(genres);
        
        // Enhanced genre mapping with scoring
        Map<String, Double> categoryScores = new HashMap<>();
        
        // Calculate scores for each category based on genre matches
        for (String genre : genreSet) {
            String lowerGenre = genre.toLowerCase();
            
            // Cardio: Energetic, danceable genres
            if (lowerGenre.contains("pop") || lowerGenre.contains("dance") || lowerGenre.contains("edm") ||
                lowerGenre.contains("house") || lowerGenre.contains("techno") || lowerGenre.contains("electronic") ||
                lowerGenre.contains("disco") || lowerGenre.contains("electropop") || lowerGenre.contains("synthpop") ||
                lowerGenre.contains("eurodance") || lowerGenre.contains("dancepop") || lowerGenre.contains("electro") ||
                lowerGenre.contains("trance") || lowerGenre.contains("club") || lowerGenre.contains("party")) {
                categoryScores.put("Cardio", categoryScores.getOrDefault("Cardio", 0.0) + 1.0);
            }
            
            // Strength Training: Powerful, intense genres
            if (lowerGenre.contains("rock") || lowerGenre.contains("metal") || lowerGenre.contains("punk") ||
                lowerGenre.contains("alternative") || lowerGenre.contains("grunge") || lowerGenre.contains("hard rock") ||
                lowerGenre.contains("heavy metal") || lowerGenre.contains("classic rock") || lowerGenre.contains("hardcore") ||
                lowerGenre.contains("post-rock") || lowerGenre.contains("progressive rock") || lowerGenre.contains("metalcore") ||
                lowerGenre.contains("thrash") || lowerGenre.contains("hard rock")) {
                categoryScores.put("Strength Training", categoryScores.getOrDefault("Strength Training", 0.0) + 1.0);
            }
            
            // HIIT: Intense, high-energy genres
            if (lowerGenre.contains("hip") || lowerGenre.contains("rap") || lowerGenre.contains("trap") ||
                lowerGenre.contains("drill") || lowerGenre.contains("r&b") || lowerGenre.contains("urban") ||
                lowerGenre.contains("gangsta") || lowerGenre.contains("east coast") || lowerGenre.contains("west coast") ||
                lowerGenre.contains("southern hip hop") || lowerGenre.contains("hardcore rap") || lowerGenre.contains("trap") ||
                lowerGenre.contains("drill") || lowerGenre.contains("grime")) {
                categoryScores.put("HIIT", categoryScores.getOrDefault("HIIT", 0.0) + 1.0);
            }
            
            // Yoga: Calm, relaxing genres
            if (lowerGenre.contains("indie") || lowerGenre.contains("folk") || lowerGenre.contains("acoustic") ||
                lowerGenre.contains("ambient") || lowerGenre.contains("chill") || lowerGenre.contains("lo-fi") ||
                lowerGenre.contains("jazz") || lowerGenre.contains("classical") || lowerGenre.contains("singer-songwriter") ||
                lowerGenre.contains("indie folk") || lowerGenre.contains("ambient pop") || lowerGenre.contains("dream pop") ||
                lowerGenre.contains("chillout") || lowerGenre.contains("new age") || lowerGenre.contains("meditation")) {
                categoryScores.put("Yoga", categoryScores.getOrDefault("Yoga", 0.0) + 1.0);
            }
        }
        
        // Get categories with score above threshold
        double threshold = 0.5;
        for (Map.Entry<String, Double> entry : categoryScores.entrySet()) {
            if (entry.getValue() >= threshold) {
                categories.add(entry.getKey());
            }
        }
        
        // If no strong matches, use the highest scoring category
        if (categories.isEmpty() && !categoryScores.isEmpty()) {
            String topCategory = categoryScores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .get()
                .getKey();
            categories.add(topCategory);
        }
        
        return categories;
    }
    

    private List<Map<String, Object>> ensureCategoryBalance(List<Map<String, Object>> artists) {
        // Count artists in each category
        Map<String, Integer> categoryCounts = new HashMap<>();
        for (Map<String, Object> artist : artists) {
            List<String> categories = (List<String>) artist.get("workoutCategories");
            for (String category : categories) {
                categoryCounts.put(category, categoryCounts.getOrDefault(category, 0) + 1);
            }
        }
        
        System.out.println("Initial category distribution: " + categoryCounts);
        
        // If any category has too few artists, reassign some
        int minPerCategory = 30; // Minimum artists per category
        String[] allCategories = {"Cardio", "Strength Training", "HIIT", "Yoga"};
        
        for (String category : allCategories) {
            int currentCount = categoryCounts.getOrDefault(category, 0);
            if (currentCount < minPerCategory) {
                // Find artists that can be reassigned to this category
                int needed = minPerCategory - currentCount;
                int reassigned = 0;
                
                for (Map<String, Object> artist : artists) {
                    List<String> currentCategories = (List<String>) artist.get("workoutCategories");
                    if (!currentCategories.contains(category) && reassigned < needed) {
                        // Add this category to the artist
                        currentCategories.add(category);
                        reassigned++;
                        categoryCounts.put(category, categoryCounts.getOrDefault(category, 0) + 1);
                    }
                }
            }
        }
        
        System.out.println("Final category distribution: " + categoryCounts);
        return artists;
    }

    private void printCategoryDistribution(List<Map<String, Object>> artists) {
        Map<String, Integer> categoryCounts = new HashMap<>();
        
        System.out.println("=== USER TOP ARTISTS WITH WORKOUT CATEGORIES ===");
        for (int i = 0; i < artists.size(); i++) {
            Map<String, Object> artist = artists.get(i);
            List<String> categories = (List<String>) artist.get("workoutCategories");
            
            for (String category : categories) {
                categoryCounts.put(category, categoryCounts.getOrDefault(category, 0) + 1);
            }
            
            System.out.println((i + 1) + ". " + artist.get("name") + 
                             " | Genres: " + artist.get("genres") +
                             " | Workout Categories: " + categories +
                             " | Popularity: " + artist.get("popularity"));
        }
        
        System.out.println("=== CATEGORY DISTRIBUTION ===");
        for (Map.Entry<String, Integer> entry : categoryCounts.entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue() + " artists");
        }
    }

    private List<String> determineWorkoutCategories(List<String> genres, String artistName) {
        if (genres == null || genres.isEmpty()) {
            // Try to get genres from Last.fm as fallback
            List<String> lastFmGenres = lastFmService.getArtistGenres(artistName);
            if (lastFmGenres != null && !lastFmGenres.isEmpty()) {
                genres = lastFmGenres;
                System.out.println("✅ Retrieved genres from Last.fm for " + artistName + ": " + genres);
            } else {
                // Try MusicBrainz as second fallback
                List<String> musicBrainzGenres = musicBrainzService.getArtistGenres(artistName);
                if (musicBrainzGenres != null && !musicBrainzGenres.isEmpty()) {
                    genres = musicBrainzGenres;
                    System.out.println("✅ Retrieved genres from MusicBrainz for " + artistName + ": " + genres);
                }
            }
        }
        
        if (genres == null || genres.isEmpty()) {
            // If still no genres, use popularity-based fallback
            return Arrays.asList(getFallbackCategoryBasedOnPopularity(artistName));
        }
        
        List<String> categories = new ArrayList<>();
        Set<String> genreSet = new HashSet<>(genres);
        
        // Enhanced genre mapping with more specific categories
        Map<String, Double> categoryScores = new HashMap<>();
        
        // Calculate scores for each category based on genre matches
        if (containsAny(genreSet, "pop", "dance", "edm", "house", "techno", "electronic", "disco", 
                       "electropop", "synthpop", "eurodance", "dancepop", "electro", "trance")) {
            categoryScores.put("Cardio", 2.0);
        }
        
        if (containsAny(genreSet, "rock", "metal", "hard rock", "punk", "alternative", "grunge",
                       "heavy metal", "hardcore", "post-rock", "progressive rock", "classic rock")) {
            categoryScores.put("Strength Training", 2.0);
        }
        
        if (containsAny(genreSet, "hip hop", "rap", "trap", "drill", "r&b", "urban",
                       "gangsta rap", "east coast hip hop", "west coast hip hop", "southern hip hop")) {
            categoryScores.put("HIIT", 2.0);
        }
        
        if (containsAny(genreSet, "indie", "folk", "acoustic", "ambient", "chill", "lo-fi", "jazz", "classical",
                       "singer-songwriter", "indie folk", "ambient pop", "dream pop", "chillout")) {
            categoryScores.put("Yoga", 2.0);
        }
        
        // Additional scoring based on specific genre keywords
        for (String genre : genreSet) {
            String lowerGenre = genre.toLowerCase();
            
            if (lowerGenre.contains("pop") || lowerGenre.contains("dance")) {
                categoryScores.put("Cardio", categoryScores.getOrDefault("Cardio", 0.0) + 1.0);
            }
            if (lowerGenre.contains("rock") || lowerGenre.contains("metal")) {
                categoryScores.put("Strength Training", categoryScores.getOrDefault("Strength Training", 0.0) + 1.0);
            }
            if (lowerGenre.contains("hip") || lowerGenre.contains("rap") || lowerGenre.contains("trap")) {
                categoryScores.put("HIIT", categoryScores.getOrDefault("HIIT", 0.0) + 1.0);
            }
            if (lowerGenre.contains("indie") || lowerGenre.contains("folk") || lowerGenre.contains("jazz") || 
                lowerGenre.contains("ambient") || lowerGenre.contains("chill")) {
                categoryScores.put("Yoga", categoryScores.getOrDefault("Yoga", 0.0) + 1.0);
            }
        }
        
        // Get top scoring categories (above threshold)
        double threshold = 1.5;
        for (Map.Entry<String, Double> entry : categoryScores.entrySet()) {
            if (entry.getValue() >= threshold) {
                categories.add(entry.getKey());
            }
        }
        
        // If no strong matches, use the highest scoring category
        if (categories.isEmpty() && !categoryScores.isEmpty()) {
            String topCategory = categoryScores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .get()
                .getKey();
            categories.add(topCategory);
        }
        
        // Final fallback if everything fails
        if (categories.isEmpty()) {
            categories.add(getFallbackCategoryBasedOnPopularity(artistName));
        }
        
        return categories;
    }

    private String getFallbackCategoryBasedOnPopularity(String artistName) {
        // Use artist name analysis as final fallback
        String lowerName = artistName.toLowerCase();
        
        if (lowerName.contains("pop") || lowerName.contains("dance") || lowerName.contains("dj")) {
            return "Cardio";
        } else if (lowerName.contains("rock") || lowerName.contains("metal")) {
            return "Strength Training";
        } else if (lowerName.contains("rap") || lowerName.contains("hip")) {
            return "HIIT";
        } else if (lowerName.contains("indie") || lowerName.contains("folk") || lowerName.contains("jazz")) {
            return "Yoga";
        }
        
        // Default distribution to ensure we have artists in all categories
        int hash = artistName.hashCode();
        int categoryIndex = Math.abs(hash) % 4;
        
        switch (categoryIndex) {
            case 0: return "Cardio";
            case 1: return "Strength Training";
            case 2: return "HIIT";
            case 3: return "Yoga";
            default: return "Cardio";
        }
    }

    private boolean containsAny(Set<String> genreSet, String... searchTerms) {
        for (String term : searchTerms) {
            for (String genre : genreSet) {
                if (genre.toLowerCase().contains(term.toLowerCase())) {
                    return true;
                }
            }
        }
        return false;
    }

    
    
    
    public String getArtistImageUrl(String accessToken, String artistId) {
        try {
            String url = "https://api.spotify.com/v1/artists/" + artistId;
            
            // Use pure Java HTTP client - no external dependencies
            java.net.URL spotifyUrl = new java.net.URL(url);
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) spotifyUrl.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Authorization", "Bearer " + accessToken);
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            
            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                // Read the response
                java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                // Parse the JSON response to extract image URL
                return parseImageUrlFromJson(response.toString());
            } else {
                System.out.println("Spotify API returned error code: " + responseCode);
            }
        } catch (java.net.SocketTimeoutException e) {
            System.out.println("Timeout getting artist image for ID " + artistId);
        } catch (Exception e) {
            System.out.println("Error getting artist image for ID " + artistId + ": " + e.getMessage());
        }
        return null;
    }

    
 // inside SpotifyService

 // --- helper: get recommendations by seed artists + feature params ---
 public List<ca.sheridancollege.jamsy.beans.Track> getRecommendationsByArtists(List<String> seedArtistIds, Map<String,String> featureParams, String accessToken, int limit) {
     if (seedArtistIds == null || seedArtistIds.isEmpty()) return Collections.emptyList();
     try {
         String seeds = seedArtistIds.stream().limit(5).map(id -> URLEncoder.encode(id, StandardCharsets.UTF_8)).collect(Collectors.joining(","));
         UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(SPOTIFY_API_URL + "/recommendations")
                 .queryParam("seed_artists", String.join(",", seedArtistIds))
                 .queryParam("limit", limit);

         if (featureParams != null) {
             for (Map.Entry<String,String> e : featureParams.entrySet()) {
                 builder.queryParam(e.getKey(), e.getValue());
             }
         }

         String url = builder.toUriString();
         HttpHeaders headers = new HttpHeaders();
         headers.set("Authorization", "Bearer " + accessToken);
         HttpEntity<String> entity = new HttpEntity<>(headers);
         ResponseEntity<Map> resp = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
         if (resp.getStatusCode() == HttpStatus.OK && resp.getBody() != null) {
             List<Map<String, Object>> items = (List<Map<String, Object>>) resp.getBody().get("tracks");
             List<ca.sheridancollege.jamsy.beans.Track> out = new ArrayList<>();
             if (items != null) {
                 for (Map<String,Object> t : items) {
                     out.add(mapSpotifyTrack(t));
                 }
             }
             return out;
         }
     } catch (Exception e) {
         System.out.println("❌ Error getRecommendationsByArtists: " + e.getMessage());
     }
     return Collections.emptyList();
 }

 // --- helper: artist top tracks (returns track IDs) ---
 public List<String> getArtistTopTracks(String artistId, String accessToken, int limit) {
	 checkRateLimit("artist-top-tracks");
     try {
         String url = SPOTIFY_API_URL + "/artists/" + artistId + "/top-tracks?market=US";
         HttpHeaders headers = new HttpHeaders();
         headers.set("Authorization", "Bearer " + accessToken);
         HttpEntity<String> entity = new HttpEntity<>(headers);
         ResponseEntity<Map> resp = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
         if (resp.getStatusCode() == HttpStatus.OK && resp.getBody() != null) {
             List<Map<String,Object>> tracks = (List<Map<String,Object>>) resp.getBody().get("tracks");
             if (tracks != null) {
                 return tracks.stream().map(t -> (String) t.get("id")).filter(Objects::nonNull).limit(limit).collect(Collectors.toList());
             }
         }
     } catch (Exception e) {
         System.out.println("❌ Error getArtistTopTracks: " + e.getMessage());
     }
     return Collections.emptyList();
 }

 // --- helper: get track details as Track object ---
 public ca.sheridancollege.jamsy.beans.Track getTrackById(String trackId, String accessToken) {
     try {
         String url = SPOTIFY_API_URL + "/tracks/" + trackId;
         HttpHeaders headers = new HttpHeaders();
         headers.set("Authorization", "Bearer " + accessToken);
         HttpEntity<String> entity = new HttpEntity<>(headers);
         ResponseEntity<Map> resp = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
         if (resp.getStatusCode() == HttpStatus.OK && resp.getBody() != null) {
             return mapSpotifyTrack(resp.getBody());
         }
     } catch (Exception e) {
         System.out.println("❌ Error getTrackById: " + e.getMessage());
     }
     return null;
 }

 // --- helper: related artists ids (for fallback expansion) ---
 public List<String> getRelatedArtistIds(List<String> artistIds, String accessToken) {
	    List<String> relatedIds = new ArrayList<>();
	    for (String artistId : artistIds) {
	        try {
	            // Call Spotify API
	            String url = "https://api.spotify.com/v1/artists/" + artistId + "/related-artists";
	            HttpHeaders headers = new HttpHeaders();
	            headers.set("Authorization", "Bearer " + accessToken);
	            HttpEntity<Void> entity = new HttpEntity<>(headers);

	            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
	            List<Map<String, Object>> artists = (List<Map<String, Object>>) response.getBody().get("artists");

	            // Collect IDs
	            for (Map<String, Object> artist : artists) {
	                relatedIds.add((String) artist.get("id"));
	            }

	            // sleep 200ms to avoid 429
	            Thread.sleep(200);

	        } catch (Exception e) {
	            System.out.println("Error getRelatedArtistIds: " + e.getMessage());
	        }
	    }
	    return relatedIds;
	}


 // --- helper: create playlist + add tracks (you already had these calls in WebController, but here are implementations) ---
 public String createPlaylist(String accessToken, String playlistName) {
     try {
         String userId = getSpotifyUserId(accessToken);
         String url = SPOTIFY_API_URL + "/users/" + userId + "/playlists";
         HttpHeaders headers = new HttpHeaders();
         headers.set("Authorization", "Bearer " + accessToken);
         headers.setContentType(MediaType.APPLICATION_JSON);
         Map<String,Object> body = new HashMap<>();
         body.put("name", playlistName);
         body.put("public", false);
         body.put("description", "Created from Mood/Workout discovery");
         HttpEntity<Map<String,Object>> entity = new HttpEntity<>(body, headers);
         ResponseEntity<Map> resp = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
         if (resp.getStatusCode() == HttpStatus.CREATED || resp.getStatusCode() == HttpStatus.OK) {
             return (String) resp.getBody().get("id");
         }
     } catch (Exception e) {
         System.out.println("❌ Error createPlaylist: " + e.getMessage());
     }
     return null;
 }

 public void addTracksToPlaylist(String accessToken, String playlistId, List<String> trackUris) {
     if (trackUris == null || trackUris.isEmpty()) return;
     try {
         String url = SPOTIFY_API_URL + "/playlists/" + playlistId + "/tracks";
         HttpHeaders headers = new HttpHeaders();
         headers.set("Authorization", "Bearer " + accessToken);
         headers.setContentType(MediaType.APPLICATION_JSON);
         Map<String,Object> body = new HashMap<>();
         body.put("uris", trackUris);
         HttpEntity<Map<String,Object>> entity = new HttpEntity<>(body, headers);
         restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
     } catch (Exception e) {
         System.out.println("❌ Error addTracksToPlaylist: " + e.getMessage());
     }
 }

 // --- helper: map raw Spotify track map -> your Track bean (used earlier) ---
 private ca.sheridancollege.jamsy.beans.Track mapSpotifyTrack(Map<String, Object> t) {
     ca.sheridancollege.jamsy.beans.Track track = new ca.sheridancollege.jamsy.beans.Track();
     if (t == null) return null;
     track.setId((String) t.get("id"));
     track.setName((String) t.get("name"));
     track.setPopularity(t.get("popularity") == null ? null : ((Number) t.get("popularity")).intValue());
     track.setPreviewUrl((String) t.get("preview_url"));

     // artists
     List<Map<String,Object>> artistsList = (List<Map<String,Object>>) t.get("artists");
     if (artistsList != null) {
         List<String> names = artistsList.stream()
                 .map(a -> (String) a.get("name"))
                 .collect(Collectors.toList());
         track.setArtists(names);
     }

     // album images
     Map<String,Object> album = (Map<String,Object>) t.get("album");
     if (album != null) {
         List<Map<String,Object>> images = (List<Map<String,Object>>) album.get("images");
         if (images != null && !images.isEmpty()) {
             // choose the first image (largest)
             track.setAlbumCover((String) images.get(0).get("url"));
         }
     }
     return track;
 }

    private String parseImageUrlFromJson(String jsonResponse) {
        try {
            // Simple JSON parsing - find the images array
            int imagesIndex = jsonResponse.indexOf("\"images\":");
            if (imagesIndex == -1) return null;
            
            int arrayStart = jsonResponse.indexOf("[", imagesIndex);
            int arrayEnd = jsonResponse.indexOf("]", arrayStart);
            String imagesArray = jsonResponse.substring(arrayStart, arrayEnd + 1);
            
            // Find the first URL in the images array
            int urlIndex = imagesArray.indexOf("\"url\":");
            if (urlIndex == -1) return null;
            
            int urlStart = imagesArray.indexOf("\"", urlIndex + 6) + 1;
            int urlEnd = imagesArray.indexOf("\"", urlStart);
            return imagesArray.substring(urlStart, urlEnd);
            
        } catch (Exception e) {
            System.out.println("Error parsing image URL from JSON: " + e.getMessage());
            return null;
        }
    }
    
    /*public List<String> getArtistTopTracks(String artistId, String accessToken, int limit) {
        try {
            String url = SPOTIFY_API_URL + "/artists/" + artistId + "/top-tracks?market=US";
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                List<Map<String, Object>> tracks = (List<Map<String, Object>>) responseBody.get("tracks");
                
                return tracks.stream()
                    .limit(limit)
                    .map(track -> (String) track.get("id"))
                    .collect(Collectors.toList());
            }
        } catch (Exception e) {
            System.out.println("Error getting artist top tracks: " + e.getMessage());
        }
        return Collections.emptyList();
    }*/
    
    public List<String> getRelatedArtistIds(String artistId, String accessToken, int limit) {
        String url = "https://api.spotify.com/v1/artists/" + artistId + "/related-artists";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

        List<String> ids = new ArrayList<>();
        if (response.getStatusCode().is2xxSuccessful()) {
            List<Map<String, Object>> artists = (List<Map<String, Object>>) response.getBody().get("artists");
            for (Map<String, Object> a : artists) {
                ids.add((String) a.get("id"));
                if (ids.size() >= limit) break;
            }
        }

        return ids;
    }

    public List<String> getRecommendations(List<String> seedArtists,
            List<String> seedTracks,
            String accessToken,
            int limit) {
String url = "https://api.spotify.com/v1/recommendations?"
+ "limit=" + limit
+ (seedArtists.isEmpty() ? "" : "&seed_artists=" + String.join(",", seedArtists))
+ (seedTracks.isEmpty() ? "" : "&seed_tracks=" + String.join(",", seedTracks));

HttpHeaders headers = new HttpHeaders();
headers.set("Authorization", "Bearer " + accessToken);
HttpEntity<Void> entity = new HttpEntity<>(headers);

RestTemplate restTemplate = new RestTemplate();
ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

List<String> trackIds = new ArrayList<>();
if (response.getStatusCode().is2xxSuccessful()) {
List<Map<String, Object>> tracks = (List<Map<String, Object>>) response.getBody().get("tracks");
for (Map<String, Object> t : tracks) {
trackIds.add((String) t.get("id"));
}
}

return trackIds;
}

    

    public List<Track> getRecommendationsBasedOnTracks(List<String> seedTracks, String accessToken, 
                                                      String workoutType, int limit) {
        try {
            // Convert workout type to Spotify parameters
            Map<String, Object> workoutParams = getWorkoutAudioFeatures(workoutType);
            
            String seedParam = String.join(",", seedTracks.subList(0, Math.min(5, seedTracks.size())));
            String url = SPOTIFY_API_URL + "/recommendations?limit=" + limit + 
                        "&seed_tracks=" + seedParam +
                        "&target_energy=" + workoutParams.get("energy") +
                        "&target_danceability=" + workoutParams.get("danceability") +
                        "&target_tempo=" + workoutParams.get("tempo");
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                List<Map<String, Object>> tracks = (List<Map<String, Object>>) responseBody.get("tracks");
                
                return tracks.stream()
                    .map(this::mapSpotifyTrack)
                    .collect(Collectors.toList());
            }
        } catch (Exception e) {
            System.out.println("Error getting recommendations: " + e.getMessage());
        }
        return Collections.emptyList();
    }

    private Map<String, Object> getWorkoutAudioFeatures(String workoutType) {
        Map<String, Object> params = new HashMap<>();
        switch (workoutType) {
            case "HIIT":
                params.put("energy", 0.9);
                params.put("danceability", 0.7);
                params.put("tempo", 140);
                break;
            case "Cardio":
                params.put("energy", 0.8);
                params.put("danceability", 0.8);
                params.put("tempo", 130);
                break;
            case "Strength Training":
                params.put("energy", 0.85);
                params.put("danceability", 0.5);
                params.put("tempo", 120);
                break;
            case "Yoga":
                params.put("energy", 0.4);
                params.put("danceability", 0.3);
                params.put("tempo", 90);
                break;
            default:
                params.put("energy", 0.7);
                params.put("danceability", 0.6);
                params.put("tempo", 120);
        }
        return params;
    }

    public String searchTrackId(String trackName, String artistName, String accessToken) {
        try {
            String query = "track:" + URLEncoder.encode(trackName, "UTF-8") + 
                          " artist:" + URLEncoder.encode(artistName, "UTF-8");
            String url = SPOTIFY_API_URL + "/search?q=" + query + "&type=track&limit=1";
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                Map<String, Object> tracks = (Map<String, Object>) responseBody.get("tracks");
                List<Map<String, Object>> items = (List<Map<String, Object>>) tracks.get("items");
                
                if (items != null && !items.isEmpty()) {
                    return (String) items.get(0).get("id");
                }
            }
        } catch (Exception e) {
            System.out.println("Error searching track ID: " + e.getMessage());
        }
        return null;
    }
    
    public String getTrackPreviewUrl(String trackId, String accessToken) {
        try {
            String url = SPOTIFY_API_URL + "/tracks/" + trackId;
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> trackData = response.getBody();
                return (String) trackData.get("preview_url");
            }
        } catch (Exception e) {
            System.out.println("Error getting track preview: " + e.getMessage());
        }
        return null;
    }
    
    

    public List<String> searchArtistTopTracks(String artistName, String accessToken, int limit) {
        try {
            String query = "artist:" + URLEncoder.encode(artistName, "UTF-8");
            String url = SPOTIFY_API_URL + "/search?q=" + query + "&type=track&limit=" + limit;
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                Map<String, Object> tracks = (Map<String, Object>) responseBody.get("tracks");
                List<Map<String, Object>> items = (List<Map<String, Object>>) tracks.get("items");
                
                return items.stream()
                    .map(track -> (String) track.get("id"))
                    .collect(Collectors.toList());
            }
        } catch (Exception e) {
            System.out.println("Error searching artist tracks: " + e.getMessage());
        }
        return Collections.emptyList();
    }

    public List<Track> getRecommendationsWithParams(List<String> seedTracks, Map<String, Object> params, 
                                                   String accessToken, int limit) {
        try {
            StringBuilder urlBuilder = new StringBuilder(SPOTIFY_API_URL + "/recommendations?limit=" + limit);
            
            // Add seed tracks
            if (!seedTracks.isEmpty()) {
                urlBuilder.append("&seed_tracks=").append(String.join(",", seedTracks));
            }
            
            // Add all parameters
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                if (entry.getKey().startsWith("target_") || entry.getKey().startsWith("min_") || 
                    entry.getKey().startsWith("max_")) {
                    urlBuilder.append("&").append(entry.getKey()).append("=").append(entry.getValue());
                }
            }
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(urlBuilder.toString(), HttpMethod.GET, entity, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                List<Map<String, Object>> tracks = (List<Map<String, Object>>) responseBody.get("tracks");
                
                return tracks.stream()
                    .map(this::mapSpotifyTrack)
                    .collect(Collectors.toList());
            }
        } catch (Exception e) {
            System.out.println("Error getting recommendations with params: " + e.getMessage());
        }
        return Collections.emptyList();
    }
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    

    public List<Track> getTopTracks(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        String url = "https://api.spotify.com/v1/me/top/tracks?limit=50";
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

        List<Track> tracks = extractTrackItems(response.getBody());
        System.out.println("Getting top tracks");
        return tracks;
    }

    private List<Track> extractTrackItems(Map<String, Object> response) {
        List<Track> tracks = new ArrayList<>();
        if (response == null || !response.containsKey("items"))
            return tracks;

        List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("items");
        for (Map<String, Object> item : items) {
            Track track = new Track();
            track.setName((String) item.get("name"));
            track.setExplicit((Boolean) item.get("explicit"));
            track.setIsrc(extractISRC(item));
            track.setPreviewUrl((String) item.get("preview_url"));

            // Artists
            List<Map<String, Object>> artistObjs = (List<Map<String, Object>>) item.get("artists");
            List<String> artistNames = new ArrayList<>();
            for (Map<String, Object> artist : artistObjs) {
                artistNames.add((String) artist.get("name"));
            }
            track.setArtists(artistNames);

            // Album cover
            Map<String, Object> album = (Map<String, Object>) item.get("album");
            if (album != null && album.containsKey("images")) {
                List<Map<String, Object>> images = (List<Map<String, Object>>) album.get("images");
                if (!images.isEmpty()) {
                    track.setAlbumCover((String) images.get(0).get("url"));
                }
            }

            tracks.add(track);
        }

        return tracks;
    }

    private String extractISRC(Map<String, Object> item) {
        try {
            Map<String, Object> externalIds = (Map<String, Object>) item.get("external_ids");
            return externalIds != null ? (String) externalIds.get("isrc") : null;
        } catch (Exception e) {
            return null;
        }
    }

    

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractSearchTracks(Map<String, Object> response) {
        List<Map<String, Object>> tracks = new ArrayList<>();

        if (response == null || !response.containsKey("tracks"))
            return tracks;

        Map<String, Object> tracksWrapper = (Map<String, Object>) response.get("tracks");
        if (tracksWrapper == null || !tracksWrapper.containsKey("items"))
            return tracks;

        tracks.addAll((List<Map<String, Object>>) tracksWrapper.get("items"));
        return tracks;
    }

    public List<Track> searchTrack(String query, String accessToken,
            boolean excludeExplicit, boolean excludeLoveSongs, boolean excludeFolk) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);

        HttpEntity<String> entity = new HttpEntity<>(headers);
        String url = SPOTIFY_API_URL + "/search?q=" + query + "&type=track&limit=50";

        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
        List<Map<String, Object>> rawTracks = extractSearchTracks(response.getBody());

        // Convert raw tracks to Track objects (optional but ideal)
        List<Track> tracks = rawTracks.stream()
                .map(this::mapToTrack)
                .toList();

        return tracks;
    }

    private Track mapToTrack(Map<String, Object> trackData) {
        Track track = new Track();
        track.setName((String) trackData.get("name"));
        track.setIsrc((String) ((Map<String, Object>) trackData.getOrDefault("external_ids", Map.of()))
                .getOrDefault("isrc", ""));

        // Get preview URL
        track.setPreviewUrl((String) trackData.get("preview_url"));

        // Get album cover
        Map<String, Object> album = (Map<String, Object>) trackData.get("album");
        if (album != null && album.get("images") instanceof List<?> images && !images.isEmpty()) {
            Map<String, Object> firstImage = (Map<String, Object>) images.get(0);
            track.setAlbumCover((String) firstImage.get("url"));
        }

        // Get artist names
        List<Map<String, Object>> artistList = (List<Map<String, Object>>) trackData.get("artists");
        if (artistList != null) {
            List<String> artistNames = artistList.stream()
                    .map(artist -> (String) artist.get("name"))
                    .toList();
            track.setArtists(artistNames);
        }

        // Set genres (optional; if your API includes them or from fallback)
        track.setGenres((List<String>) trackData.getOrDefault("genres", List.of()));

        return track;
    }

    public Optional<Track> getCurrentlyPlayingTrack(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        String url = "https://api.spotify.com/v1/me/player/currently-playing";
        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> item = (Map<String, Object>) response.getBody().get("item");
                if (item != null) {
                    return Optional.of(mapToTrack(item));
                }
            }
        } catch (Exception e) {
            // fallback
            System.out.println("⚠️ Error fetching currently playing, falling back to recently played.");
        }

        // Fallback: recently played
        url = "https://api.spotify.com/v1/me/player/recently-played?limit=1";
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
        List<Map<String, Object>> items = (List<Map<String, Object>>) response.getBody().get("items");
        if (items != null && !items.isEmpty()) {
            Map<String, Object> track = (Map<String, Object>) items.get(0).get("track");
            return Optional.of(mapToTrack(track));
        }

        return Optional.empty();
    }

    public Track getRandomSpotifyTrack(String accessToken) {
        List<Track> allTracks = getTracksFromMultipleSources(accessToken);
        if (allTracks.isEmpty())
            return null;
        Collections.shuffle(allTracks);
        return allTracks.get(0);
    }

    public List<Track> getTracksFromMultipleSources(String accessToken) {
        List<Track> allTracks = new ArrayList<>();

        // Add top tracks
        System.out.println("Calling get top tracks which has filter method inside");
        allTracks.addAll(getTopTracks(accessToken));

        // Add recently played (optional fallback if not present)
        getRecentlyPlayedTracks(accessToken).ifPresent(allTracks::addAll);

        // Shuffle to ensure randomness
        Collections.shuffle(allTracks);

        return allTracks.stream().limit(20).collect(Collectors.toList());
    }

    public List<Track> mergeAndShuffleTracks(String accessToken) {
        List<Track> tracks = new ArrayList<>();

        // Get tracks from Spotify top tracks
        tracks.addAll(getTopTracks(accessToken));


// Shuffle the tracks
        Collections.shuffle(tracks);

                return tracks.stream().limit(20).collect(Collectors.toList());
    }

    public Optional<List<Track>> getRecentlyPlayedTracks(String accessToken) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            String url = "https://api.spotify.com/v1/me/player/recently-played?limit=20";
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            List<Map<String, Object>> items = (List<Map<String, Object>>) response.getBody().get("items");

            List<Track> tracks = new ArrayList<>();
            for (Map<String, Object> item : items) {
                Map<String, Object> trackData = (Map<String, Object>) item.get("track");
                if (trackData != null) {
                    tracks.add(mapToTrack(trackData));
                }
            }

            return Optional.of(tracks);
        } catch (Exception e) {
            System.out.println("⚠️ Failed to fetch recently played tracks: " + e.getMessage());
            return Optional.empty();
        }
    }
    
//  Playlist templates added
    public List<Track> getRecommendationsFromTemplate(PlaylistTemplate template, String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        // Build Spotify recommendations URL
        String url = SPOTIFY_API_URL + "/recommendations"
                + "?limit=50"
                + "&seed_genres=" + String.join(",", template.getSeedGenres())
                + "&target_energy=" + (template.getTargetEnergy() / 100.0) // Spotify expects 0–1
                + "&target_tempo=" + template.getTargetTempo();

        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

        List<Track> tracks = new ArrayList<>();
        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            List<Map<String, Object>> items = (List<Map<String, Object>>) response.getBody().get("tracks");
            if (items != null) {
                for (Map<String, Object> item : items) {
                    tracks.add(mapToTrack(item));
                }
            }
        }

        // Shuffle and limit
        Collections.shuffle(tracks);
        return tracks.stream().limit(20).collect(Collectors.toList());
    }
    
    
    
    /**
     * Fetch a list of artists that match a given 'mood' mapping.
     * Returns a list of maps with keys: "id", "name", "image" (image may be null).
     */
    public List<Map<String, String>> getTopArtistsForMood(String accessToken, String mood, int limit) {
        if (accessToken == null || mood == null) {
            return Collections.emptyList();
        }

        // Simple mood -> genre/keyword mapping. Tweak to suit your product.
        Map<String, String[]> moodToGenres = new HashMap<>();
        moodToGenres.put("Energetic", new String[]{"dance", "electronic", "pop"});
        moodToGenres.put("Powerful", new String[]{"hip-hop", "rock", "metal"});
        moodToGenres.put("Intense", new String[]{"edm", "drum and bass", "electronic"});
        moodToGenres.put("Calm", new String[]{"ambient", "classical", "acoustic"});
        moodToGenres.put("Focused", new String[]{"instrumental", "indie", "alternative"});
        moodToGenres.put("Upbeat", new String[]{"pop", "disco", "funk"});
        moodToGenres.put("Motivated", new String[]{"rock", "pop", "electronic"});
        moodToGenres.put("Chill", new String[]{"lofi", "chill", "soul"});
        moodToGenres.put("Aggressive", new String[]{"metal", "hardcore", "rap"});
        // default
        String[] genres = moodToGenres.getOrDefault(mood, new String[]{"pop", "indie", "electronic"});

        // Build a search query like: genre:"dance" OR genre:"electronic"
        String query = Arrays.stream(genres)
                .map(g -> "genre:\"" + g + "\"")
                .collect(Collectors.joining(" OR "));

        try {
            String encodedQ = URLEncoder.encode(query, StandardCharsets.UTF_8.toString());
            String url = SPOTIFY_API_URL + "/search?type=artist&limit=" + limit + "&q=" + encodedQ;

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            Map<String, Object> body = response.getBody();
            if (body == null || !body.containsKey("artists")) {
                return Collections.emptyList();
            }

            Map<String, Object> artistsObj = (Map<String, Object>) body.get("artists");
            List<Map<String, Object>> items = (List<Map<String, Object>>) artistsObj.get("items");
            List<Map<String, String>> result = new ArrayList<>();

            if (items != null) {
                for (Map<String, Object> item : items) {
                    Map<String, String> artistMap = new HashMap<>();
                    artistMap.put("id", (String) item.get("id"));
                    artistMap.put("name", (String) item.get("name"));

                    // get first image if present
                    Object imagesObj = item.get("images");
                    if (imagesObj instanceof List) {
                        List<Map<String, Object>> images = (List<Map<String, Object>>) imagesObj;
                        if (!images.isEmpty()) {
                            artistMap.put("image", (String) images.get(0).get("url"));
                        } else {
                            artistMap.put("image", null);
                        }
                    } else {
                        artistMap.put("image", null);
                    }
                    result.add(artistMap);
                }
            }

            return result;
        } catch (Exception e) {
            // log and return empty - don't break user flow
            e.printStackTrace();
            return Collections.emptyList();
        }
    }


}
