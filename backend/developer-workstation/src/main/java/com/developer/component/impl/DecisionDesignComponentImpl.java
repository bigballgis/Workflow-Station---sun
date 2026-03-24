package com.developer.component.impl;

import com.developer.component.DecisionDesignComponent;
import com.developer.component.FunctionUnitComponent;
import com.developer.dto.DecisionDefinitionRequest;
import com.developer.dto.DecisionTableModel;
import com.developer.dto.ValidationResult;
import com.developer.entity.DecisionDefinition;
import com.developer.entity.FunctionUnit;
import com.developer.exception.BusinessException;
import com.developer.exception.ResourceNotFoundException;
import com.developer.service.DecisionDefinitionService;
import com.developer.validation.DmnXmlParser;
import com.developer.validation.DmnXmlValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 决策设计组件实现
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DecisionDesignComponentImpl implements DecisionDesignComponent {

    private final DecisionDefinitionService decisionDefinitionService;
    private final DmnXmlValidator dmnXmlValidator;
    private final DmnXmlParser dmnXmlParser;
    private final FunctionUnitComponent functionUnitComponent;

    @Override
    @Transactional
    public DecisionDefinition create(Long functionUnitId, DecisionDefinitionRequest request) {
        FunctionUnit functionUnit = functionUnitComponent.getById(functionUnitId);

        if (decisionDefinitionService.existsByFunctionUnitIdAndDecisionKey(functionUnitId, request.getDecisionKey())) {
            throw new BusinessException("CONFLICT_DECISION_KEY_EXISTS",
                    "Decision key '" + request.getDecisionKey() + "' already exists in this function unit");
        }

        // Validate DMN XML before persisting
        ValidationResult validationResult = dmnXmlValidator.validate(request.getDmnXml());
        if (!validationResult.isValid()) {
            throw new BusinessException("INVALID_DMN_XML",
                    "DMN XML validation failed: " + validationResult.getErrors());
        }

        String hitPolicy = request.getHitPolicy();
        if (hitPolicy == null || hitPolicy.isBlank()) {
            hitPolicy = dmnXmlParser.extractHitPolicy(request.getDmnXml());
        }

        DecisionDefinition decisionDefinition = DecisionDefinition.builder()
                .functionUnit(functionUnit)
                .decisionKey(request.getDecisionKey())
                .decisionName(request.getDecisionName())
                .dmnXml(request.getDmnXml())
                .hitPolicy(hitPolicy)
                .description(request.getDescription())
                .build();

        return decisionDefinitionService.save(decisionDefinition);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DecisionDefinition> list(Long functionUnitId) {
        // Verify function unit exists
        functionUnitComponent.getById(functionUnitId);
        return decisionDefinitionService.findByFunctionUnitId(functionUnitId);
    }

    @Override
    @Transactional(readOnly = true)
    public DecisionDefinition getById(Long functionUnitId, Long decisionId) {
        // Verify function unit exists
        functionUnitComponent.getById(functionUnitId);
        return decisionDefinitionService.findById(decisionId)
                .orElseThrow(() -> new ResourceNotFoundException("DecisionDefinition", decisionId));
    }

    @Override
    @Transactional
    public DecisionDefinition update(Long functionUnitId, Long decisionId, DecisionDefinitionRequest request) {
        // Verify function unit exists
        functionUnitComponent.getById(functionUnitId);

        DecisionDefinition existing = decisionDefinitionService.findById(decisionId)
                .orElseThrow(() -> new ResourceNotFoundException("DecisionDefinition", decisionId));

        // Check for key conflict (excluding current record)
        if (decisionDefinitionService.existsByFunctionUnitIdAndDecisionKeyAndIdNot(
                functionUnitId, request.getDecisionKey(), decisionId)) {
            throw new BusinessException("CONFLICT_DECISION_KEY_EXISTS",
                    "Decision key '" + request.getDecisionKey() + "' already exists in this function unit");
        }

        // Validate DMN XML before persisting
        ValidationResult validationResult = dmnXmlValidator.validate(request.getDmnXml());
        if (!validationResult.isValid()) {
            throw new BusinessException("INVALID_DMN_XML",
                    "DMN XML validation failed: " + validationResult.getErrors());
        }

        String hitPolicy = request.getHitPolicy();
        if (hitPolicy == null || hitPolicy.isBlank()) {
            hitPolicy = dmnXmlParser.extractHitPolicy(request.getDmnXml());
        }

        existing.setDecisionKey(request.getDecisionKey());
        existing.setDecisionName(request.getDecisionName());
        existing.setDmnXml(request.getDmnXml());
        existing.setHitPolicy(hitPolicy);
        existing.setDescription(request.getDescription());

        return decisionDefinitionService.save(existing);
    }

    @Override
    @Transactional
    public void delete(Long functionUnitId, Long decisionId) {
        // Verify function unit exists
        functionUnitComponent.getById(functionUnitId);

        DecisionDefinition existing = decisionDefinitionService.findById(decisionId)
                .orElseThrow(() -> new ResourceNotFoundException("DecisionDefinition", decisionId));

        decisionDefinitionService.deleteById(existing.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public ValidationResult validate(Long decisionId) {
        DecisionDefinition existing = decisionDefinitionService.findById(decisionId)
                .orElseThrow(() -> new ResourceNotFoundException("DecisionDefinition", decisionId));

        return dmnXmlValidator.validate(existing.getDmnXml());
    }

    @Override
    @Transactional(readOnly = true)
    public DecisionTableModel getModel(Long decisionId) {
        DecisionDefinition existing = decisionDefinitionService.findById(decisionId)
                .orElseThrow(() -> new ResourceNotFoundException("DecisionDefinition", decisionId));

        return dmnXmlParser.parseToModel(existing.getDmnXml());
    }

    @Override
    @Transactional
    public DecisionDefinition updateFromModel(Long decisionId, DecisionTableModel model) {
        DecisionDefinition existing = decisionDefinitionService.findById(decisionId)
                .orElseThrow(() -> new ResourceNotFoundException("DecisionDefinition", decisionId));

        String newXml = dmnXmlParser.toXml(model);

        // Validate the generated XML
        ValidationResult validationResult = dmnXmlValidator.validate(newXml);
        if (!validationResult.isValid()) {
            throw new BusinessException("INVALID_DMN_XML",
                    "Generated DMN XML validation failed: " + validationResult.getErrors());
        }

        existing.setDmnXml(newXml);
        if (model.getHitPolicy() != null) {
            existing.setHitPolicy(model.getHitPolicy());
        }
        if (model.getDecisionName() != null) {
            existing.setDecisionName(model.getDecisionName());
        }

        return decisionDefinitionService.save(existing);
    }
}
