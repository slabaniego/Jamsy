package ca.sheridancollege.jamsy.services;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import ca.sheridancollege.jamsy.beans.Track;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class LastFmService {

    @Value("${lastfm.api.key}")
    private String apiKey;
    
    private static final String BASE_URL = "http://ws.audioscrobbler.com/2.0/";
    private final RestTemplate restTemplate = new RestTemplate();

    // Map workout types to Last.fm tags (genres)
    static final Map<String, List<String>> WORKOUT_TO_TAGS = Map.of(
        "cardio", List.of("pop", "dance", "electronic", "disco", "hip hop"),
        "strength training", List.of("rock", "metal", "hard rock", "punk", "alternative"),
        "strength", List.of("rock", "metal", "hard rock", "punk", "alternative"),
        "hiit", List.of("electronic", "dubstep", "drum and bass", "trap", "edm"),
        "yoga", List.of("ambient", "chillout", "meditation", "new age", "acoustic")
    );

    /**
     * Get similar artists for a given artist
     */
    public List<String> getSimilarArtists(String artistName, int limit) {
        try {
            String url = BASE_URL + "?method=artist.getsimilar" +
                "&artist=" + URLEncoder.encode(artistName, StandardCharsets.UTF_8) +
                "&api_key=" + apiKey +
                "&format=json" +
                "&limit=" + limit;
            
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                Map<String, Object> similarArtists = (Map<String, Object>) body.get("similarartists");
                
                if (similarArtists != null) {
                    List<Map<String, Object>> artistList = (List<Map<String, Object>>) similarArtists.get("artist");
                    if (artistList != null) {
                        return artistList.stream()
                            .map(artist -> (String) artist.get("name"))
                            .limit(limit)
                            .collect(Collectors.toList());
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Last.fm similar artists error for " + artistName + ": " + e.getMessage());
        }
        return Collections.emptyList();
    }

    /**
     * Get top tracks for an artist
     */
    public List<Track> getArtistTopTracks(String artistName, int limit) {
        try {
            String url = BASE_URL + "?method=artist.gettoptracks" +
                "&artist=" + URLEncoder.encode(artistName, StandardCharsets.UTF_8) +
                "&api_key=" + apiKey +
                "&format=json" +
                "&limit=" + limit;

            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                Map<String, Object> topTracks = (Map<String, Object>) body.get("toptracks");

                if (topTracks != null) {
                    List<Map<String, Object>> trackList = (List<Map<String, Object>>) topTracks.get("track");
                    if (trackList != null) {
                        return trackList.stream()
                            .map(trackMap -> {
                                Track track = new Track();
                                track.setId("lastfm-" + UUID.randomUUID().toString());
                                track.setName((String) trackMap.get("name"));

                                // ✅ Set artists as a list
                                Map<String, Object> artistInfo = (Map<String, Object>) trackMap.get("artist");
                                if (artistInfo != null) {
                                    String artistFromApi = (String) artistInfo.get("name");
                                    if (artistFromApi != null && !artistFromApi.isBlank()) {
                                        track.setArtists(List.of(artistFromApi));
                                    } else {
                                        track.setArtists(List.of("Unknown Artist"));
                                    }
                                } else {
                                    track.setArtists(List.of("Unknown Artist"));
                                }

                                return track;
                            })
                            .limit(limit)
                            .collect(Collectors.toList());
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Last.fm top tracks error for " + artistName + ": " + e.getMessage());
        }
        return Collections.emptyList();
    }

    /**
     * Get tags (genres) for an artist - returns list of genre names
     */
    public List<String> getArtistTags(String artistName) {
        try {
            String url = BASE_URL + "?method=artist.gettoptags" +
                "&artist=" + URLEncoder.encode(artistName, StandardCharsets.UTF_8) +
                "&api_key=" + apiKey +
                "&format=json";
            
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                Map<String, Object> topTags = (Map<String, Object>) body.get("toptags");
                
                if (topTags != null) {
                    List<Map<String, Object>> tagList = (List<Map<String, Object>>) topTags.get("tag");
                    if (tagList != null) {
                        return tagList.stream()
                            .map(tag -> ((String) tag.get("name")).toLowerCase())
                            .collect(Collectors.toList());
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Last.fm tags error for " + artistName + ": " + e.getMessage());
        }
        return Collections.emptyList();
    }

    /**
     * Get artist genres - this is an alias for getArtistTags for backward compatibility
     */
    public List<String> getArtistGenres(String artistName) {
        return getArtistTags(artistName);
    }

    /**
     * Check if artist matches workout genre
     */
    public boolean artistMatchesWorkout(String artistName, String workout) {
        List<String> artistTags = getArtistTags(artistName);
        List<String> workoutTags = WORKOUT_TO_TAGS.getOrDefault(workout.toLowerCase(), Collections.emptyList());
        
        return artistTags.stream()
            .anyMatch(artistTag -> workoutTags.stream()
                .anyMatch(workoutTag -> artistTag.contains(workoutTag) || workoutTag.contains(artistTag)));
    }

    /**
     * Get top artists by genre (for workout matching)
     */
    public List<String> getTopArtistsByTag(String tag, int limit) {
        try {
            String url = BASE_URL + "?method=tag.gettopartists" +
                "&tag=" + URLEncoder.encode(tag, StandardCharsets.UTF_8) +
                "&api_key=" + apiKey +
                "&format=json" +
                "&limit=" + limit;
            
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                Map<String, Object> topArtists = (Map<String, Object>) body.get("topartists");
                
                if (topArtists != null) {
                    List<Map<String, Object>> artistList = (List<Map<String, Object>>) topArtists.get("artist");
                    if (artistList != null) {
                        return artistList.stream()
                            .map(artist -> (String) artist.get("name"))
                            .limit(limit)
                            .collect(Collectors.toList());
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Last.fm top artists by tag error for " + tag + ": " + e.getMessage());
        }
        return Collections.emptyList();
    }
}
	
	
	
	

