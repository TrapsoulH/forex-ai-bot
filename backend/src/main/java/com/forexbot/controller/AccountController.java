package com.forexbot.controller;

import com.forexbot.dto.ChangePasswordForm;
import com.forexbot.dto.UpdateProfileForm;
import com.forexbot.model.User;
import com.forexbot.service.UserService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequestMapping("/account")
public class AccountController {

    private final UserService userService;

    public AccountController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public String accountPage(@AuthenticationPrincipal UserDetails principal, Model model) {
        User user = userService.findByUsername(principal.getUsername());
        UpdateProfileForm profileForm = new UpdateProfileForm();
        profileForm.setFullName(user.getFullName() != null ? user.getFullName() : "");
        model.addAttribute("user", user);
        model.addAttribute("profileForm", profileForm);
        model.addAttribute("passwordForm", new ChangePasswordForm());
        return "account/settings";
    }

    @PostMapping("/profile")
    public String updateProfile(@AuthenticationPrincipal UserDetails principal,
                                @Valid @ModelAttribute("profileForm") UpdateProfileForm form,
                                BindingResult result,
                                Model model,
                                RedirectAttributes redirectAttrs) {
        if (result.hasErrors()) {
            User user = userService.findByUsername(principal.getUsername());
            model.addAttribute("user", user);
            model.addAttribute("passwordForm", new ChangePasswordForm());
            model.addAttribute("profileError", true);
            return "account/settings";
        }
        try {
            userService.updateProfile(principal.getUsername(), form);
            redirectAttrs.addFlashAttribute("success", "Your name has been updated.");
        } catch (IllegalArgumentException e) {
            redirectAttrs.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/account";
    }

    @PostMapping("/password")
    public String changePassword(@AuthenticationPrincipal UserDetails principal,
                                 @Valid @ModelAttribute("passwordForm") ChangePasswordForm form,
                                 BindingResult result,
                                 Model model,
                                 RedirectAttributes redirectAttrs) {
        if (result.hasErrors()) {
            User user = userService.findByUsername(principal.getUsername());
            model.addAttribute("user", user);
            model.addAttribute("profileForm", buildProfileForm(user));
            model.addAttribute("passwordError", true);
            return "account/settings";
        }
        try {
            userService.changePassword(principal.getUsername(), form);
            redirectAttrs.addFlashAttribute("success", "Password updated successfully.");
        } catch (IllegalArgumentException e) {
            redirectAttrs.addFlashAttribute("passwordFormError", e.getMessage());
        }
        return "redirect:/account";
    }

    private UpdateProfileForm buildProfileForm(User user) {
        UpdateProfileForm f = new UpdateProfileForm();
        f.setFullName(user.getFullName() != null ? user.getFullName() : "");
        return f;
    }
}
