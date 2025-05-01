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
            helper.setFrom(new InternetAddress("hello@rohandev.online","Rohan Chakravarty"));

            // Prepare the Thymeleaf context
            Context ctx = new Context();
            ctx.setVariable("subject", "Thank you for contacting Rohan’s Portfolio");
            ctx.setVariable("name",    contact.getName());
            ctx.setVariable("projectUrl", "https://www.rohandev.online/#projects");

            // Process the HTML template
            String htmlContent = templateEngine.process("user_ack", ctx);

            helper.setFrom("hello@rohandev.online");
            helper.setTo(contact.getEmail());
            helper.setSubject("Thank you for contacting Rohan’s Portfolio");
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
        message.setFrom("hello@rohandev.online");
        message.setTo("rohan.chakravarty02@gmail.com");

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

}
