package com.admin.repository;

import com.admin.entity.LogRetentionPolicy;
import com.admin.enums.LogType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LogRetentionPolicyRepository extends JpaRepository<LogRetentionPolicy, String> {
    
    Optional<LogRetentionPolicy> findByLogType(LogType logType);
    
    List<LogRetentionPolicy> findByEnabled(Boolean enabled);
}
