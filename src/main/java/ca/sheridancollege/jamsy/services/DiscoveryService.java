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

    @Autowired
    public DiscoveryService(LastFmService lastFmService) {
        this.lastFmService = lastFmService;
    }

    public List<Track> getDiscoveryTracks(List<String> seedArtistNames, String workout, int limit) {
        System.out.println("Analyzing selected artists: " + seedArtistNames);
        
        // Step 1: Analyze genres of selected artists
        Map<String, Integer> genreFrequency = analyzeArtistGenres(seedArtistNames);
        System.out.println("Genre analysis: " + genreFrequency);

        // Step 2: Get similar artists for each seed artist
        List<String> allSimilarArtists = new ArrayList<>();
        Set<String> uniqueArtists = new HashSet<>();
        
        for (String artistName : seedArtistNames) {
            List<String> similarArtists = lastFmService.getSimilarArtists(artistName, 10);
            
            for (String similarArtist : similarArtists) {
                if (uniqueArtists.add(similarArtist)) {
                    allSimilarArtists.add(similarArtist);
                }
            }
        }

        System.out.println("Found similar artists: " + allSimilarArtists.size());
        System.out.println("Similar artists: " + allSimilarArtists);

        // Step 3: Get 3 tracks from each similar artist (like before)
        Map<String, Track> uniqueTracks = new LinkedHashMap<>();
        for (String similarArtist : allSimilarArtists) {
            if (uniqueTracks.size() >= limit * 2) break; // Get more than needed for better shuffling
            
            System.out.println("Getting tracks for: " + similarArtist);
            List<Track> artistTracks = lastFmService.getArtistTopTracks(similarArtist, 3);
            System.out.println("Found " + artistTracks.size() + " tracks for " + similarArtist);
            
            // Add tracks to result
            for (Track track : artistTracks) {
                if (uniqueTracks.size() >= limit * 2) break;
                String trackKey = track.getName() + "-" + track.getArtistName();
                if (!uniqueTracks.containsKey(trackKey) && track.getName() != null && track.getArtistName() != null) {
                    uniqueTracks.put(trackKey, track);
                }
            }
        }

        System.out.println("Total unique tracks found: " + uniqueTracks.size());
        
        // Step 4: Print all retrieved songs
        System.out.println("=== ALL RETRIEVED SONGS ===");
        List<Track> allTracks = new ArrayList<>(uniqueTracks.values());
        for (Track track : allTracks) {
            System.out.println("Song: " + track.getName() + " by " + track.getArtistName());
        }
        System.out.println("===========================");

        // Step 5: Shuffle and return exactly 20 tracks
        Collections.shuffle(allTracks);
        
        if (allTracks.size() > limit) {
            allTracks = allTracks.subList(0, limit);
        }
        
        System.out.println("Final track count for display: " + allTracks.size());
        return allTracks;
    }

    private Map<String, Integer> analyzeArtistGenres(List<String> artistNames) {
        Map<String, Integer> genreCount = new HashMap<>();
        
        for (String artistName : artistNames) {
            List<String> genres = lastFmService.getArtistGenres(artistName);
            for (String genre : genres) {
                genreCount.put(genre, genreCount.getOrDefault(genre, 0) + 1);
            }
        }
        
        return genreCount;
    }

    private List<String> findRelevantSimilarArtists(List<String> seedArtistNames, Map<String, Integer> genreFrequency, String workout) {
        List<String> allSimilarArtists = new ArrayList<>();
        Set<String> uniqueArtists = new HashSet<>();
        
        // Get top 3 genres
        List<String> topGenres = genreFrequency.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(3)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());

        System.out.println("Top genres from selection: " + topGenres);

        // Get similar artists with basic genre matching
        for (String artistName : seedArtistNames) {
            List<String> similarArtists = lastFmService.getSimilarArtists(artistName, 10);
            
            for (String similarArtist : similarArtists) {
                if (uniqueArtists.add(similarArtist)) {
                    // Simple check: include if it matches workout OR shares a genre
                    if (lastFmService.artistMatchesWorkout(similarArtist, workout) || 
                        sharesAnyGenre(similarArtist, topGenres)) {
                        allSimilarArtists.add(similarArtist);
                        System.out.println("Added similar artist: " + similarArtist);
                    }
                }
            }
        }

        System.out.println("Total similar artists found: " + allSimilarArtists.size());
        Collections.shuffle(allSimilarArtists);
        return allSimilarArtists;
    }

    private boolean sharesAnyGenre(String artistName, List<String> topGenres) {
        List<String> artistGenres = lastFmService.getArtistGenres(artistName);
        return artistGenres.stream()
            .anyMatch(artistGenre -> topGenres.stream()
                .anyMatch(topGenre -> artistGenre.toLowerCase().contains(topGenre.toLowerCase())));
    }
    
    private boolean isArtistRelevantForWorkout(String artistName, String workout, List<String> topGenres) {
        // First priority: check if artist matches workout vibe
        boolean matchesWorkout = lastFmService.artistMatchesWorkout(artistName, workout);
        
        // If it matches workout, include it regardless of genre
        if (matchesWorkout) {
            return true;
        }
        
        // Second priority: check if artist shares any genre with top genres
        List<String> artistGenres = lastFmService.getArtistGenres(artistName);
        
        return artistGenres.stream()
            .anyMatch(artistGenre -> topGenres.stream()
                .anyMatch(topGenre -> artistGenre.toLowerCase().contains(topGenre.toLowerCase()) || 
                                     topGenre.toLowerCase().contains(artistGenre.toLowerCase())));
    }

    private boolean isArtistRelevant(String artistName, List<String> topGenres, String workout) {
        List<String> artistGenres = lastFmService.getArtistGenres(artistName);
        
        boolean sharesGenres = artistGenres.stream()
            .anyMatch(artistGenre -> topGenres.stream()
                .anyMatch(topGenre -> artistGenre.contains(topGenre) || topGenre.contains(artistGenre)));

        boolean matchesWorkout = lastFmService.artistMatchesWorkout(artistName, workout);

        return sharesGenres && matchesWorkout;
    }

    private void addTracksToResult(Map<String, Track> result, List<Track> tracks, int maxSize) {
        // Shuffle the incoming tracks to avoid same-artist clustering
        List<Track> shuffledTracks = new ArrayList<>(tracks);
        Collections.shuffle(shuffledTracks);
        
        for (Track track : shuffledTracks) {
            if (result.size() >= maxSize) break;
            
            String trackKey = track.getName() + "-" + track.getArtistName();
            if (!result.containsKey(trackKey) && track.getName() != null && track.getArtistName() != null) {
                result.put(trackKey, track);
            }
        }
    }
    
	/* Extended playlist ( final result ) */
    public List<Track> generateOneHourPlaylist(List<Track> likedTracks, int targetMinutes) {
        int targetMs = targetMinutes * 60 * 1000;
        List<Track> finalPlaylist = new ArrayList<>(likedTracks);

        // Start with duration of liked tracks
        int totalDuration = likedTracks.stream()
                                       .mapToInt(t -> t.getDurationMs() > 0 ? t.getDurationMs() : 0)
                                       .sum();

        // Collect similar songs
        List<Track> pool = new ArrayList<>();
        for (Track seed : likedTracks) {
            pool.addAll(lastFmService.getSimilarTracks(seed.getName(), seed.getArtistName(), 20));
        }

        // Deduplicate and shuffle
        Collections.shuffle(pool);
        Set<String> seen = new HashSet<>();
        List<Track> uniquePool = pool.stream()
            .filter(t -> seen.add(t.getName() + "-" + t.getArtistName()))
            .collect(Collectors.toList());

        // Add until 1 hour
        for (Track candidate : uniquePool) {
            if (totalDuration >= targetMs) break;
            if (candidate.getDurationMs() > 0) {
                finalPlaylist.add(candidate);
                totalDuration += candidate.getDurationMs();
            }
        }

        return finalPlaylist;
    }
    
    public List<Map<String, Object>> convertTracksForFrontend(List<Track> tracks) {
        List<Map<String, Object>> frontendTracks = new ArrayList<>();

        for (Track track : tracks) {
            Map<String, Object> frontendTrack = new HashMap<>();
            
            frontendTrack.put("id", track.getId() != null ? track.getId() : UUID.randomUUID().toString());
            frontendTrack.put("name", track.getName() != null ? track.getName() : "Unknown Title");

            // Artists → prefer list, fallback to single name
            if (track.getArtists() != null && !track.getArtists().isEmpty()) {
                frontendTrack.put("artists", track.getArtists());
            } else if (track.getArtistName() != null && !track.getArtistName().isEmpty()) {
                frontendTrack.put("artists", Collections.singletonList(track.getArtistName()));
            } else {
                frontendTrack.put("artists", Collections.singletonList("Unknown Artist"));
            }

            // Album cover
            frontendTrack.put("albumCover", 
                (track.getImageUrl() != null && !track.getImageUrl().isEmpty())
                    ? track.getImageUrl()
                    : "/images/default-cover.jpg");

            // Preview (if set)
            frontendTrack.put("previewUrl", track.getPreviewUrl());

            // Genres
            frontendTrack.put("genres", track.getGenres() != null ? track.getGenres() : Collections.emptyList());

            // Duration (ms)
            frontendTrack.put("durationMs", track.getDurationMs());

            frontendTracks.add(frontendTrack);
        }

        return frontendTracks;
    }
}