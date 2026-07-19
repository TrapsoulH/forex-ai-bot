package com.forexbot.dto;

import com.forexbot.validation.PasswordsMatch;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
@PasswordsMatch(password = "password", confirmPassword = "confirmPassword")
public class AcceptInviteForm {

    private String token;

    @NotBlank(message = "Full name is required")
    @Size(max = 120, message = "Full name must be under 120 characters")
    private String fullName;

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
