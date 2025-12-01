package com.rohan.portfolio.auth.controller;

import com.rohan.portfolio.auth.dto.*;
import com.rohan.portfolio.auth.exception.TokenRefreshException;
import com.rohan.portfolio.auth.service.AuthenticationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

/**
 * Main entry point for Auth.
 * Handles everything from OTP login and registration to token rotation.
 *
 * We use stateless JWTs for access, but keep the Refresh Token in a
 * secure, HTTP-only cookie to prevent XSS attacks.
 */
@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
public class AuthenticationController {

    private final AuthenticationService authenticationService;

    @Value("${application.security.jwt.refresh-token.expiration}")
    private long refreshTokenMaxAgeSeconds;

    // Set this in prod properties if you have subdomains (e.g., .rohandev.online)
    @Value("${application.security.cookie.domain:}")
    private String cookieDomain;

    /**
     * Rotates the refresh token.
     * Use this when the short-lived access token expires.
     *
     * If the cookie is valid, we give you a new pair.
     * If it's invalid or missing, we force a logout.
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(
            @CookieValue(name = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response
    ) {
        if (refreshToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            AuthenticationResult result = authenticationService.refreshToken(refreshToken);

            // Rotate the cookie and send back the new access token
            setRefreshCookie(response, result.getRefreshToken());
            return ResponseEntity.ok(new AuthenticationResponse(result.getAccessToken()));

        } catch (TokenRefreshException e) {
            // Token is likely compromised or just plain expired. Clean up the client state.
            clearRefreshCookie(response);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Simple client-side logout.
     * Just kills the cookie so the browser stops sending it.
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletResponse response) {
        clearRefreshCookie(response);
        return ResponseEntity.ok(Map.of("message", "Logged out"));
    }

    /**
     * Admin only.
     * Hard deletes a user. The service layer handles self-deletion checks.
     */
    @DeleteMapping("/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteUser(
            @RequestParam("username") String username,
            Principal principal
    ) {
        try {
            authenticationService.deleteUser(username, principal.getName());
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "User deleted"
            ));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));
        } catch (UsernameNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Admin "Kill Switch".
     * Immediately invalidates a user's session by nuking their refresh token in the DB.
     * Use this if an account looks suspicious.
     */
    @PostMapping("/revoke")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> revokeAccess(
            @RequestParam("username") String username,
            Principal principal
    ) {
        try {
            authenticationService.revokeToken(username, principal.getName());

            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Session for user " + username + " successfully revoked.");
            return ResponseEntity.ok(response);

        } catch (UsernameNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Not Found", "message", e.getMessage()));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal Server Error", "message", "Could not revoke session."));
        }
    }

    /**
     * Step 1 of Login: Request the code.
     * We rate limit this in the service to prevent spam.
     */
    @PostMapping("/send-otp")
    public ResponseEntity<?> sendOtp(@Valid @RequestBody OtpRequest request) {
        try {
            authenticationService.sendOtp(request.getUsername());
            return ResponseEntity.ok(Map.of(
                    "message", "OTP sent to " + request.getUsername()
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to send OTP"));
        }
    }

    /**
     * Step 2 of Login: Verify code.
     *
     * If they are a new user (incomplete profile), we return 202 Accepted and tell them to register.
     * If they are an existing user, we return 200 OK and log them in immediately.
     */
    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(
            @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response
    ) {
        try {
            VerifyOtpResponse result = authenticationService.verifyOtp(request, httpRequest);

            if (result.isNewUser()) {
                // Profile incomplete, don't issue JWTs yet.
                return ResponseEntity.accepted().body(result);
            }

            // Regular login
            setRefreshCookie(response, result.getRefreshToken());
            return ResponseEntity.ok(new AuthenticationResponse(result.getAccessToken()));

        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Step 3 (Optional): Complete Registration.
     * Only for new users who passed Step 2 but didn't have a profile.
     */
    @PostMapping("/complete-registration")
    public ResponseEntity<?> completeRegistration(
            @Valid @RequestBody FullRegistrationRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response
    ) {
        try {
            AuthenticationResult result = authenticationService.completeRegistration(request, httpRequest);

            // Registration done, log them in.
            setRefreshCookie(response, result.getRefreshToken());
            return ResponseEntity.ok(new AuthenticationResponse(result.getAccessToken()));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Helper to make validation errors (like invalid email) look nice for the frontend.
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Map<String, String> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
        return errors;
    }

    // --- Cookie Helpers ---

    private void setRefreshCookie(HttpServletResponse response, String token) {
        // HttpOnly is crucial here to stop XSS scripts from stealing the token
        ResponseCookie cookie = ResponseCookie.from("refreshToken", token)
                .httpOnly(true)
                .secure(true) // Always true for prod
                .path("/")
                .maxAge(refreshTokenMaxAgeSeconds)
                .sameSite("None") // Needed since frontend/backend are on different domains
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(0) // Expire immediately
                .sameSite("None")
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}