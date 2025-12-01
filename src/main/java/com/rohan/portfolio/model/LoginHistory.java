package com.rohan.portfolio.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
public class LoginHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private String ipAddress;
    private String userAgent;
    private LocalDateTime loginTime;
    private boolean success;
    private String failureReason;
}