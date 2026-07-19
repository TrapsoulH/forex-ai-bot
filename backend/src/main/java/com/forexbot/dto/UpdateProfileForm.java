package com.forexbot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileForm {

    @NotBlank(message = "Full name is required")
    @Size(max = 120, message = "Full name must be under 120 characters")
    private String fullName;

    @Pattern(
        regexp = "^(\\+[1-9]\\d{6,14})?$",
        message = "Phone number must be in E.164 format, e.g. +27821234567"
    )
    private String phone;
}
