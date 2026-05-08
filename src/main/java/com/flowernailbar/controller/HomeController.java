package com.flowernailbar.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * HomeController — Page Controller for the landing page.
 * Implements the Page Controller pattern (one controller per page).
 *
 * GET / → renders home.html (Thymeleaf template)
 */
@Controller
public class HomeController {

    private static final Logger log = LoggerFactory.getLogger(HomeController.class);

    @GetMapping("/")
    public String home(Model model) {
        log.info("[HomeController] GET / — rendering homepage");
        model.addAttribute("pageTitle", "Flower Nail Bar – Luxury Nail Salon");
        return "home";
    }

    @GetMapping("/health")
    public String health() {
        return "redirect:/api/health";
    }
}
