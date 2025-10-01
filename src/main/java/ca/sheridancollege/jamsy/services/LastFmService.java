package ca.sheridancollege.jamsy.services;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import ca.sheridancollege.jamsy.beans.Track;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class LastFmService {

    @Value("${lastfm.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${lastfm.api.base-url}")
    private String baseUrl;

    public List<String> getArtistGenres(String artistName) {
        try {
            String url = baseUrl + "?method=artist.getInfo&artist=" +
                    URLEncoder.encode(artistName, StandardCharsets.UTF_8) +
                    "&api_key=" + apiKey + "&format=json";

            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> body = response.getBody();
                if (body != null && body.containsKey("artist")) {
                    Map<String, Object> artist = (Map<String, Object>) body.get("artist");
                    if (artist.containsKey("tags")) {
                        Map<String, Object> tags = (Map<String, Object>) artist.get("tags");
                        if (tags.containsKey("tag")) {
                            List<Map<String, Object>> tagList = (List<Map<String, Object>>) tags.get("tag");
                            return tagList.stream()
                                    .map(tag -> (String) tag.get("name"))
                                    .filter(Objects::nonNull)
                                    .collect(Collectors.toList());
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("❌ Error getting genres for " + artistName + ": " + e.getMessage());
        }
        return Collections.emptyList();
    }

    public List<String> getSimilarArtists(String artistName, int limit) {
        try {
            // Add null check to prevent the "Cannot invoke String.length() because s is null" error
            if (artistName == null || artistName.trim().isEmpty()) {
                System.out.println("❌ Error getting similar artists: artistName is null or empty");
                return Collections.emptyList();
            }
            
            String url = baseUrl + "?method=artist.getSimilar&artist=" +
                    URLEncoder.encode(artistName, StandardCharsets.UTF_8) +
                    "&api_key=" + apiKey + "&format=json&limit=" + limit;

            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> body = response.getBody();
                if (body != null && body.containsKey("similarartists")) {
                    Map<String, Object> similarArtists = (Map<String, Object>) body.get("similarartists");
                    if (similarArtists.containsKey("artist")) {
                        List<Map<String, Object>> artistList = (List<Map<String, Object>>) similarArtists.get("artist");
                        return artistList.stream()
                                .map(artist -> (String) artist.get("name"))
                                .filter(Objects::nonNull)
                                .limit(limit)
                                .collect(Collectors.toList());
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("❌ Error getting similar artists for " + artistName + ": " + e.getMessage());
        }
        return Collections.emptyList();
    }

    public List<Track> getArtistTopTracks(String artistName, int limit) {
        try {
            // Add null check to prevent the "Cannot invoke String.length() because s is null" error
            if (artistName == null || artistName.trim().isEmpty()) {
                System.out.println("❌ Error getting top tracks: artistName is null or empty");
                return Collections.emptyList();
            }
            
            String url = baseUrl + "?method=artist.getTopTracks&artist=" +
                    URLEncoder.encode(artistName, StandardCharsets.UTF_8) +
                    "&api_key=" + apiKey + "&format=json&limit=" + limit;

            System.out.println("🎶 Fetching Last.fm tracks for: " + artistName);
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> body = response.getBody();
                if (body != null && body.containsKey("toptracks")) {
                    Map<String, Object> topTracks = (Map<String, Object>) body.get("toptracks");
                    if (topTracks.containsKey("track")) {
                        List<Map<String, Object>> trackList = (List<Map<String, Object>>) topTracks.get("track");

                        System.out.println("✅ Found " + trackList.size() + " tracks for " + artistName);

                        return trackList.stream().map(trackMap -> {
                            Track track = new Track();

                            // Name
                            track.setName((String) trackMap.get("name"));

                            // Artist (normalize into both fields)
                            if (trackMap.containsKey("artist")) {
                                Map<String, Object> artist = (Map<String, Object>) trackMap.get("artist");
                                if (artist.containsKey("name")) {
                                    String aName = (String) artist.get("name");
                                    track.setArtistName(aName);
                                    track.setArtists(Collections.singletonList(aName)); // ✅ key fix
                                }
                            }

                            // Album image (fallback to medium size)
                            if (trackMap.containsKey("image")) {
                                List<Map<String, Object>> images = (List<Map<String, Object>>) trackMap.get("image");
                                if (images != null) {
                                    for (Map<String, Object> image : images) {
                                        if ("medium".equals(image.get("size")) && image.containsKey("#text")) {
                                            String imageUrl = (String) image.get("#text");
                                            if (imageUrl != null && !imageUrl.isEmpty()) {
                                                track.setImageUrl(imageUrl);
                                                break;
                                            }
                                        }
                                    }
                                }
                            }

                            System.out.println("➡ Track: " + track.getName() + " by " + track.getArtists());
                            return track;
                        }).limit(limit).collect(Collectors.toList());
                    }
                }
            } else {
                System.out.println("⚠️ Last.fm API error: " + response.getStatusCode());
            }
        } catch (Exception e) {
            System.out.println("❌ Error getting top tracks for " + artistName + ": " + e.getMessage());
        }
        return Collections.emptyList();
    }
    
    // Similar tracks 
    public List<Track> getSimilarTracks(String trackName, String artistName, int limit) {
        try {
            // Add null checks to prevent the "Cannot invoke String.length() because s is null" error
            if (trackName == null || artistName == null || trackName.trim().isEmpty() || artistName.trim().isEmpty()) {
                System.out.println("❌ Error getting similar tracks: trackName or artistName is null or empty");
                return Collections.emptyList();
            }
            
            String url = baseUrl + "?method=track.getSimilar" +
                    "&artist=" + URLEncoder.encode(artistName, StandardCharsets.UTF_8) +
                    "&track=" + URLEncoder.encode(trackName, StandardCharsets.UTF_8) +
                    "&api_key=" + apiKey +
                    "&format=json&limit=" + limit;

            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> body = response.getBody();
                if (body != null && body.containsKey("similartracks")) {
                    Map<String, Object> similarTracks = (Map<String, Object>) body.get("similartracks");
                    if (similarTracks.containsKey("track")) {
                        List<Map<String, Object>> trackList =
                                (List<Map<String, Object>>) similarTracks.get("track");

                        return trackList.stream().map(trackMap -> {
                            Track track = new Track();
                            track.setName((String) trackMap.get("name"));

                            // Artist info
                            if (trackMap.containsKey("artist")) {
                                Map<String, Object> artist = (Map<String, Object>) trackMap.get("artist");
                                if (artist.containsKey("name")) {
                                    String aName = (String) artist.get("name");
                                    track.setArtistName(aName);
                                    track.setArtists(Collections.singletonList(aName));
                                }
                            }

                            // Album image
                            if (trackMap.containsKey("image")) {
                                List<Map<String, Object>> images =
                                        (List<Map<String, Object>>) trackMap.get("image");
                                for (Map<String, Object> image : images) {
                                    if ("medium".equals(image.get("size"))) {
                                        String imageUrl = (String) image.get("#text");
                                        if (imageUrl != null && !imageUrl.isEmpty()) {
                                            track.setImageUrl(imageUrl);
                                            break;
                                        }
                                    }
                                }
                            }

                            return track;
                        }).limit(limit).collect(Collectors.toList());
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("❌ Error getting similar tracks: " + e.getMessage());
        }
        return Collections.emptyList();
    }


    public boolean artistMatchesWorkout(String artistName, String workout) {
        List<String> genres = getArtistGenres(artistName);

        Map<String, List<String>> workoutGenres = Map.of(
                "cardio", Arrays.asList("electronic", "dance", "pop", "hip hop", "edm", "house", "techno", "trance"),
                "strength", Arrays.asList("rock", "metal", "hard rock", "punk", "alternative", "industrial"),
                "yoga", Arrays.asList("ambient", "chillout", "meditation", "new age", "classical", "folk"),
                "running", Arrays.asList("pop", "indie", "alternative", "electropop", "synthpop")
        );

        List<String> targetGenres = workoutGenres.getOrDefault(
                workout.toLowerCase(),
                Arrays.asList("pop", "electronic", "dance")
        );

        return genres.stream().anyMatch(genre ->
                targetGenres.stream().anyMatch(target -> genre.toLowerCase().contains(target.toLowerCase())));
    }
}
