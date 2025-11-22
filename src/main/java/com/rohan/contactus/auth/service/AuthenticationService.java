package com.rohan.contactus.auth.service;

import com.rohan.contactus.auth.dto.LoginRequest;
import com.rohan.contactus.auth.dto.AuthenticationResult;
import com.rohan.contactus.auth.dto.RegisterRequest;
import com.rohan.contactus.auth.exception.TokenRefreshException;
import com.rohan.contactus.auth.repository.OtpRepository;
import com.rohan.contactus.auth.repository.RefreshTokenRepository;
import com.rohan.contactus.auth.repository.UserRepository;
import com.rohan.contactus.model.Otp;
import com.rohan.contactus.model.RefreshToken;
import com.rohan.contactus.model.User;
import com.rohan.contactus.service.EmailService;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserDetailsService userDetailsService;
    private final OtpRepository otpRepository;
    private final EmailService emailService;

    //registration
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${application.security.otp.expiration-minutes:5}") // Retrieve expiry duration
    private int otpExpiryMinutes;

    // --- 1. SEND OTP (Auto-Registers if User doesn't exist) ---
    @Transactional
    public void sendOtp(String username) {

        User user = userRepository.findByUsername(username)
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setUsername(username);
                    // NOTE: Hardcoding 'ROLE_ADMIN' for the first user if needed, or stick to 'ROLE_USER'
                    newUser.setRoles("ROLE_USER");
                    return userRepository.save(newUser);
                });

        // Generate 6-digit OTP
        String otpCode = String.format("%06d", new SecureRandom().nextInt(999999));

        // Save/Update OTP in DB
        Otp otp = otpRepository.findByUser(user).orElse(new Otp());
        otp.setUser(user);
        otp.setCode(otpCode);
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(otpExpiryMinutes));
        otpRepository.save(otp);

        // --- PRODUCTION FIX: Send HTML Email instead of System.out.println ---
        emailService.sendOtpEmail(username, otpCode, otpExpiryMinutes);
    }

    // --- 2. AUTHENTICATE (Verify OTP) ---
    @Transactional
    public AuthenticationResult authenticate(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("User not found. Request OTP first."));

        Otp otpEntity = otpRepository.findByUser(user)
                .orElseThrow(() -> new BadCredentialsException("No OTP found. Request a code."));

        if (otpEntity.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadCredentialsException("OTP expired.");
        }
        if (!otpEntity.getCode().equals(request.getOtp())) {
            throw new BadCredentialsException("Invalid OTP.");
        }

        // Consumable OTP: Delete after use
        otpRepository.delete(otpEntity);

        final String accessToken = jwtService.generateAccessToken(user);
        final String refreshTokenString = jwtService.generateRefreshToken(user);

        saveRefreshToken(user, refreshTokenString);

        return new AuthenticationResult(accessToken, refreshTokenString);
    }

    @Transactional
    public AuthenticationResult refreshToken(String oldRefreshToken) {

        String username;

        // 1. Validate token structure and signature (CRITICAL FIX for Expired RT)
        try {
            username = jwtService.extractUsername(oldRefreshToken);
        } catch (ExpiredJwtException e) {
            // Session is over: throw exception that controller will handle (403/clear cookie).
            throw new TokenRefreshException(oldRefreshToken, "Refresh token expired. Please log in again.");
        } catch (Exception e) {
            throw new TokenRefreshException(oldRefreshToken, "Invalid refresh token signature.");
        }

        // 2. Load UserDetails for subsequent checks
        final User userDetails = (User) userDetailsService.loadUserByUsername(username);

        // 3. Verify signature (non-expiry check)
        if (!jwtService.isTokenValid(oldRefreshToken, userDetails)) {
            throw new TokenRefreshException(oldRefreshToken, "Invalid refresh token signature.");
        }

        // 4. DB Check for Compromise (Rotation)
        RefreshToken activeToken = refreshTokenRepository.findByUserId(userDetails.getId())
                .orElseThrow(() -> new TokenRefreshException(oldRefreshToken, "No active session found."));

        if (!activeToken.getToken().equals(oldRefreshToken)) {
            // Replay attack: Revoke token
            refreshTokenRepository.delete(activeToken);
            throw new TokenRefreshException(oldRefreshToken, "Refresh token was reused. Session revoked.");
        }

        // 5. Generate NEW tokens and save/rotate
        final String newAccessToken = jwtService.generateAccessToken(userDetails);
        final String newRefreshTokenString = jwtService.generateRefreshToken(userDetails);

        saveRefreshToken(userDetails, newRefreshTokenString);

        return new AuthenticationResult(newAccessToken, newRefreshTokenString);
    }

    /**
     * Helper method to save/update the RefreshToken, using EntityManager
     * to safely handle the @MapsId (Shared Primary Key) relationship.
     */
    private void saveRefreshToken(User user, String tokenString) {

        // Attempt to find an existing token using the user's ID
        RefreshToken existingToken = refreshTokenRepository.findByUserId(user.getId())
                .orElse(null);

        if (existingToken == null) {
            // --- Creation Logic for NEW Token ---
            existingToken = new RefreshToken();

            // ESSENTIAL for @MapsId: Set the ID and the User for the new entity
            existingToken.setId(user.getId());
            existingToken.setUser(user);
            existingToken.setToken(tokenString);

            // Use EntityManager.persist() for explicit insertion of the new entity (Fixes AssertionFailure)
            entityManager.persist(existingToken);

        } else {
            // --- Update Logic for EXISTING Token ---
            existingToken.setToken(tokenString);

            // Use EntityManager.merge() for updating an existing entity (Fixes AssertionFailure)
            entityManager.merge(existingToken);
        }
    }

    /**
     * Deletes a user account after checking for self-deletion prevention.
     * @param usernameToDelete The username of the target user.
     * @param currentAdminUsername The username of the user making the request.
     */
    @Transactional
    public void deleteUser(String usernameToDelete, String currentAdminUsername) {

        // --- CRITICAL SECURITY CHECK ---
        if (usernameToDelete.equals(currentAdminUsername)) {
            // Throw AccessDeniedException to correctly trigger a 403 Forbidden response.
            throw new AccessDeniedException("An administrator cannot delete their own account.");
        }
        // --- END CRITICAL SECURITY CHECK ---

        // 1. Find the user by username
        User userToDelete = userRepository.findByUsername(usernameToDelete)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + usernameToDelete));

        // 2. Delete the user
        userRepository.delete(userToDelete);
    }

    /**
     * Finds a user's active Refresh Token and deletes it from the database,
     * immediately terminating the session.
     * @param username The username (email) of the user whose session must be revoked.
     */
    @Transactional
    public void revokeToken(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        // Delete the associated Refresh Token record by user ID (Terminates the session)
        refreshTokenRepository.deleteByUserId(user.getId());
    }
}