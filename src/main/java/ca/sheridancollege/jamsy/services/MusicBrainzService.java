package ca.sheridancollege.jamsy.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class MusicBrainzService {

	 private static final String MUSIC_BRAINZ_API_URL = "https://musicbrainz.org/ws/2";

	    public List<Map<String, Object>> searchTrack(String query) {
	        RestTemplate restTemplate = new RestTemplate();
	        String url = MUSIC_BRAINZ_API_URL + "/recording?query=" + query + "&fmt=json";

	        Map<String, Object> response = restTemplate.getForObject(url, Map.class);
	        return extractTracks(response);
	    }

	    private List<Map<String, Object>> extractTracks(Map<String, Object> response) {
	        List<Map<String, Object>> tracks = new ArrayList<>();
	        if (response == null || response.get("recordings") == null) {
	            return tracks;
	        }
	        List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("recordings");
	        for (Map<String, Object> item : items) {
	            tracks.add(item);
	        }
	        return tracks;
	    }
	    
}
