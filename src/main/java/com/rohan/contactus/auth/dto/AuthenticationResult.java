package com.rohan.contactus.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthenticationResult {
    private String accessToken;
    private String refreshToken;
}
