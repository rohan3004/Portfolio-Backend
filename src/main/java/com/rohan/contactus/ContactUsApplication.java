package com.rohan.contactus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@SpringBootApplication
@EnableMethodSecurity
public class ContactUsApplication {

    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String newHash = encoder.encode("Cactus!42Lemon$Giraffe1530");
        System.out.println("New Hash for 'testpassword': " + newHash);
        SpringApplication.run(ContactUsApplication.class, args);
    }

}
