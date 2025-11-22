package com.rohan.contactus.auth.repository;

import com.rohan.contactus.model.Otp;
import com.rohan.contactus.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface OtpRepository extends JpaRepository<Otp, Long> {
    Optional<Otp> findByUser(User user);
}