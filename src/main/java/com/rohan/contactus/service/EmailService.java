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
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("hello@rohandev.online");
        message.setTo(contact.getEmail());
        message.setSubject("Thank you for contacting Rohan’s Portfolio"); // concise subject :contentReference[oaicite:9]{index=9}

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
}
