package com.developer.repository;

import com.developer.entity.EmailMonitorRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmailMonitorRuleRepository extends JpaRepository<EmailMonitorRule, Long> {

    List<EmailMonitorRule> findByFunctionUnitIdOrderByNameAsc(Long functionUnitId);

    Optional<EmailMonitorRule> findByRuleUid(String ruleUid);

    boolean existsByFunctionUnitIdAndName(Long functionUnitId, String name);

    boolean existsByFunctionUnitIdAndNameAndIdNot(Long functionUnitId, String name, Long id);

    Optional<EmailMonitorRule> findByFunctionUnitIdAndStartEventId(Long functionUnitId, String startEventId);

    boolean existsByFunctionUnitIdAndStartEventIdAndIdNot(Long functionUnitId, String startEventId, Long id);
}
