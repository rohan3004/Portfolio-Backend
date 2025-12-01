package com.rohan.portfolio.controller;


import com.rohan.portfolio.auth.dto.IpResponse;
import com.rohan.portfolio.service.IpService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IpController {

    private final IpService ipService;

    public IpController(IpService ipService) {
        this.ipService = ipService;
    }


    @GetMapping("/v1/your_ip")
    public ResponseEntity<IpResponse> getClientIp(HttpServletRequest request, @RequestHeader(value = "X-Forwarded-For", required = false)String forwarded_for) {

        String clientIp = forwarded_for != null && !forwarded_for.isEmpty()
                ? forwarded_for.split(",")[0].trim()
                : request.getRemoteAddr();

        return ResponseEntity.ok(ipService.lookup(clientIp));
    }
}
