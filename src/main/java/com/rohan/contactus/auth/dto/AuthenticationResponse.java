package com.rohan.contactus.auth.dto;

import lombok.Data; //Static Constructor
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class AuthenticationResponse {
    // Access token is sent in the body. Refresh Token is sent via HttpOnly Cookie.
    @NonNull
    private String accessToken;
    private String tokenType = "Bearer";
}
