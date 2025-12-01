package com.rohan.portfolio.auth.repository;

import com.rohan.portfolio.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email); // Changed from findByUsername to findByEmail for clarity
    boolean existsByUsername(String username); // Check for duplicate display names
}