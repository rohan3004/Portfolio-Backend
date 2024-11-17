package com.rohan.contactus.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/contact")
                .allowedOrigins("https://www.rohandev.online","https://rohandev.online","https://rohan3004.github.io") // Change to your actual origin
                .allowedMethods("POST", "GET", "OPTIONS");

        registry.addMapping("/github/**")
                .allowedOrigins("https://www.rohandev.online","https://rohandev.online","https://rohan3004.github.io") // Change to your actual origin
                .allowedMethods("GET");

        registry.addMapping("/your_ip")
                .allowedOrigins("https://www.rohandev.online","https://rohandev.online","https://rohan3004.github.io")
                .allowedMethods("GET")
                .allowCredentials(true)
                .allowedHeaders("Authorization", "Cache-Control", "Content-Type")
                .exposedHeaders("X-Forwarded-For");
    }
}