package com.workflow.email.inbound.repository;

import com.workflow.email.inbound.entity.SysEmailMonitorRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SysEmailMonitorRuleRepository extends JpaRepository<SysEmailMonitorRule, String> {

    List<SysEmailMonitorRule> findByEnabledTrue();
}
