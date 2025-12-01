package com.rohan.portfolio.auth.service;

import com.rohan.portfolio.auth.dto.AuthenticationResult;
import com.rohan.portfolio.auth.dto.FullRegistrationRequest;
import com.rohan.portfolio.auth.dto.LoginRequest;
import com.rohan.portfolio.auth.dto.VerifyOtpResponse;
import com.rohan.portfolio.auth.exception.TokenRefreshException;
import com.rohan.portfolio.auth.repository.LoginHistoryRepository; // FIXED: Added Import
import com.rohan.portfolio.auth.repository.OtpRepository;
import com.rohan.portfolio.auth.repository.RefreshTokenRepository;
import com.rohan.portfolio.auth.repository.UserRepository;
import com.rohan.portfolio.model.LoginHistory;
import com.rohan.portfolio.model.Otp;
import com.rohan.portfolio.model.RefreshToken;
import com.rohan.portfolio.model.User;
import com.rohan.portfolio.service.EmailService;
import com.rohan.portfolio.service.ScraperService;
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
import java.util.HashMap;
import java.util.Map;
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
    private final ScraperService scraperService;

    //registration
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${application.security.otp.expiration-minutes:5}")
    private int otpExpiryMinutes;

    // Cooldown for scraping in hours
    private static final long SCRAPE_COOLDOWN_HOURS = 3;


    private static final Map<String, String> URL_TEMPLATES = Map.of(
            "codechef", "https://www.codechef.com/users/{username}",
            "codeforces", "https://codeforces.com/profile/{username}",
            "geeksforgeeks", "https://www.geeksforgeeks.org/user/{username}",
            "leetcode", "https://leetcode.com/u/{username}"
    );

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

    // --- 2. VERIFY OTP ---
    @Transactional
    public VerifyOtpResponse verifyOtp(LoginRequest request, HttpServletRequest httpRequest) {
        User user = userRepository.findByEmail(request.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("User not found."));

        Otp otpEntity = otpRepository.findByUser(user)
                .orElseThrow(() -> new BadCredentialsException("No OTP found."));

        if (otpEntity.getExpiresAt().isBefore(LocalDateTime.now())) {
            logLoginAttempt(user, false, "OTP Expired", httpRequest);
            throw new BadCredentialsException("OTP expired.");
        }
        if (!otpEntity.getCode().equals(request.getOtp())) {
            logLoginAttempt(user, false, "Invalid OTP", httpRequest);
            throw new BadCredentialsException("Invalid OTP.");
        }

        otpRepository.delete(otpEntity);

        if (user.getAccountStatus() != User.AccountStatus.ACTIVE) {
            throw new BadCredentialsException("Account is " + user.getAccountStatus());
        }

        user.setLastLoginAt(LocalDateTime.now());
        user.setEmailVerified(true);

        if (!user.isProfileComplete()) {
            String tempToken = UUID.randomUUID().toString();
            user.setTempRegistrationToken(tempToken);
            userRepository.save(user);
            return new VerifyOtpResponse(true, "Registration required", tempToken, user.getEmail(), null, null);
        } else {
            // --- TRIGGER SCRAPE IF DUE ---
            triggerScrapeIfDue(user);
            // -----------------------------

            final String accessToken = jwtService.generateAccessToken(user);
            final String refreshTokenString = jwtService.generateRefreshToken(user);
            saveRefreshToken(user, refreshTokenString);

            logLoginAttempt(user, true, "Success", httpRequest);
            triggerLoginEmail(user, httpRequest);

            return new VerifyOtpResponse(false, "Login successful", null, user.getEmail(), accessToken, refreshTokenString);
        }
    }

    // --- 3. COMPLETE REGISTRATION ---
    @Transactional
    public AuthenticationResult completeRegistration(FullRegistrationRequest request, HttpServletRequest httpRequest) {

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

        // Update Device Info for dd.json
        user.setDeviceId(request.getDeviceId());
        user.setPlatform(request.getPlatform());
        user.setAppVersion(request.getAppVersion());
        user.setOsVersion(request.getOsVersion());
        user.setScreenResolution(request.getScreenResolution());
        user.setBrowserFingerprint(request.getBrowserFingerprint());

        user.setProfileComplete(true);
        user.setFirstLoginAt(LocalDateTime.now());
        user.setTempRegistrationToken(null);

        userRepository.save(user);

        // --- Trigger Initial Scrape & Device Details Upload ---
        triggerInitialScrape(user);
        // ----------------------------------------------------

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

        // --- TRIGGER SCRAPE IF DUE (On Session Renewal) ---
        triggerScrapeIfDue(user);
        // --------------------------------------------------

        final String newAccessToken = jwtService.generateAccessToken(user);
        final String newRefreshTokenString = jwtService.generateRefreshToken(user);

        saveRefreshToken(user, newRefreshTokenString);
        return new AuthenticationResult(newAccessToken, newRefreshTokenString);
    }

    // --- HELPERS ---

    private void triggerLoginEmail(User user, HttpServletRequest req) {
        String ip = getClientIp(req);
        String ua = req.getHeader("User-Agent");

        // Gather non-null coding handles for the email
        Map<String, String> handles = new HashMap<>();
        if (user.getCodechefHandle() != null) handles.put("CodeChef", user.getCodechefHandle());
        if (user.getLeetcodeHandle() != null) handles.put("LeetCode", user.getLeetcodeHandle());
        if (user.getCodeforcesHandle() != null) handles.put("Codeforces", user.getCodeforcesHandle());
        if (user.getGfgHandle() != null) handles.put("Gfg", user.getGfgHandle());

        // Pass resolution if we have it stored (from registration) or null
        String resolution = user.getScreenResolution();

        emailService.sendLoginNotification(user.getEmail(), ip, ua, resolution, handles);
    }

    private void triggerInitialScrape(User user) {
        // 1. Build Device Details Map
        Map<String, Object> deviceDetails = new HashMap<>();
        deviceDetails.put("username", user.getUsername());
        deviceDetails.put("fullName", user.getFullName());
        deviceDetails.put("email", user.getEmail());
        deviceDetails.put("registrationIp", user.getRegistrationIp());
        deviceDetails.put("registrationUserAgent", user.getRegistrationUserAgent());
        deviceDetails.put("deviceId", user.getDeviceId());
        deviceDetails.put("platform", user.getPlatform());
        deviceDetails.put("osVersion", user.getOsVersion());
        deviceDetails.put("browserFingerprint", user.getBrowserFingerprint());
        deviceDetails.put("createdAt", user.getCreatedAt() != null ? user.getCreatedAt().toString() : null);

        // 2. Build Targets Map
        Map<String, String> targets = new HashMap<>();
        addIfPresent(targets, "codechef", user.getCodechefHandle());
        addIfPresent(targets, "codeforces", user.getCodeforcesHandle());
        addIfPresent(targets, "geeksforgeeks", user.getGfgHandle());
        addIfPresent(targets, "leetcode", user.getLeetcodeHandle());

        // 3. Trigger Fire-and-Forget Job
        // Note: user.getEmail() acts as the reportId/S3 folder name
        if (!targets.isEmpty()) {
            scraperService.startScrapingJob(user.getEmail(), targets, deviceDetails);
        }
    }

    private void addIfPresent(Map<String, String> targets, String platform, String handle) {
        if (handle != null && !handle.isBlank()) {
            targets.put(platform, URL_TEMPLATES.get(platform).replace("{username}", handle));
        }
    }

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

    private void triggerScrapeIfDue(User user) {
        // If never scraped OR scraped more than 3 hours ago
        if (user.getLastScrapedAt() == null ||
                Duration.between(user.getLastScrapedAt(), LocalDateTime.now()).toHours() >= SCRAPE_COOLDOWN_HOURS) {

            triggerInitialScrape(user);

            // Update timestamp
            user.setLastScrapedAt(LocalDateTime.now());
            userRepository.save(user);
        }
    }
}