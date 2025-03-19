package ca.sheridancollege.jamsy.models;

import java.util.List;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

@Entity
@Data
public class SongAction {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	
	private String isrc;
	private String songName;
	private String artist;
	private String action; // like or unlike
	
	 @ElementCollection
	    private List<String> genres;  // Changed from String to List<String>

}
