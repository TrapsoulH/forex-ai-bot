package com.forexbot.dto;

import com.forexbot.model.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class InviteUserForm {

    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email address")
    private String email;

    private User.Role role = User.Role.USER;
}
