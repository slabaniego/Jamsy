package ca.sheridancollege.jamsy.security;

import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
	
	@Value("${SPOTIFY_CLIENT_ID}")
    private String clientId;
    
    @Value("${SPOTIFY_CLIENT_SECRET}")
    private String clientSecret;

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
	    http
	        .csrf(csrf -> csrf.disable())
	        .authorizeHttpRequests(auth -> auth
	            .requestMatchers("/", "/login", "/error", "/oauth2/**", "/h2-console/**", "/filters", "/spotify/exchange").permitAll()
	            .anyRequest().authenticated()
	        )
	        .headers(headers -> headers.frameOptions(frame -> frame.disable()))
	        .oauth2Login(oauth2 -> oauth2
	            .loginPage("/login")
	            .defaultSuccessUrl("/filters", true)
	        );

	    return http.build();
	}

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository() {
        return new InMemoryClientRegistrationRepository(spotifyClientRegistration());
    }

    private ClientRegistration spotifyClientRegistration() {
        return ClientRegistration.withRegistrationId("spotify")
            .clientId(clientId)
            .clientSecret(clientSecret)
            .scope("user-top-read", "user-library-read", "user-read-recently-played")
            .authorizationUri("https://accounts.spotify.com/authorize")
            .tokenUri("https://accounts.spotify.com/api/token")
            .userInfoUri("https://api.spotify.com/v1/me")
            .userNameAttributeName("id")
            .redirectUri("http://localhost:8080/login/oauth2/code/spotify")
            .clientName("Spotify")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .build();
    }
}
