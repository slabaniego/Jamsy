package ca.sheridancollege.jamsy.services;

import ca.sheridancollege.jamsy.beans.Track;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;


@Service
public class DiscoveryService {

    private final LastFmService lastFmService;
    
    private final int NUMBER_OF_ARTISTS = 10;
    
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${lastfm.api.base-url}")
    private String baseUrl;
    
    @Value("${lastfm.api.key}")
    private String apiKey;

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
        	
        	//using last fm service to get similar artists to the selected artist
            List<String> similarArtists = lastFmService.getSimilarArtists(artistName, NUMBER_OF_ARTISTS);
            
            for (String similarArtist : similarArtists) {
                if (uniqueArtists.add(similarArtist)) {
                    allSimilarArtists.add(similarArtist);
                }
            }
        }

        System.out.println("Found similar artists: " + allSimilarArtists.size());
        System.out.println("Similar artists: " + allSimilarArtists);

        // Step 3: Get ONLY 1 track from each similar artist
        Map<String, Track> uniqueTracks = new LinkedHashMap<>();
        Set<String> artistsUsed = new HashSet<>();
        
        // Shuffle the artists first to ensure random selection
        Collections.shuffle(allSimilarArtists);
        
        for (String similarArtist : allSimilarArtists) {
            if (uniqueTracks.size() >= limit * 2) break; // Get more than needed for better shuffling
            
            // Skip if we've already used this artist
            if (artistsUsed.contains(similarArtist)) {
                continue;
            }
            
            System.out.println("Getting tracks for: " + similarArtist);
            List<Track> artistTracks = lastFmService.getArtistTopTracks(similarArtist, 3);
            System.out.println("Found " + artistTracks.size() + " tracks for " + similarArtist);
            
            // Take only ONE track from this artist
            if (!artistTracks.isEmpty()) {
                // Randomly select one track from the available tracks
                Track selectedTrack = artistTracks.get(new Random().nextInt(artistTracks.size()));
                String trackKey = selectedTrack.getName() + "-" + selectedTrack.getArtistName();
                
                if (!uniqueTracks.containsKey(trackKey) && selectedTrack.getName() != null && selectedTrack.getArtistName() != null) {
                    uniqueTracks.put(trackKey, selectedTrack);
                    artistsUsed.add(similarArtist); // Mark this artist as used
                    System.out.println("✅ Selected 1 track from " + similarArtist + ": " + selectedTrack.getName());
                }
            }
        }

        System.out.println("Total unique tracks found: " + uniqueTracks.size());
        
        // Step 4: Print all retrieved songs with artist info
        System.out.println("=== ALL RETRIEVED SONGS (1 per artist) ===");
        List<Track> allTracks = new ArrayList<>(uniqueTracks.values());
        for (Track track : allTracks) {
            System.out.println("🎵 " + track.getName() + " by " + track.getArtistName());
        }
        System.out.println("Total artists represented: " + artistsUsed.size());
        System.out.println("===========================");

        // Step 5: Shuffle and return exactly the limit
        Collections.shuffle(allTracks);
        
        if (allTracks.size() > limit) {
            allTracks = allTracks.subList(0, limit);
        }
        
        System.out.println("Final track count for display: " + allTracks.size());
        System.out.println("Final artists represented: " + 
            allTracks.stream().map(Track::getArtistName).distinct().count());
        
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
    
    
    /*UPDATED ATIN START*/
	/* Extended playlist ( final result ) */
    public List<Track> generateOneHourPlaylist(List<Track> likedTracks, int targetMinutes) {
    	
    	System.out.println("Inside the generate playlist method");
        int targetMs = targetMinutes * 60 * 1000;
        int minSongs = 50; // Minimum 50 songs
        
        System.out.println("🎵 Starting playlist generation with " + likedTracks.size() + " liked tracks");
        System.out.println("🎯 Target: " + minSongs + " songs, " + targetMinutes + " minutes");
        
        // Create sets for tracking - EXCLUDE LIKED TRACKS
        Set<String> likedTrackKeys = likedTracks.stream()
                .map(t -> t.getName().toLowerCase() + "|" + t.getArtistName().toLowerCase())
                .collect(Collectors.toSet());
        
        Set<String> addedTrackKeys = new HashSet<>(); // DO NOT include liked tracks
        Map<String, Integer> artistCount = new HashMap<>();
        
        // Step 1: Get similar tracks ONLY for the liked tracks
        List<Track> similarPool = new ArrayList<>();
        Set<String> processedSeeds = new HashSet<>();
        
        for (Track likedTrack : likedTracks) {
            String seedKey = likedTrack.getName().toLowerCase() + "|" + likedTrack.getArtistName().toLowerCase();
            if (!processedSeeds.add(seedKey)) {
                continue; // Skip duplicate seeds
            }
            
            System.out.println("🔍 Finding similar tracks for: " + likedTrack.getName() + " by " + likedTrack.getArtistName());
            
            List<Track> similarTracks = lastFmService.getSimilarTracks(
                likedTrack.getName(), 
                likedTrack.getArtistName(), 
                25 // Get more to account for obscurity filtering
            );
            
            System.out.println("📊 Found " + similarTracks.size() + " similar tracks for " + likedTrack.getName());
            
            // Filter out duplicates and tracks already in liked songs
            for (Track similar : similarTracks) {
                String trackKey = similar.getName().toLowerCase() + "|" + similar.getArtistName().toLowerCase();
                
                if (!likedTrackKeys.contains(trackKey) && !addedTrackKeys.contains(trackKey)) {
                    similarPool.add(similar);
                }
            }
        }
        
        System.out.println("📊 Total similar pool: " + similarPool.size() + " tracks");
        
        // Step 2: Remove duplicates from similar pool
        Set<String> poolKeys = new HashSet<>();
        List<Track> uniqueSimilarPool = similarPool.stream()
                .filter(t -> {
                    String key = t.getName().toLowerCase() + "|" + t.getArtistName().toLowerCase();
                    return poolKeys.add(key);
                })
                .collect(Collectors.toList());
        
        System.out.println("📊 After deduplication: " + uniqueSimilarPool.size() + " unique similar tracks");
        
        // Step 3: Sort by match score (best matches first)
        uniqueSimilarPool.sort(Comparator.comparing(Track::getMatchScore).reversed());
        
        // Step 4: Build final playlist - DO NOT INCLUDE LIKED TRACKS
        List<Track> finalPlaylist = new ArrayList<>(); // Start empty - no liked tracks!
        int totalDuration = 0;
        
        // Step 5: Add similar tracks that match our criteria
        for (Track candidate : uniqueSimilarPool) {
            // Stop if we have enough songs AND duration
            if (finalPlaylist.size() >= minSongs && totalDuration >= targetMs) {
                break;
            }
            
            String artistKey = candidate.getArtistName().toLowerCase();
            String trackKey = candidate.getName().toLowerCase() + "|" + artistKey;
            
            // Skip if already added
            if (addedTrackKeys.contains(trackKey)) {
                continue;
            }
            
            // Check artist limit (max 2 per artist)
            int currentArtistCount = artistCount.getOrDefault(artistKey, 0);
            if (currentArtistCount >= 2) {
                continue;
            }
            
            // Add the track
            finalPlaylist.add(candidate);
            addedTrackKeys.add(trackKey);
            artistCount.put(artistKey, currentArtistCount + 1);
            totalDuration += candidate.getDurationMs() > 0 ? candidate.getDurationMs() : 180000;
            
            System.out.println("✅ Added to playlist: " + candidate.getName() + " by " + candidate.getArtistName());
        }
        
        // Step 6: If we still don't have enough songs, try different approach
        if (finalPlaylist.size() < minSongs) {
            System.out.println("🔄 Need more songs, current: " + finalPlaylist.size() + ", target: " + minSongs);
            getMoreObscureTracks(likedTracks, finalPlaylist, addedTrackKeys, artistCount, minSongs - finalPlaylist.size(), likedTrackKeys);
        }
        
        // Final shuffle for variety
        Collections.shuffle(finalPlaylist);
        
        System.out.println("🎉 Final playlist: " + finalPlaylist.size() + " similar tracks (NO LIKED TRACKS)");
        System.out.println("⏱️  Total duration: " + (totalDuration / 60000) + " minutes");
        
        return finalPlaylist;
    }

    private void getMoreObscureTracks(List<Track> likedTracks, List<Track> finalPlaylist, 
                                     Set<String> addedTrackKeys, Map<String, Integer> artistCount, 
                                     int needed, Set<String> likedTrackKeys) {
        if (needed <= 0) return;
        
        System.out.println("🔍 Getting " + needed + " more obscure tracks...");
        
        // Try getting tracks by tags or other methods to find obscure artists
        for (Track likedTrack : likedTracks) {
            if (needed <= 0) break;
            
            // Try getting top tracks from similar obscure artists
            List<Track> artistBasedTracks = getTracksFromSimilarObscureArtists(likedTrack, needed * 2, likedTrackKeys);
            
            for (Track candidate : artistBasedTracks) {
                if (needed <= 0) break;
                
                String artistKey = candidate.getArtistName().toLowerCase();
                String trackKey = candidate.getName().toLowerCase() + "|" + artistKey;
                
                // Skip if already added, in liked tracks, or artist limit reached
                if (addedTrackKeys.contains(trackKey) || 
                    likedTrackKeys.contains(trackKey) ||
                    artistCount.getOrDefault(artistKey, 0) >= 2) {
                    continue;
                }
                
                // Add the track
                finalPlaylist.add(candidate);
                addedTrackKeys.add(trackKey);
                artistCount.put(artistKey, artistCount.getOrDefault(artistKey, 0) + 1);
                needed--;
                
                System.out.println("✅ Added extra obscure: " + candidate.getName() + " by " + candidate.getArtistName());
            }
        }
    }

    private List<Track> getTracksFromSimilarObscureArtists(Track seed, int limit, Set<String> likedTrackKeys) {
        List<Track> tracks = new ArrayList<>();
        try {
            // Get similar artists
            String url = baseUrl + "?method=artist.getSimilar" +
                    "&artist=" + URLEncoder.encode(seed.getArtistName(), StandardCharsets.UTF_8) +
                    "&api_key=" + apiKey +
                    "&format=json&limit=10";
            
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> body = response.getBody();
                if (body != null && body.containsKey("similarartists")) {
                    Map<String, Object> similarArtists = (Map<String, Object>) body.get("similarartists");
                    if (similarArtists.containsKey("artist")) {
                        List<Map<String, Object>> artistList = 
                            (List<Map<String, Object>>) similarArtists.get("artist");
                        
                        for (Map<String, Object> artist : artistList) {
                            if (tracks.size() >= limit) break;
                            
                            String artistName = (String) artist.get("name");
                            
                            // Check if this artist is obscure
                            if (lastFmService.isArtistObscure(artistName)) {
                                // Get top tracks from this obscure artist
                                List<Track> topTracks = getTopTracksForArtist(artistName, 3);
                                
                                for (Track track : topTracks) {
                                    String trackKey = track.getName().toLowerCase() + "|" + track.getArtistName().toLowerCase();
                                    if (!likedTrackKeys.contains(trackKey)) {
                                        tracks.add(track);
                                        if (tracks.size() >= limit) break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("❌ Error getting similar artists: " + e.getMessage());
        }
        return tracks;
    }

    private List<Track> getTopTracksForArtist(String artistName, int limit) {
        try {
            String url = baseUrl + "?method=artist.getTopTracks" +
                    "&artist=" + URLEncoder.encode(artistName, StandardCharsets.UTF_8) +
                    "&api_key=" + apiKey +
                    "&format=json&limit=" + limit;
            
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> body = response.getBody();
                if (body != null && body.containsKey("toptracks")) {
                    Map<String, Object> topTracks = (Map<String, Object>) body.get("toptracks");
                    if (topTracks.containsKey("track")) {
                        List<Map<String, Object>> trackList = 
                            (List<Map<String, Object>>) topTracks.get("track");
                        
                        return trackList.stream().map(trackMap -> {
                            Track track = new Track();
                            track.setName((String) trackMap.get("name"));
                            track.setArtistName(artistName);
                            track.setArtists(Collections.singletonList(artistName));
                            track.setMatchScore(0.6f); // Good score for top tracks
                            
                            // Duration
                            if (trackMap.containsKey("duration")) {
                                Number duration = (Number) trackMap.get("duration");
                                track.setDurationMs(duration != null ? duration.intValue() * 1000 : 180000);
                            } else {
                                track.setDurationMs(180000);
                            }
                            
                            // Album image
                            if (trackMap.containsKey("image")) {
                                List<Map<String, Object>> images =
                                        (List<Map<String, Object>>) trackMap.get("image");
                                for (Map<String, Object> image : images) {
                                    if ("medium".equals(image.get("size"))) {
                                        String imageUrl = (String) image.get("#text");
                                        if (imageUrl != null && !imageUrl.isEmpty()) {
                                            track.setImageUrl(imageUrl);
                                            break;
                                        }
                                    }
                                }
                            }
                            
                            return track;
                        }).limit(limit).collect(Collectors.toList());
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("❌ Error getting top tracks for " + artistName + ": " + e.getMessage());
        }
        return Collections.emptyList();
    }
	
    public List<Map<String, Object>> convertTracksForFrontend(List<Track> tracks) {
        List<Map<String, Object>> frontendTracks = new ArrayList<>();

        for (Track track : tracks) {
            Map<String, Object> frontendTrack = new HashMap<>();
            
            frontendTrack.put("id", track.getId() != null ? track.getId() : UUID.randomUUID().toString());
            frontendTrack.put("name", track.getName() != null ? track.getName() : "Unknown Title");


            // Preview (if set)
            frontendTrack.put("previewUrl", track.getPreviewUrl());

            // Genres
            frontendTrack.put("genres", track.getGenres() != null ? track.getGenres() : Collections.emptyList());


            frontendTracks.add(frontendTrack);
        }

        return frontendTracks;
    }
}
