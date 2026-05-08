package com.flowernailbar.config;

import com.flowernailbar.controller.AuthController;
import com.flowernailbar.model.User;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Exposes the logged-in user (if any) to every Thymeleaf view as ${currentUser}.
 *
 * The layout fragment uses this to switch the navbar between
 * a "Sign in" link and the user dropdown.
 */
@ControllerAdvice
public class CurrentUserAdvice {

    @ModelAttribute("currentUser")
    public User currentUser(HttpSession session) {
        Object u = session.getAttribute(AuthController.SESSION_USER_KEY);
        return (u instanceof User) ? (User) u : null;
    }
}
