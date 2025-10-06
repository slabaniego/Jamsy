package ca.sheridancollege.jamsy.services.recommendation;

import ca.sheridancollege.jamsy.beans.Track;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class RecommendationFilterService {

    /**
     * Apply filters to a list of tracks.
     *
     * @param tracks           the original track list
     * @param excludeExplicit  if true, drop explicit tracks
     * @param excludeLoveSongs if true, drop love/romance-themed tracks
     * @param excludeFolk      if true, drop folk/indie/folk-pop songs
     * @return filtered list of tracks
     */
    public List<Track> filterTracks(
            List<Track> tracks,
            boolean excludeExplicit,
            boolean excludeLoveSongs,
            boolean excludeFolk
    ) {
        return tracks.stream()
                .filter(track -> !shouldExclude(track, excludeExplicit, excludeLoveSongs, excludeFolk))
                .collect(Collectors.toList());
    }

    private boolean shouldExclude(Track track,
                                  boolean excludeExplicit,
                                  boolean excludeLoveSongs,
                                  boolean excludeFolk) {
        if (track == null) return true; // drop null entries

        // Explicit filter
        if (excludeExplicit && Boolean.TRUE.equals(track.isExplicit())) {
            return true;
        }

        // Love songs filter (check name + genres)
        if (excludeLoveSongs && containsLoveTheme(track)) {
            return true;
        }

        // Folk filter (check genres)
        if (excludeFolk && containsFolk(track)) {
            return true;
        }

        return false;
    }

    private boolean containsLoveTheme(Track track) {
        String name = track.getName() != null ? track.getName().toLowerCase(Locale.ROOT) : "";
        List<String> genres = track.getGenres() != null ? track.getGenres() : List.of();

        return name.contains("love") ||
               name.contains("romance") ||
               genres.stream().anyMatch(g -> g.toLowerCase(Locale.ROOT).contains("romance") ||
                                             g.toLowerCase(Locale.ROOT).contains("love"));
    }

    private boolean containsFolk(Track track) {
        List<String> genres = track.getGenres() != null ? track.getGenres() : List.of();
        return genres.stream().anyMatch(g -> g.toLowerCase(Locale.ROOT).contains("folk"));
    }
}
