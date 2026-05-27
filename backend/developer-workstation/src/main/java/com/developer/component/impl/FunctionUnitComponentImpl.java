package com.developer.component.impl;

import com.developer.component.FunctionUnitComponent;
import com.developer.dto.DevGroupAssignmentRequest;
import com.developer.dto.FunctionUnitRequest;
import com.developer.dto.FunctionUnitResponse;
import com.developer.dto.ValidationResult;
import com.developer.dto.VersionResponse;
import com.developer.entity.*;
import com.developer.enums.FunctionUnitStatus;
import com.developer.exception.DeveloperBusinessException;
import com.developer.exception.ResourceNotFoundException;
import com.developer.entity.FunctionUnitDevGroupAssignment;
import com.developer.repository.*;
import com.developer.security.FunctionUnitWorkspaceAccessService;
import com.developer.security.WorkspaceAccessAction;
import com.developer.component.VersionComponent;
import com.developer.util.BpmnIdRewriter;
import com.developer.util.BpmnProcessIdRewriter;
import com.developer.util.DeveloperWorkstationSequenceSynchronizer;
import com.developer.util.FormConfigJsonBindingIdRewriter;
import com.developer.util.MinimalBpmnTemplate;
import com.developer.util.XmlEncodingUtil;
import com.developer.service.UserDisplayNameService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import com.platform.security.util.SecurityContextUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Function unit component implementation.
 */
@Component
@Slf4j
public class FunctionUnitComponentImpl implements FunctionUnitComponent {
    
    private final FunctionUnitRepository functionUnitRepository;
    private final ProcessDefinitionRepository processDefinitionRepository;
    private final TableDefinitionRepository tableDefinitionRepository;
    private final FormDefinitionRepository formDefinitionRepository;
    private final ActionDefinitionRepository actionDefinitionRepository;
    private final DecisionDefinitionRepository decisionDefinitionRepository;
    private final FormTableBindingRepository formTableBindingRepository;
    private final FormStageBindingRepository formStageBindingRepository;
    private final TableRelationRepository tableRelationRepository;
    private final SubTableViewConfigRepository subTableViewConfigRepository;
    private final VersionRepository versionRepository;
    private final IconRepository iconRepository;
    private final ObjectMapper objectMapper;
    private final UserDisplayNameService userDisplayNameService;
    private final FunctionUnitWorkspaceAccessService functionUnitWorkspaceAccessService;
    private final FunctionUnitDevGroupAssignmentRepository functionUnitDevGroupAssignmentRepository;
    private final VersionComponent versionComponent;
    private final DeveloperWorkstationSequenceSynchronizer sequenceSynchronizer;
    
    public FunctionUnitComponentImpl(
            FunctionUnitRepository functionUnitRepository,
            ProcessDefinitionRepository processDefinitionRepository,
            TableDefinitionRepository tableDefinitionRepository,
            FormDefinitionRepository formDefinitionRepository,
            ActionDefinitionRepository actionDefinitionRepository,
            DecisionDefinitionRepository decisionDefinitionRepository,
            FormTableBindingRepository formTableBindingRepository,
            FormStageBindingRepository formStageBindingRepository,
            TableRelationRepository tableRelationRepository,
            SubTableViewConfigRepository subTableViewConfigRepository,
            VersionRepository versionRepository,
            IconRepository iconRepository,
            ObjectMapper objectMapper,
            UserDisplayNameService userDisplayNameService,
            FunctionUnitWorkspaceAccessService functionUnitWorkspaceAccessService,
            FunctionUnitDevGroupAssignmentRepository functionUnitDevGroupAssignmentRepository,
            VersionComponent versionComponent,
            DeveloperWorkstationSequenceSynchronizer sequenceSynchronizer) {
        this.functionUnitRepository = functionUnitRepository;
        this.processDefinitionRepository = processDefinitionRepository;
        this.tableDefinitionRepository = tableDefinitionRepository;
        this.formDefinitionRepository = formDefinitionRepository;
        this.actionDefinitionRepository = actionDefinitionRepository;
        this.decisionDefinitionRepository = decisionDefinitionRepository;
        this.formTableBindingRepository = formTableBindingRepository;
        this.formStageBindingRepository = formStageBindingRepository;
        this.tableRelationRepository = tableRelationRepository;
        this.subTableViewConfigRepository = subTableViewConfigRepository;
        this.versionRepository = versionRepository;
        this.iconRepository = iconRepository;
        this.objectMapper = objectMapper;
        this.userDisplayNameService = userDisplayNameService;
        this.functionUnitWorkspaceAccessService = functionUnitWorkspaceAccessService;
        this.functionUnitDevGroupAssignmentRepository = functionUnitDevGroupAssignmentRepository;
        this.versionComponent = versionComponent;
        this.sequenceSynchronizer = sequenceSynchronizer;
    }
    
    /**
     * Returns the current operator.
     * Prefer Spring Security Context; return "system" when unavailable.
     * 
     * Cases that yield "system":
     * - No authentication (not logged in)
     * - Anonymous user
     * - System background job
     * - Exception while resolving operator
     * 
     * @return current operator username, or "system" when unavailable
     */
    private String getCurrentOperator() {
        try {
            return SecurityContextUtils.getCurrentUsername().orElse("system");
        } catch (Exception e) {
            log.debug("Failed to get current operator from security context: {}", e.getMessage());
        }
        return "system";
    }
    
    @Override
    @Transactional
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('TECH_LEAD', 'TEAM_LEAD')")
    public FunctionUnit create(FunctionUnitRequest request) {
        if (functionUnitRepository.existsByName(request.getName())) {
            throw new DeveloperBusinessException("CONFLICT_NAME_EXISTS", 
                    "Function unit name already exists: " + request.getName(),
                    "Please use a different name");
        }
        
        // Generate unique code
        String code = generateUniqueCode(request.getName());
        
        FunctionUnit functionUnit = FunctionUnit.builder()
                .name(request.getName())
                .code(code)
                .description(request.getDescription())
                .status(FunctionUnitStatus.DRAFT)
                .build();
        
        if (request.getIconId() != null) {
            Icon icon = iconRepository.findById(request.getIconId())
                    .orElseThrow(() -> new ResourceNotFoundException("Icon", request.getIconId()));
            functionUnit.setIcon(icon);
        }
        
        functionUnit = functionUnitRepository.save(functionUnit);

        String initialBpmnXml = MinimalBpmnTemplate.build(functionUnit.getCode());
        ProcessDefinition initialProcess = ProcessDefinition.builder()
                .functionUnit(functionUnit)
                .functionUnitVersionId(functionUnit.getId())
                .bpmnXml(XmlEncodingUtil.encode(initialBpmnXml))
                .build();
        processDefinitionRepository.save(initialProcess);

        return functionUnit;
    }
    
    /**
     * Generate a unique function unit code
     * Format: {functionUnitName}-{yyyyMMdd}-{random6chars}
     *
     * Note: prefix is sanitized to [a-z0-9-] only; empty or digit-leading prefixes are avoided for BPMN compatibility.
     */
    private String generateUniqueCode(String functionUnitName) {
        String prefix = normalizeCodePrefix(functionUnitName);
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        SecureRandom random = new SecureRandom();
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder randomPart = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                randomPart.append(chars.charAt(random.nextInt(chars.length())));
            }
            String code = prefix + "-" + datePart + "-" + randomPart;
            if (!functionUnitRepository.existsByCode(code)) {
                return code;
            }
        }
        // Use timestamp as last resort
        return prefix + "-" + datePart + "-" + (System.currentTimeMillis() % 1000000);
    }

    /**
     * Normalize FunctionUnit name into a prefix usable as code/processId.
     *
     * Flowable/BPMN constraints (XML Name / xsd:ID safe subset):
     * - First character must be [a-z_] (no leading digit)
     * - Subsequent characters only [a-z0-9_.-]
     * - Used for `<bpmn:process id="...">` and dw_function_units.code (length=50); truncated so total length stays within 50.
     */
    private String normalizeCodePrefix(String name) {
        // Reserve space for "-yyyyMMdd-random6" => 1 + 8 + 1 + 6 = 16 chars
        // total length limit is 50 => prefix max length is 34
        final int maxPrefixLen = 34;

        if (name == null) {
            return "fu";
        }
        String raw = name.trim();
        if (raw.isEmpty()) {
            return "fu";
        }

        StringBuilder out = new StringBuilder();
        char prev = 0;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            char mapped;
            if (Character.isLetterOrDigit(c)) {
                mapped = Character.toLowerCase(c);
            } else if (c == '_' || c == '-' || c == '.') {
                mapped = c;
            } else if (Character.isWhitespace(c)) {
                mapped = '-';
            } else {
                continue;
            }
            // collapse repeating separators
            if ((mapped == '-' || mapped == '_' || mapped == '.') && mapped == prev) {
                continue;
            }
            out.append(mapped);
            prev = mapped;
            if (out.length() >= maxPrefixLen + 8) {
                // avoid excessive work on very long names; we'll truncate later anyway
                break;
            }
        }

        String s = out.toString();
        // trim separators on both ends
        s = s.replaceAll("^[-_.]+", "").replaceAll("[-_.]+$", "");
        if (s.isEmpty()) {
            return "fu";
        }

        // XML Name: first char must be letter or '_' (we restrict to [a-z_])
        char first = s.charAt(0);
        boolean firstOk = (first >= 'a' && first <= 'z') || first == '_';
        if (!firstOk) {
            s = "fu-" + s;
        }
        // avoid leading separators after prefixing
        s = s.replaceAll("^[-_.]+", "");
        if (s.isEmpty()) {
            return "fu";
        }

        // keep within prefix budget (leave room for date+random suffix)
        if (s.length() > maxPrefixLen) {
            s = s.substring(0, maxPrefixLen);
            s = s.replaceAll("[-_.]+$", "");
            if (s.isEmpty()) {
                return "fu";
            }
        }

        // extra safety: if starts with "xml" (case-insensitive) it may confuse tooling; prefix it
        if (s.length() >= 3 && s.regionMatches(true, 0, "xml", 0, 3)) {
            s = "fu-" + s;
            if (s.length() > maxPrefixLen) {
                s = s.substring(0, maxPrefixLen).replaceAll("[-_.]+$", "");
                if (s.isEmpty()) {
                    return "fu";
                }
            }
        }

        return s;
    }
    
    @Override
    @Transactional
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('TECH_LEAD', 'TEAM_LEAD', 'DEVELOPER')")
    public FunctionUnit update(Long id, FunctionUnitRequest request) {
        functionUnitWorkspaceAccessService.assertCanAccess(id, WorkspaceAccessAction.MODIFY);
        FunctionUnit functionUnit = getById(id);
        
        if (functionUnitRepository.existsByNameAndIdNot(request.getName(), id)) {
            throw new DeveloperBusinessException("CONFLICT_NAME_EXISTS", 
                    "Function unit name already exists: " + request.getName(),
                    "Please use a different name");
        }
        
        functionUnit.setName(request.getName());
        functionUnit.setDescription(request.getDescription());
        
        if (request.getIconId() != null) {
            Icon icon = iconRepository.findById(request.getIconId())
                    .orElseThrow(() -> new ResourceNotFoundException("Icon", request.getIconId()));
            functionUnit.setIcon(icon);
        } else {
            functionUnit.setIcon(null);
        }
        
        return functionUnitRepository.save(functionUnit);
    }
    
    @Override
    @Transactional
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('TECH_LEAD', 'TEAM_LEAD')")
    public void delete(Long id) {
        functionUnitWorkspaceAccessService.assertCanAccess(id, WorkspaceAccessAction.DELETE);
        FunctionUnit functionUnit = getById(id);
        if (functionUnit.getStatus() != FunctionUnitStatus.ARCHIVED) {
            functionUnit.setStatus(FunctionUnitStatus.ARCHIVED);
            functionUnitRepository.save(functionUnit);
            log.info("Archived function unit id={}, name={}", id, functionUnit.getName());
            return;
        }
        functionUnitDevGroupAssignmentRepository.deleteByFunctionUnitId(id);
        functionUnitRepository.delete(functionUnit);
        log.info("Permanently deleted archived function unit id={}", id);
    }
    
    @Override
    @Transactional(readOnly = true)
    public FunctionUnit getById(Long id) {
        return functionUnitRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FunctionUnit", id));
    }
    
    @Override
    @Transactional(readOnly = true)
    public FunctionUnitResponse getByIdAsResponse(Long id) {
        functionUnitWorkspaceAccessService.assertCanAccess(id, WorkspaceAccessAction.VIEW);
        FunctionUnit entity = getById(id);
        return toResponse(entity);
    }
    
    @Override
    @Transactional(readOnly = true)
    public Page<FunctionUnitResponse> list(String name, String status, Pageable pageable) {
        Specification<FunctionUnit> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            // Only show enabled versions to users
            predicates.add(cb.equal(root.get("enabled"), true));
            
            if (name != null && !name.trim().isEmpty()) {
                // Escape SQL LIKE special characters to prevent injection
                String escapedName = name.trim().toLowerCase()
                        .replace("\\", "\\\\")
                        .replace("%", "\\%")
                        .replace("_", "\\_");
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + escapedName + "%", '\\'));
            }
            
            if (status != null && !status.trim().isEmpty()) {
                predicates.add(cb.equal(root.get("status"), FunctionUnitStatus.valueOf(status)));
            }

            java.util.Set<Long> visible = functionUnitWorkspaceAccessService.visibleFunctionUnitIds();
            if (visible != null && visible.isEmpty()) {
                predicates.add(cb.disjunction());
            } else if (visible != null) {
                predicates.add(root.get("id").in(visible));
            }
            
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        
        // Query via Specification; load associations manually
        // Specification has no EntityGraph; toResponse handles lazy loading safely
        Page<FunctionUnit> page = functionUnitRepository.findAll(spec, pageable);
        
        // Trigger lazy loading in transaction so associations are initialized
        page.getContent().forEach(entity -> {
            try {
                // Trigger lazy loading
                if (entity.getTableDefinitions() != null) {
                    entity.getTableDefinitions().size();
                }
                if (entity.getFormDefinitions() != null) {
                    entity.getFormDefinitions().size();
                }
                if (entity.getActionDefinitions() != null) {
                    entity.getActionDefinitions().size();
                }
                if (entity.getDecisionDefinitions() != null) {
                    entity.getDecisionDefinitions().size();
                }
                if (entity.getProcessDefinition() != null) {
                    entity.getProcessDefinition().getId();
                }
            } catch (Exception e) {
                log.warn("Failed to eagerly load relations for function unit {}: {}", entity.getId(), e.getMessage());
            }
        });
        
        return page.map(this::toResponse);
    }
    
    @Override
    @Transactional
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('TECH_LEAD', 'TEAM_LEAD', 'DEVELOPER')")
    public FunctionUnit publish(Long id, String changeLog) {
        functionUnitWorkspaceAccessService.assertCanAccess(id, WorkspaceAccessAction.MODIFY);
        FunctionUnit functionUnit = getById(id);
        
        // Validate function unit completeness
        ValidationResult validationResult = validate(id);
        if (!validationResult.isValid()) {
            throw new DeveloperBusinessException("BIZ_INVALID_FUNCTION_UNIT", 
                    "Function unit validation failed, cannot publish",
                    "Please fix validation errors before retrying");
        }
        
        // Compute new version number
        String newVersion = calculateNextVersion(functionUnit.getCurrentVersion());
        
        // Check version exists to avoid unique constraint conflict
        boolean versionAlreadyExists = versionRepository.findByFunctionUnitIdAndVersionNumber(id, newVersion).isPresent();
        if (versionAlreadyExists) {
            // Version snapshot exists but currentVersion not updated (failed deploy); allow completing status update
            log.warn("Version snapshot {} already exists but function unit status not updated, continuing publish flow, functionUnitId={}", newVersion, id);
        } else {
            // Create version snapshot
            try {
                byte[] snapshotData = createSnapshot(functionUnit);
                Version version = Version.builder()
                        .functionUnit(functionUnit)
                        .versionNumber(newVersion)
                        .changeLog(changeLog)
                        .snapshotData(snapshotData)
                        .publishedBy(getCurrentOperator())
                        .build();
                versionRepository.save(version);
            } catch (DeveloperBusinessException e) {
                throw e;
            } catch (Exception e) {
                log.error("Failed to create version snapshot, functionUnitId={}, version={}: {}", id, newVersion, e.getMessage(), e);
                throw new DeveloperBusinessException("SYS_SNAPSHOT_ERROR", "Failed to create version snapshot: " + e.getMessage());
            }
        }
        
        // Update function unit status
        functionUnit.setStatus(FunctionUnitStatus.PUBLISHED);
        functionUnit.setCurrentVersion(newVersion);
        
        return functionUnitRepository.save(functionUnit);
    }
    
    @Override
    @Transactional
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('TECH_LEAD', 'TEAM_LEAD')")
    public FunctionUnit clone(Long id, String newName) {
        functionUnitWorkspaceAccessService.assertCanAccess(id, WorkspaceAccessAction.MODIFY);
        sequenceSynchronizer.synchronizeAll();
        if (functionUnitRepository.existsByName(newName)) {
            throw new DeveloperBusinessException("CONFLICT_NAME_EXISTS", 
                    "Function unit name already exists: " + newName,
                    "Please use a different name");
        }
        
        FunctionUnit source = getById(id);
        List<TableDefinition> sourceTables = tableDefinitionRepository.findByFunctionUnitIdWithFields(id);
        List<FormDefinition> sourceForms = formDefinitionRepository.findByFunctionUnitIdWithBindings(id);
        List<TableRelation> sourceRelations = tableRelationRepository.findByFunctionUnitId(id);
        
        // Create new function unit with new unique code
        FunctionUnit cloned = FunctionUnit.builder()
                .name(newName)
                .code(generateUniqueCode(newName))
                .description(source.getDescription())
                .icon(source.getIcon())
                .status(FunctionUnitStatus.DRAFT)
                .build();
        cloned = functionUnitRepository.save(cloned);
        
        // Clone order: clone all ProcessDefinition dependencies (tables/forms/actions) first,
        // collect old→new ID map, then write process definition and rewrite BPMN ID references.
        // Otherwise BPMN still references source subTableId/formId/actionIds and deploy validation fails.
        
        // Clone table definitions
        Map<Long, TableDefinition> tableMapping = new HashMap<>();
        for (TableDefinition sourceTable : sourceTables) {
            TableDefinition clonedTable = cloneTable(sourceTable, cloned);
            tableMapping.put(sourceTable.getId(), clonedTable);
        }
        
        // Clone FK relations after all tables (FKs may cross tables)
        Map<Long, Map<String, FieldDefinition>> clonedFieldLookup = new HashMap<>();
        for (Map.Entry<Long, TableDefinition> entry : tableMapping.entrySet()) {
            Map<String, FieldDefinition> fieldMap = new HashMap<>();
            for (FieldDefinition field : entry.getValue().getFieldDefinitions()) {
                fieldMap.put(field.getFieldName(), field);
            }
            clonedFieldLookup.put(entry.getKey(), fieldMap);
        }
        for (TableDefinition sourceTable : sourceTables) {
            if (sourceTable.getForeignKeys() != null) {
                TableDefinition clonedTable = tableMapping.get(sourceTable.getId());
                for (ForeignKey sourceFk : sourceTable.getForeignKeys()) {
                    TableDefinition clonedRefTable = sourceFk.getRefTableDefinition() != null
                            ? tableMapping.get(sourceFk.getRefTableDefinition().getId()) : null;
                    FieldDefinition clonedField = sourceFk.getFieldDefinition() != null
                            ? clonedFieldLookup.getOrDefault(sourceTable.getId(), Map.of())
                                .get(sourceFk.getFieldDefinition().getFieldName()) : null;
                    FieldDefinition clonedRefField = sourceFk.getRefFieldDefinition() != null && clonedRefTable != null
                            ? clonedFieldLookup.getOrDefault(sourceFk.getRefTableDefinition().getId(), Map.of())
                                .get(sourceFk.getRefFieldDefinition().getFieldName()) : null;
                    
                    if (clonedField != null && clonedRefTable != null && clonedRefField != null) {
                        ForeignKey clonedFk = ForeignKey.builder()
                                .tableDefinition(clonedTable)
                                .fieldDefinition(clonedField)
                                .refTableDefinition(clonedRefTable)
                                .refFieldDefinition(clonedRefField)
                                .onDelete(sourceFk.getOnDelete())
                                .onUpdate(sourceFk.getOnUpdate())
                                .build();
                        clonedTable.getForeignKeys().add(clonedFk);
                    }
                }
                tableDefinitionRepository.save(clonedTable);
            }
        }

        cloneTableRelations(sourceRelations, cloned, tableMapping);
        
        // Clone form definitions (with TableBindings); collect form id map
        Map<Long, Long> formIdMapping = new HashMap<>();
        for (FormDefinition sourceForm : sourceForms) {
            FormDefinition clonedForm = cloneForm(sourceForm, cloned, tableMapping);
            formIdMapping.put(sourceForm.getId(), clonedForm.getId());
        }
        
        // Clone action definitions; collect action id map
        Map<Long, Long> actionIdMapping = new HashMap<>();
        for (ActionDefinition sourceAction : source.getActionDefinitions()) {
            ActionDefinition clonedAction = cloneAction(sourceAction, cloned);
            actionIdMapping.put(sourceAction.getId(), clonedAction.getId());
        }
        
        // Clone decision definitions
        for (DecisionDefinition sourceDecision : source.getDecisionDefinitions()) {
            cloneDecision(sourceDecision, cloned);
        }
        
        // Clone process definition last; rewrite BPMN ID references
        if (source.getProcessDefinition() != null) {
            // Old→new ID map (fallback)
            Map<Long, Long> tableIdMapping = new HashMap<>();
            for (Map.Entry<Long, TableDefinition> entry : tableMapping.entrySet()) {
                tableIdMapping.put(entry.getKey(), entry.getValue().getId());
            }
            // Clone-side name→new ID map (preferred; fixes dirty BPMN where id/name diverge).
            // Names reused as-is; cloneTable/cloneForm keep source names.
            Map<String, Long> clonedTableNameToId = new HashMap<>();
            for (TableDefinition clonedTable : tableMapping.values()) {
                clonedTableNameToId.put(clonedTable.getTableName(), clonedTable.getId());
            }
            Map<String, Long> clonedFormNameToId = new HashMap<>();
            for (FormDefinition sourceForm : sourceForms) {
                Long clonedFormId = formIdMapping.get(sourceForm.getId());
                if (clonedFormId != null) {
                    clonedFormNameToId.put(sourceForm.getFormName(), clonedFormId);
                }
            }
            String rewrittenBpmn = BpmnIdRewriter.rewrite(
                    source.getProcessDefinition().getBpmnXml(),
                    tableIdMapping,
                    formIdMapping,
                    actionIdMapping,
                    clonedTableNameToId,
                    clonedFormNameToId);
            rewrittenBpmn = BpmnProcessIdRewriter.rewriteToFunctionUnitCode(rewrittenBpmn, cloned.getCode());
            ProcessDefinition clonedProcess = ProcessDefinition.builder()
                    .functionUnit(cloned)
                    .functionUnitVersionId(cloned.getId())
                    .bpmnXml(rewrittenBpmn)
                    .build();
            processDefinitionRepository.save(clonedProcess);
        }
        
        return cloned;
    }
    
    @Override
    @Transactional(readOnly = true)
    public ValidationResult validate(Long id) {
        functionUnitWorkspaceAccessService.assertCanAccess(id, WorkspaceAccessAction.VIEW);
        FunctionUnit functionUnit = getById(id);
        ValidationResult result = new ValidationResult();
        
        // Check process definition exists
        if (functionUnit.getProcessDefinition() == null) {
            result.addWarning("MISSING_PROCESS", "Function unit has no process definition", null);
        }
        
        // Check primary table exists
        boolean hasMainTable = functionUnit.getTableDefinitions().stream()
                .anyMatch(t -> t.getTableType() == com.developer.enums.TableType.MAIN);
        if (!hasMainTable) {
            result.addWarning("MISSING_MAIN_TABLE", "Function unit has no main table", null);
        }
        
        // Check process form exists
        boolean hasProcessForm = functionUnit.getFormDefinitions().stream()
                .anyMatch(f -> f.getFormType() == com.developer.enums.FormType.PROCESS);
        if (!hasProcessForm) {
            result.addWarning("MISSING_PROCESS_FORM", "Function unit has no process form", null);
        }
        
        // BPMN-DMN cross-reference validation
        validateBpmnDmnCrossReferences(functionUnit, result);
        
        // DECISION_TABLE action config validation
        validateDecisionTableActions(functionUnit, result);
        
        return result;
    }
    
    /**
     * BPMN-DMN cross-reference validation
     * Ensure BPMN decision keys exist in same function unit decision definitions
     */
    private void validateBpmnDmnCrossReferences(FunctionUnit functionUnit, ValidationResult result) {
        List<DecisionDefinition> decisions = functionUnit.getDecisionDefinitions();
        
        // No decision errors when there are no DecisionDefinitions
        if (decisions == null || decisions.isEmpty()) {
            return;
        }
        
        // Cannot cross-validate without process definition
        if (functionUnit.getProcessDefinition() == null) {
            return;
        }
        
        String bpmnXml = functionUnit.getProcessDefinition().getBpmnXml();
        if (bpmnXml == null || bpmnXml.isBlank()) {
            return;
        }
        
        // Decode BPMN XML (may be Base64)
        String decodedBpmnXml = XmlEncodingUtil.smartDecode(bpmnXml);
        
        // Extract decision keys from DMN service tasks in BPMN XML
        Set<String> referencedKeys = extractDmnReferenceKeys(decodedBpmnXml, functionUnit.getId());
        
        // Build set of existing decision definition keys
        Set<String> definedKeys = new HashSet<>();
        for (DecisionDefinition decision : decisions) {
            definedKeys.add(decision.getDecisionKey());
        }
        
        // Check BPMN decision keys exist in DecisionDefinition list
        for (String referencedKey : referencedKeys) {
            if (!definedKeys.contains(referencedKey)) {
                result.addError("INVALID_DECISION_REFERENCE",
                        "BPMN process references decision key '" + referencedKey + "' which does not exist in this function unit",
                        referencedKey);
            } else {
                decisions.stream()
                        .filter(d -> referencedKey.equals(d.getDecisionKey()))
                        .findFirst()
                        .ifPresent(d -> {
                            if (d.getDmnXml() == null || d.getDmnXml().isBlank()) {
                                result.addError("EMPTY_DMN_XML",
                                        "BPMN references decision key '" + referencedKey + "' but its DMN XML is empty",
                                        referencedKey);
                            }
                        });
            }
        }
        
        // Check for DecisionDefinitions not referenced by BPMN
        for (String definedKey : definedKeys) {
            if (!referencedKeys.contains(definedKey)) {
                result.addWarning("UNREFERENCED_DECISION",
                        "Decision definition '" + definedKey + "' is not referenced by any BPMN service task",
                        definedKey);
            }
        }
    }
    
    /**
     * DECISION_TABLE action config validation
     * When ActionType is DECISION_TABLE, validate config_json has decisionKey, inputMappings, outputMappings,
     * and decisionKey references a DecisionDefinition in the same function unit.
     */
    private void validateDecisionTableActions(FunctionUnit functionUnit, ValidationResult result) {
        List<ActionDefinition> actions = functionUnit.getActionDefinitions();
        if (actions == null || actions.isEmpty()) {
            return;
        }
        
        // Build set of existing decision definition keys
        Set<String> definedDecisionKeys = new HashSet<>();
        List<DecisionDefinition> decisions = functionUnit.getDecisionDefinitions();
        if (decisions != null) {
            for (DecisionDefinition decision : decisions) {
                definedDecisionKeys.add(decision.getDecisionKey());
            }
        }
        
        for (ActionDefinition action : actions) {
            if (action.getActionType() != com.developer.enums.ActionType.DECISION_TABLE) {
                continue;
            }
            
            Map<String, Object> config = action.getConfigJson();
            String actionName = action.getActionName();
            
            if (config == null || config.isEmpty()) {
                result.addError("MISSING_DECISION_CONFIG",
                        "DECISION_TABLE action '" + actionName + "' has empty config_json",
                        actionName);
                continue;
            }
            
            // Validate required field: decisionKey
            Object decisionKeyObj = config.get("decisionKey");
            boolean hasDecisionKey = decisionKeyObj instanceof String dk && !dk.isBlank();
            if (!hasDecisionKey) {
                result.addError("MISSING_DECISION_KEY",
                        "DECISION_TABLE action '" + actionName + "' config_json is missing required field 'decisionKey'",
                        actionName);
            }
            
            // Validate required field: inputMappings
            if (!config.containsKey("inputMappings")) {
                result.addError("MISSING_INPUT_MAPPINGS",
                        "DECISION_TABLE action '" + actionName + "' config_json is missing required field 'inputMappings'",
                        actionName);
            }
            
            // Validate required field: outputMappings
            if (!config.containsKey("outputMappings")) {
                result.addError("MISSING_OUTPUT_MAPPINGS",
                        "DECISION_TABLE action '" + actionName + "' config_json is missing required field 'outputMappings'",
                        actionName);
            }
            
            // Validate decisionKey references DecisionDefinition in same function unit
            if (hasDecisionKey) {
                String decisionKey = (String) decisionKeyObj;
                if (!definedDecisionKeys.contains(decisionKey)) {
                    result.addError("INVALID_DECISION_REFERENCE",
                            "DECISION_TABLE action '" + actionName + "' references decision key '" + decisionKey + "' which does not exist in this function unit",
                            actionName);
                } else if (decisions != null) {
                    decisions.stream()
                            .filter(d -> decisionKey.equals(d.getDecisionKey()))
                            .findFirst()
                            .ifPresent(d -> {
                                if (d.getDmnXml() == null || d.getDmnXml().isBlank()) {
                                    result.addError("EMPTY_DMN_XML",
                                            "DECISION_TABLE action '" + actionName + "' references decision '" + decisionKey + "' which has no DMN XML content",
                                            actionName);
                                }
                            });
                }
            }
        }
    }
    
    /**
     * Extract decisionTableReferenceKey from DMN service tasks in BPMN XML
     * Supports two formats:
     * 1. Attribute: flowable:decisionTableReferenceKey="key"
     * 2. Extension element: flowable:field name="decisionTableReferenceKey" > flowable:string
     */
    private Set<String> extractDmnReferenceKeys(String bpmnXml, Long functionUnitId) {
        Set<String> keys = new HashSet<>();
        try {
            Document document = parseXmlSecurely(bpmnXml);
            
            // Find all serviceTask elements
            NodeList serviceTasks = document.getElementsByTagNameNS("*", "serviceTask");
            for (int i = 0; i < serviceTasks.getLength(); i++) {
                Element serviceTask = (Element) serviceTasks.item(i);
                
                // Check DMN service task (flowable:type="dmn")
                if (!isDmnServiceTask(serviceTask)) {
                    continue;
                }
                
                // Try attribute for decisionTableReferenceKey
                String key = extractKeyFromAttribute(serviceTask);
                if (key == null || key.isBlank()) {
                    // Try extension element extraction
                    key = extractKeyFromExtensionElements(serviceTask);
                }
                
                if (key != null && !key.isBlank()) {
                    keys.add(key.trim());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse BPMN XML for DMN cross-reference validation, functionUnitId={}: {}",
                    functionUnitId, e.getMessage());
        }
        return keys;
    }
    
    /**
     * Check whether serviceTask is DMN type
     */
    private boolean isDmnServiceTask(Element serviceTask) {
        // Check type attribute under all namespace prefixes
        var attributes = serviceTask.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            var attr = attributes.item(i);
            if ("type".equals(attr.getLocalName()) && "dmn".equals(attr.getNodeValue())) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Extract decisionTableReferenceKey from serviceTask attributes
     */
    private String extractKeyFromAttribute(Element serviceTask) {
        var attributes = serviceTask.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            var attr = attributes.item(i);
            if ("decisionTableReferenceKey".equals(attr.getLocalName())) {
                return attr.getNodeValue();
            }
        }
        return null;
    }
    
    /**
     * Extract decisionTableReferenceKey from extension elements
     * Format: {@code <flowable:field name="decisionTableReferenceKey"><flowable:string>key</flowable:string></flowable:field>}
     */
    private String extractKeyFromExtensionElements(Element serviceTask) {
        NodeList extensionElements = serviceTask.getElementsByTagNameNS("*", "extensionElements");
        for (int i = 0; i < extensionElements.getLength(); i++) {
            Element extElem = (Element) extensionElements.item(i);
            NodeList fields = extElem.getElementsByTagNameNS("*", "field");
            for (int j = 0; j < fields.getLength(); j++) {
                Element field = (Element) fields.item(j);
                if ("decisionTableReferenceKey".equals(field.getAttribute("name"))) {
                    // Read value from flowable:string child
                    NodeList stringElements = field.getElementsByTagNameNS("*", "string");
                    if (stringElements.getLength() > 0) {
                        return stringElements.item(0).getTextContent().trim();
                    }
                    // Read value from flowable:expression child
                    NodeList exprElements = field.getElementsByTagNameNS("*", "expression");
                    if (exprElements.getLength() > 0) {
                        return exprElements.item(0).getTextContent().trim();
                    }
                }
            }
        }
        return null;
    }
    
    /**
     * XXE-safe XML parsing
     */
    private Document parseXmlSecurely(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        
        // XXE prevention
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new InputSource(new StringReader(xml)));
    }
    
    @Override
    public boolean existsByName(String name) {
        return functionUnitRepository.existsByName(name);
    }
    
    @Override
    public boolean existsByNameAndIdNot(String name, Long id) {
        return functionUnitRepository.existsByNameAndIdNot(name, id);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<VersionResponse> getVersionHistory(Long functionUnitId) {
        functionUnitWorkspaceAccessService.assertCanAccess(functionUnitId, WorkspaceAccessAction.VIEW);
        FunctionUnit functionUnit = functionUnitRepository.findById(functionUnitId)
                .orElseThrow(() -> new ResourceNotFoundException("FunctionUnit", functionUnitId));
        String activeVersionNumber = functionUnit.getCurrentVersion();
        return versionRepository.findByFunctionUnitIdOrderByPublishedAtDesc(functionUnitId)
                .stream()
                .map(v -> {
                    VersionResponse resp = VersionResponse.from(v, activeVersionNumber);
                    resp.setCreatedBy(resolveUserDisplayName(v.getPublishedBy()));
                    return resp;
                })
                .toList();
    }

    @Override
    @Transactional
    public FunctionUnit rollback(Long functionUnitId, Long versionId) {
        functionUnitWorkspaceAccessService.assertCanAccess(functionUnitId, WorkspaceAccessAction.MODIFY);
        return versionComponent.rollback(functionUnitId, versionId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> compareVersions(Long functionUnitId, Long versionId1, Long versionId2) {
        functionUnitWorkspaceAccessService.assertCanAccess(functionUnitId, WorkspaceAccessAction.VIEW);
        assertVersionBelongsToFunctionUnit(functionUnitId, versionId1);
        assertVersionBelongsToFunctionUnit(functionUnitId, versionId2);
        return versionComponent.compare(versionId1, versionId2);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportVersion(Long functionUnitId, Long versionId) {
        functionUnitWorkspaceAccessService.assertCanAccess(functionUnitId, WorkspaceAccessAction.VIEW);
        assertVersionBelongsToFunctionUnit(functionUnitId, versionId);
        return versionComponent.exportVersion(versionId);
    }

    /**
     * Authorization: ensure version belongs to functionUnit so users cannot read another unit's versions.
     */
    private void assertVersionBelongsToFunctionUnit(Long functionUnitId, Long versionId) {
        Version version = versionComponent.getById(versionId);
        if (version.getFunctionUnit() == null
                || !functionUnitId.equals(version.getFunctionUnit().getId())) {
            throw new DeveloperBusinessException(
                    "BIZ_VERSION_MISMATCH",
                    "Version " + versionId + " does not belong to function unit " + functionUnitId);
        }
    }

    private String resolveUserDisplayName(String userId) {
        return userDisplayNameService.resolve(userId);
    }
    
    private FunctionUnitResponse toResponse(FunctionUnit entity) {
        FunctionUnitResponse.IconInfo iconInfo = null;
        try {
            if (entity.getIcon() != null) {
                Icon icon = entity.getIcon();
                iconInfo = FunctionUnitResponse.IconInfo.builder()
                        .id(icon.getId())
                        .name(icon.getName())
                        .svgContent(icon.getSvgContent())
                        .build();
            }
        } catch (Exception e) {
            log.warn("Failed to load icon for function unit {}: {}", entity.getId(), e.getMessage());
        }
        
        // Safely get collection size to avoid LazyInitializationException
        int tableCount = 0;
        int formCount = 0;
        int actionCount = 0;
        boolean hasProcess = false;
        
        try {
            if (entity.getTableDefinitions() != null) {
                tableCount = entity.getTableDefinitions().size();
            }
        } catch (Exception e) {
            log.warn("Failed to load table definitions for function unit {}: {}", entity.getId(), e.getMessage());
        }
        
        try {
            if (entity.getFormDefinitions() != null) {
                formCount = entity.getFormDefinitions().size();
            }
        } catch (Exception e) {
            log.warn("Failed to load form definitions for function unit {}: {}", entity.getId(), e.getMessage());
        }
        
        try {
            if (entity.getActionDefinitions() != null) {
                actionCount = entity.getActionDefinitions().size();
            }
        } catch (Exception e) {
            log.warn("Failed to load action definitions for function unit {}: {}", entity.getId(), e.getMessage());
        }
        
        int decisionCount = 0;
        try {
            if (entity.getDecisionDefinitions() != null) {
                decisionCount = entity.getDecisionDefinitions().size();
            }
        } catch (Exception e) {
            log.warn("Failed to load decision definitions for function unit {}: {}", entity.getId(), e.getMessage());
        }
        
        try {
            hasProcess = entity.getProcessDefinition() != null;
        } catch (Exception e) {
            log.warn("Failed to load process definition for function unit {}: {}", entity.getId(), e.getMessage());
        }
        
        return FunctionUnitResponse.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .name(entity.getName())
                .description(entity.getDescription())
                .iconId(entity.getIcon() != null ? entity.getIcon().getId() : null)
                .icon(iconInfo)
                .status(entity.getStatus())
                .currentVersion(entity.getCurrentVersion())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedBy(entity.getUpdatedBy())
                .updatedAt(entity.getUpdatedAt())
                .tableCount(tableCount)
                .formCount(formCount)
                .actionCount(actionCount)
                .decisionCount(decisionCount)
                .hasProcess(hasProcess)
                .assignedVirtualGroupIds(functionUnitDevGroupAssignmentRepository.findByFunctionUnitId(entity.getId())
                        .stream()
                        .map(FunctionUnitDevGroupAssignment::getVirtualGroupId)
                        .toList())
                .build();
    }

    @Override
    @Transactional
    public void replaceDevGroupAssignments(Long functionUnitId, DevGroupAssignmentRequest request) {
        functionUnitWorkspaceAccessService.assertCanAccess(functionUnitId, WorkspaceAccessAction.ASSIGN_DEV_GROUPS);
        getById(functionUnitId);
        functionUnitDevGroupAssignmentRepository.deleteByFunctionUnitId(functionUnitId);
        String operator = getCurrentOperator();
        if (request.getVirtualGroupIds() == null) {
            return;
        }
        for (String gid : request.getVirtualGroupIds()) {
            if (gid == null || gid.isBlank()) {
                continue;
            }
            functionUnitDevGroupAssignmentRepository.save(FunctionUnitDevGroupAssignment.builder()
                    .functionUnitId(functionUnitId)
                    .virtualGroupId(gid.trim())
                    .createdAt(Instant.now())
                    .createdBy(operator)
                    .build());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getDevGroupAssignments(Long functionUnitId) {
        functionUnitWorkspaceAccessService.assertCanAccess(functionUnitId, WorkspaceAccessAction.VIEW);
        getById(functionUnitId);
        return functionUnitDevGroupAssignmentRepository.findByFunctionUnitId(functionUnitId).stream()
                .map(FunctionUnitDevGroupAssignment::getVirtualGroupId)
                .toList();
    }
    
    private String calculateNextVersion(String currentVersion) {
        if (currentVersion == null || currentVersion.isEmpty()) {
            return "1.0.0";
        }
        try {
            String[] parts = currentVersion.split("\\.");
            if (parts.length != 3) {
                log.warn("Malformed version string '{}', falling back to 1.0.0", currentVersion);
                return "1.0.0";
            }
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            int patch = Integer.parseInt(parts[2]) + 1;
            return major + "." + minor + "." + patch;
        } catch (NumberFormatException e) {
            log.warn("Failed to parse version '{}': {}, falling back to 1.0.0", currentVersion, e.getMessage());
            return "1.0.0";
        }
    }
    
    private byte[] createSnapshot(FunctionUnit functionUnit) throws Exception {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("name", functionUnit.getName());
        snapshot.put("code", functionUnit.getCode());
        snapshot.put("description", functionUnit.getDescription());
        snapshot.put("status", functionUnit.getStatus() != null ? functionUnit.getStatus().name() : null);
        snapshot.put("processXml", functionUnit.getProcessDefinition() != null ? 
                functionUnit.getProcessDefinition().getBpmnXml() : null);
        
        // Snapshot table definitions with fields
        List<Map<String, Object>> tableSnapshots = new ArrayList<>();
        for (TableDefinition table : functionUnit.getTableDefinitions()) {
            Map<String, Object> tableSnap = new HashMap<>();
            tableSnap.put("tableName", table.getTableName());
            tableSnap.put("tableType", table.getTableType() != null ? table.getTableType().name() : null);
            tableSnap.put("tableDisplayName", table.getTableDisplayName());
            tableSnap.put("description", table.getDescription());
            
            List<Map<String, Object>> fieldSnapshots = new ArrayList<>();
            for (FieldDefinition field : table.getFieldDefinitions()) {
                Map<String, Object> fieldSnap = new HashMap<>();
                fieldSnap.put("fieldName", field.getFieldName());
                fieldSnap.put("dataType", field.getDataType() != null ? field.getDataType().name() : null);
                fieldSnap.put("length", field.getLength());
                fieldSnap.put("precision", field.getPrecision());
                fieldSnap.put("scale", field.getScale());
                fieldSnap.put("nullable", field.getNullable());
                fieldSnap.put("defaultValue", field.getDefaultValue());
                fieldSnap.put("isPrimaryKey", field.getIsPrimaryKey());
                fieldSnap.put("isUnique", field.getIsUnique());
                fieldSnap.put("description", field.getDescription());
                fieldSnap.put("sortOrder", field.getSortOrder());
                fieldSnapshots.add(fieldSnap);
            }
            tableSnap.put("fieldDefinitions", fieldSnapshots);
            tableSnapshots.add(tableSnap);
        }
        snapshot.put("tableDefinitions", tableSnapshots);
        
        // Snapshot form definitions
        List<Map<String, Object>> formSnapshots = new ArrayList<>();
        for (FormDefinition form : functionUnit.getFormDefinitions()) {
            Map<String, Object> formSnap = new HashMap<>();
            formSnap.put("formName", form.getFormName());
            formSnap.put("formType", form.getFormType() != null ? form.getFormType().name() : null);
            formSnap.put("configJson", form.getConfigJson());
            formSnap.put("description", form.getDescription());
            formSnap.put("boundTableName", form.getBoundTableName());
            formSnapshots.add(formSnap);
        }
        snapshot.put("formDefinitions", formSnapshots);
        
        // Snapshot action definitions
        List<Map<String, Object>> actionSnapshots = new ArrayList<>();
        for (ActionDefinition action : functionUnit.getActionDefinitions()) {
            Map<String, Object> actionSnap = new HashMap<>();
            actionSnap.put("actionName", action.getActionName());
            actionSnap.put("actionType", action.getActionType() != null ? action.getActionType().name() : null);
            actionSnap.put("configJson", action.getConfigJson());
            actionSnap.put("icon", action.getIcon());
            actionSnap.put("buttonColor", action.getButtonColor());
            actionSnap.put("description", action.getDescription());
            actionSnap.put("isDefault", action.getIsDefault());
            actionSnapshots.add(actionSnap);
        }
        snapshot.put("actionDefinitions", actionSnapshots);
        
        // Snapshot decision definitions
        List<Map<String, Object>> decisionSnapshots = new ArrayList<>();
        for (DecisionDefinition decision : functionUnit.getDecisionDefinitions()) {
            Map<String, Object> decisionSnap = new HashMap<>();
            decisionSnap.put("decisionKey", decision.getDecisionKey());
            decisionSnap.put("decisionName", decision.getDecisionName());
            decisionSnap.put("dmnXml", decision.getDmnXml());
            decisionSnap.put("hitPolicy", decision.getHitPolicy());
            decisionSnap.put("description", decision.getDescription());
            decisionSnapshots.add(decisionSnap);
        }
        snapshot.put("decisionDefinitions", decisionSnapshots);
        
        return objectMapper.writeValueAsBytes(snapshot);
    }
    
    private TableDefinition cloneTable(TableDefinition source, FunctionUnit target) {
        TableDefinition cloned = TableDefinition.builder()
                .functionUnit(target)
                .tableName(source.getTableName())
                .tableType(source.getTableType())
                .tableDisplayName(source.getTableDisplayName())
                .description(source.getDescription())
                .build();
        cloned = tableDefinitionRepository.save(cloned);
        
        // Clone fields
        for (FieldDefinition sourceField : source.getFieldDefinitions()) {
            FieldDefinition clonedField = FieldDefinition.builder()
                    .tableDefinition(cloned)
                    .fieldName(sourceField.getFieldName())
                    .dataType(sourceField.getDataType())
                    .length(sourceField.getLength())
                    .precision(sourceField.getPrecision())
                    .scale(sourceField.getScale())
                    .nullable(sourceField.getNullable())
                    .defaultValue(sourceField.getDefaultValue())
                    .isPrimaryKey(sourceField.getIsPrimaryKey())
                    .isUnique(sourceField.getIsUnique())
                    .description(sourceField.getDescription())
                    .sortOrder(sourceField.getSortOrder())
                    .build();
            cloned.getFieldDefinitions().add(clonedField);
        }
        
        return tableDefinitionRepository.save(cloned);
    }
    
    private void cloneTableRelations(List<TableRelation> sourceRelations,
                                     FunctionUnit cloned,
                                     Map<Long, TableDefinition> tableMapping) {
        for (TableRelation sourceRelation : sourceRelations) {
            TableDefinition sourceTable = tableMapping.get(sourceRelation.getSourceTableId());
            TableDefinition targetTable = tableMapping.get(sourceRelation.getTargetTableId());
            if (sourceTable == null || targetTable == null) {
                log.warn("Skipping table relation clone: sourceTableId={}, targetTableId={}",
                        sourceRelation.getSourceTableId(), sourceRelation.getTargetTableId());
                continue;
            }
            TableRelation clonedRelation = TableRelation.builder()
                    .functionUnit(cloned)
                    .sourceTableId(sourceTable.getId())
                    .sourceFieldName(sourceRelation.getSourceFieldName())
                    .relationType(sourceRelation.getRelationType())
                    .targetTableId(targetTable.getId())
                    .targetFieldName(sourceRelation.getTargetFieldName())
                    .build();
            tableRelationRepository.save(clonedRelation);
        }
    }

    private FormDefinition cloneForm(FormDefinition source, FunctionUnit target, Map<Long, TableDefinition> tableMapping) {
        Map<String, Object> configJson = deepCopyMap(source.getConfigJson());
        Map<String, String> fieldPermissions = source.getFieldPermissions() != null
                ? new HashMap<>(source.getFieldPermissions()) : new HashMap<>();

        FormDefinition cloned = FormDefinition.builder()
                .functionUnit(target)
                .formName(source.getFormName())
                .formType(source.getFormType())
                .configJson(configJson != null ? configJson : new HashMap<>())
                .description(source.getDescription())
                .fieldPermissions(fieldPermissions)
                .showLiveValues(source.getShowLiveValues())
                .build();

        if (source.getBoundTable() != null && tableMapping.containsKey(source.getBoundTable().getId())) {
            cloned.setBoundTable(tableMapping.get(source.getBoundTable().getId()));
        }

        FormDefinition savedForm = formDefinitionRepository.save(cloned);

        Map<Long, Long> bindingIdMapping = new HashMap<>();
        List<FormTableBinding> sourceBindings = formTableBindingRepository.findByFormIdWithTable(source.getId());
        for (FormTableBinding sourceBinding : sourceBindings) {
            TableDefinition clonedTable = sourceBinding.getTable() != null
                    ? tableMapping.get(sourceBinding.getTable().getId()) : null;
            FormTableBinding clonedBinding = FormTableBinding.builder()
                    .form(savedForm)
                    .table(clonedTable)
                    .relationTableId(sourceBinding.getRelationTableId())
                    .bindingType(sourceBinding.getBindingType())
                    .bindingMode(sourceBinding.getBindingMode())
                    .foreignKeyField(sourceBinding.getForeignKeyField())
                    .sortOrder(sourceBinding.getSortOrder())
                    .subMode(sourceBinding.getSubMode())
                    .build();
            FormTableBinding savedBinding = formTableBindingRepository.save(clonedBinding);
            bindingIdMapping.put(sourceBinding.getId(), savedBinding.getId());
            cloneSubTableViewConfigIfPresent(sourceBinding, savedBinding);
        }

        for (FormStageBinding sourceStage : formStageBindingRepository.findByFormId(source.getId())) {
            FormStageBinding clonedStage = FormStageBinding.builder()
                    .form(savedForm)
                    .stageId(sourceStage.getStageId())
                    .stageName(sourceStage.getStageName())
                    .readOnly(sourceStage.getReadOnly())
                    .build();
            formStageBindingRepository.save(clonedStage);
        }

        if (configJson != null) {
            FormConfigJsonBindingIdRewriter.remapBindingIds(configJson, bindingIdMapping);
            savedForm.setConfigJson(configJson);
            savedForm = formDefinitionRepository.save(savedForm);
        }

        return savedForm;
    }

    private void cloneSubTableViewConfigIfPresent(FormTableBinding sourceBinding, FormTableBinding savedBinding) {
        subTableViewConfigRepository.findByBindingId(sourceBinding.getId()).ifPresent(sourceConfig -> {
            List<SubTableViewField> copiedFields = new ArrayList<>();
            if (sourceConfig.getViewFields() != null) {
                for (SubTableViewField sourceField : sourceConfig.getViewFields()) {
                    copiedFields.add(SubTableViewField.builder()
                            .fieldName(sourceField.getFieldName())
                            .displayLabel(sourceField.getDisplayLabel())
                            .columnWidth(sourceField.getColumnWidth())
                            .sortOrder(sourceField.getSortOrder())
                            .visible(sourceField.getVisible())
                            .build());
                }
            }
            SubTableViewConfig newConfig = SubTableViewConfig.builder()
                    .binding(savedBinding)
                    .viewFields(new ArrayList<>())
                    .build();
            SubTableViewConfig savedConfig = subTableViewConfigRepository.save(newConfig);
            for (SubTableViewField field : copiedFields) {
                field.setViewConfig(savedConfig);
            }
            savedConfig.setViewFields(copiedFields);
            savedConfig = subTableViewConfigRepository.save(savedConfig);
            savedBinding.setSubListViewId(savedConfig.getId());
            formTableBindingRepository.save(savedBinding);
        });
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
            throw new DeveloperBusinessException("SYS_JSON_ERROR",
                    "Failed to deep copy form configJson: " + e.getMessage());
        }
    }
    
    private ActionDefinition cloneAction(ActionDefinition source, FunctionUnit target) {
        ActionDefinition cloned = ActionDefinition.builder()
                .functionUnit(target)
                .actionName(source.getActionName())
                .actionType(source.getActionType())
                .configJson(new HashMap<>(source.getConfigJson()))
                .icon(source.getIcon())
                .buttonColor(source.getButtonColor())
                .description(source.getDescription())
                .isDefault(source.getIsDefault())
                .build();
        return actionDefinitionRepository.save(cloned);
    }
    
    private void cloneDecision(DecisionDefinition source, FunctionUnit target) {
        DecisionDefinition cloned = DecisionDefinition.builder()
                .functionUnit(target)
                .decisionKey(source.getDecisionKey())
                .decisionName(source.getDecisionName())
                .dmnXml(source.getDmnXml())
                .hitPolicy(source.getHitPolicy())
                .description(source.getDescription())
                .build();
        decisionDefinitionRepository.save(cloned);
    }
}
