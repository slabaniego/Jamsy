package ca.sheridancollege.jamsy.services.spotify;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import ca.sheridancollege.jamsy.beans.Track;

@Service
public class SpotifyTrackService {
	
	private final SpotifyApiClient spotifyClientService = new SpotifyApiClient();
	private final RestTemplate restTemplate = new RestTemplate();
	
	public Track getTrackById(String trackId, String accessToken) {
	     try {
	         String url = spotifyClientService.SPOTIFY_API_URL + "/tracks/" + trackId;
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
	
	public String getTrackPreviewUrl(String trackId, String accessToken) {
        try {
            String url = spotifyClientService.SPOTIFY_API_URL + "/tracks/" + trackId;
            
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
	
	private String cleanTrackName(String raw) {
        return raw.replaceAll("\\(.*?\\)", "")  // remove (feat. SZA), (Remaster), etc.
                  .replaceAll("-.*", "")        // remove " - Journey" if you’re already passing artist separately
                  .trim();
    }

	/**
     * Search Spotify for a track ID by track name and artist name.
     * If ID is missing, this can be used to populate it.
     */
    public String searchTrackId(String trackName, String artistName, String accessToken) {
        if (cleanTrackName(trackName) == null || artistName == null || cleanTrackName(trackName).isBlank() || artistName.isBlank()) {
            System.out.println("⚠️ searchTrackId called with invalid params: " + cleanTrackName(trackName) + " / " + artistName);
            return null;
        }

        try {
            String query = "track:" + URLEncoder.encode(cleanTrackName(trackName), StandardCharsets.UTF_8) +
                           " artist:" + URLEncoder.encode(artistName, StandardCharsets.UTF_8);

            String url = spotifyClientService.SPOTIFY_API_URL + "/search?q=" + query + "&type=track&limit=1";

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                Map<String, Object> tracks = (Map<String, Object>) responseBody.get("tracks");
                List<Map<String, Object>> items = (List<Map<String, Object>>) tracks.get("items");

                if (items != null && !items.isEmpty()) {
                    Map<String, Object> trackData = items.get(0);
                    String spotifyId = (String) trackData.get("id");

                    System.out.println("✅ Found Spotify ID for " + cleanTrackName(trackName) + " - " + artistName + ": " + spotifyId);
                    return spotifyId;
                } else {
                    System.out.println("❌ No matching track found on Spotify for: " + cleanTrackName(trackName) + " - " + artistName);
                }
            }
        } catch (Exception e) {
            System.out.println("❌ Error searching track ID for " + cleanTrackName(trackName) + " - " + artistName + ": " + e.getMessage());
        }

        return null;
    }

    public List<Track> searchTrack(String query, String accessToken,
            boolean excludeExplicit, boolean excludeLoveSongs, boolean excludeFolk) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);

        HttpEntity<String> entity = new HttpEntity<>(headers);
        String url = spotifyClientService.SPOTIFY_API_URL + "/search?q=" + query + "&type=track&limit=50";

        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
        List<Map<String, Object>> rawTracks = extractSearchTracks(response.getBody());

        // Convert raw tracks to Track objects (optional but ideal)
        List<Track> tracks = rawTracks.stream()
                .map(this::mapToTrack)
                .toList();

        return tracks;
    }
    
    // Convenience overload
    public List<Track> searchTrack(String query, String accessToken) {
        return searchTrack(query, accessToken, false, false, false);
    }

    public List<String> searchArtistTopTracks(String artistName, String accessToken, int limit) {
        try {
            String query = "artist:" + URLEncoder.encode(artistName, "UTF-8");
            String url = spotifyClientService.SPOTIFY_API_URL + "/search?q=" + query + "&type=track&limit=" + limit;
            
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

    
 // --- helper: map raw Spotify track map -> your Track bean (used earlier) ---
    private Track mapSpotifyTrack(Map<String, Object> t) {
    	Track track = new Track();
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
    
    public Track mapToTrack(Map<String, Object> trackData) {
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
    
    // Helper method 
    private String extractISRC(Map<String, Object> item) {
        try {
            Map<String, Object> externalIds = (Map<String, Object>) item.get("external_ids");
            return externalIds != null ? (String) externalIds.get("isrc") : null;
        } catch (Exception e) {
            return null;
        }
    }
    
    public List<Track> extractTrackItems(Map<String, Object> response) {
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
    
    
	/* Audio Features */
    public Map<String, Double> getArtistAudioFeatures(String accessToken, String artistId) {
        try {
            // Get artist's top tracks
            String topTracksUrl = spotifyClientService.SPOTIFY_API_URL + "/artists/" + artistId + "/top-tracks?market=US";
            
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
            String audioFeaturesUrl = spotifyClientService.SPOTIFY_API_URL + "/audio-features?ids=" + trackIdsParam;
            
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
                String topTracksUrl = spotifyClientService.SPOTIFY_API_URL + "/artists/" + artistId + "/top-tracks?market=US";
                
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
}
