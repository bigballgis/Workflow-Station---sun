package com.developer.component.impl;

import com.developer.component.FormDesignComponent;
import com.developer.dto.FormDefinitionRequest;
import com.developer.dto.FormTableBindingRequest;
import com.developer.dto.ValidationResult;
import com.developer.entity.FieldDefinition;
import com.developer.entity.FormDefinition;
import com.developer.entity.FormTableBinding;
import com.developer.entity.FunctionUnit;
import com.developer.entity.TableDefinition;
import com.developer.enums.BindingMode;
import com.developer.enums.BindingType;
import com.developer.exception.BusinessException;
import com.developer.exception.ResourceNotFoundException;
import com.developer.repository.FormDefinitionRepository;
import com.developer.repository.FormTableBindingRepository;
import com.developer.repository.FunctionUnitRepository;
import com.developer.repository.TableDefinitionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 表单设计组件实现
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FormDesignComponentImpl implements FormDesignComponent {
    
    private final FormDefinitionRepository formDefinitionRepository;
    private final FunctionUnitRepository functionUnitRepository;
    private final TableDefinitionRepository tableDefinitionRepository;
    private final FormTableBindingRepository formTableBindingRepository;
    private final ObjectMapper objectMapper;
    
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
        return formDefinitionRepository.findByIdWithBindings(id)
                .orElseThrow(() -> new ResourceNotFoundException("FormDefinition", id));
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<FormDefinition> getByFunctionUnitId(Long functionUnitId) {
        return formDefinitionRepository.findByFunctionUnitIdWithBindings(functionUnitId);
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
    
    // ========== 表绑定管理方法实现 ==========
    
    @Override
    @Transactional
    public FormTableBinding createBinding(Long formId, FormTableBindingRequest request) {
        FormDefinition form = getById(formId);
        TableDefinition table = tableDefinitionRepository.findById(request.getTableId())
                .orElseThrow(() -> new ResourceNotFoundException("TableDefinition", request.getTableId()));
        
        // 检查是否已绑定该表
        if (formTableBindingRepository.existsByFormIdAndTableId(formId, request.getTableId())) {
            throw new BusinessException("BINDING_EXISTS", 
                    "该表已绑定到此表单",
                    "请勿重复绑定");
        }
        
        // 检查主表绑定唯一性
        if (request.getBindingType() == BindingType.PRIMARY) {
            if (formTableBindingRepository.existsByFormIdAndBindingType(formId, BindingType.PRIMARY)) {
                throw new BusinessException("PRIMARY_BINDING_EXISTS", 
                        "此表单已有主表绑定",
                        "请先删除现有主表绑定");
            }
        }
        
        // 验证外键字段（子表和关联表需要）
        if (request.getBindingType() != BindingType.PRIMARY && request.getForeignKeyField() != null) {
            validateForeignKeyField(table, request.getForeignKeyField());
        }
        
        // 设置默认绑定模式
        BindingMode bindingMode = request.getBindingMode();
        if (bindingMode == null) {
            bindingMode = request.getBindingType() == BindingType.PRIMARY 
                    ? BindingMode.EDITABLE 
                    : BindingMode.READONLY;
        }
        
        // 计算排序顺序
        int sortOrder = request.getSortOrder() != null 
                ? request.getSortOrder() 
                : (int) formTableBindingRepository.countByFormId(formId);
        
        FormTableBinding binding = FormTableBinding.builder()
                .form(form)
                .table(table)
                .bindingType(request.getBindingType())
                .bindingMode(bindingMode)
                .foreignKeyField(request.getForeignKeyField())
                .sortOrder(sortOrder)
                .build();
        
        return formTableBindingRepository.save(binding);
    }
    
    @Override
    @Transactional
    public FormTableBinding updateBinding(Long bindingId, FormTableBindingRequest request) {
        FormTableBinding binding = formTableBindingRepository.findById(bindingId)
                .orElseThrow(() -> new ResourceNotFoundException("FormTableBinding", bindingId));
        
        // 如果更改了绑定类型为主表，检查唯一性
        if (request.getBindingType() == BindingType.PRIMARY && binding.getBindingType() != BindingType.PRIMARY) {
            if (formTableBindingRepository.existsByFormIdAndBindingType(binding.getFormId(), BindingType.PRIMARY)) {
                throw new BusinessException("PRIMARY_BINDING_EXISTS", 
                        "此表单已有主表绑定",
                        "请先删除现有主表绑定");
            }
        }
        
        // 验证外键字段
        if (request.getBindingType() != BindingType.PRIMARY && request.getForeignKeyField() != null) {
            validateForeignKeyField(binding.getTable(), request.getForeignKeyField());
        }
        
        binding.setBindingType(request.getBindingType());
        if (request.getBindingMode() != null) {
            binding.setBindingMode(request.getBindingMode());
        }
        binding.setForeignKeyField(request.getForeignKeyField());
        if (request.getSortOrder() != null) {
            binding.setSortOrder(request.getSortOrder());
        }
        
        return formTableBindingRepository.save(binding);
    }
    
    @Override
    @Transactional
    public void deleteBinding(Long bindingId) {
        FormTableBinding binding = formTableBindingRepository.findById(bindingId)
                .orElseThrow(() -> new ResourceNotFoundException("FormTableBinding", bindingId));
        formTableBindingRepository.delete(binding);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<FormTableBinding> getBindings(Long formId) {
        return formTableBindingRepository.findByFormIdWithTable(formId);
    }
    
    /**
     * 验证外键字段是否存在于表中
     */
    private void validateForeignKeyField(TableDefinition table, String foreignKeyField) {
        boolean fieldExists = table.getFieldDefinitions().stream()
                .anyMatch(field -> field.getFieldName().equals(foreignKeyField));
        
        if (!fieldExists) {
            throw new BusinessException("INVALID_FOREIGN_KEY", 
                    "指定的外键字段在表中不存在: " + foreignKeyField,
                    "请检查字段名是否正确");
        }
    }
}
