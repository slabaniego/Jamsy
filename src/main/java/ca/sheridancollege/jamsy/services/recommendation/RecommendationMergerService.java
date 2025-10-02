package ca.sheridancollege.jamsy.services.recommendation;

import ca.sheridancollege.jamsy.beans.Track;
import ca.sheridancollege.jamsy.services.LastFmService;
import ca.sheridancollege.jamsy.services.spotify.SpotifyUserService;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class RecommendationMergerService {

    private final SpotifyUserService spotifyUserService;
    private final LastFmService lastFmService;

    public RecommendationMergerService(
            SpotifyUserService spotifyUserService,
            LastFmService lastFmService
    ) {
        this.spotifyUserService = spotifyUserService;
        this.lastFmService = lastFmService;
    }

    /**
     * Merge Spotify + Last.fm recs and shuffle.
     */
    public List<Track> mergeAndShuffle(String accessToken, int limit) {
        List<Track> combined = new ArrayList<>();

        // ✅ Get Spotify top tracks
        combined.addAll(spotifyUserService.getTopTracks(accessToken));

        // ✅ Get Spotify recently played
        spotifyUserService.getRecentlyPlayedTracks(accessToken)
                .ifPresent(combined::addAll);

        // ✅ Add Last.fm data (example: similar tracks for first top track)
        if (!combined.isEmpty()) {
            Track seed = combined.get(0); // take the first top/recent track
            if (seed.getName() != null && seed.getArtists() != null && !seed.getArtists().isEmpty()) {
                List<Track> similar = lastFmService.getSimilarTracks(seed.getName(), seed.getArtists().get(0), 10);
                combined.addAll(similar);
            }
        }

        // ✅ Shuffle
        Collections.shuffle(combined);

        // ✅ Deduplicate by ID or name+artist
        Map<String, Track> unique = new LinkedHashMap<>();
        for (Track t : combined) {
            if (t.getId() != null) {
                unique.putIfAbsent("id:" + t.getId(), t);
            } else {
                String key = "na:" + (t.getName() + "|" + String.join(",", t.getArtists())).toLowerCase();
                unique.putIfAbsent(key, t);
            }
        }

        return unique.values().stream().limit(limit).toList();
    }
}
