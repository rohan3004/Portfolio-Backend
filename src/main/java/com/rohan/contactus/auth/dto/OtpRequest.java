package com.rohan.contactus.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OtpRequest {
    @NotBlank(message = "Email cannot be empty.")
    @Email(message = "Must be a valid email format.")
    private String username;
}