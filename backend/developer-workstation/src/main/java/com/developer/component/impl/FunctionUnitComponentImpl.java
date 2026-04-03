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
import com.developer.util.XmlEncodingUtil;
import com.developer.service.UserDisplayNameService;
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
 * 功能单元组件实现
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
    private final VersionRepository versionRepository;
    private final IconRepository iconRepository;
    private final ObjectMapper objectMapper;
    private final UserDisplayNameService userDisplayNameService;
    private final FunctionUnitWorkspaceAccessService functionUnitWorkspaceAccessService;
    private final FunctionUnitDevGroupAssignmentRepository functionUnitDevGroupAssignmentRepository;
    
    public FunctionUnitComponentImpl(
            FunctionUnitRepository functionUnitRepository,
            ProcessDefinitionRepository processDefinitionRepository,
            TableDefinitionRepository tableDefinitionRepository,
            FormDefinitionRepository formDefinitionRepository,
            ActionDefinitionRepository actionDefinitionRepository,
            DecisionDefinitionRepository decisionDefinitionRepository,
            VersionRepository versionRepository,
            IconRepository iconRepository,
            ObjectMapper objectMapper,
            UserDisplayNameService userDisplayNameService,
            FunctionUnitWorkspaceAccessService functionUnitWorkspaceAccessService,
            FunctionUnitDevGroupAssignmentRepository functionUnitDevGroupAssignmentRepository) {
        this.functionUnitRepository = functionUnitRepository;
        this.processDefinitionRepository = processDefinitionRepository;
        this.tableDefinitionRepository = tableDefinitionRepository;
        this.formDefinitionRepository = formDefinitionRepository;
        this.actionDefinitionRepository = actionDefinitionRepository;
        this.decisionDefinitionRepository = decisionDefinitionRepository;
        this.versionRepository = versionRepository;
        this.iconRepository = iconRepository;
        this.objectMapper = objectMapper;
        this.userDisplayNameService = userDisplayNameService;
        this.functionUnitWorkspaceAccessService = functionUnitWorkspaceAccessService;
        this.functionUnitDevGroupAssignmentRepository = functionUnitDevGroupAssignmentRepository;
    }
    
    /**
     * 获取当前操作者
     * 优先从 Spring Security Context 获取，如果无法获取则返回 "system"
     * 
     * 返回 "system" 的情况：
     * - 没有认证信息（未登录）
     * - 匿名用户
     * - 系统后台任务
     * - 获取过程中发生异常
     * 
     * @return 当前操作者用户名，如果无法获取则返回 "system"
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
        
        // 生成唯一编码
        String code = generateUniqueCode();
        
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
        
        return functionUnitRepository.save(functionUnit);
    }
    
    /**
     * 生成唯一的功能单元编码
     * 格式：fu-{yyyyMMdd}-{random6chars}
     */
    private String generateUniqueCode() {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        SecureRandom random = new SecureRandom();
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder randomPart = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                randomPart.append(chars.charAt(random.nextInt(chars.length())));
            }
            String code = "fu-" + datePart + "-" + randomPart;
            if (!functionUnitRepository.existsByCode(code)) {
                return code;
            }
        }
        // 极端情况下使用时间戳
        return "fu-" + datePart + "-" + System.currentTimeMillis() % 1000000;
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
        functionUnitRepository.delete(functionUnit);
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
        
        // 使用 Specification 查询，但需要手动处理关联加载
        // 由于 Specification 不支持 EntityGraph，我们在 toResponse 中安全处理懒加载
        Page<FunctionUnit> page = functionUnitRepository.findAll(spec, pageable);
        
        // 在事务内触发懒加载，确保所有关联数据都被加载
        page.getContent().forEach(entity -> {
            try {
                // 触发懒加载
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
        
        // 验证功能单元完整性
        ValidationResult validationResult = validate(id);
        if (!validationResult.isValid()) {
            throw new DeveloperBusinessException("BIZ_INVALID_FUNCTION_UNIT", 
                    "Function unit validation failed, cannot publish",
                    "Please fix validation errors before retrying");
        }
        
        // 计算新版本号
        String newVersion = calculateNextVersion(functionUnit.getCurrentVersion());
        
        // 检查版本号是否已存在，避免唯一约束冲突
        boolean versionAlreadyExists = versionRepository.findByFunctionUnitIdAndVersionNumber(id, newVersion).isPresent();
        if (versionAlreadyExists) {
            // 版本快照已存在但 currentVersion 尚未更新，说明上次 deploy 中途失败，允许继续完成状态更新
            log.warn("Version snapshot {} already exists but function unit status not updated, continuing publish flow, functionUnitId={}", newVersion, id);
        } else {
            // 创建版本快照
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
        
        // 更新功能单元状态
        functionUnit.setStatus(FunctionUnitStatus.PUBLISHED);
        functionUnit.setCurrentVersion(newVersion);
        
        return functionUnitRepository.save(functionUnit);
    }
    
    @Override
    @Transactional
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('TECH_LEAD', 'TEAM_LEAD')")
    public FunctionUnit clone(Long id, String newName) {
        functionUnitWorkspaceAccessService.assertCanAccess(id, WorkspaceAccessAction.MODIFY);
        if (functionUnitRepository.existsByName(newName)) {
            throw new DeveloperBusinessException("CONFLICT_NAME_EXISTS", 
                    "Function unit name already exists: " + newName,
                    "Please use a different name");
        }
        
        FunctionUnit source = getById(id);
        
        // 创建新的功能单元（生成新的唯一编码）
        FunctionUnit cloned = FunctionUnit.builder()
                .name(newName)
                .code(generateUniqueCode())
                .description(source.getDescription())
                .icon(source.getIcon())
                .status(FunctionUnitStatus.DRAFT)
                .build();
        cloned = functionUnitRepository.save(cloned);
        
        // 克隆流程定义
        if (source.getProcessDefinition() != null) {
            ProcessDefinition clonedProcess = ProcessDefinition.builder()
                    .functionUnit(cloned)
                    .bpmnXml(source.getProcessDefinition().getBpmnXml())
                    .build();
            processDefinitionRepository.save(clonedProcess);
        }
        
        // 克隆表定义
        Map<Long, TableDefinition> tableMapping = new HashMap<>();
        for (TableDefinition sourceTable : source.getTableDefinitions()) {
            TableDefinition clonedTable = cloneTable(sourceTable, cloned);
            tableMapping.put(sourceTable.getId(), clonedTable);
        }
        
        // 克隆外键关系（需要在所有表克隆完成后处理，因为外键可能跨表引用）
        Map<Long, Map<String, FieldDefinition>> clonedFieldLookup = new HashMap<>();
        for (Map.Entry<Long, TableDefinition> entry : tableMapping.entrySet()) {
            Map<String, FieldDefinition> fieldMap = new HashMap<>();
            for (FieldDefinition field : entry.getValue().getFieldDefinitions()) {
                fieldMap.put(field.getFieldName(), field);
            }
            clonedFieldLookup.put(entry.getKey(), fieldMap);
        }
        for (TableDefinition sourceTable : source.getTableDefinitions()) {
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
        
        // 克隆表单定义（包含 TableBindings）
        for (FormDefinition sourceForm : source.getFormDefinitions()) {
            cloneForm(sourceForm, cloned, tableMapping);
        }
        
        // 克隆动作定义
        for (ActionDefinition sourceAction : source.getActionDefinitions()) {
            cloneAction(sourceAction, cloned);
        }
        
        // 克隆决策定义
        for (DecisionDefinition sourceDecision : source.getDecisionDefinitions()) {
            cloneDecision(sourceDecision, cloned);
        }
        
        return cloned;
    }
    
    @Override
    @Transactional(readOnly = true)
    public ValidationResult validate(Long id) {
        functionUnitWorkspaceAccessService.assertCanAccess(id, WorkspaceAccessAction.VIEW);
        FunctionUnit functionUnit = getById(id);
        ValidationResult result = new ValidationResult();
        
        // 检查是否有流程定义
        if (functionUnit.getProcessDefinition() == null) {
            result.addWarning("MISSING_PROCESS", "Function unit has no process definition", null);
        }
        
        // 检查是否有主表
        boolean hasMainTable = functionUnit.getTableDefinitions().stream()
                .anyMatch(t -> t.getTableType() == com.developer.enums.TableType.MAIN);
        if (!hasMainTable) {
            result.addWarning("MISSING_MAIN_TABLE", "Function unit has no main table", null);
        }
        
        // 检查是否有流程表单
        boolean hasProcessForm = functionUnit.getFormDefinitions().stream()
                .anyMatch(f -> f.getFormType() == com.developer.enums.FormType.PROCESS);
        if (!hasProcessForm) {
            result.addWarning("MISSING_PROCESS_FORM", "Function unit has no process form", null);
        }
        
        // BPMN-DMN 交叉引用验证
        validateBpmnDmnCrossReferences(functionUnit, result);
        
        // DECISION_TABLE 动作配置验证
        validateDecisionTableActions(functionUnit, result);
        
        return result;
    }
    
    /**
     * BPMN-DMN 交叉引用验证
     * 检查 BPMN 流程中引用的决策键是否存在于同一功能单元的决策定义中
     */
    private void validateBpmnDmnCrossReferences(FunctionUnit functionUnit, ValidationResult result) {
        List<DecisionDefinition> decisions = functionUnit.getDecisionDefinitions();
        
        // 无 DecisionDefinition 时不产生决策相关错误
        if (decisions == null || decisions.isEmpty()) {
            return;
        }
        
        // 如果没有流程定义，无法进行交叉引用验证
        if (functionUnit.getProcessDefinition() == null) {
            return;
        }
        
        String bpmnXml = functionUnit.getProcessDefinition().getBpmnXml();
        if (bpmnXml == null || bpmnXml.isBlank()) {
            return;
        }
        
        // 解码 BPMN XML（可能是 Base64 编码）
        String decodedBpmnXml = XmlEncodingUtil.smartDecode(bpmnXml);
        
        // 从 BPMN XML 中提取所有 DMN 服务任务引用的决策键
        Set<String> referencedKeys = extractDmnReferenceKeys(decodedBpmnXml, functionUnit.getId());
        
        // 构建已有决策定义的 key 集合
        Set<String> definedKeys = new HashSet<>();
        for (DecisionDefinition decision : decisions) {
            definedKeys.add(decision.getDecisionKey());
        }
        
        // 检查 BPMN 引用的决策键是否存在于 DecisionDefinition 列表中
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
        
        // 检查是否有未被 BPMN 引用的 DecisionDefinition
        for (String definedKey : definedKeys) {
            if (!referencedKeys.contains(definedKey)) {
                result.addWarning("UNREFERENCED_DECISION",
                        "Decision definition '" + definedKey + "' is not referenced by any BPMN service task",
                        definedKey);
            }
        }
    }
    
    /**
     * DECISION_TABLE 动作配置验证
     * 当 ActionType 为 DECISION_TABLE 时，校验 config_json 包含 decisionKey、inputMappings、outputMappings，
     * 并校验 decisionKey 引用同一功能单元内存在的 DecisionDefinition。
     */
    private void validateDecisionTableActions(FunctionUnit functionUnit, ValidationResult result) {
        List<ActionDefinition> actions = functionUnit.getActionDefinitions();
        if (actions == null || actions.isEmpty()) {
            return;
        }
        
        // 构建已有决策定义的 key 集合
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
            
            // 校验必填字段: decisionKey
            Object decisionKeyObj = config.get("decisionKey");
            boolean hasDecisionKey = decisionKeyObj instanceof String dk && !dk.isBlank();
            if (!hasDecisionKey) {
                result.addError("MISSING_DECISION_KEY",
                        "DECISION_TABLE action '" + actionName + "' config_json is missing required field 'decisionKey'",
                        actionName);
            }
            
            // 校验必填字段: inputMappings
            if (!config.containsKey("inputMappings")) {
                result.addError("MISSING_INPUT_MAPPINGS",
                        "DECISION_TABLE action '" + actionName + "' config_json is missing required field 'inputMappings'",
                        actionName);
            }
            
            // 校验必填字段: outputMappings
            if (!config.containsKey("outputMappings")) {
                result.addError("MISSING_OUTPUT_MAPPINGS",
                        "DECISION_TABLE action '" + actionName + "' config_json is missing required field 'outputMappings'",
                        actionName);
            }
            
            // 校验 decisionKey 引用同一功能单元内存在的 DecisionDefinition
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
     * 从 BPMN XML 中提取所有 DMN 服务任务引用的 decisionTableReferenceKey
     * 支持两种格式:
     * 1. 属性格式: flowable:decisionTableReferenceKey="key"
     * 2. 扩展元素格式: flowable:field name="decisionTableReferenceKey" > flowable:string
     */
    private Set<String> extractDmnReferenceKeys(String bpmnXml, Long functionUnitId) {
        Set<String> keys = new HashSet<>();
        try {
            Document document = parseXmlSecurely(bpmnXml);
            
            // 查找所有 serviceTask 元素
            NodeList serviceTasks = document.getElementsByTagNameNS("*", "serviceTask");
            for (int i = 0; i < serviceTasks.getLength(); i++) {
                Element serviceTask = (Element) serviceTasks.item(i);
                
                // 检查是否为 DMN 类型的服务任务 (flowable:type="dmn")
                if (!isDmnServiceTask(serviceTask)) {
                    continue;
                }
                
                // 尝试从属性提取 decisionTableReferenceKey
                String key = extractKeyFromAttribute(serviceTask);
                if (key == null || key.isBlank()) {
                    // 尝试从扩展元素提取
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
     * 检查 serviceTask 元素是否为 DMN 类型
     */
    private boolean isDmnServiceTask(Element serviceTask) {
        // 检查所有可能的命名空间前缀下的 type 属性
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
     * 从 serviceTask 属性中提取 decisionTableReferenceKey
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
     * 从扩展元素中提取 decisionTableReferenceKey
     * 格式: <flowable:field name="decisionTableReferenceKey"><flowable:string>key</flowable:string></flowable:field>
     */
    private String extractKeyFromExtensionElements(Element serviceTask) {
        NodeList extensionElements = serviceTask.getElementsByTagNameNS("*", "extensionElements");
        for (int i = 0; i < extensionElements.getLength(); i++) {
            Element extElem = (Element) extensionElements.item(i);
            NodeList fields = extElem.getElementsByTagNameNS("*", "field");
            for (int j = 0; j < fields.getLength(); j++) {
                Element field = (Element) fields.item(j);
                if ("decisionTableReferenceKey".equals(field.getAttribute("name"))) {
                    // 尝试从 flowable:string 子元素获取值
                    NodeList stringElements = field.getElementsByTagNameNS("*", "string");
                    if (stringElements.getLength() > 0) {
                        return stringElements.item(0).getTextContent().trim();
                    }
                    // 尝试从 flowable:expression 子元素获取值
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
     * XXE 安全的 XML 解析
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
        return versionRepository.findByFunctionUnitIdOrderByPublishedAtDesc(functionUnitId)
                .stream()
                .map(v -> {
                    VersionResponse resp = VersionResponse.from(v);
                    resp.setCreatedBy(resolveUserDisplayName(v.getPublishedBy()));
                    return resp;
                })
                .toList();
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
        
        // 安全地获取集合大小，避免 LazyInitializationException
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
        
        // 克隆字段
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
    
    private void cloneForm(FormDefinition source, FunctionUnit target, Map<Long, TableDefinition> tableMapping) {
        FormDefinition cloned = FormDefinition.builder()
                .functionUnit(target)
                .formName(source.getFormName())
                .formType(source.getFormType())
                .configJson(source.getConfigJson() != null ? new HashMap<>(source.getConfigJson()) : new HashMap<>())
                .description(source.getDescription())
                .build();
        
        if (source.getBoundTable() != null && tableMapping.containsKey(source.getBoundTable().getId())) {
            cloned.setBoundTable(tableMapping.get(source.getBoundTable().getId()));
        }
        
        // 克隆 FormTableBindings
        if (source.getTableBindings() != null) {
            for (FormTableBinding sourceBinding : source.getTableBindings()) {
                TableDefinition clonedTable = sourceBinding.getTable() != null
                        ? tableMapping.get(sourceBinding.getTable().getId()) : null;
                FormTableBinding clonedBinding = FormTableBinding.builder()
                        .form(cloned)
                        .table(clonedTable)
                        .bindingType(sourceBinding.getBindingType())
                        .bindingMode(sourceBinding.getBindingMode())
                        .foreignKeyField(sourceBinding.getForeignKeyField())
                        .sortOrder(sourceBinding.getSortOrder())
                        .build();
                cloned.getTableBindings().add(clonedBinding);
            }
        }
        
        formDefinitionRepository.save(cloned);
    }
    
    private void cloneAction(ActionDefinition source, FunctionUnit target) {
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
        actionDefinitionRepository.save(cloned);
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
