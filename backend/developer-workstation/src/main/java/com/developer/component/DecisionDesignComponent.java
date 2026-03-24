package com.developer.component;

import com.developer.dto.DecisionDefinitionRequest;
import com.developer.dto.DecisionTableModel;
import com.developer.dto.ValidationResult;
import com.developer.entity.DecisionDefinition;

import java.util.List;

/**
 * 决策设计组件接口
 */
public interface DecisionDesignComponent {

    DecisionDefinition create(Long functionUnitId, DecisionDefinitionRequest request);

    List<DecisionDefinition> list(Long functionUnitId);

    DecisionDefinition getById(Long functionUnitId, Long decisionId);

    DecisionDefinition update(Long functionUnitId, Long decisionId, DecisionDefinitionRequest request);

    void delete(Long functionUnitId, Long decisionId);

    ValidationResult validate(Long decisionId);

    DecisionTableModel getModel(Long decisionId);

    DecisionDefinition updateFromModel(Long decisionId, DecisionTableModel model);
}
