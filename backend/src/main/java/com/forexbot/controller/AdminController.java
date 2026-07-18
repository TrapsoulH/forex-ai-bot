package com.forexbot.controller;

import com.forexbot.dto.CreateUserForm;
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
        model.addAttribute("users",      userService.findAllUsers());
        model.addAttribute("totalUsers", userRepository.count());
        model.addAttribute("activeUsers",userRepository.countByEnabled(true));
        model.addAttribute("adminCount", userRepository.countByRole(User.Role.ADMIN));
        model.addAttribute("form",       new CreateUserForm());
        model.addAttribute("roles",      User.Role.values());
        return "admin/users";
    }

    // ── Create user ───────────────────────────────────────────────────────────

    @PostMapping("/users/create")
    public String createUser(@Valid @ModelAttribute("form") CreateUserForm form,
                             BindingResult result,
                             Model model,
                             RedirectAttributes ra) {
        if (result.hasErrors()) {
            model.addAttribute("users",      userService.findAllUsers());
            model.addAttribute("totalUsers", userRepository.count());
            model.addAttribute("activeUsers",userRepository.countByEnabled(true));
            model.addAttribute("adminCount", userRepository.countByRole(User.Role.ADMIN));
            model.addAttribute("roles",      User.Role.values());
            model.addAttribute("showForm",   true);   // keep form open on error
            return "admin/users";
        }
        try {
            User created = userService.adminCreateUser(form);
            log.info("Admin created user: {}", created.getUsername());
            ra.addFlashAttribute("success", "Account created for " + created.getFullName() + " (@" + created.getUsername() + ")");
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
