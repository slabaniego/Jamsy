package ca.sheridancollege.jamsy.services.spotify;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SpotifyAuthService {
	@Value("${spotify.client.id}")
    private String clientId;

    @Value("${spotify.client.secret}")
    private String clientSecret;

    @Value("${spotify.mobile.redirect-uri}")
    private String mobileRedirectUri;

	
}
