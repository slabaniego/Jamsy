package ca.sheridancollege.jamsy.services.playlist;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;

import ca.sheridancollege.jamsy.beans.Track;
import ca.sheridancollege.jamsy.services.spotify.SpotifyTrackService;
import ca.sheridancollege.jamsy.services.spotify.SpotifyUserService;


/*
 * Service prepares candidate tracks for playlists
 * */
@Service
public class PlaylistDataService {

	private final SpotifyUserService spotifyUserService;
    private final SpotifyTrackService spotifyTrackService;

    public PlaylistDataService(SpotifyUserService spotifyUserService,
                               SpotifyTrackService spotifyTrackService) {
        this.spotifyUserService = spotifyUserService;
        this.spotifyTrackService = spotifyTrackService;
    }

    // Reuse SpotifyUserService
    public List<Track> getTopTracks(String accessToken) {
        return spotifyUserService.getTopTracks(accessToken);
    }

    // Reuse SpotifyTrackService
    public List<String> searchArtistTopTracks(String artistName, String accessToken, int limit) {
        return spotifyTrackService.searchArtistTopTracks(artistName, accessToken, limit);
    }

    // Collect tracks from multiple sources (Spotify top + recently played, etc.)
    public List<Track> getTracksFromMultipleSources(String accessToken) {
        List<Track> allTracks = new ArrayList<>();
        allTracks.addAll(spotifyUserService.getTopTracks(accessToken));
        spotifyUserService.getRecentlyPlayedTracks(accessToken).ifPresent(allTracks::addAll);

        Collections.shuffle(allTracks);
        return allTracks.stream().limit(20).toList();
    }

    // Merge + shuffle
    public List<Track> mergeAndShuffleTracks(String accessToken) {
        List<Track> tracks = new ArrayList<>(spotifyUserService.getTopTracks(accessToken));
        Collections.shuffle(tracks);
        return tracks.stream().limit(20).toList();
    }
	
}
