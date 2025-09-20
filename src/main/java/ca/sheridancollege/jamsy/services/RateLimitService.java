package ca.sheridancollege.jamsy.services;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitService {
    
    private final Map<String, Long> lastRequestTimes = new ConcurrentHashMap<>();
    private final Map<String, Integer> retryCounts = new ConcurrentHashMap<>();
    
    private static final long DEFAULT_DELAY_MS = 1000; // 1 second between requests
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 2000; // 2 seconds for retries
    
    public void waitIfNeeded(String endpoint) {
        long currentTime = System.currentTimeMillis();
        long lastRequestTime = lastRequestTimes.getOrDefault(endpoint, 0L);
        
        long timeSinceLastRequest = currentTime - lastRequestTime;
        if (timeSinceLastRequest < DEFAULT_DELAY_MS) {
            try {
                Thread.sleep(DEFAULT_DELAY_MS - timeSinceLastRequest);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastRequestTimes.put(endpoint, System.currentTimeMillis());
    }
    
    public boolean shouldRetry(String endpoint, HttpClientErrorException e) {
        if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
            int retryCount = retryCounts.getOrDefault(endpoint, 0);
            if (retryCount < MAX_RETRIES) {
                retryCounts.put(endpoint, retryCount + 1);
                
                // Check for Retry-After header
                HttpHeaders headers = e.getResponseHeaders();
                if (headers != null && headers.containsKey("Retry-After")) {
                    String retryAfter = headers.getFirst("Retry-After");
                    try {
                        long delay = Long.parseLong(retryAfter) * 1000;
                        Thread.sleep(delay);
                    } catch (InterruptedException | NumberFormatException ex) {
                        try {
                            Thread.sleep(RETRY_DELAY_MS);
                        } catch (InterruptedException ignored) {}
                    }
                } else {
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ignored) {}
                }
                return true;
            }
        }
        retryCounts.remove(endpoint);
        return false;
    }
    
    public void resetRetryCount(String endpoint) {
        retryCounts.remove(endpoint);
    }
}