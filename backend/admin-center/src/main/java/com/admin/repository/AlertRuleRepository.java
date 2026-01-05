package com.admin.repository;

import com.admin.entity.AlertRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertRuleRepository extends JpaRepository<AlertRule, String> {
    List<AlertRule> findByEnabled(Boolean enabled);
    List<AlertRule> findByMetricName(String metricName);
}
