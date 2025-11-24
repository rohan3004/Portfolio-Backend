package com.rohan.contactus.scheduler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rohan.contactus.auth.repository.UserRepository;
import com.rohan.contactus.model.User;
import com.rohan.contactus.service.EmailService;
import com.rohan.contactus.service.ScraperService;
import com.rohan.contactus.service.S3Service;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ScrapingScheduler {

    private static final Logger logger = LoggerFactory.getLogger(ScrapingScheduler.class);

    private final ScraperService scraperService;
    private final S3Service s3Service;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;

    private static final Map<String, String> URL_TEMPLATES = Map.of(
            "codechef", "https://www.codechef.com/users/{username}",
            "codeforces", "https://codeforces.com/profile/{username}",
            "geeksforgeeks", "https://www.geeksforgeeks.org/user/{username}",
            "leetcode", "https://leetcode.com/u/{username}"
    );

    // --- JOB 1: 7:00 PM DAILY - TRIGGER SCRAPE FOR ALL USERS ---
    @Scheduled(cron = "0 0 19 * * *")
    public void runScheduledScrape() {
        logger.info("Starting daily scrape job for ALL users.");

        // Fetch all users (ensure this is performant enough for your user base size)
        List<User> allUsers = userRepository.findAll();

        for (User user : allUsers) {
            try {
                Map<String, String> targets = new HashMap<>();
                addIfPresent(targets, "codechef", user.getCodechefHandle());
                addIfPresent(targets, "codeforces", user.getCodeforcesHandle());
                addIfPresent(targets, "geeksforgeeks", user.getGfgHandle());
                addIfPresent(targets, "leetcode", user.getLeetcodeHandle());

                if (!targets.isEmpty()) {
                    logger.info("Triggering scrape for user: {}", user.getEmail());
                    // Pass the email as reportId
                    scraperService.startScrapingJob(user.getEmail(), targets);
                }
            } catch (Exception e) {
                logger.error("Failed to trigger scrape for user: " + user.getEmail(), e);
            }
        }
    }

    // --- JOB 2: 7:30 PM DAILY - CHECK PROGRESS & NOTIFY ALL USERS ---
    @Scheduled(cron = "0 30 19 * * *")
    public void checkProgressAndNotify() {
        logger.info("Starting progress check for ALL users.");

        List<User> allUsers = userRepository.findAll();

        for (User user : allUsers) {
            processUserProgress(user);
        }
    }

    /**
     * Process individual user progress check.
     * Handles S3 fetch, parsing, logic, and DB update.
     */
    private void processUserProgress(User user) {
        try {
            // 1. Fetch summary.json from S3 using Email as key
            String objectKey = user.getEmail() + "/summary.json";
            String jsonContent = s3Service.downloadFileAsString(objectKey);

            if (jsonContent == null) {
                // Silent return if no report exists yet
                return;
            }

            // 2. Parse JSON
            JsonNode root = objectMapper.readTree(jsonContent);
            long newTotal = 0;
            Map<String, Long> platformStats = new HashMap<>();

            // Parse CodeChef
            if (root.has("codechef")) {
                long count = root.get("codechef").path("problems_solved_total").asLong(0);
                newTotal += count;
                platformStats.put("CodeChef", count);
            }

            // Parse Codeforces
            if (root.has("codeforces")) {
                long count = root.get("codeforces").path("problems_solved_total").asLong(0);
                newTotal += count;
                platformStats.put("Codeforces", count);
            }

            // Parse GeeksForGeeks
            if (root.has("geeksforgeeks")) {
                long count = root.get("geeksforgeeks").path("problems_solved_total").asLong(0);
                newTotal += count;
                platformStats.put("GFG", count);
            }

            // Parse LeetCode
            if (root.has("leetcode")) {
                long count = root.get("leetcode").path("problems_solved_total").asLong(0);
                newTotal += count;
                platformStats.put("LeetCode", count);
            }

            long oldTotal = user.getTotalSolvedQuestions() != null ? user.getTotalSolvedQuestions() : 0;

            // 3. Compare & Notify
            // Only notify if they have previously solved questions (oldTotal > 0) but made no progress today
            if (newTotal <= oldTotal && oldTotal > 0) {
                logger.info("Streak broken for {}. Sending reminder.", user.getEmail());

                // FIX: Pass the platformStats map as the 4th argument
                emailService.sendStreakReminder(user.getEmail(), user.getFullName(), oldTotal, platformStats);
            }

            // 4. Update DB (Only if count changed, to save writes)
            if (newTotal != oldTotal) {
                user.setTotalSolvedQuestions(newTotal);
                userRepository.save(user); // Save individual user
            }

        } catch (Exception e) {
            logger.error("Failed to check progress for user: " + user.getEmail(), e);
        }
    }

    private void addIfPresent(Map<String, String> targets, String platform, String handle) {
        if (handle != null && !handle.isBlank()) {
            targets.put(platform, URL_TEMPLATES.get(platform).replace("{username}", handle));
        }
    }
}