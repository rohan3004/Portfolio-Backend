package com.rohan.contactus.auth.service;

import com.rohan.contactus.auth.dto.*;
import com.rohan.contactus.auth.exception.TokenRefreshException;
import com.rohan.contactus.auth.repository.LoginHistoryRepository; // FIXED: Added Import
import com.rohan.contactus.auth.repository.OtpRepository;
import com.rohan.contactus.auth.repository.RefreshTokenRepository;
import com.rohan.contactus.auth.repository.UserRepository;
import com.rohan.contactus.model.LoginHistory;
import com.rohan.contactus.model.Otp;
import com.rohan.contactus.model.RefreshToken;
import com.rohan.contactus.model.User;
import com.rohan.contactus.service.EmailService;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration; // FIXED: Added Import
import java.time.LocalDateTime;
import java.util.UUID;     // FIXED: Added Import

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserDetailsService userDetailsService;
    private final OtpRepository otpRepository;
    private final EmailService emailService;
    private final LoginHistoryRepository loginHistoryRepository; // Field exists, import was missing

    //registration
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${application.security.otp.expiration-minutes:5}")
    private int otpExpiryMinutes;

    // --- 1. SEND / RESEND OTP ---
    @Transactional
    public void sendOtp(String email) {

        // Find or Create Skeleton User
        User user = userRepository.findByEmail(email)
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setEmail(email);
                    newUser.setRoles("ROLE_USER");
                    newUser.setProfileComplete(false); // Mark as incomplete
                    newUser.setCreatedAt(LocalDateTime.now());
                    return userRepository.save(newUser);
                });

        Otp otp = otpRepository.findByUser(user).orElse(new Otp());
        String otpCode;

        // Rate Limit & Reuse Logic
        // FIXED: Duration class is now imported
        if (otp.getLastSentAt() != null &&
                Duration.between(otp.getLastSentAt(), LocalDateTime.now()).getSeconds() < 60) {
            throw new IllegalStateException("Please wait 60 seconds before requesting a new code.");
        }

        // Reuse existing code if valid, otherwise generate new
        if (otp.getCode() != null && otp.getExpiresAt().isAfter(LocalDateTime.now())) {
            otpCode = otp.getCode();
        } else {
            otpCode = String.format("%06d", new SecureRandom().nextInt(999999));
            otp.setCode(otpCode);
            otp.setExpiresAt(LocalDateTime.now().plusMinutes(otpExpiryMinutes));
        }

        otp.setUser(user);
        otp.setLastSentAt(LocalDateTime.now()); // FIXED: Otp class must have this field
        otpRepository.save(otp);

        emailService.sendOtpEmail(email, otpCode, otpExpiryMinutes);
    }

    // --- 2. VERIFY OTP (Intermediate Step) ---
    @Transactional
    public VerifyOtpResponse verifyOtp(LoginRequest request, HttpServletRequest httpRequest) {
        User user = userRepository.findByEmail(request.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("User not found."));

        Otp otpEntity = otpRepository.findByUser(user)
                .orElseThrow(() -> new BadCredentialsException("No OTP found."));

        // Validate
        if (otpEntity.getExpiresAt().isBefore(LocalDateTime.now())) {
            logLoginAttempt(user, false, "OTP Expired", httpRequest);
            throw new BadCredentialsException("OTP expired.");
        }
        if (!otpEntity.getCode().equals(request.getOtp())) {
            logLoginAttempt(user, false, "Invalid OTP", httpRequest);
            throw new BadCredentialsException("Invalid OTP.");
        }

        // OTP Correct
        otpRepository.delete(otpEntity); // Consume OTP

        // Verify Status
        if (user.getAccountStatus() != User.AccountStatus.ACTIVE) {
            throw new BadCredentialsException("Account is " + user.getAccountStatus());
        }

        user.setLastLoginAt(LocalDateTime.now());
        user.setEmailVerified(true);

        if (!user.isProfileComplete()) {
            // --- SCENARIO A: New User / Incomplete Profile ---
            String tempToken = UUID.randomUUID().toString(); // FIXED: UUID imported
            user.setTempRegistrationToken(tempToken);
            userRepository.save(user);

            // FIX: Updated to match 6-argument constructor (added null for refreshToken)
            return new VerifyOtpResponse(true, "Registration required", tempToken, user.getEmail(), null, null);
        } else {
            // --- SCENARIO B: Existing User (Full Login) ---
            final String accessToken = jwtService.generateAccessToken(user);
            final String refreshTokenString = jwtService.generateRefreshToken(user);
            saveRefreshToken(user, refreshTokenString);

            logLoginAttempt(user, true, "Success", httpRequest);
            // FIX: Updated to match 6-argument constructor
            return new VerifyOtpResponse(false, "Login successful", null, user.getEmail(), accessToken, refreshTokenString);
        }
    }

    // --- 3. COMPLETE REGISTRATION (Final Step) ---
    @Transactional
    public AuthenticationResult completeRegistration(FullRegistrationRequest request, HttpServletRequest httpRequest) {

        // Verify username uniqueness
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalStateException("Username '" + request.getUsername() + "' is already taken.");
        }

        User user = userRepository.findAll().stream()
                .filter(u -> request.getRegistrationToken().equals(u.getTempRegistrationToken()))
                .findFirst()
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired registration token."));

        // Update User Details
        user.setUsername(request.getUsername());
        user.setFullName(request.getFullName());
        user.setPhoneNumber(request.getPhoneNumber());

        user.setGender(request.getGender());
        user.setDateOfBirth(request.getDateOfBirth());
        user.setPinCode(request.getPinCode());
        user.setCountry(request.getCountry());
        user.setState(request.getState());
        user.setCity(request.getCity());

        user.setCodechefHandle(request.getCodechefHandle());
        user.setCodeforcesHandle(request.getCodeforcesHandle());
        user.setGfgHandle(request.getGfgHandle());
        user.setLeetcodeHandle(request.getLeetcodeHandle());

        user.setRegistrationIp(getClientIp(httpRequest));
        user.setRegistrationUserAgent(httpRequest.getHeader("User-Agent"));
        user.setReferralSource(request.getReferralSource());

        // Fingerprinting
        user.setDeviceId(request.getDeviceId());
        user.setPlatform(request.getPlatform());
        user.setAppVersion(request.getAppVersion());
        user.setOsVersion(request.getOsVersion());
        user.setScreenResolution(request.getScreenResolution());
        user.setBrowserFingerprint(request.getBrowserFingerprint());

        user.setProfileComplete(true);
        user.setFirstLoginAt(LocalDateTime.now());
        user.setTempRegistrationToken(null); // Consume token

        userRepository.save(user);

        // Issue Tokens
        final String accessToken = jwtService.generateAccessToken(user);
        final String refreshTokenString = jwtService.generateRefreshToken(user);
        saveRefreshToken(user, refreshTokenString);

        logLoginAttempt(user, true, "Registration Success", httpRequest);

        return new AuthenticationResult(accessToken, refreshTokenString);
    }

    // --- 4. REFRESH TOKEN ---
    @Transactional
    public AuthenticationResult refreshToken(String oldRefreshToken) {
        String email;
        try {
            email = jwtService.extractUsername(oldRefreshToken);
        } catch (ExpiredJwtException e) {
            throw new TokenRefreshException(oldRefreshToken, "Refresh token expired.");
        } catch (Exception e) {
            throw new TokenRefreshException(oldRefreshToken, "Invalid refresh token.");
        }

        final User user = (User) userDetailsService.loadUserByUsername(email);

        if (!jwtService.isTokenValid(oldRefreshToken, user)) {
            throw new TokenRefreshException(oldRefreshToken, "Invalid signature.");
        }

        RefreshToken activeToken = refreshTokenRepository.findByUserId(user.getId())
                .orElseThrow(() -> new TokenRefreshException(oldRefreshToken, "Session not found."));

        if (!activeToken.getToken().equals(oldRefreshToken)) {
            refreshTokenRepository.delete(activeToken);
            throw new TokenRefreshException(oldRefreshToken, "Token reused. Revoked.");
        }

        final String newAccessToken = jwtService.generateAccessToken(user);
        final String newRefreshTokenString = jwtService.generateRefreshToken(user);

        saveRefreshToken(user, newRefreshTokenString);
        return new AuthenticationResult(newAccessToken, newRefreshTokenString);
    }

    // --- HELPERS ---

    private void logLoginAttempt(User user, boolean success, String reason, HttpServletRequest req) {
        LoginHistory history = new LoginHistory();
        history.setUserId(user.getId());
        history.setLoginTime(LocalDateTime.now());
        history.setSuccess(success);
        history.setFailureReason(reason);
        history.setIpAddress(getClientIp(req));
        history.setUserAgent(req.getHeader("User-Agent"));
        loginHistoryRepository.save(history); // FIXED: Repository is now imported and accessible
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) return request.getRemoteAddr();
        return xfHeader.split(",")[0];
    }

    private void saveRefreshToken(User user, String tokenString) {
        RefreshToken existingToken = refreshTokenRepository.findByUserId(user.getId()).orElse(null);
        if (existingToken == null) {
            existingToken = new RefreshToken();
            existingToken.setId(user.getId());
            existingToken.setUser(user);
            existingToken.setToken(tokenString);
            entityManager.persist(existingToken);
        } else {
            existingToken.setToken(tokenString);
            entityManager.merge(existingToken);
        }
    }

    @Transactional
    public void deleteUser(String emailToDelete, String currentAdminEmail) {
        if (emailToDelete.equals(currentAdminEmail)) throw new AccessDeniedException("No self-delete.");
        User u = userRepository.findByEmail(emailToDelete).orElseThrow(() -> new UsernameNotFoundException("User not found"));
        userRepository.delete(u);
    }

    @Transactional
    public void revokeToken(String emailToDelete, String currentAdminEmail) {
        if (emailToDelete.equals(currentAdminEmail)) throw new AccessDeniedException("No self-revoke.");
        User user = userRepository.findByEmail(emailToDelete).orElseThrow(() -> new UsernameNotFoundException("User not found: " + emailToDelete));
        refreshTokenRepository.deleteByUserId(user.getId());
    }
}