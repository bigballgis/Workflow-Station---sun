package com.admin.repository;

import com.admin.entity.EmailMonitorRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmailMonitorRuleRepository extends JpaRepository<EmailMonitorRule, String> {

    List<EmailMonitorRule> findByFunctionUnitId(String functionUnitId);

    List<EmailMonitorRule> findByEnabledTrue();

    void deleteByFunctionUnitId(String functionUnitId);
}
