package ca.sheridancollege.jamsy.services;

import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.*;

// Add this import
import java.util.stream.Collectors;

@Service
public class SpotifyService {

    private static final String CLIENT_ID = "a889ff03eaa84050b3d323debcf1498f";
    private static final String CLIENT_SECRET = "1b8849fc75db4306bf791e3617e7a195";

    public String getUserAccessToken(String code) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        String body = "grant_type=authorization_code&code=" + code +
                      "&redirect_uri=http://localhost:8080/callback&client_id=" + CLIENT_ID + "&client_secret=" + CLIENT_SECRET;

        HttpEntity<String> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.exchange("https://accounts.spotify.com/api/token", HttpMethod.POST, entity, Map.class);

        return (String) response.getBody().get("access_token");
    }

    public List<Map<String, Object>> getTracksFromMultipleSources(String accessToken) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        List<Map<String, Object>> allTracks = new ArrayList<>();

        // Get the account creation date (proxy)
        String accountCreationDate = getAccountCreationDate(accessToken);
        System.out.println("Account Creation Date (Proxy): " + accountCreationDate);

        // 1. Fetch ALL Liked Songs (with pagination)
        String likedSongsUrl = "https://api.spotify.com/v1/me/tracks?limit=50";
        while (likedSongsUrl != null) {
            ResponseEntity<Map> likedSongsResponse = restTemplate.exchange(likedSongsUrl, HttpMethod.GET, entity, Map.class);
            allTracks.addAll(extractTracks(likedSongsResponse.getBody(), accountCreationDate, "liked_songs"));

            // Check if there are more pages
            String nextPageUrl = (String) likedSongsResponse.getBody().get("next");
            likedSongsUrl = nextPageUrl;
        }

        // 2. Fetch Top Tracks (limit: 50)
        String topTracksUrl = "https://api.spotify.com/v1/me/top/tracks?limit=50&time_range=medium_term";
        ResponseEntity<Map> topTracksResponse = restTemplate.exchange(topTracksUrl, HttpMethod.GET, entity, Map.class);
        allTracks.addAll(extractTracks(topTracksResponse.getBody(), accountCreationDate, "top_tracks"));

        // 3. Fetch Recently Played Tracks (in batches of 50)
        int recentlyPlayedLimit = 200; // Desired limit
        int batchSize = 50; // Maximum allowed by Spotify API
        String recentlyPlayedUrl = "https://api.spotify.com/v1/me/player/recently-played?limit=" + batchSize;
        List<Map<String, Object>> recentlyPlayedTracks = new ArrayList<>();

        while (recentlyPlayedTracks.size() < recentlyPlayedLimit) {
            ResponseEntity<Map> recentlyPlayedResponse = restTemplate.exchange(recentlyPlayedUrl, HttpMethod.GET, entity, Map.class);
            List<Map<String, Object>> batchTracks = extractTracks(recentlyPlayedResponse.getBody(), accountCreationDate, "recently_played");
            recentlyPlayedTracks.addAll(batchTracks);

            // Check if there are more tracks to fetch
            if (batchTracks.size() < batchSize) {
                break; // No more tracks available
            }

            // Get the timestamp of the last track in the batch for pagination
            String lastTimestamp = (String) batchTracks.get(batchTracks.size() - 1).get("played_at");
            recentlyPlayedUrl = "https://api.spotify.com/v1/me/player/recently-played?limit=" + batchSize + "&before=" + lastTimestamp;
        }

        // Add the fetched recently played tracks to the main list
        allTracks.addAll(recentlyPlayedTracks);

        return allTracks;
    }

    public String getAccountCreationDate(String accessToken) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        // Fetch the first 50 saved tracks
        String likedSongsUrl = "https://api.spotify.com/v1/me/tracks?limit=50";
        ResponseEntity<Map> likedSongsResponse = restTemplate.exchange(likedSongsUrl, HttpMethod.GET, entity, Map.class);
        Map<String, Object> responseBody = likedSongsResponse.getBody();

        if (responseBody == null || responseBody.get("items") == null) {
            return null; // Return null if response or items is null
        }

        List<Map<String, Object>> items = (List<Map<String, Object>>) responseBody.get("items");

        // Find the oldest track based on the "added_at" field
        String oldestDate = null;
        for (Map<String, Object> item : items) {
            if (item == null) {
                continue; // Skip null items
            }

            String addedAt = (String) item.get("added_at");
            if (addedAt != null && (oldestDate == null || addedAt.compareTo(oldestDate) < 0)) {
                oldestDate = addedAt;
            }
        }

        return oldestDate; // Return the oldest date as a proxy for account creation date
    }
    
    private List<Map<String, Object>> extractTracks(Map<String, Object> response, String accountCreationDate, String source) {
        List<Map<String, Object>> tracks = new ArrayList<>();
        if (response == null || response.get("items") == null) {
            return tracks; // Return empty list if response or items is null
        }

        List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("items");
        for (Map<String, Object> item : items) {
            if (item == null || item.get("track") == null) {
                continue; // Skip null items or tracks
            }

            Map<String, Object> track = (Map<String, Object>) item.get("track");
            if (track == null) {
                continue; // Skip null tracks
            }

            // Add the source field to the track
            track.put("source", source);

            // Add the played_at field for recently played tracks
            if (source.equals("recently_played")) {
                track.put("played_at", item.get("played_at"));
            }

            // Add the track to the list
            tracks.add(track);
        }

        return tracks;
    }

    public List<Map<String, Object>> getNonPopularTracks(String accessToken, int maxPopularity, int limit) {
        List<Map<String, Object>> allTracks = getTracksFromMultipleSources(accessToken);

        // Filter non-popular tracks
        List<Map<String, Object>> nonPopularTracks = allTracks.stream()
                .filter(track -> track != null && track.get("popularity") != null && (int) track.get("popularity") < maxPopularity)
                .collect(Collectors.toList());

        // Remove duplicates using ISRC or track ID
        Map<String, Map<String, Object>> uniqueTracks = new HashMap<>();
        for (Map<String, Object> track : nonPopularTracks) {
            if (track == null) {
                continue; // Skip null tracks
            }

            Map<String, Object> externalIds = (Map<String, Object>) track.get("external_ids");
            String isrc = externalIds != null ? (String) externalIds.getOrDefault("isrc", track.get("id").toString()) : track.get("id").toString();
            uniqueTracks.put(isrc, track);
        }

        // Convert unique tracks back to a list
        List<Map<String, Object>> selectedTracks = new ArrayList<>(uniqueTracks.values());

        // Shuffle the tracks to ensure randomness
        Collections.shuffle(selectedTracks);

        // Limit the number of tracks to return
        return selectedTracks.stream()
                .limit(limit)
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> enrichTrackData(List<Map<String, Object>> tracks, String accessToken) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        // Get artist details in batches
        Set<String> artistIds = new HashSet<>();
        for (Map<String, Object> track : tracks) {
            List<Map<String, Object>> artists = (List<Map<String, Object>>) track.get("artists");
            for (Map<String, Object> artist : artists) {
                artistIds.add((String) artist.get("id"));
            }
        }

        Map<String, Map<String, Object>> artistDetails = new HashMap<>();
        List<String> artistIdList = new ArrayList<>(artistIds);
        for (int i = 0; i < artistIdList.size(); i += 50) {
            List<String> batch = artistIdList.subList(i, Math.min(i + 50, artistIdList.size()));
            String artistUrl = "https://api.spotify.com/v1/artists?ids=" + String.join(",", batch);
            ResponseEntity<Map> response = restTemplate.exchange(artistUrl, HttpMethod.GET, entity, Map.class);
            List<Map<String, Object>> artists = (List<Map<String, Object>>) response.getBody().get("artists");
            for (Map<String, Object> artist : artists) {
                artistDetails.put((String) artist.get("id"), artist);
            }
        }

        // Enrich track data
        List<Map<String, Object>> enrichedTracks = new ArrayList<>();
        for (Map<String, Object> track : tracks) {
            Map<String, Object> enrichedTrack = new HashMap<>();
            enrichedTrack.put("name", track.get("name"));

            // Extract artists
            List<Map<String, Object>> artists = (List<Map<String, Object>>) track.get("artists");
            enrichedTrack.put("artists", artists.stream()
                    .map(artist -> (String) artist.get("name"))
                    .collect(Collectors.toList()));

            // Extract ISRC
            Map<String, Object> externalIds = (Map<String, Object>) track.get("external_ids");
            enrichedTrack.put("isrc", externalIds != null ? (String) externalIds.getOrDefault("isrc", "N/A") : "N/A");

            // Extract genres
            List<String> genres = new ArrayList<>();
            for (Map<String, Object> artist : artists) {
                Map<String, Object> artistDetail = artistDetails.get(artist.get("id"));
                if (artistDetail != null) {
                    List<String> artistGenres = (List<String>) artistDetail.get("genres");
                    if (artistGenres != null) {
                        genres.addAll(artistGenres);
                    }
                }
            }
            enrichedTrack.put("genres", genres);

            // Extract popularity
            enrichedTrack.put("popularity", track.get("popularity"));

            // Detect language
            enrichedTrack.put("language", detectLanguage((String) track.get("name")));

            // Extract album cover
            Map<String, Object> album = (Map<String, Object>) track.get("album");
            List<Map<String, Object>> images = (List<Map<String, Object>>) album.get("images");
            enrichedTrack.put("album_cover", images != null && !images.isEmpty() ? (String) images.get(0).get("url") : "No image available");

            enrichedTracks.add(enrichedTrack);
        }

        return enrichedTracks;
    }

    private String detectLanguage(String text) {
        // Simplified language detection (you can integrate a proper language detection library)
        return text.matches(".*[a-zA-Z].*") ? "English" : "Unknown";
    }
}