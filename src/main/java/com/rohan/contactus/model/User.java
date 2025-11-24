package com.rohan.contactus.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

@Entity
@Table(name = "_user")
@Getter
@Setter
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Authentication Identifier
    @Column(unique = true, nullable = false)
    private String email;

    // --- Profile Data ---
    @Column(unique = true)
    private String username; // Display Name (Unique)

    private String fullName;
    private String phoneNumber;

    // --- TRACK PROGRESS (Fixed: added with default value) ---
    @Column(nullable = false, columnDefinition = "bigint default 0")
    private Long totalSolvedQuestions = 0L;

    private String gender;
    private String dateOfBirth; // Stored as String or LocalDate

    // Location
    private String pinCode;
    private String country;
    private String state;
    private String city;

    // Tech / Audit
    private String registrationIp;
    private String registrationUserAgent;
    private String referralSource;

    private boolean isEmailVerified;
    private boolean isProfileComplete; // FALSE until registration form is filled

    // Device Fingerprinting (Hashed/Non-sensitive)
    private String deviceId;
    private String platform; // Windows, Android
    private String osVersion;
    private String appVersion;
    private String screenResolution;

    @Column(columnDefinition = "TEXT")
    private String browserFingerprint; // JSON blob of fonts/accept-lang etc

    // Coding Profiles
    private String codechefHandle;
    private String gfgHandle;
    private String leetcodeHandle;
    private String codeforcesHandle;

    // Timestamps & Status
    private LocalDateTime createdAt;
    private LocalDateTime firstLoginAt;
    private LocalDateTime lastLoginAt;
    private int failedLoginAttempts;

    @Enumerated(EnumType.STRING)
    private AccountStatus accountStatus = AccountStatus.ACTIVE;

    // Temporary token to authorize the /complete-registration endpoint
    private String tempRegistrationToken;

    private String roles; // "ROLE_USER,ROLE_ADMIN"

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (roles == null) return java.util.Collections.emptyList();
        return Arrays.stream(roles.split(","))
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }

    @Override public String getPassword() { return null; }
    @Override public String getUsername() { return email; } // Spring Security uses Email
    public String getDisplayName() { return username; } // Actual Display Name

    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return accountStatus != AccountStatus.BLOCKED; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return accountStatus != AccountStatus.DELETED; }

    public enum AccountStatus { ACTIVE, BLOCKED, DELETED }
}