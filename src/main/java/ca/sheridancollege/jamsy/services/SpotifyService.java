package ca.sheridancollege.jamsy.services;

import ca.sheridancollege.jamsy.beans.Track;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SpotifyService {

    private static final String CLIENT_ID = "a889ff03eaa84050b3d323debcf1498f";
    private static final String CLIENT_SECRET = "1b8849fc75db4306bf791e3617e7a195";
    private static final String SPOTIFY_API_URL = "https://api.spotify.com/v1";

    private final RestTemplate restTemplate = new RestTemplate();
    private final DeezerService deezerService;
    private final LastFmService lastFmService;

    public SpotifyService(LastFmService last, DeezerService deezerService) {
        this.deezerService = deezerService;
        this.lastFmService = last;
    }

    public String getUserAccessToken(String code) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        String body = "grant_type=authorization_code&code=" + code +
                "&redirect_uri=http://localhost:8080/login/oauth2/code/spotify" +
                "&client_id=" + CLIENT_ID + "&client_secret=" + CLIENT_SECRET;

        HttpEntity<String> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.exchange("https://accounts.spotify.com/api/token", HttpMethod.POST, entity, Map.class);

        return (String) response.getBody().get("access_token");
    }

    public List<Track> getTopTracks(String accessToken, boolean excludeExplicit, boolean excludeLoveSongs, boolean excludeFolk) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        String url = "https://api.spotify.com/v1/me/top/tracks?limit=50";
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

        List<Track> tracks = extractTrackItems(response.getBody());
        System.out.println("Getting top tracks");
        return applyFilters(tracks, excludeExplicit, excludeLoveSongs, excludeFolk);
    }

    private List<Track> extractTrackItems(Map<String, Object> response) {
        List<Track> tracks = new ArrayList<>();
        if (response == null || !response.containsKey("items")) return tracks;

        List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("items");
        for (Map<String, Object> item : items) {
            Track track = new Track();
            track.setName((String) item.get("name"));
            track.setExplicit((Boolean) item.get("explicit"));
            track.setIsrc(extractISRC(item));
            track.setPreviewUrl((String) item.get("preview_url"));

            // Artists
            List<Map<String, Object>> artistObjs = (List<Map<String, Object>>) item.get("artists");
            List<String> artistNames = new ArrayList<>();
            for (Map<String, Object> artist : artistObjs) {
                artistNames.add((String) artist.get("name"));
            }
            track.setArtists(artistNames);

            // Album cover
            Map<String, Object> album = (Map<String, Object>) item.get("album");
            if (album != null && album.containsKey("images")) {
                List<Map<String, Object>> images = (List<Map<String, Object>>) album.get("images");
                if (!images.isEmpty()) {
                    track.setAlbumCover((String) images.get(0).get("url"));
                }
            }

            tracks.add(track);
        }

        return tracks;
    }

    private String extractISRC(Map<String, Object> item) {
        try {
            Map<String, Object> externalIds = (Map<String, Object>) item.get("external_ids");
            return externalIds != null ? (String) externalIds.get("isrc") : null;
        } catch (Exception e) {
            return null;
        }
    }

    public List<Track> applyFilters(List<Track> tracks, boolean excludeExplicit, boolean excludeLoveSongs, boolean excludeFolk) {
        List<Track> filtered = new ArrayList<>();

        int total = 0;
        int skippedExplicit = 0;
        int skippedLove = 0;
        int skippedFolk = 0;

        for (Track track : tracks) {
            if (track == null) continue;
            total++;

            // 🔍 Fetch genres from Last.fm if missing
            if ((track.getGenres() == null || track.getGenres().isEmpty()) && track.getArtists() != null && !track.getArtists().isEmpty()) {
                List<String> genres = lastFmService.getGenresForTrack(track.getName(), track.getArtists().get(0));
                if (!genres.isEmpty()) {
                    track.setGenres(genres);
                    System.out.println("🎼 Genres for " + track.getName() + ": " + genres);
                }
            }

            boolean hasLove = track.getName() != null && track.getName().toLowerCase().contains("love");
            boolean isFolk = track.getGenres() != null && track.getGenres().stream().anyMatch(g -> g.toLowerCase().contains("folk"));

            if (excludeExplicit && track.isExplicit()) {
                skippedExplicit++;
                continue;
            }

            if (excludeLoveSongs && hasLove) {
                skippedLove++;
                continue;
            }

            if (excludeFolk && isFolk) {
                skippedFolk++;
                continue;
            }

            // Enrich preview URL via Deezer fallback
            if (track.getPreviewUrl() == null || track.getPreviewUrl().isBlank()) {
                String fallback = deezerService.getPreviewUrlFallback(track.getName(), track.getArtists());
                System.out.println("🎧 Fallback preview for " + track.getName() + ": " + fallback);
                if (fallback != null) track.setPreviewUrl(fallback);
            }

            // Enrich album cover via Deezer fallback
            if (track.getAlbumCover() == null || track.getAlbumCover().isBlank()) {
                String cover = deezerService.getAlbumCoverFallback(track.getName(), track.getArtists());
                if (cover != null) track.setAlbumCover(cover);
            }

            filtered.add(track);
        }

        // Summary log
        System.out.println("🎛️ Filter Summary:");
        System.out.println("Total input: " + total);
        if (excludeExplicit) System.out.println("⛔ Removed explicit: " + skippedExplicit);
        if (excludeLoveSongs) System.out.println("💔 Removed love songs: " + skippedLove);
        if (excludeFolk) System.out.println("🪕 Removed folk songs: " + skippedFolk);
        System.out.println("✅ Tracks kept: " + filtered.size());

        return filtered;
    }
    
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractSearchTracks(Map<String, Object> response) {
        List<Map<String, Object>> tracks = new ArrayList<>();

        if (response == null || !response.containsKey("tracks")) return tracks;

        Map<String, Object> tracksWrapper = (Map<String, Object>) response.get("tracks");
        if (tracksWrapper == null || !tracksWrapper.containsKey("items")) return tracks;

        tracks.addAll((List<Map<String, Object>>) tracksWrapper.get("items"));
        return tracks;
    }
    
    public List<Track> searchTrack(String query, String accessToken,
            boolean excludeExplicit, boolean excludeLoveSongs, boolean excludeFolk) {
		HttpHeaders headers = new HttpHeaders();
		headers.set("Authorization", "Bearer " + accessToken);
		
		HttpEntity<String> entity = new HttpEntity<>(headers);
		String url = SPOTIFY_API_URL + "/search?q=" + query + "&type=track&limit=50";
		
		ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
		List<Map<String, Object>> rawTracks = extractSearchTracks(response.getBody());
		
		// Convert raw tracks to Track objects (optional but ideal)
		List<Track> tracks = rawTracks.stream()
		.map(this::mapToTrack)
		.toList();
		
		return applyFilters(tracks, excludeExplicit, excludeLoveSongs, excludeFolk);
	}

    private Track mapToTrack(Map<String, Object> trackData) {
        Track track = new Track();
        track.setName((String) trackData.get("name"));
        track.setIsrc((String) ((Map<String, Object>) trackData.getOrDefault("external_ids", Map.of())).getOrDefault("isrc", ""));

        // Get preview URL
        track.setPreviewUrl((String) trackData.get("preview_url"));

        // Get album cover
        Map<String, Object> album = (Map<String, Object>) trackData.get("album");
        if (album != null && album.get("images") instanceof List<?> images && !images.isEmpty()) {
            Map<String, Object> firstImage = (Map<String, Object>) images.get(0);
            track.setAlbumCover((String) firstImage.get("url"));
        }

        // Get artist names
        List<Map<String, Object>> artistList = (List<Map<String, Object>>) trackData.get("artists");
        if (artistList != null) {
            List<String> artistNames = artistList.stream()
                    .map(artist -> (String) artist.get("name"))
                    .toList();
            track.setArtists(artistNames);
        }

        // Set genres (optional; if your API includes them or from fallback)
        track.setGenres((List<String>) trackData.getOrDefault("genres", List.of()));

        return track;
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
                    return Optional.of(mapToTrack(item));
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
            return Optional.of(mapToTrack(track));
        }

        return Optional.empty();
    }

    public Track getRandomSpotifyTrack(String accessToken) {
        List<Track> allTracks = getTracksFromMultipleSources(accessToken);
        if (allTracks.isEmpty()) return null;
        Collections.shuffle(allTracks);
        return allTracks.get(0);
    }
    
    public List<Track> getTracksFromMultipleSources(String accessToken) {
        List<Track> allTracks = new ArrayList<>();

        // Add top tracks
        System.out.println("Calling get top tracks which has filter method inside");
        allTracks.addAll(getTopTracks(accessToken, false, false, false));

        // Add recently played (optional fallback if not present)
        getRecentlyPlayedTracks(accessToken).ifPresent(allTracks::addAll);

        // Shuffle to ensure randomness
        Collections.shuffle(allTracks);

        return allTracks.stream().limit(20).collect(Collectors.toList());
    }
    
//    public List<Track> mergeAndShuffleTracks(String accessToken, boolean excludeExplicit, boolean excludeLoveSongs, boolean excludeFolk) {
//        List<Track> mergedTracks = new ArrayList<>();
//
//        System.out.println("💡 Fetching top tracks...");
//        List<Track> topTracks = getTopTracks(accessToken, excludeExplicit, excludeLoveSongs, excludeFolk);
//        System.out.println("🔢 Top tracks size: " + topTracks.size());
//        mergedTracks.addAll(topTracks);
//
//        getRecentlyPlayedTracks(accessToken).ifPresent(recent -> {
//            System.out.println("🕒 Recently played size: " + recent.size());
//            mergedTracks.addAll(recent);
//        });
//
//        Collections.shuffle(mergedTracks);
//        System.out.println("🎲 Shuffled sample: " + mergedTracks.stream().map(Track::getName).limit(5).toList());
//
//        return mergedTracks.stream().limit(20).toList();
//    }

    public List<Track> mergeAndShuffleTracks(String accessToken, boolean excludeExplicit, boolean excludeLoveSongs, boolean excludeFolk) {
        List<Track> mergedTracks = new ArrayList<>();

        // Add multiple sources
        List<Track> topTracks = getTopTracks(accessToken, excludeExplicit, excludeLoveSongs, excludeFolk); // already calls applyFilters
        mergedTracks.addAll(topTracks);
        
        System.out.println("Applying filters in merge and shuffle tracks");
        getRecentlyPlayedTracks(accessToken).ifPresent(rp -> mergedTracks.addAll(
            applyFilters(rp, excludeExplicit, excludeLoveSongs, excludeFolk) // 💡 this wasn't filtered before
        ));

        Collections.shuffle(mergedTracks);

        List<Track> limited = mergedTracks.stream().limit(20).toList();
        System.out.println("🎲 Final Shuffled Tracks: " + limited.stream().map(Track::getName).toList());
        return limited;
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
                    tracks.add(mapToTrack(trackData));
                }
            }

            return Optional.of(tracks);
        } catch (Exception e) {
            System.out.println("⚠️ Failed to fetch recently played tracks: " + e.getMessage());
            return Optional.empty();
        }
    }
}
