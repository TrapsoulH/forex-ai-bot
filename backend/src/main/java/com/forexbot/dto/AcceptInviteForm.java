package com.forexbot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AcceptInviteForm {

    private String token;

    @NotBlank(message = "Full name is required")
    @Size(max = 120, message = "Full name must be under 120 characters")
    private String fullName;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    @NotBlank(message = "Please confirm your password")
    private String confirmPassword;
}
