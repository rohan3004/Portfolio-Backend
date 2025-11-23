package com.rohan.contactus.controller;

import com.rohan.contactus.model.User;
import com.rohan.contactus.service.ScraperService;
import com.rohan.contactus.service.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ReportController {

    private final ScraperService scraperService;
    private final S3Service s3Service;

    private static final Map<String, String> URL_TEMPLATES = Map.of(
            "codechef", "https://www.codechef.com/users/{username}",
            "codeforces", "https://codeforces.com/profile/{username}",
            "geeksforgeeks", "https://www.geeksforgeeks.org/user/{username}",
            "leetcode", "https://leetcode.com/u/{username}"
    );

    @PostMapping("/v1/generate-report")
    public ResponseEntity<?> startGeneration(@AuthenticationPrincipal User user) {

        String reportId = user.getUsername(); // This is the email
        if (reportId == null || reportId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "User authentication failed or username missing"));
        }

        // 1. Fetch Handles from DB (User Entity)
        Map<String, String> targets = new HashMap<>();

        if (user.getCodechefHandle() != null && !user.getCodechefHandle().isBlank()) {
            targets.put("codechef", URL_TEMPLATES.get("codechef").replace("{username}", user.getCodechefHandle()));
        }
        if (user.getCodeforcesHandle() != null && !user.getCodeforcesHandle().isBlank()) {
            targets.put("codeforces", URL_TEMPLATES.get("codeforces").replace("{username}", user.getCodeforcesHandle()));
        }
        if (user.getGfgHandle() != null && !user.getGfgHandle().isBlank()) {
            targets.put("geeksforgeeks", URL_TEMPLATES.get("geeksforgeeks").replace("{username}", user.getGfgHandle()));
        }
        if (user.getLeetcodeHandle() != null && !user.getLeetcodeHandle().isBlank()) {
            targets.put("leetcode", URL_TEMPLATES.get("leetcode").replace("{username}", user.getLeetcodeHandle()));
        }

        if (targets.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No coding profiles linked to this account. Please update your profile."));
        }

        // 2. Prepare Device Details (dd.json) from DB Data
        Map<String, Object> deviceDetails = new HashMap<>();
        deviceDetails.put("username", user.getUsername());
        deviceDetails.put("fullName", user.getFullName());
        deviceDetails.put("registrationIp", user.getRegistrationIp());
        deviceDetails.put("registrationUserAgent", user.getRegistrationUserAgent());
        deviceDetails.put("deviceId", user.getDeviceId());
        deviceDetails.put("platform", user.getPlatform());
        deviceDetails.put("osVersion", user.getOsVersion());
        deviceDetails.put("appVersion", user.getAppVersion());
        deviceDetails.put("screenResolution", user.getScreenResolution());
        deviceDetails.put("browserFingerprint", user.getBrowserFingerprint());
        deviceDetails.put("referralSource", user.getReferralSource());
        deviceDetails.put("createdAt", user.getCreatedAt() != null ? user.getCreatedAt().toString() : null);

        // 3. Start Async Job
        scraperService.startScrapingJob(reportId, targets, deviceDetails);

        return ResponseEntity.accepted().body(Map.of(
                "message", "Scraping initiated successfully.",
                "reportId", reportId,
                "targets", targets.keySet(), // Inform user which platforms are being scraped
                "note", "Check /reports/" + reportId + " later for results."
        ));
    }

    @GetMapping(value = "/v1/reports/{reportId}", produces = "application/json")
    public ResponseEntity<?> getReportSummary(@PathVariable String reportId) {
        String objectKey = reportId + "/summary.json";
        String jsonContent = s3Service.downloadFileAsString(objectKey);

        if (jsonContent == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(jsonContent);
    }
}