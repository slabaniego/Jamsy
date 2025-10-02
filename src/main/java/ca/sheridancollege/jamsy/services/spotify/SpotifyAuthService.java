package ca.sheridancollege.jamsy.services.spotify;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import jakarta.servlet.http.HttpSession;

/*
 * Handles tokens, refreshes, and session mannagement
 * */
@Service
public class SpotifyAuthService {

 	@Value("${spotify.client.id}")
    private String clientId;

    @Value("${spotify.client.secret}")
    private String clientSecret;

    @Value("${spotify.mobile.redirect-uri}")
    private String mobileRedirectUri;
    
    private final RestTemplate restTemplate = new RestTemplate();
    
    public String getUserAccessToken(String code, String redirectUri) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        String body = "grant_type=authorization_code&code=" + code +
                "&redirect_uri=" + redirectUri +
                "&client_id=" + clientId + "&client_secret=" + clientSecret;

        HttpEntity<String> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.exchange(
                "https://accounts.spotify.com/api/token",
                HttpMethod.POST, entity, Map.class);

        return (String) response.getBody().get("access_token");
    }

    public String getUserAccessToken(String code) {
        return getUserAccessToken(code, mobileRedirectUri);
    }

    public Map<String, String> refreshAccessToken(String refreshToken) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setBasicAuth(clientId, clientSecret);

            String body = "grant_type=refresh_token&refresh_token=" + refreshToken;

            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    "https://accounts.spotify.com/api/token",
                    HttpMethod.POST, entity, Map.class);

            Map<String, String> tokens = new HashMap<>();
            tokens.put("access_token", (String) response.getBody().get("access_token"));
            if (response.getBody().containsKey("refresh_token")) {
                tokens.put("refresh_token", (String) response.getBody().get("refresh_token"));
            } else {
                tokens.put("refresh_token", refreshToken); // Return the same refresh token if not provided
            }
            tokens.put("token_type", (String) response.getBody().get("token_type"));
            tokens.put("expires_in", String.valueOf(response.getBody().get("expires_in")));

            return tokens;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.BAD_REQUEST && 
                e.getResponseBodyAsString().contains("invalid_grant")) {
                // Refresh token is invalid, we need to re-authenticate
                throw new InvalidRefreshTokenException("Refresh token is invalid, user needs to re-authenticate");
            }
            throw e; // Re-throw other exceptions
        }
    }
    
    public class InvalidRefreshTokenException extends RuntimeException {
        public InvalidRefreshTokenException(String message) {
            super(message);
        }
    }
    
    public String ensureValidAccessToken(HttpSession session) {
        String accessToken = (String) session.getAttribute("accessToken");
        String refreshToken = (String) session.getAttribute("refreshToken");
        Long expiryTime = (Long) session.getAttribute("accessTokenExpiry");

        // If token is missing or expired
        if (accessToken == null || expiryTime == null || System.currentTimeMillis() > expiryTime) {
            try {
                Map<String, String> tokens = refreshAccessToken(refreshToken);
                accessToken = tokens.get("access_token");

                // Save updated values in session
                session.setAttribute("accessToken", accessToken);
                session.setAttribute("refreshToken", tokens.get("refresh_token"));
                session.setAttribute("accessTokenExpiry",
                        System.currentTimeMillis() + (Long.parseLong(tokens.get("expires_in")) * 1000));
            } catch (InvalidRefreshTokenException e) {
                // Clear the session and force re-authentication
                session.removeAttribute("accessToken");
                session.removeAttribute("refreshToken");
                session.removeAttribute("accessTokenExpiry");
                throw new AuthenticationRequiredException("Please re-authenticate with Spotify");
            }
        }

        return accessToken;
    }
    
    public class AuthenticationRequiredException extends RuntimeException {
        public AuthenticationRequiredException(String message) {
            super(message);
        }
    }
}
