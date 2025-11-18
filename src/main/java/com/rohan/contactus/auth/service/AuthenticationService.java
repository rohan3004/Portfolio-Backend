package com.rohan.contactus.auth.service;

import com.rohan.contactus.auth.dto.LoginRequest;
import com.rohan.contactus.auth.dto.AuthenticationResult;
import com.rohan.contactus.auth.dto.RegisterRequest;
import com.rohan.contactus.auth.exception.TokenRefreshException;
import com.rohan.contactus.auth.repository.RefreshTokenRepository;
import com.rohan.contactus.auth.repository.UserRepository;
import com.rohan.contactus.model.RefreshToken;
import com.rohan.contactus.model.User;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserDetailsService userDetailsService;

    //registration
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public AuthenticationResult authenticate(LoginRequest request) {
        // 1. Authenticate credentials
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        // 2. Load UserDetails
        final User userDetails = (User) userDetailsService.loadUserByUsername(request.getUsername());

        // 3. Generate tokens
        final String accessToken = jwtService.generateAccessToken(userDetails);
        final String refreshTokenString = jwtService.generateRefreshToken(userDetails);

        // 4. Store/Update the Refresh Token (Uses explicit persistence fix)
        saveRefreshToken(userDetails, refreshTokenString);

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
    @Transactional
    public User register(RegisterRequest request) {
        // 1. Check if user already exists
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new IllegalStateException("User already exists: " + request.getUsername());
        }

        // 2. Hash the password
        String hashedPassword = passwordEncoder.encode(request.getPassword());

        // 3. Create the new User entity
        User newUser = new User();
        newUser.setUsername(request.getUsername());
        newUser.setPassword(hashedPassword);
        newUser.setRoles("ROLE_USER"); // Default role for new users

        // 4. Save the user to the database
        return userRepository.save(newUser);
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