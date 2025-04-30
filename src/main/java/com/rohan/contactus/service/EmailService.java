package com.rohan.contactus.service;

import com.rohan.contactus.entity.Contact;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    public void sendEmail(Contact contact) {
        sendUserAcknowledgement(contact);
        sendInternalNotification(contact);
    }

    private void sendUserAcknowledgement(Contact contact) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("hello@rohandev.online");
        message.setTo(contact.getEmail());
        message.setSubject("Thank you for contacting Rohan’s Portfolio");

        String body = String.format(
                "Hi %s,%n%n" +
                        "Thank you for reaching out via my portfolio website. I appreciate your interest and will review your message shortly.%n%n" +
                        "You can expect a reply from me within 2 business days. In the meantime, feel free to explore my latest projects here: https://rohandev.online%n%n" +
                        "Best regards,%n" +
                        "Rohan Chakravarty%n" +
                        "Software Engineer | rohandev.online%n" +
                        "LinkedIn: https://www.linkedin.com/in/rohan-chakravarty-/",
                contact.getName()
        );
        message.setText(body);

        mailSender.send(message);
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
