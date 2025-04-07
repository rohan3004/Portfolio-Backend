package com.rohan.contactus.controller;


import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IPController {


    @GetMapping("/your_ip")
    public ResponseEntity<String> getClientIp(HttpServletRequest request, @RequestHeader(value = "X-Forwarded-For", required = false)String forwarded_for) {

        String clientIp = forwarded_for != null && !forwarded_for.isEmpty()
                ? forwarded_for.split(",")[0].trim()
                : request.getRemoteAddr();

        return ResponseEntity.ok(clientIp);
    }
}
