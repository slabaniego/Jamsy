package ca.sheridancollege.jamsy.services;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import ca.sheridancollege.jamsy.beans.Track;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class LastFmService {

    @Value("${lastfm.api.key}")
    private String apiKey;
    
	/*
	 * private static final String BASE_URL = "http://ws.audioscrobbler.com/2.0/";
	 */
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
                                .collect(Collectors.toList());
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Error getting genres for " + artistName + ": " + e.getMessage());
        }
        return Collections.emptyList();
    }

    public List<String> getSimilarArtists(String artistName, int limit) {
        try {
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
                            .limit(limit)
                            .collect(Collectors.toList());
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Error getting similar artists for " + artistName + ": " + e.getMessage());
        }
        return Collections.emptyList();
    }

    public List<Track> getArtistTopTracks(String artistName, int limit) {
        try {
            String url = baseUrl + "?method=artist.getTopTracks&artist=" + 
                        URLEncoder.encode(artistName, StandardCharsets.UTF_8) +
                        "&api_key=" + apiKey + "&format=json&limit=" + limit;
            
            System.out.println("Fetching Last.fm tracks for: " + artistName);
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> body = response.getBody();
                if (body != null && body.containsKey("toptracks")) {
                    Map<String, Object> topTracks = (Map<String, Object>) body.get("toptracks");
                    if (topTracks.containsKey("track")) {
                        List<Map<String, Object>> trackList = (List<Map<String, Object>>) topTracks.get("track");
                        
                        System.out.println("Found " + trackList.size() + " tracks for " + artistName);
                        
                        List<Track> result = trackList.stream()
                            .map(trackMap -> {
                                Track track = new Track();
                                
                                // Set track name
                                if (trackMap.containsKey("name")) {
                                    track.setName((String) trackMap.get("name"));
                                }
                                
                                // Set artist name
                                if (trackMap.containsKey("artist")) {
                                    Map<String, Object> artist = (Map<String, Object>) trackMap.get("artist");
                                    if (artist.containsKey("name")) {
                                        track.setArtistName((String) artist.get("name"));
                                    }
                                }
                                
                                // Set image URL
                                if (trackMap.containsKey("image")) {
                                    List<Map<String, Object>> images = (List<Map<String, Object>>) trackMap.get("image");
                                    if (images != null && !images.isEmpty()) {
                                        // Try to get medium size image (index 1)
                                        for (Map<String, Object> image : images) {
                                            if (image.containsKey("size") && "medium".equals(image.get("size")) && 
                                                image.containsKey("#text")) {
                                                String imageUrl = (String) image.get("#text");
                                                if (imageUrl != null && !imageUrl.isEmpty()) {
                                                    track.setImageUrl(imageUrl);
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                }
                                
                                System.out.println("Track: " + track.getName() + " by " + track.getArtistName());
                                return track;
                            })
                            .limit(limit)
                            .collect(Collectors.toList());
                        
                        return result;
                    }
                }
            } else {
                System.out.println("Last.fm API error: " + response.getStatusCode());
            }
        } catch (Exception e) {
            System.out.println("Error getting top tracks for " + artistName + ": " + e.getMessage());
            e.printStackTrace();
        }
        return Collections.emptyList();
    }

    public boolean artistMatchesWorkout(String artistName, String workout) {
        List<String> genres = getArtistGenres(artistName);
        
        // Map workouts to relevant genres
        Map<String, List<String>> workoutGenres = Map.of(
            "cardio", Arrays.asList("electronic", "dance", "pop", "hip hop", "edm", "house", "techno", "trance"),
            "strength", Arrays.asList("rock", "metal", "hard rock", "punk", "alternative", "industrial"),
            "yoga", Arrays.asList("ambient", "chillout", "meditation", "new age", "classical", "folk"),
            "running", Arrays.asList("pop", "indie", "alternative", "electropop", "synthpop")
        );
        
        List<String> targetGenres = workoutGenres.getOrDefault(workout.toLowerCase(), 
            Arrays.asList("pop", "electronic", "dance")); // Default genres
        
        // Check if any of the artist's genres match the workout genres
        return genres.stream()
            .anyMatch(genre -> targetGenres.stream()
                .anyMatch(target -> genre.toLowerCase().contains(target.toLowerCase())));
    }
}

	
	
	
	

