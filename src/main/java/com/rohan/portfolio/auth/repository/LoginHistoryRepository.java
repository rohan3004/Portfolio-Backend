package com.rohan.portfolio.auth.repository;

import com.rohan.portfolio.model.LoginHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginHistoryRepository extends JpaRepository<LoginHistory, Long> {
}