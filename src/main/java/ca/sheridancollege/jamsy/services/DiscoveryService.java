package ca.sheridancollege.jamsy.services;

import ca.sheridancollege.jamsy.beans.Track;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;


@Service
public class DiscoveryService {

    private final LastFmService lastFmService;
    private final SpotifyService spotifyService;

    public DiscoveryService(LastFmService lastFmService, SpotifyService spotifyService) {
        this.lastFmService = lastFmService;
        this.spotifyService = spotifyService;
    }

    public List<Track> getDiscoveryTracks(List<String> seedArtistIds, String workout, int limit) {
        Map<String, Track> uniqueTracks = new LinkedHashMap<>();
        
        // Get artist names from Spotify (with fallbacks)
        List<String> seedArtistNames = getArtistNames(seedArtistIds);
        
        // Phase 1: Get tracks from similar artists that match workout genre
        for (String artistName : seedArtistNames) {
            if (uniqueTracks.size() >= limit) break;
            
            List<String> similarArtists = lastFmService.getSimilarArtists(artistName, 10);
            for (String similarArtist : similarArtists) {
                if (uniqueTracks.size() >= limit) break;
                
                if (lastFmService.artistMatchesWorkout(similarArtist, workout)) {
                    List<Track> artistTracks = lastFmService.getArtistTopTracks(similarArtist, 5);
                    addTracksToResult(uniqueTracks, artistTracks, limit);
                }
            }
        }

        // Phase 2: If not enough tracks, get tracks from seed artists
        if (uniqueTracks.size() < limit) {
            for (String artistName : seedArtistNames) {
                if (uniqueTracks.size() >= limit) break;
                
                List<Track> artistTracks = lastFmService.getArtistTopTracks(artistName, 5);
                addTracksToResult(uniqueTracks, artistTracks, limit);
            }
        }

        // Phase 3: If still not enough, get tracks from top artists in workout genre
        if (uniqueTracks.size() < limit) {
            List<String> workoutTags = lastFmService.WORKOUT_TO_TAGS.getOrDefault(workout.toLowerCase(), 
                List.of("rock")); // default fallback
            
            for (String tag : workoutTags) {
                if (uniqueTracks.size() >= limit) break;
                
                List<String> topArtists = lastFmService.getTopArtistsByTag(tag, 10);
                for (String artist : topArtists) {
                    if (uniqueTracks.size() >= limit) break;
                    
                    List<Track> artistTracks = lastFmService.getArtistTopTracks(artist, 3);
                    addTracksToResult(uniqueTracks, artistTracks, limit);
                }
            }
        }

        return new ArrayList<>(uniqueTracks.values());
    }

    private void addTracksToResult(Map<String, Track> result, List<Track> tracks, int limit) {
        for (Track track : tracks) {
            if (result.size() >= limit) break;
            String trackKey = track.getName() + "-" + track.getArtistName();
            if (!result.containsKey(trackKey)) {
                result.put(trackKey, track);
            }
        }
    }

    private List<String> getArtistNames(List<String> artistIds) {
        List<String> artistNames = new ArrayList<>();
        for (String artistId : artistIds) {
            try {
                String artistName = spotifyService.getArtistName(artistId, "dummy-token");
                if (artistName != null) {
                    artistNames.add(artistName);
                } else {
                    artistNames.add("Artist-" + artistId.substring(0, 6));
                }
            } catch (Exception e) {
                artistNames.add("Artist-" + artistId.substring(0, 6));
            }
        }
        return artistNames;
    }
}