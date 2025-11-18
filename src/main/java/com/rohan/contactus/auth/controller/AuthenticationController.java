package com.rohan.contactus.auth.controller;

import com.rohan.contactus.auth.dto.LoginRequest;
import com.rohan.contactus.auth.dto.AuthenticationResponse;
import com.rohan.contactus.auth.dto.AuthenticationResult;
import com.rohan.contactus.auth.dto.RegisterRequest;
import com.rohan.contactus.auth.exception.TokenRefreshException;
import com.rohan.contactus.auth.service.AuthenticationService;
import com.rohan.contactus.model.User;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthenticationController {

    private final AuthenticationService authenticationService;

    @Value("${application.security.jwt.refresh-token.expiration}")
    private long REFRESH_TOKEN_MAX_AGE_SECONDS;

    // Inject the new domain property
    @Value("${application.security.cookie.domain:}") // Default to empty if not set
    private String cookieDomain;

    /**
     * Handles user login and initial token issuance.
     * @return Access Token in body, Refresh Token in HttpOnly cookie.
     */
    @PostMapping("/login")
    public ResponseEntity<AuthenticationResponse> login(@RequestBody LoginRequest request, HttpServletResponse response) {

        // AuthenticationService handles credentials check and token generation/storage
        AuthenticationResult result = authenticationService.authenticate(request);

        // 1. Set Refresh Token as the secure HttpOnly cookie
        setRefreshCookie(response, result.getRefreshToken());

        // 2. Return Access Token in body
        return ResponseEntity.ok(new AuthenticationResponse(result.getAccessToken()));
    }

    /**
     * Handles token renewal using the Refresh Token cookie.
     * @return New Access Token in body, New Refresh Token in HttpOnly cookie (rotation).
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthenticationResponse> refresh(
            @CookieValue(name = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response) {

        // If the cookie wasn't sent by the client, deny access.
        if (refreshToken == null) {
            return ResponseEntity.status(401).build();
        }

        try {
            // Service handles validation, rotation, and DB checks.
            AuthenticationResult result = authenticationService.refreshToken(refreshToken);

            // Success: Set the NEW Refresh Token cookie (rotation complete)
            setRefreshCookie(response, result.getRefreshToken());

            // Return NEW Access Token
            return ResponseEntity.ok(new AuthenticationResponse(result.getAccessToken()));

        } catch (TokenRefreshException e) {
            // Catch expired, invalid, or reused token errors.
            // Clear the expired cookie and force client to re-login.
            clearRefreshCookie(response);
            return ResponseEntity.status(403).build();
        }
    }

    /**
     * Handles user logout by clearing the Refresh Token cookie.
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletResponse response) {
        // In a complex system, you might also call a service method to revoke the token from the database.
        clearRefreshCookie(response);
        return ResponseEntity.ok("Logged out successfully.");
    }

    // --- Helper Methods for Cookie Management ---

    private void setRefreshCookie(HttpServletResponse response, String token) {
        // 1. Create the base cookie builder
        ResponseCookie.ResponseCookieBuilder cookieBuilder = ResponseCookie.from("refreshToken", token)
                .httpOnly(true)
                .secure(true)
                .path("/api/v1/auth/refresh")
                .maxAge(REFRESH_TOKEN_MAX_AGE_SECONDS)
                .sameSite("Strict");

        // 2. Add the Domain attribute if it is configured (CRITICAL FIX)
        if (cookieDomain != null && !cookieDomain.isEmpty()) {
            cookieBuilder = cookieBuilder.domain(cookieDomain);
        }

        response.addHeader(HttpHeaders.SET_COOKIE, cookieBuilder.build().toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        // Must apply the domain setting when clearing the cookie as well!
        ResponseCookie.ResponseCookieBuilder clearCookieBuilder = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(true)
                .path("/api/v1/auth/refresh")
                .maxAge(0)
                .sameSite("Strict");

        if (cookieDomain != null && !cookieDomain.isEmpty()) {
            clearCookieBuilder = clearCookieBuilder.domain(cookieDomain);
        }

        response.addHeader(HttpHeaders.SET_COOKIE, clearCookieBuilder.build().toString());
    }
    // --- New Registration Endpoint ---
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        try {
            User registeredUser = authenticationService.register(request);
            // In a real scenario, you might log in the user here immediately and return tokens.
            return ResponseEntity.status(HttpStatus.CREATED).body(registeredUser);
        } catch (IllegalStateException e) {
            // User already exists (handled by service layer logic)
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (Exception e) {
            // General registration failure
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // --- GLOBAL EXCEPTION HANDLER FOR VALIDATION ERRORS (Production Standard) ---
    /**
     * Handles validation errors (@Valid) and returns a clean 400 Bad Request response.
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Map<String, String> handleValidationExceptions(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> {
            errors.put(error.getField(), error.getDefaultMessage());
        });
        return errors;
    }

    /**
     * Deletes a user account specified by a query parameter.
     * Requires ROLE_ADMIN and prevents self-deletion.
     */
    @DeleteMapping("/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> deleteUser(
            @RequestParam("username") String username,
            Principal principal // Inject the authenticated user's context
    ) {
        // The username of the admin currently logged in
        String currentAdminUsername = principal.getName();

        try {
            authenticationService.deleteUser(username, currentAdminUsername);

            // --- INDUSTRY STANDARD RESPONSE FOR DELETION ---
            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "User " + username + " deleted successfully.");

            // Return 200 OK with custom message body (more informative than 204)
            return ResponseEntity.ok(response);

        } catch (AccessDeniedException e) {
            // Caught when admin attempts to delete self (triggers 403 Forbidden)
            Map<String, String> error = new HashMap<>();
            error.put("error", "Security Violation");
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);

        } catch (UsernameNotFoundException e) {
            // User to delete was not found.
            Map<String, String> error = new HashMap<>();
            error.put("error", "Not Found");
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);

        } catch (Exception e) {
            // General failure
            Map<String, String> error = new HashMap<>();
            error.put("error", "Internal Server Error");
            error.put("message", "Could not complete deletion due to a server error.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
    /**
     * Forcibly revokes a user's session (Refresh Token) from the database.
     * Requires ROLE_ADMIN.
     */
    @PostMapping("/revoke")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> revokeAccess(
            @RequestParam("username") String username
    ) {
        try {
            authenticationService.revokeToken(username);

            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Session for user " + username + " successfully revoked.");

            return ResponseEntity.ok(response);

        } catch (UsernameNotFoundException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Not Found");
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);

        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Internal Server Error");
            error.put("message", "Could not complete token revocation.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}