package com.rohan.contactus.service;

import io.github.bonigarcia.wdm.WebDriverManager;
import lombok.RequiredArgsConstructor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPOutputStream;

@Service
@RequiredArgsConstructor
public class ScraperService {

    private final S3Service s3Service;

    // Thread pool for async execution
    private final ExecutorService executor = Executors.newCachedThreadPool();

    /**
     * Triggers the scraping process asynchronously.
     */
    public void startScrapingJob(String reportId, Map<String, String> targets) {
        executor.execute(() -> {
            WebDriver driver = null;
            try {

                // Setup Chrome Driver
                WebDriverManager.chromedriver().setup();
                ChromeOptions options = new ChromeOptions();
                options.addArguments("--headless=new", "--disable-dev-shm-usage", "--no-sandbox");
                options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

                driver = new ChromeDriver(options);

                for (Map.Entry<String, String> entry : targets.entrySet()) {
                    processSingleTarget(driver, reportId, entry.getKey(), entry.getValue());
                }

            } catch (Exception ignored) {
            } finally {
                if (driver != null) {
                    driver.quit();
                }
            }
        });
    }

    private void processSingleTarget(WebDriver driver, String reportId, String platform, String url) {
        try {
            driver.get(url);

            try { Thread.sleep(3000); } catch (InterruptedException ignored) {}

            String pageSource = driver.getPageSource();

            // Compress
            File tempFile = File.createTempFile(platform, ".gz");
            try (FileOutputStream fos = new FileOutputStream(tempFile);
                 GZIPOutputStream gzipOS = new GZIPOutputStream(fos)) {
                gzipOS.write(pageSource.getBytes(StandardCharsets.UTF_8));
            }

            // Upload to S3
            String objectKey = reportId + "/raw/" + platform + ".gz";
            s3Service.uploadFile(objectKey, tempFile);

            // Cleanup
            tempFile.delete();

        } catch (Exception ignored) {
        }
    }
}