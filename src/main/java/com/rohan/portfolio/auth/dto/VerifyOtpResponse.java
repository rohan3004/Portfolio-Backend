package com.rohan.portfolio.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class VerifyOtpResponse {
    private boolean isNewUser;
    private String message;

    // If New User:
    private String registrationToken;
    private String email;

    // If Existing User:
    private String accessToken;
    private String refreshToken; // Added this to pass to controller
}