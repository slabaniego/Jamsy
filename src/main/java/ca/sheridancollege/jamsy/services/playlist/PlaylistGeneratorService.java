package ca.sheridancollege.jamsy.services.playlist;

import ca.sheridancollege.jamsy.beans.Track;
import ca.sheridancollege.jamsy.services.spotify.SpotifyApiClient;
import ca.sheridancollege.jamsy.services.spotify.SpotifyUserService;
import ca.sheridancollege.jamsy.services.spotify.SpotifyTrackService;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class PlaylistGeneratorService {

    private final SpotifyApiClient spotifyApiClient;
    private final SpotifyUserService spotifyUserService;
    private final SpotifyTrackService spotifyTrackService;

    public PlaylistGeneratorService(SpotifyApiClient spotifyApiClient,
                                    SpotifyUserService spotifyUserService,
                                    SpotifyTrackService spotifyTrackService) {
        this.spotifyApiClient = spotifyApiClient;
        this.spotifyUserService = spotifyUserService;
        this.spotifyTrackService = spotifyTrackService;
    }

    /**
     * Create an empty playlist in the user’s account.
     */
    public String createPlaylist(String accessToken, String playlistName) {
    	
    	System.out.println("🎯 spotifyApiClient instance: " + spotifyApiClient);

        try {
            String userId = spotifyUserService.getSpotifyUserId(accessToken);
            String url = SpotifyApiClient.SPOTIFY_API_URL + "/users/" + userId + "/playlists";

            Map<String, Object> body = new HashMap<>();
            body.put("name", playlistName);
            body.put("public", false);
            body.put("description", "Created from Mood/Workout discovery");

            Map response = spotifyApiClient.post(url, body, accessToken, Map.class);
            return response != null ? (String) response.get("id") : null;

        } catch (Exception e) {
            System.out.println("❌ Error createPlaylist: " + e.getMessage());
            return null;
        }
    }

    /**
     * Create a playlist and immediately populate it with tracks.
     */
    public String createPlaylistWithTracks(String accessToken, String playlistName, List<Track> tracks) {
        String playlistId = createPlaylist(accessToken, playlistName);

        if (playlistId == null) {
            System.out.println("❌ Failed to create playlist");
            return null;
        }

        // Convert tracks to Spotify URIs (with ID lookup if missing)
        List<String> trackUris = tracks.stream()
                .map(track -> {
                    if (track.getId() == null || track.getId().isBlank()) {
                        // Fallback: try to search ID by name + first artist
                        String artist = (track.getArtists() != null && !track.getArtists().isEmpty())
                                ? track.getArtists().get(0) : "";
                        String id = spotifyTrackService.searchTrackId(track.getName(), artist, accessToken);
                        track.setId(id);
                    }
                    return track.getId() != null ? "spotify:track:" + track.getId() : null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (!trackUris.isEmpty()) {
            addTracksToPlaylist(accessToken, playlistId, trackUris);
        } else {
            System.out.println("⚠️ No valid track URIs to add to playlist!");
        }

        return "https://open.spotify.com/playlist/" + playlistId;
    }

    /**
     * Add tracks to an existing playlist in batches of 100.
     */
    public void addTracksToPlaylist(String accessToken, String playlistId, List<String> trackUris) {
        if (trackUris == null || trackUris.isEmpty()) return;

        int batchSize = 100;
        for (int i = 0; i < trackUris.size(); i += batchSize) {
            List<String> batch = trackUris.subList(i, Math.min(i + batchSize, trackUris.size()));

            try {
                String url = SpotifyApiClient.SPOTIFY_API_URL + "/playlists/" + playlistId + "/tracks";

                Map<String, Object> body = new HashMap<>();
                body.put("uris", batch);

                spotifyApiClient.post(url, body, accessToken, Map.class);

                System.out.println("✅ Added " + batch.size() + " tracks to playlist " + playlistId);
            } catch (Exception e) {
                System.out.println("❌ Error adding tracks: " + e.getMessage());
            }
        }
    }
}
