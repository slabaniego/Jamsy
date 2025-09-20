package ca.sheridancollege.jamsy.services;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Collectors;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import ca.sheridancollege.jamsy.beans.Track;

@Service
public class MusicBrainzService {
    private static final String API_URL = "https://musicbrainz.org/ws/2/";
    private final RestTemplate restTemplate;
    private final DeezerService deezerService;

    public MusicBrainzService(RestTemplateBuilder restTemplateBuilder, DeezerService deezerService) {
        this.restTemplate = restTemplateBuilder.build();
        this.deezerService = deezerService;
    }

    public List<Track> getObscureSimilarTracks(String trackName, String artistName, int maxPopularity) {
        try {
            // Search for recordings
            String searchUrl = UriComponentsBuilder.fromHttpUrl(API_URL + "recording/")
                    .queryParam("query", "recording:\"" + trackName + "\" AND artist:\"" + artistName + "\"")
                    .queryParam("fmt", "json")
                    .build().toUriString();

            Map<String, Object> response = restTemplate.getForObject(searchUrl, Map.class);
            List<Map<String, Object>> recordings = (List<Map<String, Object>>) response.get("recordings");

            if (recordings == null || recordings.isEmpty()) {
                return Collections.emptyList();
            }

            // Get artist ID safely
            Map<String, Object> firstRecording = recordings.get(0);
            List<Map<String, Object>> artistCredits = (List<Map<String, Object>>) firstRecording.get("artist-credit");
            if (artistCredits == null || artistCredits.isEmpty()) {
                return Collections.emptyList();
            }

            Map<String, Object> firstArtist = artistCredits.get(0);
            String artistId = (String) firstArtist.get("id");
            if (artistId == null) {
                return Collections.emptyList();
            }

            // Get artist's other recordings
            String recordingsUrl = UriComponentsBuilder.fromHttpUrl(API_URL + "recording/")
                    .queryParam("artist", artistId)
                    .queryParam("fmt", "json")
                    .queryParam("limit", "10")
                    .build().toUriString();

            Map<String, Object> recordingsResponse = restTemplate.getForObject(recordingsUrl, Map.class);
            List<Map<String, Object>> similarRecordings = (List<Map<String, Object>>) recordingsResponse.get("recordings");

            if (similarRecordings == null) {
                return Collections.emptyList();
            }

            return similarRecordings.stream()
                    .filter(rec -> {
                        // Skip the original track
                        String currentTitle = (String) rec.get("title");
                        return !currentTitle.equalsIgnoreCase(trackName);
                    })
                    .map(rec -> {
                        Track track = new Track();
                        track.setName((String) rec.get("title"));
                        
                        // Get artist names
                        List<Map<String, Object>> artists = (List<Map<String, Object>>) rec.get("artist-credit");
                        List<String> artistNames = artists.stream()
                                .map(artist -> (String) artist.get("name"))
                                .collect(Collectors.toList());
                        track.setArtists(artistNames);
                        
                        // Set random popularity (0-40 range for obscurity)
                        track.setPopularity(new Random().nextInt(40));
                        
                        // Get cover art from Deezer
                        String cover = deezerService.getAlbumCoverFallback(
                                track.getName(), 
                                track.getArtists()
                        );
                        track.setAlbumCover(cover);
                        
                        return track;
                    })
                    .filter(t -> t.getPopularity() < maxPopularity)
                    .limit(3)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            System.err.println("Error getting MusicBrainz data for " + trackName + ": " + e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * Get genres for a specific artist from MusicBrainz
     */
    public List<String> getArtistGenres(String artistName) {
        try {
            // First, search for the artist
            String searchUrl = UriComponentsBuilder.fromHttpUrl(API_URL + "artist/")
                    .queryParam("query", "artist:\"" + artistName + "\"")
                    .queryParam("fmt", "json")
                    .build().toUriString();

            Map<String, Object> searchResponse = restTemplate.getForObject(searchUrl, Map.class);
            List<Map<String, Object>> artists = (List<Map<String, Object>>) searchResponse.get("artists");

            if (artists == null || artists.isEmpty()) {
                return Collections.emptyList();
            }

            // Get the first artist's ID
            String artistId = (String) artists.get(0).get("id");
            if (artistId == null) {
                return Collections.emptyList();
            }

            // Get artist details with tags (genres)
            String artistUrl = UriComponentsBuilder.fromHttpUrl(API_URL + "artist/" + artistId)
                    .queryParam("inc", "tags")
                    .queryParam("fmt", "json")
                    .build().toUriString();

            Map<String, Object> artistResponse = restTemplate.getForObject(artistUrl, Map.class);
            
            // Extract tags (genres)
            Map<String, Object> tagsWrapper = (Map<String, Object>) artistResponse.get("tags");
            if (tagsWrapper != null) {
                List<Map<String, Object>> tags = (List<Map<String, Object>>) tagsWrapper.get("tags");
                if (tags != null) {
                    return tags.stream()
                            .map(tag -> (String) tag.get("name"))
                            .filter(Objects::nonNull)
                            .limit(10) // Limit to top 10 genres
                            .collect(Collectors.toList());
                }
            }
            
        } catch (Exception e) {
            System.err.println("Error getting MusicBrainz genres for artist " + artistName + ": " + e.getMessage());
        }
        
        return Collections.emptyList();
    }

    /**
     * Get detailed artist info including genres from MusicBrainz
     */
    public Map<String, Object> getArtistInfo(String artistName) {
        try {
            // Search for the artist
            String searchUrl = UriComponentsBuilder.fromHttpUrl(API_URL + "artist/")
                    .queryParam("query", "artist:\"" + artistName + "\"")
                    .queryParam("fmt", "json")
                    .build().toUriString();

            Map<String, Object> searchResponse = restTemplate.getForObject(searchUrl, Map.class);
            List<Map<String, Object>> artists = (List<Map<String, Object>>) searchResponse.get("artists");

            if (artists == null || artists.isEmpty()) {
                return Collections.emptyMap();
            }

            // Get the first artist's details with tags
            String artistId = (String) artists.get(0).get("id");
            String artistUrl = UriComponentsBuilder.fromHttpUrl(API_URL + "artist/" + artistId)
                    .queryParam("inc", "tags")
                    .queryParam("fmt", "json")
                    .build().toUriString();

            return restTemplate.getForObject(artistUrl, Map.class);
            
        } catch (Exception e) {
            System.err.println("Error getting MusicBrainz info for artist " + artistName + ": " + e.getMessage());
            return Collections.emptyMap();
        }
    }
    
    public List<Track> getObscureSimilarTracks(String artistName, int maxPopularity) {
        try {
            // Search for the artist
            String searchUrl = UriComponentsBuilder.fromHttpUrl(API_URL + "artist/")
                    .queryParam("query", "artist:\"" + artistName + "\"")
                    .queryParam("fmt", "json")
                    .build().toUriString();

            Map<String, Object> searchResponse = restTemplate.getForObject(searchUrl, Map.class);
            List<Map<String, Object>> artists = (List<Map<String, Object>>) searchResponse.get("artists");

            if (artists == null || artists.isEmpty()) {
                return Collections.emptyList();
            }

            // Get the first artist's ID
            String artistId = (String) artists.get(0).get("id");
            
            // Get recordings by this artist
            String recordingsUrl = UriComponentsBuilder.fromHttpUrl(API_URL + "recording/")
                    .queryParam("artist", artistId)
                    .queryParam("fmt", "json")
                    .queryParam("limit", "50")
                    .build().toUriString();

            Map<String, Object> recordingsResponse = restTemplate.getForObject(recordingsUrl, Map.class);
            List<Map<String, Object>> recordings = (List<Map<String, Object>>) recordingsResponse.get("recordings");

            if (recordings == null) {
                return Collections.emptyList();
            }

            return recordings.stream()
                    .map(rec -> {
                        Track track = new Track();
                        track.setName((String) rec.get("title"));
                        
                        // Get artist names
                        List<Map<String, Object>> artistsList = (List<Map<String, Object>>) rec.get("artist-credit");
                        List<String> artistNames = artistsList.stream()
                                .map(artist -> (String) artist.get("name"))
                                .collect(Collectors.toList());
                        track.setArtists(artistNames);
                        
                        // Set low popularity for obscurity
                        track.setPopularity(new Random().nextInt(maxPopularity));
                        
                        return track;
                    })
                    .filter(track -> track.getPopularity() < maxPopularity)
                    .limit(10)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            System.out.println("Error getting MusicBrainz obscure tracks: " + e.getMessage());
            return Collections.emptyList();
        }
    }
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
}