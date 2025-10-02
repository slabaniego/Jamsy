package ca.sheridancollege.jamsy.services.spotify;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import ca.sheridancollege.jamsy.beans.Track;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Service
@AllArgsConstructor
@NoArgsConstructor
public class SpotifyUserService {
	
	private RestTemplate restTemplate;
	private SpotifyTrackService spotifyTrackService;

	 public String getSpotifyUserId(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        HttpEntity<String> entity = new HttpEntity<>("parameters", headers);
        ResponseEntity<Map> response = restTemplate.exchange(
                "https://api.spotify.com/v1/me",
                HttpMethod.GET,
                entity,
                Map.class);

        return (String) response.getBody().get("id");
    }

	 public List<Track> getTopTracks(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        String url = "https://api.spotify.com/v1/me/top/tracks?limit=50";
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

        List<Track> tracks = spotifyTrackService.extractTrackItems(response.getBody());
        System.out.println("Getting top tracks");
        return tracks;
    }
	 
	 public Optional<List<Track>> getRecentlyPlayedTracks(String accessToken) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            String url = "https://api.spotify.com/v1/me/player/recently-played?limit=20";
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            List<Map<String, Object>> items = (List<Map<String, Object>>) response.getBody().get("items");

            List<Track> tracks = new ArrayList<>();
            for (Map<String, Object> item : items) {
                Map<String, Object> trackData = (Map<String, Object>) item.get("track");
                if (trackData != null) {
                    tracks.add(spotifyTrackService.mapToTrack(trackData));
                }
            }

            return Optional.of(tracks);
        } catch (Exception e) {
            System.out.println("⚠️ Failed to fetch recently played tracks: " + e.getMessage());
            return Optional.empty();
        }
    }
	 
	 public Optional<Track> getCurrentlyPlayingTrack(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        String url = "https://api.spotify.com/v1/me/player/currently-playing";
        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> item = (Map<String, Object>) response.getBody().get("item");
                if (item != null) {
                    return Optional.of(spotifyTrackService.mapToTrack(item));
                }
            }
        } catch (Exception e) {
            // fallback
            System.out.println("⚠️ Error fetching currently playing, falling back to recently played.");
        }

        // Fallback: recently played
        url = "https://api.spotify.com/v1/me/player/recently-played?limit=1";
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
        List<Map<String, Object>> items = (List<Map<String, Object>>) response.getBody().get("items");
        if (items != null && !items.isEmpty()) {
            Map<String, Object> track = (Map<String, Object>) items.get(0).get("track");
            return Optional.of(spotifyTrackService.mapToTrack(track));
        }

        return Optional.empty();
    }
	 
	 public Track getRandomSpotifyTrack(String accessToken) {
        List<Track> allTracks = getTracksFromMultipleSources(accessToken);
        if (allTracks.isEmpty())
            return null;
        Collections.shuffle(allTracks);
        return allTracks.get(0);
    }
	 
	 public List<Track> getTracksFromMultipleSources(String accessToken) {
        List<Track> allTracks = new ArrayList<>();

        // Add top tracks
        System.out.println("Calling get top tracks which has filter method inside");
        allTracks.addAll(getTopTracks(accessToken));

        // Add recently played (optional fallback if not present)
        getRecentlyPlayedTracks(accessToken).ifPresent(allTracks::addAll);

        // Shuffle to ensure randomness
        Collections.shuffle(allTracks);

        return allTracks.stream().limit(20).collect(Collectors.toList());
    }

    public List<Track> mergeAndShuffleTracks(String accessToken) {
        List<Track> tracks = new ArrayList<>();

        // Get tracks from Spotify top tracks
        tracks.addAll(getTopTracks(accessToken));


        // Shuffle the tracks
        Collections.shuffle(tracks);

        return tracks.stream().limit(20).collect(Collectors.toList());
    }
}
