package com.developer.component.impl;

import com.developer.component.FormDesignComponent;
import com.developer.dto.FormDefinitionRequest;
import com.developer.dto.ValidationResult;
import com.developer.entity.FormDefinition;
import com.developer.entity.FunctionUnit;
import com.developer.entity.TableDefinition;
import com.developer.exception.BusinessException;
import com.developer.exception.ResourceNotFoundException;
import com.developer.repository.FormDefinitionRepository;
import com.developer.repository.FunctionUnitRepository;
import com.developer.repository.TableDefinitionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 表单设计组件实现
 */
@Component
@Slf4j
public class FormDesignComponentImpl implements FormDesignComponent {
    
    private final FormDefinitionRepository formDefinitionRepository;
    private final FunctionUnitRepository functionUnitRepository;
    private final TableDefinitionRepository tableDefinitionRepository;
    private final ObjectMapper objectMapper;
    
    /**
     * 简化构造函数，用于测试
     */
    public FormDesignComponentImpl(FormDefinitionRepository formDefinitionRepository) {
        this(formDefinitionRepository, null, null, new ObjectMapper());
    }
    
    /**
     * 完整构造函数
     */
    public FormDesignComponentImpl(
            FormDefinitionRepository formDefinitionRepository,
            FunctionUnitRepository functionUnitRepository,
            TableDefinitionRepository tableDefinitionRepository,
            ObjectMapper objectMapper) {
        this.formDefinitionRepository = formDefinitionRepository;
        this.functionUnitRepository = functionUnitRepository;
        this.tableDefinitionRepository = tableDefinitionRepository;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }
    
    @Override
    @Transactional
    public FormDefinition create(Long functionUnitId, FormDefinitionRequest request) {
        FunctionUnit functionUnit = functionUnitRepository.findById(functionUnitId)
                .orElseThrow(() -> new ResourceNotFoundException("FunctionUnit", functionUnitId));
        
        if (formDefinitionRepository.existsByFunctionUnitIdAndFormName(functionUnitId, request.getFormName())) {
            throw new BusinessException("CONFLICT_FORM_NAME_EXISTS", 
                    "表单名已存在: " + request.getFormName(),
                    "请使用其他表单名");
        }
        
        FormDefinition formDefinition = FormDefinition.builder()
                .functionUnit(functionUnit)
                .formName(request.getFormName())
                .formType(request.getFormType())
                .configJson(request.getConfigJson())
                .description(request.getDescription())
                .build();
        
        if (request.getBoundTableId() != null) {
            TableDefinition boundTable = tableDefinitionRepository.findById(request.getBoundTableId())
                    .orElseThrow(() -> new ResourceNotFoundException("TableDefinition", request.getBoundTableId()));
            formDefinition.setBoundTable(boundTable);
        }
        
        return formDefinitionRepository.save(formDefinition);
    }
    
    @Override
    @Transactional
    public FormDefinition update(Long id, FormDefinitionRequest request) {
        FormDefinition formDefinition = getById(id);
        
        if (formDefinitionRepository.existsByFunctionUnitIdAndFormNameAndIdNot(
                formDefinition.getFunctionUnit().getId(), request.getFormName(), id)) {
            throw new BusinessException("CONFLICT_FORM_NAME_EXISTS", 
                    "表单名已存在: " + request.getFormName(),
                    "请使用其他表单名");
        }
        
        formDefinition.setFormName(request.getFormName());
        formDefinition.setFormType(request.getFormType());
        formDefinition.setConfigJson(request.getConfigJson());
        formDefinition.setDescription(request.getDescription());
        
        if (request.getBoundTableId() != null) {
            TableDefinition boundTable = tableDefinitionRepository.findById(request.getBoundTableId())
                    .orElseThrow(() -> new ResourceNotFoundException("TableDefinition", request.getBoundTableId()));
            formDefinition.setBoundTable(boundTable);
        } else {
            formDefinition.setBoundTable(null);
        }
        
        return formDefinitionRepository.save(formDefinition);
    }
    
    @Override
    @Transactional
    public void delete(Long id) {
        FormDefinition formDefinition = getById(id);
        // TODO: 检查是否被流程步骤绑定
        formDefinitionRepository.delete(formDefinition);
    }
    
    @Override
    @Transactional(readOnly = true)
    public FormDefinition getById(Long id) {
        return formDefinitionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FormDefinition", id));
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<FormDefinition> getByFunctionUnitId(Long functionUnitId) {
        return formDefinitionRepository.findByFunctionUnitId(functionUnitId);
    }
    
    @Override
    @Transactional(readOnly = true)
    public String generateFormConfig(Long id) {
        FormDefinition formDefinition = getById(id);
        try {
            return objectMapper.writeValueAsString(formDefinition.getConfigJson());
        } catch (JsonProcessingException e) {
            throw new BusinessException("SYS_JSON_ERROR", "生成表单配置失败");
        }
    }
    
    @Override
    public Map<String, Object> parseFormConfig(String configJson) {
        try {
            return objectMapper.readValue(configJson, Map.class);
        } catch (JsonProcessingException e) {
            throw new BusinessException("VAL_INVALID_JSON", "无效的JSON配置");
        }
    }
    
    @Override
    @Transactional(readOnly = true)
    public ValidationResult validate(Long id) {
        FormDefinition formDefinition = getById(id);
        ValidationResult result = new ValidationResult();
        
        // 验证配置JSON
        if (formDefinition.getConfigJson() == null || formDefinition.getConfigJson().isEmpty()) {
            result.addError("EMPTY_CONFIG", "表单配置为空", null);
        }
        
        // 验证数据绑定
        if (formDefinition.getBoundTable() != null) {
            // 检查绑定的字段是否存在于表中
            Map<String, Object> config = formDefinition.getConfigJson();
            // TODO: 深度验证字段绑定
        }
        
        return result;
    }
}
