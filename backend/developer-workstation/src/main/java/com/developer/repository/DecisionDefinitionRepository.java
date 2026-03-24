package com.developer.repository;

import com.developer.entity.DecisionDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 决策定义仓库
 */
@Repository
public interface DecisionDefinitionRepository extends JpaRepository<DecisionDefinition, Long> {

    List<DecisionDefinition> findByFunctionUnitId(Long functionUnitId);

    boolean existsByFunctionUnitIdAndDecisionKey(Long functionUnitId, String decisionKey);

    boolean existsByFunctionUnitIdAndDecisionKeyAndIdNot(Long functionUnitId, String decisionKey, Long id);
}
