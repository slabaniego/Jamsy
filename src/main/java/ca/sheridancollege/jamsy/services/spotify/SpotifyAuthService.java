package ca.sheridancollege.jamsy.services.spotify;

import java.util.Base64;
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
import org.springframework.util.MultiValueMap;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import jakarta.servlet.http.HttpSession;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;


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
    
    private static final String TOKEN_URL = "https://accounts.spotify.com/api/token";
    
    private final RestTemplate restTemplate = new RestTemplate();
    
//    public String getUserAccessToken(String code, String redirectUri) {
//        HttpHeaders headers = new HttpHeaders();
//        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
//
//        String body = "grant_type=authorization_code&code=" + code +
//                "&redirect_uri=" + redirectUri +
//                "&client_id=" + clientId + "&client_secret=" + clientSecret;
//
//        HttpEntity<String> entity = new HttpEntity<>(body, headers);
//        ResponseEntity<Map> response = restTemplate.exchange(
//                "https://accounts.spotify.com/api/token",
//                HttpMethod.POST, entity, Map.class);
//
//        return (String) response.getBody().get("access_token");
//    }
//
//    public String getUserAccessToken(String code) {
//        return getUserAccessToken(code, mobileRedirectUri);
//    }
    
    /**
     * Exchanges the Spotify authorization code for an access token and refresh token.
     * <p>
     * This is part of Spotify’s OAuth 2.0 flow, where the user has already granted permission
     * and your app receives a temporary "code". You exchange that code for a long-lived access token.
     * </p>
     *
     * @param code The authorization code returned by Spotify after user login.
     * @param redirectUri The same redirect URI used during the authorization step.
     * @return A map containing Spotify tokens (access_token, refresh_token, etc.)
     */
    public Map<String, Object> exchangeCodeForAccessToken(String code, String redirectUri) {
        try {
            // ----------------------------
            // Step 1: Prepare request headers
            // ----------------------------
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            // ----------------------------
            // Step 2: Prepare form data for Spotify's /api/token endpoint
            // ----------------------------
            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("grant_type", "authorization_code");  // Standard OAuth grant type
            formData.add("code", code);                         // The temporary authorization code
            formData.add("redirect_uri", redirectUri);          // Must match the one Spotify was given
            formData.add("client_id", clientId);                // Your registered Spotify client ID
            formData.add("client_secret", clientSecret);        // Your Spotify client secret

            // Combine headers + form data into a request entity
            HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(formData, headers);

            // ----------------------------
            // Step 3: Make POST request to Spotify's token endpoint
            // ----------------------------
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<Map> response = restTemplate.exchange(
                    "https://accounts.spotify.com/api/token", // Spotify OAuth token endpoint
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            // ----------------------------
            // Step 4: Handle response and return token data
            // ----------------------------
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                // Example response body:
                // {
                //   "access_token": "ABC123...",
                //   "token_type": "Bearer",
                //   "expires_in": 3600,
                //   "refresh_token": "XYZ456..."
                // }
                return response.getBody();
            } else {
                throw new RuntimeException("Failed to exchange token: " + response.getStatusCode());
            }

        } catch (Exception e) {
            // Catch and rethrow with context for debugging
            throw new RuntimeException("Error exchanging code for access token: " + e.getMessage(), e);
        }
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
