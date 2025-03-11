package ca.sheridancollege.jamsy.security;


import org.springframework.context.annotation.Bean;
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

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(authorizeRequests ->
                authorizeRequests
                    .requestMatchers("/", "/login", "/error", "/oauth2/**").permitAll()
                    .anyRequest().authenticated()
            )
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/login")
                .defaultSuccessUrl("/tracks", true)
            );
        return http.build();
    }

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository() {
        return new InMemoryClientRegistrationRepository(spotifyClientRegistration());
    }

    private ClientRegistration spotifyClientRegistration() {
        return ClientRegistration.withRegistrationId("spotify")
            .clientId("a889ff03eaa84050b3d323debcf1498f")
            .clientSecret("1b8849fc75db4306bf791e3617e7a195")
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