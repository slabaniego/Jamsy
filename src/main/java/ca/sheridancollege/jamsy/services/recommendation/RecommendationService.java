package ca.sheridancollege.jamsy.services.recommendation;

import ca.sheridancollege.jamsy.beans.PlaylistTemplate;
import ca.sheridancollege.jamsy.beans.Track;
import ca.sheridancollege.jamsy.services.spotify.SpotifyApiClient;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RecommendationService {

    private static final String SPOTIFY_API_URL = "https://api.spotify.com/v1";
    private final SpotifyApiClient spotifyApiClient;

    public RecommendationService(SpotifyApiClient spotifyApiClient) {
        this.spotifyApiClient = spotifyApiClient;
    }

    /**
     * Get recommendations by seed artist IDs.
     */
    public List<Track> getRecommendationsByArtists(
            List<String> seedArtistIds,
            Map<String, String> featureParams,
            String accessToken,
            int limit
    ) {
        if (seedArtistIds == null || seedArtistIds.isEmpty()) return Collections.emptyList();

        String seeds = seedArtistIds.stream()
                .limit(5)
                .map(id -> URLEncoder.encode(id, StandardCharsets.UTF_8))
                .collect(Collectors.joining(","));

        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(SPOTIFY_API_URL + "/recommendations")
                .queryParam("seed_artists", seeds)
                .queryParam("limit", limit);

        if (featureParams != null) {
            featureParams.forEach(builder::queryParam);
        }

        Map response = spotifyApiClient.get(builder.toUriString(), accessToken, Map.class);
        if (response == null) return Collections.emptyList();

        List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("tracks");
        if (items == null) return Collections.emptyList();

        return items.stream().map(this::mapSpotifyTrack).collect(Collectors.toList());
    }

    /**
     * Get recommendations based on seed track IDs and workout type.
     */
    public List<Track> getRecommendationsBasedOnTracks(
            List<String> seedTracks,
            String accessToken,
            String workoutType,
            int limit
    ) {
        if (seedTracks == null || seedTracks.isEmpty()) return Collections.emptyList();

        Map<String, Object> workoutParams = getWorkoutAudioFeatures(workoutType);
        String seedParam = String.join(",", seedTracks.subList(0, Math.min(5, seedTracks.size())));

        String url = SPOTIFY_API_URL + "/recommendations?limit=" + limit +
                "&seed_tracks=" + seedParam +
                "&target_energy=" + workoutParams.get("energy") +
                "&target_danceability=" + workoutParams.get("danceability") +
                "&target_tempo=" + workoutParams.get("tempo");

        Map response = spotifyApiClient.get(url, accessToken, Map.class);
        if (response == null) return Collections.emptyList();

        List<Map<String, Object>> tracks = (List<Map<String, Object>>) response.get("tracks");
        if (tracks == null) return Collections.emptyList();

        return tracks.stream().map(this::mapSpotifyTrack).collect(Collectors.toList());
    }

    /**
     * Get recommendations from a PlaylistTemplate.
     */
    public List<Track> getRecommendationsFromTemplate(
            PlaylistTemplate template,
            String accessToken
    ) {
        String url = SPOTIFY_API_URL + "/recommendations"
                + "?limit=50"
                + "&seed_genres=" + String.join(",", template.getSeedGenres())
                + "&target_energy=" + (template.getTargetEnergy() / 100.0)
                + "&target_tempo=" + template.getTargetTempo();

        Map response = spotifyApiClient.get(url, accessToken, Map.class);
        if (response == null) return Collections.emptyList();

        List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("tracks");
        if (items == null) return Collections.emptyList();

        Collections.shuffle(items);
        return items.stream()
                .map(this::mapSpotifyTrack)
                .limit(10)
                .collect(Collectors.toList());
    }

    // --- Helpers ---

    private Map<String, Object> getWorkoutAudioFeatures(String workoutType) {
        Map<String, Object> params = new HashMap<>();
        switch (workoutType) {
            case "HIIT" -> {
                params.put("energy", 0.9);
                params.put("danceability", 0.7);
                params.put("tempo", 140);
            }
            case "Cardio" -> {
                params.put("energy", 0.8);
                params.put("danceability", 0.8);
                params.put("tempo", 130);
            }
            case "Strength Training" -> {
                params.put("energy", 0.85);
                params.put("danceability", 0.5);
                params.put("tempo", 120);
            }
            case "Yoga" -> {
                params.put("energy", 0.4);
                params.put("danceability", 0.3);
                params.put("tempo", 90);
            }
            default -> {
                params.put("energy", 0.7);
                params.put("danceability", 0.6);
                params.put("tempo", 120);
            }
        }
        return params;
    }

    private Track mapSpotifyTrack(Map<String, Object> data) {
        Track track = new Track();
        if (data == null) return track;

        track.setId((String) data.get("id"));
        track.setName((String) data.get("name"));
        track.setPreviewUrl((String) data.get("preview_url"));
        track.setPopularity(data.get("popularity") == null ? null : ((Number) data.get("popularity")).intValue());

        // artists
        List<Map<String, Object>> artists = (List<Map<String, Object>>) data.get("artists");
        if (artists != null) {
            track.setArtists(artists.stream().map(a -> (String) a.get("name")).toList());
        }

        // album cover
        Map<String, Object> album = (Map<String, Object>) data.get("album");
        if (album != null && album.containsKey("images")) {
            List<Map<String, Object>> images = (List<Map<String, Object>>) album.get("images");
            if (images != null && !images.isEmpty()) {
                track.setAlbumCover((String) images.get(0).get("url"));
            }
        }

        return track;
    }
}
