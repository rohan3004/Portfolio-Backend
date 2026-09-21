package com.rohan.portfolio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bonigarcia.wdm.WebDriverManager;
import lombok.RequiredArgsConstructor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPOutputStream;

@Service
@RequiredArgsConstructor
public class ScraperService {

    private final S3Service s3Service;
    private final ObjectMapper objectMapper;
    private final LeetCodeApiService leetCodeApiService;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    public void startScrapingJob(String reportId, Map<String, String> targets) {
        startScrapingJob(reportId, targets, null);
    }

    public void startScrapingJob(String reportId, Map<String, String> targets, Map<String, Object> deviceDetails) {
        executor.execute(() -> {
            WebDriver driver = null;
            try {

                // 1. Upload Device Details (dd.json)
                if (deviceDetails != null && !deviceDetails.isEmpty()) {
                    uploadDeviceDetails(reportId, deviceDetails);
                }

                // 2. Scrape Targets.
                //    LeetCode uses the GraphQL API (no browser). Other platforms use
                //    Selenium, and Chrome is started lazily only if one is present.
                for (Map.Entry<String, String> entry : targets.entrySet()) {
                    String platform = entry.getKey();
                    String url = entry.getValue();

                    if ("leetcode".equals(platform)) {
                        processLeetCode(reportId, platform, url);
                        continue;
                    }

                    if (driver == null) {
                        WebDriverManager.chromedriver().setup();
                        ChromeOptions options = new ChromeOptions();
                        options.addArguments("--headless=new", "--disable-dev-shm-usage", "--no-sandbox");
                        options.addArguments("--log-level=3"); // Suppress DevTools warnings
                        options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
                        driver = new ChromeDriver(options);
                    }

                    processSingleTarget(driver, reportId, platform, url);
                }

            } catch (Exception ignored) {
            } finally {
                if (driver != null) {
                    try { driver.quit(); } catch (Exception ignored) {}
                }
            }
        });
    }

    private void uploadDeviceDetails(String reportId, Map<String, Object> details) {
        try {
            // Convert Map to JSON byte array in memory
            byte[] jsonBytes = objectMapper.writeValueAsBytes(details);

            String objectKey = reportId + "/dd.json";

            // Upload bytes directly
            s3Service.uploadFile(objectKey, jsonBytes, "application/json", null);


        } catch (Exception ignored) {
        }
    }

    private void processLeetCode(String reportId, String platform, String url) {
        try {
            // Extract the username from the profile URL: https://leetcode.com/u/{username}
            String username = extractLeetCodeUsername(url);

            byte[] jsonBytes = leetCodeApiService.fetchProfileStats(username);

            // Compress the raw JSON payload in memory (gzip), mirroring the
            // other platforms' compressed uploads.
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            try (GZIPOutputStream gzipOS = new GZIPOutputStream(byteArrayOutputStream)) {
                gzipOS.write(jsonBytes);
            }
            byte[] compressedBytes = byteArrayOutputStream.toByteArray();

            String objectKey = reportId + "/raw/" + platform + ".gz";
            s3Service.uploadFile(objectKey, compressedBytes, "application/gzip", "gzip");

        } catch (Exception ignored) {
        }
    }

    private String extractLeetCodeUsername(String url) {
        String cleaned = url;
        if (cleaned.endsWith("/")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        int slash = cleaned.lastIndexOf('/');
        return slash >= 0 ? cleaned.substring(slash + 1) : cleaned;
    }

    private void processSingleTarget(WebDriver driver, String reportId, String platform, String url) {
        try {
            driver.get(url);

            // Wait for dynamic content
            try { Thread.sleep(5000); } catch (InterruptedException ignored) {}

            String pageSource = driver.getPageSource();
            if (pageSource == null) pageSource = "";

            // Compress in memory
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            try (GZIPOutputStream gzipOS = new GZIPOutputStream(byteArrayOutputStream)) {
                gzipOS.write(pageSource.getBytes(StandardCharsets.UTF_8));
            }
            byte[] compressedBytes = byteArrayOutputStream.toByteArray();

            String objectKey = reportId + "/raw/" + platform + ".gz";

            // Upload compressed bytes directly
            s3Service.uploadFile(objectKey, compressedBytes, "application/gzip", "gzip");


        } catch (Exception ignored) {
        }
    }
}