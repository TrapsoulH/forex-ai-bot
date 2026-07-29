package com.forexbot.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PublicController {

    /** Landing page — unauthenticated users see the public page, logged-in users go straight to dashboard. */
    @GetMapping("/")
    public String landing(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            return "redirect:/dashboard";
        }
        return "public/landing";
    }
}
