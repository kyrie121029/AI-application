package com.example.demo.repository;

import com.example.demo.model.AIUsageLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AIUsageLogRepository extends JpaRepository<AIUsageLog, Long> {
}
