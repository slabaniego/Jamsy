package ca.sheridancollege.jamsy.controllers;

import ca.sheridancollege.jamsy.beans.PlaylistTemplate;
import ca.sheridancollege.jamsy.exceptions.AuthenticationRequiredException;
import ca.sheridancollege.jamsy.exceptions.InvalidRefreshTokenException;
import ca.sheridancollege.jamsy.services.PlaylistTemplateService;
import ca.sheridancollege.jamsy.services.spotify.SpotifyArtistService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
public class AuthController {

    private final SpotifyArtistService spotifyArtistService;
    private final PlaylistTemplateService templateService;
    private final OAuth2AuthorizedClientService authorizedClientService;

    @Value("${spotify.client.id}")
    private String clientId;

    @Value("${spotify.web.redirect-uri}")
    private String redirectUri;

    public AuthController(
            SpotifyArtistService spotifyArtistService,
            PlaylistTemplateService templateService,
            OAuth2AuthorizedClientService authorizedClientService
    ) {
        this.spotifyArtistService = spotifyArtistService;
        this.templateService = templateService;
        this.authorizedClientService = authorizedClientService;
    }

    /** =====================
     *  LOGIN + AUTH ROUTES
     *  ===================== */

    @GetMapping("/")
    public String loginPage() {
        return "login";
    }

    /** Handle invalid session or expired token */
    @ExceptionHandler(AuthenticationRequiredException.class)
    public String handleAuthenticationRequired(AuthenticationRequiredException e, HttpSession session) {
        session.invalidate(); // clear old session

        String authUrl = "https://accounts.spotify.com/authorize?" +
                "client_id=" + clientId +
                "&response_type=code" +
                "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8) +
                "&scope=user-read-private user-read-email user-top-read user-read-recently-played " +
                "playlist-modify-public playlist-modify-private" +
                "&state=" + UUID.randomUUID();

        return "redirect:" + authUrl;
    }

    /** Show the main template selection screen after login */
    @GetMapping("/playlist-templates")
    public String showPlaylistTemplates(
            @RegisteredOAuth2AuthorizedClient("spotify") OAuth2AuthorizedClient authorizedClient,
            HttpSession session,
            Model model
    ) {
        try {
            String accessToken = authorizedClient.getAccessToken().getTokenValue();
            session.setAttribute("accessToken", accessToken);
            session.setAttribute("refreshToken", authorizedClient.getRefreshToken().getTokenValue());
            session.setAttribute("accessTokenExpiry", System.currentTimeMillis() + 3600000); // 1 hour

            // Load categorized top artists for workout/mood playlists
            List<Map<String, Object>> categorizedArtists =
                    spotifyArtistService.getUserTopArtistsWithWorkoutCategories(accessToken, 100);
            session.setAttribute("categorizedArtists", categorizedArtists);

            // Load default playlist templates
            List<PlaylistTemplate> templates = templateService.getDefaultTemplates();
            model.addAttribute("templates", templates);

            return "playlist-template";
        } catch (InvalidRefreshTokenException e) {
            throw new AuthenticationRequiredException("Please re-authenticate with Spotify");
        } catch (Exception e) {
            model.addAttribute("error", "Error loading templates: " + e.getMessage());
            return "error";
        }
    }
}
