package com.forexbot.dto;

import com.forexbot.validation.PasswordsMatch;
import com.forexbot.validation.ValidEmailDomain;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
@PasswordsMatch(password = "password", confirmPassword = "confirmPassword")
public class RegisterForm {

    @NotBlank(message = "Full name is required")
    @Size(max = 120, message = "Full name must be under 120 characters")
    private String fullName;

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be 3–50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Username may only contain letters, numbers and underscores")
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email address")
    @ValidEmailDomain
    private String email;

    @Pattern(
        regexp = "^(\\+[1-9]\\d{6,14})?$",
        message = "Phone number must be in E.164 format, e.g. +27821234567"
    )
    private String phone;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    @Pattern(
        regexp = "^(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z0-9]).{8,}$",
        message = "Password must contain at least one uppercase letter, one number, and one special character"
    )
    private String password;

    @NotBlank(message = "Please confirm your password")
    private String confirmPassword;
}
