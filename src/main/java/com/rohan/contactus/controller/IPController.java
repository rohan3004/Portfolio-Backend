package com.rohan.contactus.controller;


import com.rohan.contactus.service.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IPController {

    @Autowired
    private RateLimiter rateLimiter;

    @GetMapping("/your_ip")
    public ResponseEntity<String> getClientIp(HttpServletRequest request, @RequestHeader(value = "X-Forwarded-For", required = false)String forwarded_for) {

        if(!rateLimiter.tryConsume()){
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("Rate limit exceeded. Try again later.");
        }

        String clientIp = forwarded_for != null && !forwarded_for.isEmpty()
                ? forwarded_for.split(",")[0].trim()
                : request.getRemoteAddr();

//        clientIp = rateLimiter.nomalizeToIpv4(clientIp);

        return ResponseEntity.ok(clientIp);
    }
}
