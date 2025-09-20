package ca.sheridancollege.jamsy.beans;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

//Create a new class, e.g., UserTasteProfile.java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserTasteProfile {
 private List<Track> topTracks;
 private List<String> topGenres; // Calculated from top artists and tracks
 private List<String> topArtists;
 private List<Track> savedTracks;
 private List<Track> workoutTracks; // Tracks from playlists user named "workout", "gym", etc.

 // Getters and Setters
 public List<Track> getTopTracks() { return topTracks; }
 public void setTopTracks(List<Track> topTracks) { this.topTracks = topTracks; }
 // ... etc for other fields
}
