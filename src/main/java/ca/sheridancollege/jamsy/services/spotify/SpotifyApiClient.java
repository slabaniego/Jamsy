package ca.sheridancollege.jamsy.services.spotify;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;


/*
 *A thin wrapper around RestTemplate and rate limiting 
 **/
@Service
public class SpotifyApiClient {
	
	public static final String SPOTIFY_API_URL = "https://api.spotify.com/v1";
	 private final RestTemplate restTemplate = new RestTemplate();
	
	private static final int MAX_REQUESTS_PER_MINUTE = 50;
    private static final Map<String, RateLimitInfo> rateLimitMap = new ConcurrentHashMap<>();
	
	 // Add this inner class
    private static class RateLimitInfo {
        private int requestCount = 0;
        private long lastResetTime = System.currentTimeMillis();
    }
    
    // Modify your methods to include rate limiting
    public synchronized void checkRateLimit(String endpoint) {
        RateLimitInfo info = rateLimitMap.computeIfAbsent(endpoint, k -> new RateLimitInfo());
        
        long currentTime = System.currentTimeMillis();
        if (currentTime - info.lastResetTime > 60000) { // 1 minute
            info.requestCount = 0;
            info.lastResetTime = currentTime;
        }
        
        if (info.requestCount >= MAX_REQUESTS_PER_MINUTE) {
            try {
                Thread.sleep(1000); // Wait 1 second
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            info.requestCount = 0;
            info.lastResetTime = System.currentTimeMillis();
        }
        
        info.requestCount++;
    }

    
    /**
     * Generic GET request to Spotify API.
     */
    public <T> T get(String url, String accessToken, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<T> response = restTemplate.exchange(url, HttpMethod.GET, entity, responseType);
        return response.getBody();
    }

    /**
     * Generic POST request to Spotify API.
     */
    public <T> T post(String url, Object body, String accessToken, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Object> entity = new HttpEntity<>(body, headers);

        ResponseEntity<T> response = restTemplate.exchange(url, HttpMethod.POST, entity, responseType);
        return response.getBody();
    }

    /**
     * Generic PUT request to Spotify API (optional).
     */
    public <T> T put(String url, Object body, String accessToken, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Object> entity = new HttpEntity<>(body, headers);

        ResponseEntity<T> response = restTemplate.exchange(url, HttpMethod.PUT, entity, responseType);
        return response.getBody();
    }
}
