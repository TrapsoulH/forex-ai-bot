package com.forexbot.controller;

import com.forexbot.dto.InviteUserForm;
import com.forexbot.model.User;
import com.forexbot.repository.UserRepository;
import com.forexbot.service.UserService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
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
                             Authentication authentication,
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
            User invited = userService.inviteUser(form.getEmail(), form.getRole(), authentication.getName());
            log.info("Admin {} invited {}", authentication.getName(), invited.getEmail());
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
                             Authentication authentication,
                             RedirectAttributes ra) {
        try {
            userService.toggleUserEnabled(id, authentication.getName());
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
                             Authentication authentication,
                             RedirectAttributes ra) {
        try {
            userService.changeUserRole(id, role, authentication.getName());
            ra.addFlashAttribute("success", "Role updated.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    // ── Delete user ───────────────────────────────────────────────────────────

    @PostMapping("/users/{id}/delete")
    public String deleteUser(@PathVariable Long id,
                             Authentication authentication,
                             RedirectAttributes ra) {
        try {
            userService.deleteUser(id, authentication.getName());
            ra.addFlashAttribute("success", "User deleted.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    // ── Resend invite ─────────────────────────────────────────────────────────

    @PostMapping("/users/{id}/resend-invite")
    public String resendInvite(@PathVariable Long id,
                               Authentication authentication,
                               RedirectAttributes ra) {
        try {
            userService.resendInvite(id, authentication.getName());
            ra.addFlashAttribute("success", "Invite resent.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    // ── Unlock account ────────────────────────────────────────────────────────

    @PostMapping("/users/{id}/unlock")
    public String unlockUser(@PathVariable Long id,
                             Authentication authentication,
                             RedirectAttributes ra) {
        try {
            userService.unlockUser(id, authentication.getName());
            ra.addFlashAttribute("success", "Account unlocked.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    // ── Force password reset ──────────────────────────────────────────────────

    @PostMapping("/users/{id}/reset-password")
    public String resetPassword(@PathVariable Long id,
                                Authentication authentication,
                                RedirectAttributes ra) {
        try {
            userService.sendPasswordReset(id, authentication.getName());
            ra.addFlashAttribute("success", "Password reset email sent.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/users";
    }
}
