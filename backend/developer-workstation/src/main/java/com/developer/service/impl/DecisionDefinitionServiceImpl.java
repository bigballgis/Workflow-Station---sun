package com.developer.service.impl;

import com.developer.entity.DecisionDefinition;
import com.developer.repository.DecisionDefinitionRepository;
import com.developer.service.DecisionDefinitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 决策定义服务实现
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DecisionDefinitionServiceImpl implements DecisionDefinitionService {

    private final DecisionDefinitionRepository decisionDefinitionRepository;

    @Override
    public DecisionDefinition save(DecisionDefinition entity) {
        return decisionDefinitionRepository.save(entity);
    }

    @Override
    public Optional<DecisionDefinition> findById(Long id) {
        return decisionDefinitionRepository.findById(id);
    }

    @Override
    public List<DecisionDefinition> findByFunctionUnitId(Long functionUnitId) {
        return decisionDefinitionRepository.findByFunctionUnitId(functionUnitId);
    }

    @Override
    public void deleteById(Long id) {
        decisionDefinitionRepository.deleteById(id);
    }

    @Override
    public boolean existsByFunctionUnitIdAndDecisionKey(Long functionUnitId, String decisionKey) {
        return decisionDefinitionRepository.existsByFunctionUnitIdAndDecisionKey(functionUnitId, decisionKey);
    }

    @Override
    public boolean existsByFunctionUnitIdAndDecisionKeyAndIdNot(Long functionUnitId, String decisionKey, Long id) {
        return decisionDefinitionRepository.existsByFunctionUnitIdAndDecisionKeyAndIdNot(functionUnitId, decisionKey, id);
    }
}
