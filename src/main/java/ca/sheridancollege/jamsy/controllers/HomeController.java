package ca.sheridancollege.jamsy.controllers;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * HomeController
 * -------------------
 * Acts as the main entry point for the Jamsy web app.
 * 
 * Responsibilities:
 * - Directs users to login, home, or general static pages.
 * - Serves as a lightweight navigation layer after the app has been modularized.
 * 
 * This replaces the original WebController’s root navigation responsibilities.
 */
@Controller
public class HomeController {

    /**
     * Root route → directs to login page.
     * 
     * This method simply returns the main login page.
     * Actual authentication and token management are handled
     * by {@link AuthController}.
     */
	/*
	 * @GetMapping("/") public String loginPage() { return "login"; // Points to
	 * login.html (Spotify login button) }
	 */

    /**
     * Home or dashboard view.
     * 
     * This is shown after successful login and can serve as a
     * landing hub for navigation to discovery, playlists, etc.
     */
    @GetMapping("/home")
    public String homePage(Model model, HttpSession session) {
        String accessToken = (String) session.getAttribute("accessToken");
        if (accessToken == null) {
            // User not logged in, redirect to login
            return "redirect:/";
        }

        model.addAttribute("user", session.getAttribute("spotifyUser"));
        return "home";  // Renders home.html (dashboard)
    }

    /**
     * About page (optional).
     * 
     * Can include app description, features, or credits.
     */
    @GetMapping("/about")
    public String aboutPage() {
        return "about";  // Optional informational page
    }

    /**
     * Logout route.
     * 
     * Clears user session data and redirects to the login page.
     */
    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/";
    }
}
