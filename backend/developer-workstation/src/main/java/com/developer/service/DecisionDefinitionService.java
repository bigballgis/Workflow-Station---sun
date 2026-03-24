package com.developer.service;

import com.developer.entity.DecisionDefinition;

import java.util.List;
import java.util.Optional;

/**
 * 决策定义服务接口
 */
public interface DecisionDefinitionService {

    DecisionDefinition save(DecisionDefinition entity);

    Optional<DecisionDefinition> findById(Long id);

    List<DecisionDefinition> findByFunctionUnitId(Long functionUnitId);

    void deleteById(Long id);

    boolean existsByFunctionUnitIdAndDecisionKey(Long functionUnitId, String decisionKey);

    boolean existsByFunctionUnitIdAndDecisionKeyAndIdNot(Long functionUnitId, String decisionKey, Long id);
}
