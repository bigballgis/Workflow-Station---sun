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
import com.developer.enums.FormType;
import com.developer.exception.BusinessException;
import com.developer.exception.ResourceNotFoundException;
import com.developer.repository.FormDefinitionRepository;
import com.developer.repository.FormTableBindingRepository;
import com.developer.repository.FunctionUnitRepository;
import com.developer.repository.TableDefinitionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.i18n.I18nService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

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
    private final I18nService i18nService;
    private final JdbcTemplate jdbcTemplate;
    
    @Override
    @Transactional
    public FormDefinition create(Long functionUnitId, FormDefinitionRequest request) {
        FunctionUnit functionUnit = functionUnitRepository.findById(functionUnitId)
                .orElseThrow(() -> new ResourceNotFoundException("FunctionUnit", functionUnitId));
        
        if (formDefinitionRepository.existsByFunctionUnitIdAndFormName(functionUnitId, request.getFormName())) {
            throw new BusinessException("CONFLICT_FORM_NAME_EXISTS", 
                    i18nService.getMessage("form.name_exists", request.getFormName()),
                    i18nService.getMessage("form.use_other_name"));
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
                    i18nService.getMessage("form.name_exists", request.getFormName()),
                    i18nService.getMessage("form.use_other_name"));
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
        checkFormDependencies(id);
        formDefinitionRepository.delete(formDefinition);
    }
    
    /**
     * 检查表单是否被流程步骤引用
     * 如果被引用，抛出 BusinessException
     * 
     * @param formId 表单ID
     * @throws BusinessException 如果表单正在被使用
     */
    private void checkFormDependencies(Long formId) {
        FormDefinition form = formDefinitionRepository.findById(formId)
                .orElseThrow(() -> new ResourceNotFoundException("FormDefinition", formId));
        
        // 获取该功能单元的流程定义
        FunctionUnit functionUnit = form.getFunctionUnit();
        if (functionUnit.getProcessDefinition() != null) {
            String bpmnXml = functionUnit.getProcessDefinition().getBpmnXml();
            
            // 简化检查：在 BPMN XML 中搜索表单名称
            // 注意：这是简化实现，完整实现需要解析 BPMN XML
            if (bpmnXml != null && bpmnXml.contains(form.getFormName())) {
                throw new BusinessException(
                    "FORM_IN_USE",
                    i18nService.getMessage("form.in_use"),
                    i18nService.getMessage("form.remove_reference_first")
                );
            }
        }
        
        log.info("Form dependency check passed for form: {}", formId);
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
            throw new BusinessException("SYS_JSON_ERROR", i18nService.getMessage("form.config_generate_failed"));
        }
    }
    
    @Override
    public Map<String, Object> parseFormConfig(String configJson) {
        try {
            return objectMapper.readValue(configJson, Map.class);
        } catch (JsonProcessingException e) {
            throw new BusinessException("VAL_INVALID_JSON", i18nService.getMessage("form.invalid_json_config"));
        }
    }
    
    @Override
    @Transactional(readOnly = true)
    public ValidationResult validate(Long id) {
        FormDefinition formDefinition = getById(id);
        ValidationResult result = new ValidationResult();
        
        // 验证配置JSON
        if (formDefinition.getConfigJson() == null || formDefinition.getConfigJson().isEmpty()) {
            result.addError("EMPTY_CONFIG", i18nService.getMessage("form.empty_config"), null);
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
        
        // Deployed Relation Table binding (RELATED type with relationTableId)
        boolean isRelationTable = request.getBindingType() == BindingType.RELATED 
                && request.getRelationTableId() != null;
        
        TableDefinition table = null;
        if (!isRelationTable) {
            table = tableDefinitionRepository.findById(request.getTableId())
                    .orElseThrow(() -> new ResourceNotFoundException("TableDefinition", request.getTableId()));
            
            // 检查是否已绑定该表
            if (formTableBindingRepository.existsByFormIdAndTableId(formId, request.getTableId())) {
                throw new BusinessException("BINDING_EXISTS", 
                        i18nService.getMessage("form.binding_exists"),
                        i18nService.getMessage("form.no_duplicate_binding"));
            }
        } else {
            // 检查是否已绑定该 Relation Table
            if (formTableBindingRepository.existsByFormIdAndRelationTableId(formId, request.getRelationTableId())) {
                throw new BusinessException("BINDING_EXISTS", 
                        i18nService.getMessage("form.binding_exists"),
                        i18nService.getMessage("form.no_duplicate_binding"));
            }
        }
        
        // 检查主表绑定唯一性
        if (request.getBindingType() == BindingType.PRIMARY) {
            if (formTableBindingRepository.existsByFormIdAndBindingType(formId, BindingType.PRIMARY)) {
                throw new BusinessException("PRIMARY_BINDING_EXISTS", 
                        i18nService.getMessage("form.primary_binding_exists"),
                        i18nService.getMessage("form.remove_existing_primary"));
            }
        }
        
        // 验证外键字段（子表需要，关联表的本地表也需要）
        if (request.getBindingType() != BindingType.PRIMARY && request.getForeignKeyField() != null && table != null) {
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
                .relationTableId(isRelationTable ? request.getRelationTableId() : null)
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
                        i18nService.getMessage("form.primary_binding_exists"),
                        i18nService.getMessage("form.remove_existing_primary"));
            }
        }
        
        // 验证外键字段
        if (request.getBindingType() != BindingType.PRIMARY && request.getForeignKeyField() != null && binding.getTable() != null) {
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
                    i18nService.getMessage("form.foreign_key_not_found", foreignKeyField),
                    i18nService.getMessage("form.check_field_name"));
        }
    }
    
    // ========== Process/Task Form 扩展方法实现 ==========
    
    @Override
    @Transactional(readOnly = true)
    public void validateProcessFormUniqueness(Long functionUnitId) {
        long processFormCount = formDefinitionRepository.countByFunctionUnitIdAndFormType(functionUnitId, FormType.PROCESS);
        if (processFormCount > 0) {
            throw new BusinessException("PROCESS_FORM_ALREADY_EXISTS",
                    i18nService.getMessage("form.process_form_already_exists"),
                    i18nService.getMessage("form.only_one_process_form"));
        }
    }
    
    @Override
    @Transactional(readOnly = true)
    public void validateFieldNames(Long functionUnitId, List<String> fieldNames) {
        List<String> dataTableColumns = getDataTableColumns(functionUnitId);
        Set<String> columnSet = new HashSet<>(dataTableColumns);
        
        List<String> invalidFields = fieldNames.stream()
                .filter(name -> !columnSet.contains(name))
                .toList();
        
        if (!invalidFields.isEmpty()) {
            throw new BusinessException("INVALID_FIELD_NAME",
                    i18nService.getMessage("form.invalid_field_names", String.join(", ", invalidFields)),
                    i18nService.getMessage("form.field_must_reference_data_table"));
        }
    }
    
    @Override
    @Transactional
    public FormDefinition copyTaskForm(Long sourceFormId) {
        FormDefinition source = getById(sourceFormId);
        
        Map<String, Object> copiedConfig = deepCopyMap(source.getConfigJson());
        Map<String, String> copiedFieldPermissions = source.getFieldPermissions() != null
                ? new HashMap<>(source.getFieldPermissions()) : new HashMap<>();
        
        FormDefinition copy = FormDefinition.builder()
                .functionUnit(source.getFunctionUnit())
                .formName(source.getFormName() + "_copy")
                .formType(source.getFormType())
                .configJson(copiedConfig)
                .description(source.getDescription())
                .boundTable(source.getBoundTable())
                .fieldPermissions(copiedFieldPermissions)
                .showLiveValues(source.getShowLiveValues())
                .stageBindings(new ArrayList<>())
                .build();
        
        return formDefinitionRepository.save(copy);
    }

    private Map<String, Object> deepCopyMap(Map<String, Object> source) {
        if (source == null) {
            return null;
        }
        try {
            return objectMapper.readValue(
                    objectMapper.writeValueAsString(source),
                    new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            throw new BusinessException("SYS_JSON_ERROR", i18nService.getMessage("form.config_generate_failed"));
        }
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<String> getDataTableColumns(Long functionUnitId) {
        List<TableDefinition> tables = tableDefinitionRepository.findByFunctionUnitIdWithFields(functionUnitId);
        return tables.stream()
                .flatMap(table -> table.getFieldDefinitions().stream())
                .map(FieldDefinition::getFieldName)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public String resolveRelationTableName(FormTableBinding binding) {
        if (binding.getBindingType() != BindingType.RELATED || binding.getRelationTableId() == null) {
            return null;
        }
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT table_name FROM rt_table_definitions WHERE id = ?",
                    String.class, binding.getRelationTableId());
        } catch (Exception e) {
            return null;
        }
    }
}
