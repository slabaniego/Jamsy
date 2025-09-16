package ca.sheridancollege.jamsy.services;

import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Service;

import ca.sheridancollege.jamsy.beans.PlaylistTemplate;

@Service
public class PlaylistTemplateService {
	
	public List<PlaylistTemplate> getDefaultTemplates(){
		return Arrays.asList(
				new PlaylistTemplate("Yoga Session", "Calm and relaxing background",
						Arrays.asList("ambient", "acoustic", "chill"), 30, 80, false),
				
				new PlaylistTemplate("Weight Lifting", "Push yourself with heavy and motivational beats",
						Arrays.asList("rock", "metal", "alternative", "hip-hop"), 85, 130, true),
				
				new PlaylistTemplate("Running", "Pace yourself and keep moving",
						Arrays.asList("edm", "pop", "dance", "house"), 90, 160, false)
		);
				
	}

}
