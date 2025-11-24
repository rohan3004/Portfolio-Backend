package com.rohan.contactus.service;

import com.rohan.contactus.entity.Contact;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;

    @Autowired
    public EmailService(JavaMailSender mailSender, SpringTemplateEngine templateEngine) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
    }


    public void sendEmail(Contact contact) {
        sendUserAcknowledgement(contact);
        sendInternalNotification(contact);
    }

    private void sendUserAcknowledgement(Contact contact) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, "UTF-8");
            helper.setFrom(new InternetAddress("hello@rcxdev.com","Rohan Chakravarty"));

            // Prepare the Thymeleaf context
            Context ctx = new Context();
            ctx.setVariable("subject", "Your request just landed in Rohan’s code pipeline—watch it deploy");
            ctx.setVariable("name",    contact.getName());
            ctx.setVariable("projectUrl", "https://www.rcxdev.com/#projects");
            ctx.setVariable("footerHeader","Next Update: In Your Inbox");
            ctx.setVariable("privacyUrl","https://rcxdev.com/legal/privacy");
            ctx.setVariable("termsUrl", "https://rcxdev.com/legal/terms");
            ctx.setVariable("contactUrl", "https://rcxdev.com/#contact");

            // Process the HTML template
            String htmlContent = templateEngine.process("user_ack", ctx);

            helper.setFrom("hello@rcxdev.com");
            helper.setTo(contact.getEmail());
            helper.setSubject("Your request just landed in Rohan’s code pipeline—watch it deploy");
            helper.setText(htmlContent, true);

            mailSender.send(mime);
        } catch (MessagingException e) {
            // log or rethrow as needed
            throw new IllegalStateException("Failed to send HTML email", e);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Failed to send HTML email", e);
        }
    }

    private void sendInternalNotification(Contact contact) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("no-reply@byrohan.in");
        message.setTo("hello@rcxdev.com");//my personal address so that I can receive those submissions previously it was my mail now this forwards to my mail

        message.setSubject("New contact form submission from " + contact.getName());
        String internalBody = String.format(
                "You have a new contact submission:%n%n" +
                        "Name: %s%n" +
                        "Email: %s%n" +
                        "Contact No: %s%n" +
                        "Message:%n%s%n%n" +
                        "Received on: %s",
                contact.getName(),
                contact.getEmail(),
                contact.getContactNo(),
                contact.getMessage(),
                java.time.ZonedDateTime.now()      // timestamp for your reference
        );
        message.setText(internalBody);
        mailSender.send(message);
    }
    /**
     * NEW METHOD: Sends the OTP code using a Thymeleaf template.
     */
    public void sendOtpEmail(String recipientEmail, String otpCode, int otpExpiryMinutes) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, "UTF-8");

            // --- Sender Configuration ---
            helper.setFrom(new InternetAddress("no-reply@byrohan.in", "RCX Auth Engine"));
            helper.setTo(recipientEmail);
            helper.setSubject("Your One-Time Login Code");

            // --- Thymeleaf Context ---
            Context ctx = new Context();
            ctx.setVariable("username", recipientEmail);
            ctx.setVariable("otpCode", otpCode);
            ctx.setVariable("otpExpiryMinutes", otpExpiryMinutes);

            // Process the HTML template (otp_email.html)
            String htmlContent = templateEngine.process("otp_email", ctx);

            helper.setText(htmlContent, true); // Set HTML content
            mailSender.send(mime);

        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new IllegalStateException("Failed to send OTP email to " + recipientEmail, e);
        }
    }
    /**
     * Updated: Sends a Sci-Fi themed reminder with detailed platform stats.
     */
    public void sendStreakReminder(String recipientEmail, String name, Long currentTotal, Map<String, Long> platformStats) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, "UTF-8");

            helper.setFrom(new InternetAddress("no-reply@byrohan.in", "Rohan's Portfolio"));
            helper.setTo(recipientEmail);
            helper.setSubject("⚠️ Streak Alert: No Progress Detected Today");

            Context ctx = new Context();
            ctx.setVariable("name", name);
            ctx.setVariable("totalCount", currentTotal);
            ctx.setVariable("platformStats", platformStats); // Map<String, Long>

            String htmlContent = templateEngine.process("streak_reminder", ctx);
            helper.setText(htmlContent, true);
            mailSender.send(mime);

        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new IllegalStateException("Failed to send streak reminder", e);
        }
    }

    public void sendLoginNotification(String email, String ip, String rawUserAgent, String resolution, Map<String, String> codingHandles) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, "UTF-8");
            helper.setFrom(new InternetAddress("no-reply@byrohan.in", "Security System"));
            helper.setTo(email);
            helper.setSubject("Security Alert: New Login Detected");

            Context ctx = new Context();
            ctx.setVariable("username", email);
            ctx.setVariable("time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " UTC");
            ctx.setVariable("ip", ip);

            String os = "Unknown OS";
            String browser = "Unknown Browser";
            if (rawUserAgent != null) {
                if (rawUserAgent.contains("Windows")) os = "Windows NT";
                else if (rawUserAgent.contains("Mac")) os = "MacOS / iOS";
                else if (rawUserAgent.contains("Linux")) os = "Linux / Android";

                if (rawUserAgent.contains("Chrome")) browser = "Chrome Engine";
                else if (rawUserAgent.contains("Firefox")) browser = "Firefox";
                else if (rawUserAgent.contains("Safari")) browser = "Safari";
            }

            ctx.setVariable("os", os);
            ctx.setVariable("browser", browser);
            ctx.setVariable("resolution", resolution);
            ctx.setVariable("codingHandles", codingHandles);

            String htmlContent = templateEngine.process("login_notification", ctx);
            helper.setText(htmlContent, true);
            mailSender.send(mime);

        } catch (Exception e) {
            System.err.println("Failed to send login notification: " + e.getMessage());
        }
    }
}
