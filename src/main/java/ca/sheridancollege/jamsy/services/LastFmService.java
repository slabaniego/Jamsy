package ca.sheridancollege.jamsy.services;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;

import java.util.*;

@Service
public class LastFmService {

    private static final String API_KEY = "cde0694956a54c781c66c9547e282d1e"; 
    private static final String BASE_URL = "http://ws.audioscrobbler.com/2.0/";

    private RestTemplate restTemplate = new RestTemplate();

    // Method to get genres for a track using the track name and artist
    public List<String> getGenresForTrack(String trackName, String artistName) {
        String url = BASE_URL + "?method=track.getInfo&api_key=" + API_KEY + "&track=" + trackName + "&artist=" + artistName + "&format=json";
        
        ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
        Map<String, Object> trackInfo = (Map<String, Object>) response.getBody().get("track");

        if (trackInfo != null) {
            Map<String, Object> topTags = (Map<String, Object>) trackInfo.get("toptags");
            if (topTags != null) {
                List<Map<String, Object>> tagList = (List<Map<String, Object>>) topTags.get("tag");
                List<String> genres = new ArrayList<>();
                for (Map<String, Object> tag : tagList) {
                    genres.add((String) tag.get("name"));
                }
                return genres;
            }
        }

        return Collections.emptyList(); // Return empty if no genres found
    }

    // Method to search for similar genres or tracks
    public List<String> getSimilarGenres(String genre) {
        String url = BASE_URL + "?method=tag.getSimilar&api_key=" + API_KEY + "&tag=" + genre + "&format=json";
        
        ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
        Map<String, Object> similarGenresResponse = (Map<String, Object>) response.getBody().get("tags");

        if (similarGenresResponse != null) {
            List<Map<String, Object>> similarGenres = (List<Map<String, Object>>) similarGenresResponse.get("tag");
            List<String> genreList = new ArrayList<>();
            for (Map<String, Object> genreData : similarGenres) {
                genreList.add((String) genreData.get("name"));
            }
            return genreList;
        }

        return Collections.emptyList(); // Return empty if no similar genres found
    }
}
