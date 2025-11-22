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

    // generate-report?default_user=rohan3004&geeksforgeeks=donadutta30&codeforces=rohan.chakravarty30
    // <- Here default will be same for all platform but if custom then query like this
    @PostMapping("/v1/generate-report")
    public ResponseEntity<?> startGeneration(
            @RequestParam(name = "default_user", required = false) String defaultUser,
            @RequestParam Map<String, String> allParams,
            @AuthenticationPrincipal User user
    ) {
        String reportId = user.getUsername();
        if (reportId == null || reportId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "User authentication failed or username missing"));
        }

        Map<String, String> targets = new HashMap<>();
        for (String platform : URL_TEMPLATES.keySet()) {
            String username = allParams.getOrDefault(platform, defaultUser);
            if (username != null && !username.isBlank() && !username.equals(reportId)) {
                targets.put(platform, URL_TEMPLATES.get(platform).replace("{username}", username));
            }
        }

        if (targets.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No usernames provided"));
        }

        // Start the job asynchronously (Fire and Forget)
        scraperService.startScrapingJob(reportId, targets);

        return ResponseEntity.accepted().body(Map.of(
                "message", "Scraping initiated successfully.",
                "reportId", reportId,
                "note", "Check /reports/" + reportId + " later for results."
        ));
    }

    // 2. RESULT ENDPOINT (GET) - Authenticated
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