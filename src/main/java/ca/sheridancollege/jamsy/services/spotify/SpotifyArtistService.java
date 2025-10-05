package ca.sheridancollege.jamsy.services.spotify;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import ca.sheridancollege.jamsy.services.DeezerService;
import ca.sheridancollege.jamsy.services.LastFmService;
import ca.sheridancollege.jamsy.services.MusicBrainzService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SpotifyArtistService {
	
	private final RestTemplate restTemplate;
	private final SpotifyApiClient spotifyApiClient;
	private final Map<String, List<Map<String, Object>>> artistCache = new ConcurrentHashMap<>();
	private final LastFmService lastFmService;
	private SpotifyTrackService spotifyTrackService;
	private MusicBrainzService musicBrainzService;
	
	@Autowired
	 public SpotifyArtistService(
			 LastFmService last, 
			 RestTemplate restTemplate,
			 SpotifyTrackService spotifyTrackService,
			 MusicBrainzService musicBrainzService,
			 SpotifyApiClient spotifyApiClient
		 ) {
	        this.lastFmService = last;
	        this.restTemplate = restTemplate;
	        this.spotifyTrackService = spotifyTrackService;
	        this.musicBrainzService = musicBrainzService;
	        this.spotifyApiClient = spotifyApiClient;
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
	
	public List<Map<String, Object>> getArtistNames(List<String> artistIds, String accessToken) {
	    List<Map<String, Object>> artistDetails = new ArrayList<>();
	    for (String artistId : artistIds) {
	        try {
	            String url = "https://api.spotify.com/v1/artists/" + artistId;
	            HttpHeaders headers = new HttpHeaders();
	            headers.set("Authorization", "Bearer " + accessToken);
	            HttpEntity<String> entity = new HttpEntity<>(headers);

	            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
	            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
	                Map<String, Object> body = response.getBody();

	                Map<String, Object> artistInfo = new HashMap<>();
	                artistInfo.put("id", body.get("id"));
	                artistInfo.put("name", body.get("name"));
	                artistInfo.put("genres", body.get("genres"));

	                // get image if exists
	                List<Map<String, Object>> images = (List<Map<String, Object>>) body.get("images");
	                if (images != null && !images.isEmpty()) {
	                    artistInfo.put("imageUrl", images.get(0).get("url"));
	                }

	                artistDetails.add(artistInfo);
	            }
	        } catch (Exception e) {
	            System.out.println("Error getting artist details for " + artistId + ": " + e.getMessage());
	        }
	    }
	    return artistDetails;
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
	
	public List<String> getArtistTopTracks(String artistId, String accessToken, int limit) {
		 
		spotifyApiClient.checkRateLimit("artist-top-tracks");
	     try {
	         String url = spotifyApiClient.SPOTIFY_API_URL + "/artists/" + artistId + "/top-tracks?market=US";
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
                String url = spotifyApiClient.SPOTIFY_API_URL + "/me/top/artists?limit=" + batchSize + "&offset=" + offset + "&time_range=long_term";
                
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
                            Map<String, Double> features = spotifyTrackService.getArtistAudioFeatures(accessToken, artistId);
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
}
