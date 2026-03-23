package com.developer.component.impl;

import com.developer.component.FunctionUnitComponent;
import com.developer.dto.FunctionUnitRequest;
import com.developer.dto.FunctionUnitResponse;
import com.developer.dto.ValidationResult;
import com.developer.dto.VersionResponse;
import com.developer.entity.*;
import com.developer.enums.FunctionUnitStatus;
import com.developer.exception.BusinessException;
import com.developer.exception.ResourceNotFoundException;
import com.developer.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.security.SecureRandom;
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
    private final VersionRepository versionRepository;
    private final IconRepository iconRepository;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    
    @Value("${admin-center.url:http://localhost:8090}")
    private String adminCenterUrl;
    
    /** Bounded LRU cache for user display names (max 200 entries) */
    private final Map<String, String> userNameCache = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > 200;
                }
            });
    
    public FunctionUnitComponentImpl(
            FunctionUnitRepository functionUnitRepository,
            ProcessDefinitionRepository processDefinitionRepository,
            TableDefinitionRepository tableDefinitionRepository,
            FormDefinitionRepository formDefinitionRepository,
            ActionDefinitionRepository actionDefinitionRepository,
            VersionRepository versionRepository,
            IconRepository iconRepository,
            ObjectMapper objectMapper,
            RestTemplate restTemplate) {
        this.functionUnitRepository = functionUnitRepository;
        this.processDefinitionRepository = processDefinitionRepository;
        this.tableDefinitionRepository = tableDefinitionRepository;
        this.formDefinitionRepository = formDefinitionRepository;
        this.actionDefinitionRepository = actionDefinitionRepository;
        this.versionRepository = versionRepository;
        this.iconRepository = iconRepository;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
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
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null 
                    && authentication.isAuthenticated() 
                    && !(authentication instanceof AnonymousAuthenticationToken)) {
                String username = authentication.getName();
                if (username != null && !username.isEmpty()) {
                    return username;
                }
            }
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
            throw new BusinessException("CONFLICT_NAME_EXISTS", 
                    "功能单元名称已存在: " + request.getName(),
                    "请使用其他名称");
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
        FunctionUnit functionUnit = getById(id);
        
        if (functionUnitRepository.existsByNameAndIdNot(request.getName(), id)) {
            throw new BusinessException("CONFLICT_NAME_EXISTS", 
                    "功能单元名称已存在: " + request.getName(),
                    "请使用其他名称");
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
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('TECH_LEAD')")
    public void delete(Long id) {
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
        FunctionUnit functionUnit = getById(id);
        
        // 验证功能单元完整性
        ValidationResult validationResult = validate(id);
        if (!validationResult.isValid()) {
            throw new BusinessException("BIZ_INVALID_FUNCTION_UNIT", 
                    "功能单元验证失败，无法发布",
                    "请修复验证错误后重试");
        }
        
        // 计算新版本号
        String newVersion = calculateNextVersion(functionUnit.getCurrentVersion());
        
        // 检查版本号是否已存在，避免唯一约束冲突
        boolean versionAlreadyExists = versionRepository.findByFunctionUnitIdAndVersionNumber(id, newVersion).isPresent();
        if (versionAlreadyExists) {
            // 版本快照已存在但 currentVersion 尚未更新，说明上次 deploy 中途失败，允许继续完成状态更新
            log.warn("版本快照 {} 已存在但功能单元状态未更新，继续完成发布流程，functionUnitId={}", newVersion, id);
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
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                log.error("创建版本快照失败，functionUnitId={}, version={}: {}", id, newVersion, e.getMessage(), e);
                throw new BusinessException("SYS_SNAPSHOT_ERROR", "创建版本快照失败: " + e.getMessage());
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
        if (functionUnitRepository.existsByName(newName)) {
            throw new BusinessException("CONFLICT_NAME_EXISTS", 
                    "功能单元名称已存在: " + newName,
                    "请使用其他名称");
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
        
        return cloned;
    }
    
    @Override
    @Transactional(readOnly = true)
    public ValidationResult validate(Long id) {
        FunctionUnit functionUnit = getById(id);
        ValidationResult result = new ValidationResult();
        
        // 检查是否有流程定义
        if (functionUnit.getProcessDefinition() == null) {
            result.addWarning("MISSING_PROCESS", "功能单元没有流程定义", null);
        }
        
        // 检查是否有主表
        boolean hasMainTable = functionUnit.getTableDefinitions().stream()
                .anyMatch(t -> t.getTableType() == com.developer.enums.TableType.MAIN);
        if (!hasMainTable) {
            result.addWarning("MISSING_MAIN_TABLE", "功能单元没有主表", null);
        }
        
        // 检查是否有主表单
        boolean hasMainForm = functionUnit.getFormDefinitions().stream()
                .anyMatch(f -> f.getFormType() == com.developer.enums.FormType.MAIN);
        if (!hasMainForm) {
            result.addWarning("MISSING_MAIN_FORM", "功能单元没有主表单", null);
        }
        
        return result;
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
        return versionRepository.findByFunctionUnitIdOrderByPublishedAtDesc(functionUnitId)
                .stream()
                .map(v -> {
                    VersionResponse resp = VersionResponse.from(v);
                    resp.setCreatedBy(resolveUserDisplayName(v.getPublishedBy()));
                    return resp;
                })
                .toList();
    }
    
    @SuppressWarnings("unchecked")
    private String resolveUserDisplayName(String userId) {
        if (userId == null || userId.isEmpty()) {
            return null;
        }
        String cached = userNameCache.get(userId);
        if (cached != null) {
            return cached;
        }
        try {
            String url = adminCenterUrl + "/api/v1/admin/users/" + userId;
            Map<String, Object> userInfo = restTemplate.getForObject(url, Map.class);
            if (userInfo != null) {
                String displayName = extractDisplayName(userInfo);
                if (displayName != null) {
                    userNameCache.put(userId, displayName);
                    return displayName;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to resolve user display name for {}: {}", userId, e.getMessage());
        }
        userNameCache.put(userId, userId);
        return userId;
    }
    
    private String extractDisplayName(Map<String, Object> userInfo) {
        String fullName = (String) userInfo.get("fullName");
        if (fullName != null && !fullName.isEmpty()) return fullName;
        String displayName = (String) userInfo.get("displayName");
        if (displayName != null && !displayName.isEmpty()) return displayName;
        String username = (String) userInfo.get("username");
        if (username != null && !username.isEmpty()) return username;
        return null;
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
                .hasProcess(hasProcess)
                .build();
    }
    
    private String calculateNextVersion(String currentVersion) {
        if (currentVersion == null || currentVersion.isEmpty()) {
            return "1.0.0";
        }
        try {
            String[] parts = currentVersion.split("\\.");
            if (parts.length != 3) {
                log.warn("版本号格式异常 '{}', 回退到 1.0.0", currentVersion);
                return "1.0.0";
            }
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            int patch = Integer.parseInt(parts[2]) + 1;
            return major + "." + minor + "." + patch;
        } catch (NumberFormatException e) {
            log.warn("版本号解析失败 '{}': {}, 回退到 1.0.0", currentVersion, e.getMessage());
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
}
