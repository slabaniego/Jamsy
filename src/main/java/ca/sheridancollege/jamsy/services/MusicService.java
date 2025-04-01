package ca.sheridancollege.jamsy.services;

import java.util.List;

import org.springframework.stereotype.Service;

import ca.sheridancollege.jamsy.beans.Track;

@Service
public class MusicService {

    private final SpotifyService spotifyService;

    public MusicService(SpotifyService spotifyService) {
        this.spotifyService = spotifyService;
    }

    /**
     * Search for tracks from Spotify with optional filters.
     */
    public List<Track> searchFilteredTracks(String query, String accessToken, boolean excludeExplicit, boolean excludeLoveSongs, boolean excludeFolk) {
        return spotifyService.searchTrack(query, accessToken, excludeExplicit, excludeLoveSongs, excludeFolk);
    }
}
