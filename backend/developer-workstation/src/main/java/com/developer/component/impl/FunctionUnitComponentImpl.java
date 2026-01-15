package com.developer.component.impl;

import com.developer.component.FunctionUnitComponent;
import com.developer.dto.FunctionUnitRequest;
import com.developer.dto.FunctionUnitResponse;
import com.developer.dto.ValidationResult;
import com.developer.entity.*;
import com.developer.enums.FunctionUnitStatus;
import com.developer.exception.BusinessException;
import com.developer.exception.ResourceNotFoundException;
import com.developer.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 功能单元组件实现
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FunctionUnitComponentImpl implements FunctionUnitComponent {
    
    private final FunctionUnitRepository functionUnitRepository;
    private final ProcessDefinitionRepository processDefinitionRepository;
    private final TableDefinitionRepository tableDefinitionRepository;
    private final FormDefinitionRepository formDefinitionRepository;
    private final ActionDefinitionRepository actionDefinitionRepository;
    private final VersionRepository versionRepository;
    private final IconRepository iconRepository;
    private final ObjectMapper objectMapper;
    
    @Override
    @Transactional
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
            
            if (name != null && !name.trim().isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + name.trim().toLowerCase() + "%"));
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
        
        // 创建版本快照
        try {
            byte[] snapshotData = createSnapshot(functionUnit);
            Version version = Version.builder()
                    .functionUnit(functionUnit)
                    .versionNumber(newVersion)
                    .changeLog(changeLog)
                    .snapshotData(snapshotData)
                    .publishedBy("system") // TODO: 从安全上下文获取
                    .build();
            versionRepository.save(version);
        } catch (Exception e) {
            throw new BusinessException("SYS_SNAPSHOT_ERROR", "创建版本快照失败");
        }
        
        // 更新功能单元状态
        functionUnit.setStatus(FunctionUnitStatus.PUBLISHED);
        functionUnit.setCurrentVersion(newVersion);
        
        return functionUnitRepository.save(functionUnit);
    }
    
    @Override
    @Transactional
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
        
        // 克隆表单定义
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
        String[] parts = currentVersion.split("\\.");
        int patch = Integer.parseInt(parts[2]) + 1;
        return parts[0] + "." + parts[1] + "." + patch;
    }
    
    private byte[] createSnapshot(FunctionUnit functionUnit) throws Exception {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("name", functionUnit.getName());
        snapshot.put("description", functionUnit.getDescription());
        snapshot.put("processXml", functionUnit.getProcessDefinition() != null ? 
                functionUnit.getProcessDefinition().getBpmnXml() : null);
        // 添加更多快照数据...
        return objectMapper.writeValueAsBytes(snapshot);
    }
    
    private TableDefinition cloneTable(TableDefinition source, FunctionUnit target) {
        TableDefinition cloned = TableDefinition.builder()
                .functionUnit(target)
                .tableName(source.getTableName())
                .tableType(source.getTableType())
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
                .configJson(new HashMap<>(source.getConfigJson()))
                .description(source.getDescription())
                .build();
        
        if (source.getBoundTable() != null && tableMapping.containsKey(source.getBoundTable().getId())) {
            cloned.setBoundTable(tableMapping.get(source.getBoundTable().getId()));
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
