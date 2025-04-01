package ca.sheridancollege.jamsy.beans;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Track {
	private String externalUrl;
	private int popularity;
    private String name;
    private String isrc;
    private boolean explicit;
    private String previewUrl;
    private String albumCover;
    private List<String> artists;
    private List<String> genres;
}
