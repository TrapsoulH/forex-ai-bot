package com.forexbot.controller;

import com.forexbot.dto.InviteUserForm;
import com.forexbot.model.User;
import com.forexbot.repository.UserRepository;
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
@RequestMapping("/admin")
public class AdminController {

    private final UserService userService;
    private final UserRepository userRepository;

    public AdminController(UserService userService, UserRepository userRepository) {
        this.userService    = userService;
        this.userRepository = userRepository;
    }

    // ── User list ─────────────────────────────────────────────────────────────

    @GetMapping("/users")
    public String users(Model model) {
        model.addAttribute("users",       userService.findAllUsers());
        model.addAttribute("totalUsers",  userRepository.count());
        model.addAttribute("activeUsers", userRepository.countByEnabled(true));
        model.addAttribute("adminCount",  userRepository.countByRole(User.Role.ADMIN));
        model.addAttribute("form",        new InviteUserForm());
        model.addAttribute("roles",       User.Role.values());
        return "admin/users";
    }

    // ── Invite user ───────────────────────────────────────────────────────────

    @PostMapping("/users/invite")
    public String inviteUser(@Valid @ModelAttribute("form") InviteUserForm form,
                             BindingResult result,
                             Model model,
                             @AuthenticationPrincipal UserDetails principal,
                             RedirectAttributes ra) {
        if (result.hasErrors()) {
            model.addAttribute("users",       userService.findAllUsers());
            model.addAttribute("totalUsers",  userRepository.count());
            model.addAttribute("activeUsers", userRepository.countByEnabled(true));
            model.addAttribute("adminCount",  userRepository.countByRole(User.Role.ADMIN));
            model.addAttribute("roles",       User.Role.values());
            model.addAttribute("showForm",    true);
            return "admin/users";
        }
        try {
            User invited = userService.inviteUser(form.getEmail(), form.getRole(), principal.getUsername());
            log.info("Admin {} invited {}", principal.getUsername(), invited.getEmail());
            ra.addFlashAttribute("success",
                "Invite sent to " + invited.getEmail() + ". They'll receive an email to set up their account.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    // ── Toggle enabled / disabled ─────────────────────────────────────────────

    @PostMapping("/users/{id}/toggle")
    public String toggleUser(@PathVariable Long id,
                             @AuthenticationPrincipal UserDetails principal,
                             RedirectAttributes ra) {
        try {
            userService.toggleUserEnabled(id, principal.getUsername());
            ra.addFlashAttribute("success", "User status updated.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    // ── Change role ───────────────────────────────────────────────────────────

    @PostMapping("/users/{id}/role")
    public String changeRole(@PathVariable Long id,
                             @RequestParam User.Role role,
                             @AuthenticationPrincipal UserDetails principal,
                             RedirectAttributes ra) {
        try {
            userService.changeUserRole(id, role, principal.getUsername());
            ra.addFlashAttribute("success", "Role updated.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/users";
    }
}
