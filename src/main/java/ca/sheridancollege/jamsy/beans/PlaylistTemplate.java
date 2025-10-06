package ca.sheridancollege.jamsy.beans;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PlaylistTemplate {
	
	 private String name;
	 private String description;
	 private List<String> seedGenres;
	 private int targetEnergy;
	 private int targetTempo;
	 private boolean includeExplicit;
	 private String mood;

}
