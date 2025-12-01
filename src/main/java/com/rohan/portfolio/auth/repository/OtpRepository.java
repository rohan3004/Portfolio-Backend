package com.rohan.portfolio.auth.repository;

import com.rohan.portfolio.model.Otp;
import com.rohan.portfolio.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface OtpRepository extends JpaRepository<Otp, Long> {
    Optional<Otp> findByUser(User user);
}