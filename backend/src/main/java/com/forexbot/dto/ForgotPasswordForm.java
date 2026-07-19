package com.forexbot.dto;

import com.forexbot.validation.ValidEmailDomain;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ForgotPasswordForm {

    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email address")
    @ValidEmailDomain
    private String email;
}
