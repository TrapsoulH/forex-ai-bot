package com.forexbot.dto;

import com.forexbot.validation.PasswordsMatch;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
@PasswordsMatch(password = "newPassword", confirmPassword = "confirmPassword")
public class ChangePasswordForm {

    @NotBlank(message = "Current password is required")
    private String currentPassword;

    @NotBlank(message = "New password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    @Pattern(
        regexp = "^(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z0-9]).{8,}$",
        message = "Password must contain at least one uppercase letter, one number, and one special character"
    )
    private String newPassword;

    @NotBlank(message = "Please confirm your new password")
    private String confirmPassword;
}
