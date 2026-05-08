package com.flowernailbar.controller;

import com.flowernailbar.model.User;
import com.flowernailbar.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * AuthController — login, signup, and logout.
 *
 * Session model: on successful login/signup we set
 *   session.setAttribute("currentUser", user)
 * which the {@link com.flowernailbar.config.CurrentUserAdvice} exposes to all
 * Thymeleaf views as ${currentUser}.
 *
 * Both login and signup support a ?next=/some/path query parameter so the
 * booking flow can send the user here and bring them back afterwards.
 */
@Controller
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    public static final String SESSION_USER_KEY = "currentUser";

    @Autowired
    private AuthService authService;

    // ─────────────────────────────────────────────
    // LOGIN
    // ─────────────────────────────────────────────

    @GetMapping("/login")
    public String loginPage(
        @RequestParam(required = false) String next,
        @RequestParam(required = false) String error,
        Model model,
        HttpSession session
    ) {
        if (session.getAttribute(SESSION_USER_KEY) != null) {
            return redirectTo(next);
        }
        model.addAttribute("next", next);
        model.addAttribute("error", error);
        return "login";
    }

    @PostMapping("/login")
    public String loginSubmit(
        @RequestParam String email,
        @RequestParam String password,
        @RequestParam(required = false) String next,
        HttpSession session
    ) {
        Optional<User> userOpt = authService.login(email, password);
        if (userOpt.isEmpty()) {
            String safeNext = next == null ? "" : "&next=" + urlEncode(next);
            return "redirect:/login?error=Invalid+email+or+password" + safeNext;
        }
        session.setAttribute(SESSION_USER_KEY, userOpt.get());
        log.info("[AuthController] User {} logged in.", userOpt.get().getEmail());
        return redirectTo(next);
    }

    // ─────────────────────────────────────────────
    // SIGNUP
    // ─────────────────────────────────────────────

    @GetMapping("/signup")
    public String signupPage(
        @RequestParam(required = false) String next,
        @RequestParam(required = false) String error,
        Model model,
        HttpSession session
    ) {
        if (session.getAttribute(SESSION_USER_KEY) != null) {
            return redirectTo(next);
        }
        model.addAttribute("next", next);
        model.addAttribute("error", error);
        return "signup";
    }

    @PostMapping("/signup")
    public String signupSubmit(
        @RequestParam String fullName,
        @RequestParam String email,
        @RequestParam String phone,
        @RequestParam String password,
        @RequestParam(required = false) String next,
        HttpSession session
    ) {
        try {
            User u = authService.signup(fullName, email, phone, password);
            session.setAttribute(SESSION_USER_KEY, u);
            log.info("[AuthController] New user signed up and logged in: {}", email);
            return redirectTo(next);
        } catch (IllegalArgumentException | IllegalStateException e) {
            String safeNext = next == null ? "" : "&next=" + urlEncode(next);
            return "redirect:/signup?error=" + urlEncode(e.getMessage()) + safeNext;
        }
    }

    // ─────────────────────────────────────────────
    // LOGOUT
    // ─────────────────────────────────────────────

    @PostMapping("/logout")
    public String logout(HttpSession session, HttpServletRequest req) {
        Object u = session.getAttribute(SESSION_USER_KEY);
        if (u != null) {
            log.info("[AuthController] Logout: {}", ((User) u).getEmail());
        }
        session.invalidate();
        return "redirect:/";
    }

    // ─────────────────────────────────────────────
    // helpers
    // ─────────────────────────────────────────────

    private String redirectTo(String next) {
        // Only allow same-origin paths for safety.
        if (next != null && next.startsWith("/") && !next.startsWith("//")) {
            return "redirect:" + next;
        }
        return "redirect:/";
    }

    private String urlEncode(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }
}
