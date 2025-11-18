package com.rohan.contactus.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequest {

    @NotBlank(message = "Username/Email cannot be empty.")
    @Email(message = "Username must be a valid email format.")
    private String username;

    @NotBlank(message = "Password cannot be empty.")
    @Size(min = 8, message = "Password must be at least 8 characters long.")
    // Note: Use regex for complexity (e.g., one number, one uppercase), but Size is the minimum requirement.
    private String password;
}