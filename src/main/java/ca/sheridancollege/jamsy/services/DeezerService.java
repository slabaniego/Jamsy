package ca.sheridancollege.jamsy.services;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import ca.sheridancollege.jamsy.beans.Track;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DeezerService {

    private static final String DEEZER_API_URL = "https://api.deezer.com";
    private final RestTemplate restTemplate = new RestTemplate();

    public List<Map<String, Object>> searchTrack(String query) {
        String url = DEEZER_API_URL + "/search?q=" + query;

        Map<String, Object> response = restTemplate.getForObject(url, Map.class);
        return extractTracks(response);
    }

    private List<Map<String, Object>> extractTracks(Map<String, Object> response) {
        List<Map<String, Object>> tracks = new ArrayList<>();
        if (response == null || response.get("data") == null) {
            return tracks;
        }

        List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("data");
        for (Map<String, Object> item : items) {
            Map<String, Object> simplifiedTrack = new HashMap<>();
            simplifiedTrack.put("name", item.get("title"));
            simplifiedTrack.put("artist", ((Map<String, Object>) item.get("artist")).get("name"));
            simplifiedTrack.put("preview_url", item.get("preview"));
            simplifiedTrack.put("album_cover", ((Map<String, Object>) item.get("album")).get("cover_big"));
            simplifiedTrack.put("source", "deezer");
            tracks.add(simplifiedTrack);
        }

        return tracks;
    }
    
    public String getDeezerPreviewUrl(String trackName, String artistName) {
        String query = trackName + " " + artistName;
        String url = DEEZER_API_URL + "/search?q=" + query;

        try {
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
            if (data != null && !data.isEmpty()) {
                return (String) data.get(0).get("preview");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }
    
    public Map<String, Object> getFirstMatchingTrack(String trackName, String artistName) {
        String query = trackName + " " + artistName;
        String url = "https://api.deezer.com/search?q=" + query;

        try {
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response != null && response.containsKey("data")) {
                List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("data");
                if (!results.isEmpty()) return results.get(0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }
    
    public String getPreviewUrlFallback(String trackName, List<String> artists) {
        try {
            String query = URLEncoder.encode(trackName + " " + String.join(" ", artists), StandardCharsets.UTF_8);
            String url = "https://api.deezer.com/search?q=" + query;

            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");

            if (data != null && !data.isEmpty()) {
                return (String) data.get(0).get("preview");
            }
        } catch (Exception e) {
            System.out.println("❌ Deezer fallback failed: " + e.getMessage());
        }
        return null;
    }


    public List<Track> getArtistTopTracks(String artistName, int limit) {
        try {
            // Implement Deezer API call here
            String url = "https://api.deezer.com/search?q=artist:\"" + 
                         URLEncoder.encode(artistName, StandardCharsets.UTF_8) + 
                         "\"&limit=" + limit;
            
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                List<Map<String, Object>> data = (List<Map<String, Object>>) body.get("data");
                
                return data.stream()
                    .map(trackMap -> {
                        Track track = new Track();
                        track.setId("deezer-" + trackMap.get("id"));
                        track.setName((String) trackMap.get("title"));
                        track.setPreviewUrl((String) trackMap.get("preview"));
                        
                        // Extract artist information
                        Map<String, Object> artistInfo = (Map<String, Object>) trackMap.get("artist");
                        if (artistInfo != null) {
                            track.setArtistName((String) artistInfo.get("name"));
                        }
                        
                        return track;
                    })
                    .collect(Collectors.toList());
            }
        } catch (Exception e) {
            System.out.println("Deezer API error for artist " + artistName + ": " + e.getMessage());
        }
        return Collections.emptyList();
    }
    
    
    
    public String getAlbumCoverFallback(String trackName, List<String> artistNames) {
        List<Map<String, Object>> results = searchTrack(trackName);
        for (Map<String, Object> track : results) {
            // Get album cover safely
            String cover = null;
            if (track.get("album") instanceof Map<?, ?> albumMap) {
                Object coverObj = albumMap.get("cover_medium");
                if (coverObj instanceof String) {
                    cover = (String) coverObj;
                }
            }

            // Get artist safely
            Object artistObj = track.get("artist");
            String deezerArtist = "";
            if (artistObj instanceof Map<?, ?> artistMap) {
                Object nameObj = artistMap.get("name");
                deezerArtist = nameObj != null ? nameObj.toString().toLowerCase() : "";
            }

            for (String inputArtist : artistNames) {
                if (deezerArtist.contains(inputArtist.toLowerCase()) && cover != null) {
                    return cover;
                }
            }
        }
        return null;
    }
}
