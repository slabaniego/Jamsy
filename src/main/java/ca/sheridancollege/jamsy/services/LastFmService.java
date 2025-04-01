package ca.sheridancollege.jamsy.services;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import ca.sheridancollege.jamsy.beans.Track;

import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class LastFmService {

    private static final String API_KEY = "cde0694956a54c781c66c9547e282d1e";
    private static final String BASE_URL = "http://ws.audioscrobbler.com/2.0/";

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Get genres (tags) for a specific track and artist.
     */
    public List<String> getGenresForTrack(String trackName, String artistName) {
        String url = BASE_URL + "?method=track.getInfo&api_key=" + API_KEY
                + "&track=" + trackName + "&artist=" + artistName + "&format=json";

        ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
        if (response.getBody() == null || response.getBody().get("track") == null) return List.of();

        Map<String, Object> trackInfo = (Map<String, Object>) response.getBody().get("track");
        Map<String, Object> topTags = (Map<String, Object>) trackInfo.get("toptags");

        if (topTags != null && topTags.get("tag") instanceof List) {
            List<Map<String, Object>> tagList = (List<Map<String, Object>>) topTags.get("tag");
            List<String> genres = new ArrayList<>();
            for (Map<String, Object> tag : tagList) {
                genres.add((String) tag.get("name"));
            }
            return genres;
        }

        return List.of();
    }

    /**
     * Get genres related to a genre.
     */
    public List<String> getSimilarGenres(String genre) {
        String url = BASE_URL + "?method=tag.getSimilar&api_key=" + API_KEY + "&tag=" + genre + "&format=json";

        ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
        if (response.getBody() == null || response.getBody().get("tags") == null) return List.of();

        Map<String, Object> tagsObj = (Map<String, Object>) response.getBody().get("tags");

        if (tagsObj.get("tag") instanceof List) {
            List<Map<String, Object>> similarTags = (List<Map<String, Object>>) tagsObj.get("tag");
            List<String> similarGenres = new ArrayList<>();
            for (Map<String, Object> tag : similarTags) {
                similarGenres.add((String) tag.get("name"));
            }
            return similarGenres;
        }

        return List.of();
    }

    
    // Get similar tracks based on one track and artist (for recommendations)
     
    public List<Map<String, Object>> getSimilarTracks(String trackName, String artistName) {
        String url = BASE_URL + "?method=track.getSimilar&track=" + trackName + "&artist=" + artistName
                + "&api_key=" + API_KEY + "&format=json";

        ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
        if (response.getBody() == null || response.getBody().get("similartracks") == null) return List.of();

        Map<String, Object> similarTracksWrapper = (Map<String, Object>) response.getBody().get("similartracks");

        List<Map<String, Object>> similarTracks = new ArrayList<>();
        if (similarTracksWrapper.get("track") instanceof List) {
            similarTracks = (List<Map<String, Object>>) similarTracksWrapper.get("track");
        } else if (similarTracksWrapper.get("track") instanceof Map) {
            // sometimes Last.fm returns a single object instead of a list
            similarTracks.add((Map<String, Object>) similarTracksWrapper.get("track"));
        }

        return similarTracks;
    }
    
    private Track mapLastFmTrack(Map<String, Object> data, String tag) {
        Track track = new Track();

        track.setName((String) data.get("name"));

        Map<String, Object> artist = (Map<String, Object>) data.get("artist");
        if (artist != null) {
            track.setArtists(List.of((String) artist.get("name")));
        }

        List<Map<String, Object>> images = (List<Map<String, Object>>) data.get("image");
        if (images != null && !images.isEmpty()) {
            Map<String, Object> lastImage = images.get(images.size() - 1);
            track.setAlbumCover((String) lastImage.get("#text"));
        }

        track.setPreviewUrl(null); // fallback can be added here
        track.setGenres(List.of(tag)); // use the random tag

        return track;
    }

    public Track mapLastFmTrackPublic(Map<String, Object> data, String tag) {
        return mapLastFmTrack(data, tag);
    }
    
    public List<Track> getRecommendedTracksFromLastFm(String tag) {
        String url = "http://ws.audioscrobbler.com/2.0/?method=tag.gettoptracks&tag="
                     + URLEncoder.encode(tag, StandardCharsets.UTF_8)
                     + "&api_key=" + API_KEY + "&format=json";

        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, null, Map.class);
        Map<String, Object> responseBody = response.getBody();

        if (responseBody == null || !responseBody.containsKey("tracks")) {
            return new ArrayList<>();
        }

        Map<String, Object> tracksWrapper = (Map<String, Object>) responseBody.get("tracks");
        List<Map<String, Object>> trackData = (List<Map<String, Object>>) tracksWrapper.get("track");

        List<Track> tracks = new ArrayList<>();
        for (Map<String, Object> data : trackData) {
            tracks.add(mapLastFmTrack(data, tag));
        }

        return trackData.stream()
        	    .map(data -> mapLastFmTrack(data, tag))
        	    .collect(Collectors.toList());
    }


    public List<Track> getFreshLastFmRecommendations() {
        String[] tags = {"indie", "jazz", "rnb", "lofi", "rock", "pop", "hip-hop"};
        String tag = tags[new Random().nextInt(tags.length)];

        String url = BASE_URL + "?method=tag.gettoptracks&tag="
                     + URLEncoder.encode(tag, StandardCharsets.UTF_8)
                     + "&api_key=" + API_KEY + "&format=json";

        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, null, Map.class);
        Map<String, Object> body = response.getBody();

        if (body == null || body.get("tracks") == null) return List.of();

        List<Map<String, Object>> trackData = (List<Map<String, Object>>)
                ((Map<String, Object>) body.get("tracks")).get("track");

        Collections.shuffle(trackData); // 🔄 shuffle here

        return trackData.stream()
                .limit(15) // 🔢 optionally limit to avoid repeats
                .map(data -> mapLastFmTrack(data, tag))
                .collect(Collectors.toList());
    }
    
    public List<Track> getSimilarTracksFromRandomSpotifyTrack(String name, String artist, 
    	    boolean excludeExplicit, boolean excludeLove, boolean excludeFolk, 
    	    DeezerService deezerService) {
    	    
    	    List<Map<String, Object>> similar = getSimilarTracks(name, artist);
    	    Collections.shuffle(similar);

    	    return similar.stream()
    	        .map(data -> {
    	            Track track = mapLastFmTrack(data, "similar");
    	            
    	            // First try Spotify preview, then Deezer fallback
    	            if (track.getPreviewUrl() == null) {
    	                String deezerPreview = deezerService.getPreviewUrlFallback(
    	                    track.getName(), 
    	                    track.getArtists()
    	                );
    	                track.setPreviewUrl(deezerPreview);
    	            }

    	            // Ensure album cover exists
    	            if (track.getAlbumCover() == null) {
    	                String cover = deezerService.getAlbumCoverFallback(
    	                    track.getName(), 
    	                    track.getArtists()
    	                );
    	                track.setAlbumCover(cover);
    	            }

    	            return track;
    	        })
    	        .filter(Objects::nonNull)
    	        .limit(20)
    	        .collect(Collectors.toList());
    	}
    
    public List<Track> getSimilarTracksWithPopularity(String name, String artist, int maxPopularity) {
        List<Map<String, Object>> similar = getSimilarTracks(name, artist);
        Collections.shuffle(similar);

        return similar.stream()
            .map(data -> {
                Track track = mapLastFmTrack(data, "similar");
                
                // Calculate popularity based on listeners count
                if (data.containsKey("listeners")) {
                    int listeners = Integer.parseInt(data.get("listeners").toString());
                    int popularity = Math.min(100, listeners / 1000); // Scale listeners to 0-100
                    track.setPopularity(popularity);
                } else {
                    track.setPopularity(30); // Default for obscure tracks
                }

                return track;
            })
            .filter(t -> t.getPopularity() < maxPopularity)
            .collect(Collectors.toList());
    }

}
