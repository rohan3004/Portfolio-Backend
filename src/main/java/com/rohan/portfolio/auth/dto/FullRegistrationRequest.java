package com.rohan.portfolio.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FullRegistrationRequest {

    @NotBlank(message = "Registration Token is required")
    private String registrationToken; // Proof of OTP verification

    @NotBlank(message = "Username is required")
    @Pattern(regexp = "^[a-zA-Z0-9._-]{3,20}$", message = "Username must be 3-20 chars, alphanumeric")
    private String username; // Display Name

    @NotBlank(message = "Full Name is required")
    private String fullName;

    private String phoneNumber;

    // Demographics
    private String gender;
    private String dateOfBirth;
    private String pinCode;
    private String country;
    private String state;
    private String city;

    // Coding Profiles
    private String codechefHandle;
    private String gfgHandle;
    private String leetcodeHandle;
    private String codeforcesHandle;

    // Device / Client Info (Populated by Frontend)
    private String deviceId;
    private String platform;
    private String osVersion;
    private String appVersion;
    private String screenResolution;
    private String browserFingerprint;
    private String referralSource;
    private String language;
}