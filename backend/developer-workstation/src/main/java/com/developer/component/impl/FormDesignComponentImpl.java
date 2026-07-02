package com.developer.component.impl;

import com.developer.component.FormDesignComponent;
import com.developer.dto.FormDefinitionRequest;
import com.developer.dto.FormTableBindingRequest;
import com.developer.dto.FormTableBindingResponse;
import com.developer.dto.ValidationResult;
import com.developer.entity.FieldDefinition;
import com.developer.entity.FormDefinition;
import com.developer.entity.FormTableBinding;
import com.developer.entity.FunctionUnit;
import com.developer.entity.SubTableViewConfig;
import com.developer.entity.SubTableViewField;
import com.developer.entity.TableDefinition;
import com.developer.enums.BindingLinkMode;
import com.developer.enums.BindingMode;
import com.developer.enums.BindingType;
import com.developer.enums.FormType;
import com.developer.enums.SubMode;
import com.developer.enums.TableType;
import com.developer.exception.DeveloperBusinessException;
import com.developer.exception.ResourceNotFoundException;
import com.developer.repository.FormDefinitionRepository;
import com.developer.repository.FormTableBindingRepository;
import com.developer.repository.FunctionUnitRepository;
import com.developer.repository.SubTableViewConfigRepository;
import com.developer.repository.TableDefinitionRepository;
import com.developer.service.SubTableViewService;
import com.developer.util.FormConfigJsonBindingIdRewriter;
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
 * Form design component implementation.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FormDesignComponentImpl implements FormDesignComponent {

    private static final Long SYSTEM_USER_TABLE_ID = -1_000_000_001L;

    /**
     * Reserved synthetic field names that are NOT Data_Table columns and must be exempt from
     * field-name validation. Keep in sync with the developer-workstation frontend
     * REQUEST_ID_FIELD and the user-portal RequestIdEnricher.REQUEST_ID_FIELD.
     */
    private static final Set<String> RESERVED_VIRTUAL_FIELD_NAMES = Set.of("__request_id");

    /**
     * Standard audit field names that are auto-appended to every new table by
     * {@code TableDesignComponentImpl}.  They must pass form-field validation even on
     * tables that were created before the auto-audit initializer ran.
     */
    private static final Set<String> ALWAYS_VALID_AUDIT_FIELDS = Set.of(
            "created_at", "created_by", "updated_at", "updated_by");

    private final FormDefinitionRepository formDefinitionRepository;
    private final FunctionUnitRepository functionUnitRepository;
    private final TableDefinitionRepository tableDefinitionRepository;
    private final FormTableBindingRepository formTableBindingRepository;
    private final SubTableViewConfigRepository subTableViewConfigRepository;
    private final ObjectMapper objectMapper;
    private final I18nService i18nService;
    private final JdbcTemplate jdbcTemplate;
    private final SubTableViewService subTableViewService;
    
    @Override
    @Transactional
    public FormDefinition create(Long functionUnitId, FormDefinitionRequest request) {
        FunctionUnit functionUnit = functionUnitRepository.findById(functionUnitId)
                .orElseThrow(() -> new ResourceNotFoundException("FunctionUnit", functionUnitId));
        
        if (formDefinitionRepository.existsByFunctionUnitIdAndFormName(functionUnitId, request.getFormName())) {
            throw new DeveloperBusinessException("CONFLICT_FORM_NAME_EXISTS", 
                    i18nService.getMessage("form.name_exists", request.getFormName()),
                    i18nService.getMessage("form.use_other_name"));
        }

        if (request.getFormType() == FormType.PROCESS) {
            validateProcessFormUniqueness(functionUnitId);
        }
        
        FormDefinition formDefinition = FormDefinition.builder()
                .functionUnit(functionUnit)
                .formName(request.getFormName())
                .formType(request.getFormType())
                .configJson(request.getConfigJson())
                .displayName(request.getDescription())
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
            throw new DeveloperBusinessException("CONFLICT_FORM_NAME_EXISTS", 
                    i18nService.getMessage("form.name_exists", request.getFormName()),
                    i18nService.getMessage("form.use_other_name"));
        }

        if (request.getFormType() == FormType.PROCESS) {
            long fuId = formDefinition.getFunctionUnit().getId();
            long processCount = formDefinitionRepository.countByFunctionUnitIdAndFormType(fuId, FormType.PROCESS);
            if (formDefinition.getFormType() != FormType.PROCESS && processCount > 0) {
                throw new DeveloperBusinessException("PROCESS_FORM_ALREADY_EXISTS",
                        i18nService.getMessage("form.process_form_already_exists"),
                        i18nService.getMessage("form.only_one_process_form"));
            }
        }
        
        Map<String, Object> mergedConfigJson = preserveExistingSubListViewsOnAccidentalEmpty(
                formDefinition.getConfigJson(), request.getConfigJson());
        formDefinition.setFormName(request.getFormName());
        formDefinition.setFormType(request.getFormType());
        formDefinition.setConfigJson(mergedConfigJson);
        formDefinition.setDisplayName(request.getDescription());
        
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
     * Checks whether the form is referenced from a process step BPMN XML.
     * Throws {@link DeveloperBusinessException} if still in use.
     *
     * @param formId form primary key
     * @throws DeveloperBusinessException when BPMN XML still references this form name
     */
    private void checkFormDependencies(Long formId) {
        FormDefinition form = formDefinitionRepository.findById(formId)
                .orElseThrow(() -> new ResourceNotFoundException("FormDefinition", formId));
        
        // Load BPMN XML for this function unit.
        FunctionUnit functionUnit = form.getFunctionUnit();
        if (functionUnit.getProcessDefinition() != null) {
            String bpmnXml = functionUnit.getProcessDefinition().getBpmnXml();
            
            // Heuristic: search form name in BPMN XML (full parse not implemented).
            if (bpmnXml != null && bpmnXml.contains(form.getFormName())) {
                throw new DeveloperBusinessException(
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> preserveExistingSubListViewsOnAccidentalEmpty(
            Map<String, Object> existingConfigJson,
            Map<String, Object> incomingConfigJson) {
        if (incomingConfigJson == null || !(incomingConfigJson.get("subListViews") instanceof Map<?, ?> incomingRaw)
                || existingConfigJson == null || !(existingConfigJson.get("subListViews") instanceof Map<?, ?> existingRaw)) {
            return incomingConfigJson;
        }

        Map<String, Object> mergedConfigJson = new LinkedHashMap<>(incomingConfigJson);
        Map<String, Object> mergedSubListViews = new LinkedHashMap<>();
        incomingRaw.forEach((key, value) -> mergedSubListViews.put(String.valueOf(key), value));

        existingRaw.forEach((bindingId, existingValue) -> {
            Object incomingValue = mergedSubListViews.get(String.valueOf(bindingId));
            if (!(existingValue instanceof Map<?, ?> existingView)
                    || !(incomingValue instanceof Map<?, ?> incomingView)
                    || !(existingView.get("columns") instanceof List<?> existingColumns)
                    || !(incomingView.get("columns") instanceof List<?> incomingColumns)) {
                return;
            }
            boolean allowEmptyColumns = Boolean.TRUE.equals(incomingView.get("allowEmptyColumns"));
            if (!allowEmptyColumns && !existingColumns.isEmpty() && incomingColumns.isEmpty()) {
                mergedSubListViews.put(String.valueOf(bindingId), existingValue);
            }
        });

        mergedConfigJson.put("subListViews", mergedSubListViews);
        return mergedConfigJson;
    }
    
    @Override
    @Transactional(readOnly = true)
    public String generateFormConfig(Long id) {
        FormDefinition formDefinition = getById(id);
        try {
            return objectMapper.writeValueAsString(formDefinition.getConfigJson());
        } catch (JsonProcessingException e) {
            throw new DeveloperBusinessException("SYS_JSON_ERROR", i18nService.getMessage("form.config_generate_failed"));
        }
    }
    
    @Override
    public Map<String, Object> parseFormConfig(String configJson) {
        try {
            return objectMapper.readValue(configJson, Map.class);
        } catch (JsonProcessingException e) {
            throw new DeveloperBusinessException("VAL_INVALID_JSON", i18nService.getMessage("form.invalid_json_config"));
        }
    }
    
    @Override
    @Transactional(readOnly = true)
    public ValidationResult validate(Long id) {
        FormDefinition formDefinition = getById(id);
        ValidationResult result = new ValidationResult();
        
        // Validate config JSON presence.
        if (formDefinition.getConfigJson() == null || formDefinition.getConfigJson().isEmpty()) {
            result.addError("EMPTY_CONFIG", i18nService.getMessage("form.empty_config"), null);
        }
        
        return result;
    }
    
    // ========== Form table binding operations ==========
    
    @Override
    @Transactional
    public FormTableBindingResponse createBinding(Long formId, FormTableBindingRequest request) {
        FormDefinition form = getById(formId);
        
        // Deployed Relation Table binding (RELATED type with relationTableId)
        boolean isRelationTable = request.getBindingType() == BindingType.RELATED 
                && request.getRelationTableId() != null;
        
        TableDefinition table = null;
        if (!isRelationTable) {
            table = tableDefinitionRepository.findById(request.getTableId())
                    .orElseThrow(() -> new ResourceNotFoundException("TableDefinition", request.getTableId()));
            
            // Reject duplicate binding to the same TableDefinition.
            if (formTableBindingRepository.existsByFormIdAndTableId(formId, request.getTableId())) {
                throw new DeveloperBusinessException("BINDING_EXISTS", 
                        i18nService.getMessage("form.binding_exists"),
                        i18nService.getMessage("form.no_duplicate_binding"));
            }
        } else {
            // Reject duplicate binding to the same deployed Relation Table.
            if (formTableBindingRepository.existsByFormIdAndRelationTableId(formId, request.getRelationTableId())) {
                throw new DeveloperBusinessException("BINDING_EXISTS", 
                        i18nService.getMessage("form.binding_exists"),
                        i18nService.getMessage("form.no_duplicate_binding"));
            }
        }
        
        // PRIMARY binding must be unique per form.
        if (request.getBindingType() == BindingType.PRIMARY) {
            if (formTableBindingRepository.existsByFormIdAndBindingType(formId, BindingType.PRIMARY)) {
                throw new DeveloperBusinessException("PRIMARY_BINDING_EXISTS", 
                        i18nService.getMessage("form.primary_binding_exists"),
                        i18nService.getMessage("form.remove_existing_primary"));
            }
        }

        enforcePrimarySubBindingRules(form, request, table, isRelationTable);
        
        // Validate foreign-key field (SUB / local RELATED tables).
        if (request.getBindingType() != BindingType.PRIMARY && request.getForeignKeyField() != null && table != null) {
            validateForeignKeyField(table, request.getForeignKeyField());
        }
        
        // Default binding mode when omitted.
        BindingMode bindingMode = request.getBindingMode();
        if (bindingMode == null) {
            bindingMode = request.getBindingType() == BindingType.PRIMARY 
                    ? BindingMode.EDITABLE 
                    : BindingMode.READONLY;
        }

        // SUB without subMode: match frontend Full mode default so DB row is not NULL while lists assume FULL.
        SubMode effectiveSubMode = request.getSubMode();
        if (request.getBindingType() == BindingType.SUB && effectiveSubMode == null) {
            effectiveSubMode = SubMode.FULL;
        }

        // Compute sort order default.
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
                .bindingLinkMode(request.getBindingLinkMode() != null
                        ? request.getBindingLinkMode() : BindingLinkMode.structuralFk)
                .sortOrder(sortOrder)
                .build();

        binding = formTableBindingRepository.save(binding);

        // Auto-create default sub-table list view for SUB bindings with FULL mode
        if (request.getBindingType() == BindingType.SUB && effectiveSubMode != SubMode.FORM_ONLY) {
            try {
                var viewConfig = subTableViewService.createDefaultViewConfig(binding.getId());
                binding.setSubListViewId(viewConfig.getId());
                binding = formTableBindingRepository.save(binding);
                log.info("Created default sub-table list view for binding: {}", binding.getId());
            } catch (Exception e) {
                log.warn("Failed to create default sub-table list view for binding {}: {}", binding.getId(), e.getMessage());
            }
        }

        // Set sub mode
        if (request.getBindingType() == BindingType.SUB) {
            binding.setSubMode(effectiveSubMode);
            binding = formTableBindingRepository.save(binding);
        }

        var reloadedOpt = formTableBindingRepository.findByIdWithTable(binding.getId());
        FormTableBinding out = reloadedOpt.orElse(binding);
        String relationName = resolveRelationTableName(out);
        return FormTableBindingResponse.fromPersisted(out, formId, isRelationTable ? null : table, relationName);
    }
    
    @Override
    @Transactional
    public FormTableBinding updateBinding(Long bindingId, FormTableBindingRequest request) {
        FormTableBinding binding = formTableBindingRepository.findById(bindingId)
                .orElseThrow(() -> new ResourceNotFoundException("FormTableBinding", bindingId));

        FormDefinition form = getById(binding.getFormId());
        boolean isRelationTable = request.getBindingType() == BindingType.RELATED
                && request.getRelationTableId() != null;
        TableDefinition tableForRules = binding.getTable();
        enforcePrimarySubBindingRules(form, request, tableForRules, isRelationTable);
        
        // When upgrading to PRIMARY, enforce uniqueness.
        if (request.getBindingType() == BindingType.PRIMARY && binding.getBindingType() != BindingType.PRIMARY) {
            if (formTableBindingRepository.existsByFormIdAndBindingType(binding.getFormId(), BindingType.PRIMARY)) {
                throw new DeveloperBusinessException("PRIMARY_BINDING_EXISTS", 
                        i18nService.getMessage("form.primary_binding_exists"),
                        i18nService.getMessage("form.remove_existing_primary"));
            }
        }
        
        // Validate FK field exists on bound table when provided.
        if (request.getBindingType() != BindingType.PRIMARY && request.getForeignKeyField() != null && binding.getTable() != null) {
            validateForeignKeyField(binding.getTable(), request.getForeignKeyField());
        }
        
        binding.setBindingType(request.getBindingType());
        if (request.getBindingMode() != null) {
            binding.setBindingMode(request.getBindingMode());
        }
        binding.setForeignKeyField(request.getForeignKeyField());
        if (request.getBindingLinkMode() != null) {
            binding.setBindingLinkMode(request.getBindingLinkMode());
        }
        if (request.getSortOrder() != null) {
            binding.setSortOrder(request.getSortOrder());
        }
        if (request.getSubMode() != null) {
            binding.setSubMode(request.getSubMode());
        }

        binding = formTableBindingRepository.save(binding);
        return formTableBindingRepository.findByIdWithTable(binding.getId()).orElse(binding);
    }
    
    @Override
    @Transactional
    public void deleteBinding(Long bindingId) {
        FormTableBinding binding = formTableBindingRepository.findById(bindingId)
                .orElseThrow(() -> new ResourceNotFoundException("FormTableBinding", bindingId));

        // Delete associated sub-table view config if exists
        if (binding.getSubListViewId() != null) {
            try {
                subTableViewService.getViewConfig(bindingId);
                // The view config will be cleaned up via cascade or manual deletion
                log.info("Cleaning up sub-table view config for binding: {}", bindingId);
            } catch (Exception e) {
                log.debug("No sub-table view config to clean up for binding: {}", bindingId);
            }
        }

        formTableBindingRepository.delete(binding);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<FormTableBinding> getBindings(Long formId) {
        return formTableBindingRepository.findByFormIdWithTable(formId);
    }
    
    /** Ensures FK field exists on the bound table definition. */
    private void validateForeignKeyField(TableDefinition table, String foreignKeyField) {
        boolean fieldExists = table.getFieldDefinitions().stream()
                .anyMatch(field -> field.getFieldName().equals(foreignKeyField));
        
        if (!fieldExists) {
            throw new DeveloperBusinessException("INVALID_FOREIGN_KEY", 
                    i18nService.getMessage("form.foreign_key_not_found", foreignKeyField),
                    i18nService.getMessage("form.check_field_name"));
        }
    }

    /**
     * PROCESS / TASK form binding rules:
     * <ul>
     *   <li>PRIMARY must target MAIN table metadata (hosts primary form payload);</li>
     *   <li>SUB must target SUB tables and requires an existing PRIMARY plus FK pointing to main;</li>
     *   <li>RELATED backs Lookup widgets and may bind RELATION designer tables or admin-deployed relation tables—
     *       PRIMARY and FK are optional.</li>
     * </ul>
     * Non PROCESS / TASK forms skip these extras.
     */
    private void enforcePrimarySubBindingRules(FormDefinition form, FormTableBindingRequest request,
            TableDefinition table, boolean isDeployedRelationTableBinding) {
        FormType ft = form.getFormType();
        if (ft != FormType.PROCESS && ft != FormType.TASK) {
            return;
        }
        if (request.getBindingType() == BindingType.PRIMARY) {
            if (table != null && table.getTableType() != TableType.MAIN) {
                throw new DeveloperBusinessException("PRIMARY_REQUIRES_MAIN_TABLE",
                        i18nService.getMessage("form.primary_binding_requires_main_table"),
                        i18nService.getMessage("form.choose_main_physics_table"));
            }
        }
        if (request.getBindingType() == BindingType.SUB) {
            if (!formTableBindingRepository.existsByFormIdAndBindingType(form.getId(), BindingType.PRIMARY)) {
                throw new DeveloperBusinessException("SUB_REQUIRES_PRIMARY",
                        i18nService.getMessage("form.primary_binding_required_before_sub"),
                        i18nService.getMessage("form.add_primary_binding_first"));
            }
            if (table != null && table.getTableType() != TableType.SUB) {
                throw new DeveloperBusinessException("SUB_BINDING_REQUIRES_SUB_TABLE",
                        i18nService.getMessage("form.sub_binding_requires_sub_table"),
                        i18nService.getMessage("form.choose_sub_physics_table"));
            }
            if (request.getForeignKeyField() == null || request.getForeignKeyField().isBlank()) {
                throw new DeveloperBusinessException("SUB_REQUIRES_FOREIGN_KEY",
                        i18nService.getMessage("form.sub_binding_requires_foreign_key"),
                        i18nService.getMessage("form.specify_fk_to_main"));
            }
        }
        // RELATED bindings are allowed under PROCESS/TASK for Lookup lookups without PRIMARY/FK requirements.
        // Local-table RELATED bindings must reference RELATION-typed catalog tables—never MAIN/SUB by mistake.
        if (request.getBindingType() == BindingType.RELATED && !isDeployedRelationTableBinding
                && table != null && table.getTableType() != TableType.RELATION) {
            throw new DeveloperBusinessException("RELATED_BINDING_REQUIRES_RELATION_TABLE",
                    i18nService.getMessage("form.related_binding_requires_relation_table"),
                    i18nService.getMessage("form.choose_relation_physics_table"));
        }
    }
    
    // ========== Process/Task form helpers ==========
    
    @Override
    @Transactional(readOnly = true)
    public void validateProcessFormUniqueness(Long functionUnitId) {
        long processFormCount = formDefinitionRepository.countByFunctionUnitIdAndFormType(functionUnitId, FormType.PROCESS);
        if (processFormCount > 0) {
            throw new DeveloperBusinessException("PROCESS_FORM_ALREADY_EXISTS",
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
                // Request ID is a derived virtual field (not a Data_Table column) — exempt it.
                .filter(name -> !RESERVED_VIRTUAL_FIELD_NAMES.contains(name))
                // Standard audit fields are auto-appended to every table by TableDesignComponentImpl.
                .filter(name -> !ALWAYS_VALID_AUDIT_FIELDS.contains(name))
                .filter(name -> !columnSet.contains(name))
                .toList();
        
        if (!invalidFields.isEmpty()) {
            throw new DeveloperBusinessException("INVALID_FIELD_NAME",
                    i18nService.getMessage("form.invalid_field_names", String.join(", ", invalidFields)),
                    i18nService.getMessage("form.field_must_reference_data_table"));
        }
    }
    
    @Override
    @Transactional
    public FormDefinition copyTaskForm(Long sourceFormId) {
        FormDefinition source = getById(sourceFormId);
        
        // Load source bindings before saving the copy
        List<FormTableBinding> sourceBindings = formTableBindingRepository.findByFormIdWithTable(sourceFormId);
        
        // Load source sub-table view configs (bindingId -> SubTableViewConfig)
        Map<Long, SubTableViewConfig> sourceViewConfigs = new HashMap<>();
        for (FormTableBinding sb : sourceBindings) {
            if (sb.getSubListViewId() != null) {
                subTableViewConfigRepository.findByBindingId(sb.getId())
                        .ifPresent(cfg -> sourceViewConfigs.put(sb.getId(), cfg));
            }
        }
        
        Map<String, Object> copiedConfig = deepCopyMap(source.getConfigJson());
        Map<String, String> copiedFieldPermissions = source.getFieldPermissions() != null
                ? new HashMap<>(source.getFieldPermissions()) : new HashMap<>();
        
        FormDefinition copy = FormDefinition.builder()
                .functionUnit(source.getFunctionUnit())
                .formName(source.getFormName() + "_copy")
                .formType(source.getFormType())
                .configJson(copiedConfig)
                .displayName(source.getDisplayName())
                .boundTable(source.getBoundTable())
                .fieldPermissions(copiedFieldPermissions)
                .showLiveValues(source.getShowLiveValues())
                .stageBindings(new ArrayList<>())
                .build();
        
        FormDefinition savedCopy = formDefinitionRepository.save(copy);
        
        // Copy all table bindings from source to the new form
        Map<Long, Long> bindingIdMapping = new HashMap<>(); // oldBindingId -> newBindingId
        for (FormTableBinding sourceBinding : sourceBindings) {
            FormTableBinding newBinding = FormTableBinding.builder()
                    .form(savedCopy)
                    .table(sourceBinding.getTable())
                    .relationTableId(sourceBinding.getRelationTableId())
                    .bindingType(sourceBinding.getBindingType())
                    .bindingMode(sourceBinding.getBindingMode())
                    .foreignKeyField(sourceBinding.getForeignKeyField())
                    .sortOrder(sourceBinding.getSortOrder())
                    .subMode(sourceBinding.getSubMode())
                    .build();
            
            FormTableBinding savedBinding = formTableBindingRepository.save(newBinding);
            bindingIdMapping.put(sourceBinding.getId(), savedBinding.getId());
            
            // Copy SubTableViewConfig if exists
            SubTableViewConfig sourceConfig = sourceViewConfigs.get(sourceBinding.getId());
            if (sourceConfig != null) {
                List<SubTableViewField> copiedFields = new ArrayList<>();
                if (sourceConfig.getViewFields() != null) {
                    for (SubTableViewField sourceField : sourceConfig.getViewFields()) {
                        SubTableViewField newField = SubTableViewField.builder()
                                .viewConfig(null) // will be set after config is saved
                                .fieldName(sourceField.getFieldName())
                                .displayLabel(sourceField.getDisplayLabel())
                                .columnWidth(sourceField.getColumnWidth())
                                .sortOrder(sourceField.getSortOrder())
                                .visible(sourceField.getVisible())
                                .build();
                        copiedFields.add(newField);
                    }
                }
                
                SubTableViewConfig newConfig = SubTableViewConfig.builder()
                        .binding(savedBinding)
                        .viewFields(new ArrayList<>())
                        .build();
                SubTableViewConfig savedConfig = subTableViewConfigRepository.save(newConfig);
                
                // Set the back-reference and save fields
                for (SubTableViewField field : copiedFields) {
                    field.setViewConfig(savedConfig);
                }
                savedConfig.setViewFields(copiedFields);
                savedConfig = subTableViewConfigRepository.save(savedConfig);
                
                // Update the binding's subListViewId to point to the new config
                savedBinding.setSubListViewId(savedConfig.getId());
                formTableBindingRepository.save(savedBinding);
            }
        }
        
        // Remap binding IDs in configJson (subForms, subListViews, relationViews, subTablePortalViews)
        FormConfigJsonBindingIdRewriter.remapBindingIds(copiedConfig, bindingIdMapping);
        savedCopy.setConfigJson(copiedConfig);
        savedCopy = formDefinitionRepository.save(savedCopy);
        
        return savedCopy;
    }

    @Override
    @Transactional
    public FormDefinition copyProcessToTaskForm(Long sourceFormId) {
        FormDefinition source = getById(sourceFormId);
        
        if (source.getFormType() != FormType.PROCESS) {
            throw new DeveloperBusinessException("INVALID_FORM_TYPE",
                    i18nService.getMessage("form.copy_process_to_task_only"),
                    i18nService.getMessage("form.source_must_be_process_form"));
        }
        
        // Load source bindings before saving the copy
        List<FormTableBinding> sourceBindings = formTableBindingRepository.findByFormIdWithTable(sourceFormId);
        
        // Load source sub-table view configs (bindingId -> SubTableViewConfig)
        Map<Long, SubTableViewConfig> sourceViewConfigs = new HashMap<>();
        for (FormTableBinding sb : sourceBindings) {
            if (sb.getSubListViewId() != null) {
                subTableViewConfigRepository.findByBindingId(sb.getId())
                        .ifPresent(cfg -> sourceViewConfigs.put(sb.getId(), cfg));
            }
        }
        
        Map<String, Object> copiedConfig = deepCopyMap(source.getConfigJson());
        Map<String, String> copiedFieldPermissions = source.getFieldPermissions() != null
                ? new HashMap<>(source.getFieldPermissions()) : new HashMap<>();
        
        // Generate a unique name for the TASK form copy
        String copyName = source.getFormName() + "_task_copy";
        
        FormDefinition copy = FormDefinition.builder()
                .functionUnit(source.getFunctionUnit())
                .formName(copyName)
                .formType(FormType.TASK)  // Changed from PROCESS to TASK
                .configJson(copiedConfig)
                .displayName(source.getDisplayName())
                .boundTable(source.getBoundTable())
                .fieldPermissions(copiedFieldPermissions)
                .showLiveValues(source.getShowLiveValues())
                .stageBindings(new ArrayList<>())
                .build();
        
        FormDefinition savedCopy = formDefinitionRepository.save(copy);
        
        // Copy all table bindings from source to the new form
        Map<Long, Long> bindingIdMapping = new HashMap<>(); // oldBindingId -> newBindingId
        for (FormTableBinding sourceBinding : sourceBindings) {
            FormTableBinding newBinding = FormTableBinding.builder()
                    .form(savedCopy)
                    .table(sourceBinding.getTable())
                    .relationTableId(sourceBinding.getRelationTableId())
                    .bindingType(sourceBinding.getBindingType())
                    .bindingMode(sourceBinding.getBindingMode())
                    .foreignKeyField(sourceBinding.getForeignKeyField())
                    .sortOrder(sourceBinding.getSortOrder())
                    .subMode(sourceBinding.getSubMode())
                    .build();
            
            FormTableBinding savedBinding = formTableBindingRepository.save(newBinding);
            bindingIdMapping.put(sourceBinding.getId(), savedBinding.getId());
            
            // Copy SubTableViewConfig if exists
            SubTableViewConfig sourceConfig = sourceViewConfigs.get(sourceBinding.getId());
            if (sourceConfig != null) {
                List<SubTableViewField> copiedFields = new ArrayList<>();
                if (sourceConfig.getViewFields() != null) {
                    for (SubTableViewField sourceField : sourceConfig.getViewFields()) {
                        SubTableViewField newField = SubTableViewField.builder()
                                .viewConfig(null) // will be set after config is saved
                                .fieldName(sourceField.getFieldName())
                                .displayLabel(sourceField.getDisplayLabel())
                                .columnWidth(sourceField.getColumnWidth())
                                .sortOrder(sourceField.getSortOrder())
                                .visible(sourceField.getVisible())
                                .build();
                        copiedFields.add(newField);
                    }
                }
                
                SubTableViewConfig newConfig = SubTableViewConfig.builder()
                        .binding(savedBinding)
                        .viewFields(new ArrayList<>())
                        .build();
                SubTableViewConfig savedConfig = subTableViewConfigRepository.save(newConfig);
                
                // Set the back-reference and save fields
                for (SubTableViewField field : copiedFields) {
                    field.setViewConfig(savedConfig);
                }
                savedConfig.setViewFields(copiedFields);
                savedConfig = subTableViewConfigRepository.save(savedConfig);
                
                // Update the binding's subListViewId to point to the new config
                savedBinding.setSubListViewId(savedConfig.getId());
                formTableBindingRepository.save(savedBinding);
            }
        }
        
        // Remap binding IDs in configJson (subForms, subListViews, relationViews, subTablePortalViews)
        FormConfigJsonBindingIdRewriter.remapBindingIds(copiedConfig, bindingIdMapping);
        savedCopy.setConfigJson(copiedConfig);
        savedCopy = formDefinitionRepository.save(savedCopy);
        
        return savedCopy;
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
            throw new DeveloperBusinessException("SYS_JSON_ERROR", i18nService.getMessage("form.config_generate_failed"));
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
        if (SYSTEM_USER_TABLE_ID.equals(binding.getRelationTableId())) {
            return "sys_users";
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
