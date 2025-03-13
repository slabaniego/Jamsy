package ca.sheridancollege.jamsy.services;

import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SpotifyService {

    private static final String CLIENT_ID = "a889ff03eaa84050b3d323debcf1498f";
    private static final String CLIENT_SECRET = "1b8849fc75db4306bf791e3617e7a195";
    private static final String DEEZER_API_URL = "https://api.deezer.com";

    public String getUserAccessToken(String code) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        String body = "grant_type=authorization_code&code=" + code +
                      "&redirect_uri=http://localhost:8080/login/oauth2/code/spotify&client_id=" + CLIENT_ID + "&client_secret=" + CLIENT_SECRET;

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

        // Fetch Liked Songs
        String likedSongsUrl = "https://api.spotify.com/v1/me/tracks?limit=50";
        while (likedSongsUrl != null) {
            ResponseEntity<Map> likedSongsResponse = restTemplate.exchange(likedSongsUrl, HttpMethod.GET, entity, Map.class);
            allTracks.addAll(extractTracks(likedSongsResponse.getBody(), "liked_songs"));

            String nextPageUrl = (String) likedSongsResponse.getBody().get("next");
            likedSongsUrl = nextPageUrl;
        }

        // Fetch Top Tracks
        String topTracksUrl = "https://api.spotify.com/v1/me/top/tracks?limit=50&time_range=medium_term";
        ResponseEntity<Map> topTracksResponse = restTemplate.exchange(topTracksUrl, HttpMethod.GET, entity, Map.class);
        allTracks.addAll(extractTracks(topTracksResponse.getBody(), "top_tracks"));

        // Fetch Recently Played Tracks
        int recentlyPlayedLimit = 200;
        int batchSize = 50;
        String recentlyPlayedUrl = "https://api.spotify.com/v1/me/player/recently-played?limit=" + batchSize;
        List<Map<String, Object>> recentlyPlayedTracks = new ArrayList<>();

        while (recentlyPlayedTracks.size() < recentlyPlayedLimit) {
            ResponseEntity<Map> recentlyPlayedResponse = restTemplate.exchange(recentlyPlayedUrl, HttpMethod.GET, entity, Map.class);
            List<Map<String, Object>> batchTracks = extractTracks(recentlyPlayedResponse.getBody(), "recently_played");
            recentlyPlayedTracks.addAll(batchTracks);

            if (batchTracks.size() < batchSize) {
                break;
            }

            String lastTimestamp = (String) batchTracks.get(batchTracks.size() - 1).get("played_at");
            recentlyPlayedUrl = "https://api.spotify.com/v1/me/player/recently-played?limit=" + batchSize + "&before=" + lastTimestamp;
        }

        allTracks.addAll(recentlyPlayedTracks);

        return allTracks;
    }

    private List<Map<String, Object>> extractTracks(Map<String, Object> response, String source) {
        List<Map<String, Object>> tracks = new ArrayList<>();
        if (response == null || response.get("items") == null) {
            return tracks;
        }

        List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("items");
        for (Map<String, Object> item : items) {
            if (item == null || item.get("track") == null) {
                continue;
            }

            Map<String, Object> track = (Map<String, Object>) item.get("track");
            if (track == null) {
                continue;
            }

            track.put("source", source);

            if (source.equals("recently_played")) {
                track.put("played_at", item.get("played_at"));
            }

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
                continue;
            }

            Map<String, Object> externalIds = (Map<String, Object>) track.get("external_ids");
            String isrc = externalIds != null ? (String) externalIds.getOrDefault("isrc", track.get("id").toString()) : track.get("id").toString();
            uniqueTracks.put(isrc, track);
        }

        // Convert unique tracks back to a list
        List<Map<String, Object>> selectedTracks = new ArrayList<>(uniqueTracks.values());

        // Shuffle the tracks to ensure randomness
        Collections.shuffle(selectedTracks);

        // Filter tracks with preview URLs
        List<Map<String, Object>> tracksWithPreviews = new ArrayList<>();
        for (Map<String, Object> track : selectedTracks) {
            String previewUrl = getPreviewUrl(track);
            if (previewUrl != null) {
                track.put("preview_url", previewUrl);
                tracksWithPreviews.add(track);
            }

            // Stop once we have enough tracks with previews
            if (tracksWithPreviews.size() >= limit) {
                break;
            }
        }

        return tracksWithPreviews;
    }

    private String getPreviewUrl(Map<String, Object> track) {
        // Try Spotify preview URL first
        String previewUrl = (String) track.get("preview_url");

        // If Spotify preview URL is not available, try Deezer
        if (previewUrl == null || previewUrl.isEmpty()) {
            String trackName = (String) track.get("name");
            List<Map<String, Object>> artists = (List<Map<String, Object>>) track.get("artists");
            String artistName = artists.stream()
                    .map(artist -> (String) artist.get("name"))
                    .collect(Collectors.joining(", "));

            previewUrl = getDeezerPreviewUrl(trackName, artistName);
        }

        return previewUrl;
    }

    /**
     * Fetch a Deezer preview URL for a given track name and artist.
     */
    private String getDeezerPreviewUrl(String trackName, String artistName) {
        RestTemplate restTemplate = new RestTemplate();
        String query = trackName + " " + artistName;
        String url = DEEZER_API_URL + "/search?q=" + query;

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, null, Map.class);
            Map<String, Object> responseBody = response.getBody();

            if (responseBody != null && responseBody.containsKey("data")) {
                List<Map<String, Object>> tracks = (List<Map<String, Object>>) responseBody.get("data");
                if (!tracks.isEmpty()) {
                    // Return the preview URL of the first matching track
                    return (String) tracks.get(0).get("preview");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null; // No preview URL found
    }

    public List<Map<String, Object>> enrichTrackData(List<Map<String, Object>> tracks, String accessToken) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        // Fetch artist details
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

            // Add preview URL
            enrichedTrack.put("preview_url", track.get("preview_url"));
            enrichedTracks.add(enrichedTrack);
        }

        return enrichedTracks;
    }

    private String detectLanguage(String text) {
        return text.matches(".*[a-zA-Z].*") ? "English" : "Unknown";
    }
}