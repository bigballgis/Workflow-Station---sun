package com.developer.component.impl;

import com.developer.component.FormDesignComponent;
import com.developer.dto.FormConfigPasteRepairRequest;
import com.developer.dto.FormConfigPasteRepairResponse;
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
import com.developer.enums.FormScene;
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
import com.developer.util.FormConfigJsonOrphanBindingRepair;
import com.developer.util.FormConfigJsonPasteBindingMapper;
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
     * 判定唯一来源 = platform-common {@link com.platform.common.audit.SystemAuditFields}。
     */
    private static final Set<String> ALWAYS_VALID_AUDIT_FIELDS =
            com.platform.common.audit.SystemAuditFields.ALL;

    private final FormTableBindingRestorer formTableBindingRestorer;
    private final FormConfigJsonTableProvisioner formConfigJsonTableProvisioner;
    private final OwnerFieldFormReconciler ownerFieldFormReconciler;
    private final FormDefinitionRepository formDefinitionRepository;
    private final FunctionUnitRepository functionUnitRepository;
    private final TableDefinitionRepository tableDefinitionRepository;
    private final FormTableBindingRepository formTableBindingRepository;
    private final SubTableViewConfigRepository subTableViewConfigRepository;
    private final ObjectMapper objectMapper;
    private final I18nService i18nService;
    private final JdbcTemplate jdbcTemplate;
    private final SubTableViewService subTableViewService;
    
    /**
     * Suffix that distinguishes the My Requests row of a scene pair. Form names are unique per
     * function unit, and the pair is linked by BPMN node id rather than by name, so a suffix is
     * enough — see {@code docs/design/form-design-scene-pairing-and-views-form.md}.
     */
    private static final String REQUEST_SCENE_NAME_SUFFIX = " (My Request)";

    @Override
    @Transactional
    public FormDefinition create(Long functionUnitId, FormDefinitionRequest request) {
        FunctionUnit functionUnit = functionUnitRepository.findById(functionUnitId)
                .orElseThrow(() -> new ResourceNotFoundException("FunctionUnit", functionUnitId));

        FormScene requestedScene = resolveScene(request.getScene());
        validateActionFormScene(request.getFormType(), requestedScene);

        if (isScenePairRequested(request)) {
            return createScenePair(functionUnit, request);
        }

        requireFormNameAvailable(functionUnitId, request.getFormName());
        if (request.getFormType() == FormType.PROCESS) {
            validateProcessFormUniqueness(functionUnitId, requestedScene);
        }

        return saveNewForm(functionUnit, request, request.getFormName(), requestedScene,
                request.getConfigJson());
    }

    /**
     * Pair creation applies only to the form types that render a workflow step. ACTION forms open
     * from a To Do task page only, and DETAIL forms belong to a view rather than a step, so neither
     * has a second scene to design.
     */
    private boolean isScenePairRequested(FormDefinitionRequest request) {
        return Boolean.TRUE.equals(request.getCreateBothScenes())
                && (request.getFormType() == FormType.PROCESS || request.getFormType() == FormType.TASK);
    }

    /**
     * Creates the To Do and My Requests designs of one step in a single transaction, so a failure
     * on the second row cannot leave half a pair behind. Both start as an empty canvas: the whole
     * point of two rows is that each scene gets its own layout.
     */
    private FormDefinition createScenePair(FunctionUnit functionUnit, FormDefinitionRequest request) {
        Long functionUnitId = functionUnit.getId();
        String taskName = request.getFormName();
        String requestName = taskName + REQUEST_SCENE_NAME_SUFFIX;

        // Both names are checked up front: reporting the conflict before writing anything keeps the
        // 409 accurate about which name the developer has to change.
        requireFormNameAvailable(functionUnitId, taskName);
        requireFormNameAvailable(functionUnitId, requestName);

        if (request.getFormType() == FormType.PROCESS) {
            validateProcessFormUniqueness(functionUnitId, FormScene.TASK);
            validateProcessFormUniqueness(functionUnitId, FormScene.REQUEST);
        }

        FormDefinition taskForm = saveNewForm(functionUnit, request, taskName, FormScene.TASK,
                request.getConfigJson());
        saveNewForm(functionUnit, request, requestName, FormScene.REQUEST, emptyFormConfig());

        // The To Do row is returned so the designer lands on it; the My Requests row is reachable
        // from its own tab in the form list.
        return taskForm;
    }

    private FormDefinition saveNewForm(FunctionUnit functionUnit, FormDefinitionRequest request,
                                       String formName, FormScene scene,
                                       Map<String, Object> configJson) {
        FormDefinition formDefinition = FormDefinition.builder()
                .functionUnit(functionUnit)
                .formName(formName)
                .formType(request.getFormType())
                .scene(scene)
                .configJson(configJson)
                .displayName(request.getDescription())
                .build();

        if (request.getBoundTableId() != null) {
            TableDefinition boundTable = tableDefinitionRepository.findById(request.getBoundTableId())
                    .orElseThrow(() -> new ResourceNotFoundException("TableDefinition", request.getBoundTableId()));
            formDefinition.setBoundTable(boundTable);
        }

        // Owner field reconciliation: one per table / same column+config across forms /
        // no silent reuse of an existing business column; provisions the column (§3.4, §4.2).
        ownerFieldFormReconciler.reconcile(functionUnit.getId(), formDefinition, request.getConfigJson());

        return formDefinitionRepository.save(formDefinition);
    }

    /** A fresh, empty form-create canvas. */
    private static Map<String, Object> emptyFormConfig() {
        Map<String, Object> configJson = new HashMap<>();
        configJson.put("rule", new ArrayList<>());
        configJson.put("options", new HashMap<>());
        return configJson;
    }

    private void requireFormNameAvailable(Long functionUnitId, String formName) {
        if (formDefinitionRepository.existsByFunctionUnitIdAndFormName(functionUnitId, formName)) {
            throw new DeveloperBusinessException("CONFLICT_FORM_NAME_EXISTS",
                    i18nService.getMessage("form.name_exists", formName),
                    i18nService.getMessage("form.use_other_name"));
        }
    }

    /**
     * ACTION forms are opened by a FORM_POPUP action button, and those exist only on To Do task
     * pages: My Requests has no action-button mechanism at all, because action ids are carried by
     * BPMN user tasks and My Requests does not correspond to one. A REQUEST-scene ACTION form could
     * therefore never be opened, so it is rejected outright instead of being saved and silently
     * never rendering.
     */
    private void validateActionFormScene(FormType formType, FormScene scene) {
        if (formType == FormType.ACTION && scene == FormScene.REQUEST) {
            throw new DeveloperBusinessException("INVALID_ACTION_FORM_SCENE",
                    i18nService.getMessage("form.action_form_todo_only"),
                    i18nService.getMessage("form.action_form_use_todo_scene"));
        }
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

        // An omitted scene means "leave it as it is". Defaulting to TASK here would
        // let any ordinary save from the designer — which does not send the scene —
        // silently demote a My Requests design back to the To Do scene.
        FormScene targetScene = request.getScene() != null
                ? request.getScene()
                : resolveScene(formDefinition.getScene());
        validateActionFormScene(request.getFormType(), targetScene);
        if (request.getFormType() == FormType.PROCESS) {
            long fuId = formDefinition.getFunctionUnit().getId();
            long processCount = formDefinitionRepository
                    .countByFunctionUnitIdAndFormTypeAndScene(fuId, FormType.PROCESS, targetScene);
            // Uniqueness is per scene: turning into the PROCESS form of a scene
            // this form does not already occupy is what needs a free slot.
            boolean alreadyOccupiesSlot = formDefinition.getFormType() == FormType.PROCESS
                    && resolveScene(formDefinition.getScene()) == targetScene;
            if (!alreadyOccupiesSlot && processCount > 0) {
                throw new DeveloperBusinessException("PROCESS_FORM_ALREADY_EXISTS",
                        i18nService.getMessage("form.process_form_already_exists"),
                        i18nService.getMessage("form.only_one_process_form"));
            }
        }

        Map<String, Object> mergedConfigJson = preserveExistingSubListViewsOnAccidentalEmpty(
                formDefinition.getConfigJson(), request.getConfigJson());
        formDefinition.setFormName(request.getFormName());
        formDefinition.setFormType(request.getFormType());
        formDefinition.setScene(targetScene);
        formDefinition.setConfigJson(mergedConfigJson);
        formDefinition.setDisplayName(request.getDescription());
        // Omitted (null) means "not sent by this caller" — leave the persisted value as-is.
        // ACTION/PROCESS-scene saves never send this key; only the TASK-scene Form Designer's
        // field-permission panel does.
        if (request.getFieldPermissions() != null) {
            formDefinition.setFieldPermissions(request.getFieldPermissions());
        }

        if (request.getBoundTableId() != null) {
            TableDefinition boundTable = tableDefinitionRepository.findById(request.getBoundTableId())
                    .orElseThrow(() -> new ResourceNotFoundException("TableDefinition", request.getBoundTableId()));
            formDefinition.setBoundTable(boundTable);
        } else {
            formDefinition.setBoundTable(null);
        }

        // Owner field reconciliation: one per table / same column+config across forms /
        // no silent reuse of an existing business column; provisions the column (§3.4, §4.2).
        ownerFieldFormReconciler.reconcile(
                formDefinition.getFunctionUnit().getId(), formDefinition, mergedConfigJson);

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
        FormDefinition form = formDefinitionRepository.findByIdWithBindings(id)
                .orElseThrow(() -> new ResourceNotFoundException("FormDefinition", id));
        if (form.getTableBindings() == null || form.getTableBindings().isEmpty()) {
            formTableBindingRestorer.repairFunctionUnitForms(form.getFunctionUnit().getId());
            form = formDefinitionRepository.findByIdWithBindings(id)
                    .orElseThrow(() -> new ResourceNotFoundException("FormDefinition", id));
        }
        repairFormConfigBindingKeysIfNeeded(form);
        return form;
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<FormDefinition> getByFunctionUnitId(Long functionUnitId) {
        formTableBindingRestorer.repairFunctionUnitForms(functionUnitId);
        List<FormDefinition> forms = formDefinitionRepository.findByFunctionUnitIdWithBindings(functionUnitId);
        forms.forEach(this::repairFormConfigBindingKeysIfNeeded);
        return forms;
    }

    private void repairFormConfigBindingKeysIfNeeded(FormDefinition form) {
        if (form == null || form.getConfigJson() == null) {
            return;
        }
        List<FormTableBinding> bindings = form.getTableBindings();
        if (bindings == null || bindings.isEmpty()) {
            return;
        }
        FormConfigJsonOrphanBindingRepair.repairOrphanedBindingKeys(form.getConfigJson(), bindings);
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
        validateProcessFormUniqueness(functionUnitId, FormScene.TASK);
    }

    @Override
    @Transactional(readOnly = true)
    public void validateProcessFormUniqueness(Long functionUnitId, FormScene scene) {
        long processFormCount = formDefinitionRepository
                .countByFunctionUnitIdAndFormTypeAndScene(functionUnitId, FormType.PROCESS, resolveScene(scene));
        if (processFormCount > 0) {
            throw new DeveloperBusinessException("PROCESS_FORM_ALREADY_EXISTS",
                    i18nService.getMessage("form.process_form_already_exists"),
                    i18nService.getMessage("form.only_one_process_form"));
        }
    }

    /** Legacy payloads carry no scene; they mean the To Do design. */
    private static FormScene resolveScene(FormScene scene) {
        return scene == null ? FormScene.TASK : scene;
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

    @Override
    @Transactional
    public FormConfigPasteRepairResponse repairPastedConfig(
            Long functionUnitId, Long formId, FormConfigPasteRepairRequest request) {
        FormDefinition form = getById(formId);
        if (form.getFunctionUnit() == null
                || !Objects.equals(form.getFunctionUnit().getId(), functionUnitId)) {
            throw new ResourceNotFoundException("FormDefinition", formId);
        }
        Map<String, Object> pasted = deepCopyMap(request.getConfigJson());
        List<FormTableBinding> bindings = formTableBindingRepository.findByFormIdWithTable(formId);
        List<String> createdTables = new ArrayList<>();

        Map<Long, Long> provisionBindingMap = new LinkedHashMap<>();
        // Provision mutates Table Design; only when apply=true (persist path / confirmed save).
        if (request.isCreateMissingTables() && request.isApply()) {
            FormConfigJsonTableProvisioner.ProvisionResult provision =
                    formConfigJsonTableProvisioner.provision(functionUnitId, form, pasted);
            provisionBindingMap.putAll(provision.bindingIdMapping());
            createdTables.addAll(provision.createdTableNames());
            if (!provisionBindingMap.isEmpty()) {
                FormConfigJsonBindingIdRewriter.remapIds(
                        pasted, provisionBindingMap, Map.of(), Map.of(), Map.of());
            }
            bindings = formTableBindingRepository.findByFormIdWithTable(formId);
            form = getById(formId);
        }

        if (bindings.isEmpty()) {
            throw new DeveloperBusinessException(
                    "FORM_BINDINGS_REQUIRED",
                    i18nService.getMessage("form.bindings_required_for_paste_repair"),
                    i18nService.getMessage("form.create_bindings_before_paste"));
        }

        Map<Long, Set<String>> tableFields = new HashMap<>();
        for (TableDefinition table : tableDefinitionRepository.findByFunctionUnitIdWithFields(functionUnitId)) {
            if (table.getId() == null || table.getFieldDefinitions() == null) {
                continue;
            }
            tableFields.put(
                    table.getId(),
                    table.getFieldDefinitions().stream()
                            .map(FieldDefinition::getFieldName)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toCollection(LinkedHashSet::new)));
        }

        FormConfigJsonPasteBindingMapper.MappingResult mapping =
                FormConfigJsonPasteBindingMapper.buildMapping(pasted, bindings, tableFields);

        Map<Long, Long> bindingMap = new LinkedHashMap<>(provisionBindingMap);
        bindingMap.putAll(mapping.bindingIdMapping());

        if (!mapping.bindingIdMapping().isEmpty() || !mapping.relationTableIdMapping().isEmpty()) {
            FormConfigJsonBindingIdRewriter.remapIds(
                    pasted,
                    mapping.bindingIdMapping(),
                    Map.of(),
                    Map.of(),
                    mapping.relationTableIdMapping());
        }
        FormConfigJsonOrphanBindingRepair.repairOrphanedBindingKeys(pasted, bindings);

        List<String> warnings = new ArrayList<>();
        for (Long stale : mapping.unmappedStaleBindingIds()) {
            if (!bindingMap.containsKey(stale)) {
                warnings.add("UNMAPPED_BINDING:" + stale);
            }
        }
        if (mapping.mixedSource()) {
            warnings.add("MIXED_SOURCE");
        }
        for (String tableName : createdTables) {
            warnings.add("CREATED_TABLE:" + tableName);
        }

        boolean applied = false;
        if (request.isApply()) {
            form.setConfigJson(pasted);
            formDefinitionRepository.save(form);
            applied = true;
        }

        Map<String, String> bindingOut = new LinkedHashMap<>();
        bindingMap.forEach((k, v) -> bindingOut.put(String.valueOf(k), String.valueOf(v)));
        Map<String, String> tableOut = new LinkedHashMap<>();
        mapping.relationTableIdMapping().forEach((k, v) -> tableOut.put(String.valueOf(k), String.valueOf(v)));

        return FormConfigPasteRepairResponse.builder()
                .configJson(pasted)
                .bindingIdMapping(bindingOut)
                .relationTableIdMapping(tableOut)
                .warnings(warnings)
                .mixedSource(mapping.mixedSource())
                .applied(applied)
                .createdTableNames(createdTables)
                .build();
    }
}
