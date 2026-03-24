package com.developer.component.impl;

import com.developer.component.VersionComponent;
import com.developer.entity.*;
import com.developer.enums.ActionType;
import com.developer.enums.DataType;
import com.developer.enums.FormType;
import com.developer.enums.FunctionUnitStatus;
import com.developer.enums.TableType;
import com.developer.exception.BusinessException;
import com.developer.exception.ResourceNotFoundException;
import com.developer.repository.FunctionUnitRepository;
import com.developer.repository.VersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 版本管理组件实现
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class VersionComponentImpl implements VersionComponent {
    
    private final VersionRepository versionRepository;
    private final FunctionUnitRepository functionUnitRepository;
    private final ObjectMapper objectMapper;
    
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
    public Version createVersion(Long functionUnitId, String changeLog) {
        FunctionUnit functionUnit = functionUnitRepository.findById(functionUnitId)
                .orElseThrow(() -> new ResourceNotFoundException("FunctionUnit", functionUnitId));
        
        String newVersion = calculateNextVersion(functionUnit.getCurrentVersion());
        
        try {
            byte[] snapshotData = createSnapshot(functionUnit);
            
            Version version = Version.builder()
                    .functionUnit(functionUnit)
                    .versionNumber(newVersion)
                    .changeLog(changeLog)
                    .snapshotData(snapshotData)
                    .publishedBy(getCurrentOperator())
                    .build();
            
            version = versionRepository.save(version);
            
            functionUnit.setCurrentVersion(newVersion);
            functionUnitRepository.save(functionUnit);
            
            return version;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to create version snapshot, functionUnitId={}, version={}: {}", functionUnitId, newVersion, e.getMessage(), e);
            throw new BusinessException("SYS_SNAPSHOT_ERROR", "Failed to create version snapshot: " + e.getMessage());
        }
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<Version> getVersionHistory(Long functionUnitId) {
        return versionRepository.findByFunctionUnitIdOrderByPublishedAtDesc(functionUnitId);
    }
    
    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> compare(Long versionId1, Long versionId2) {
        Version version1 = getById(versionId1);
        Version version2 = getById(versionId2);
        
        Map<String, Object> result = new HashMap<>();
        result.put("version1", Map.of(
                "id", version1.getId(),
                "versionNumber", version1.getVersionNumber(),
                "publishedAt", version1.getPublishedAt()
        ));
        result.put("version2", Map.of(
                "id", version2.getId(),
                "versionNumber", version2.getVersionNumber(),
                "publishedAt", version2.getPublishedAt()
        ));
        
        try {
            Map<String, Object> snapshot1 = objectMapper.readValue(version1.getSnapshotData(), Map.class);
            Map<String, Object> snapshot2 = objectMapper.readValue(version2.getSnapshotData(), Map.class);
            
            Map<String, Object> differences = findDifferences(snapshot1, snapshot2);
            result.put("differences", differences);
        } catch (Exception e) {
            log.error("Failed to compare versions", e);
            result.put("differences", Map.of("error", "Failed to compare versions"));
        }
        
        return result;
    }
    
    @Override
    @Transactional
    public FunctionUnit rollback(Long functionUnitId, Long versionId) {
        FunctionUnit functionUnit = functionUnitRepository.findById(functionUnitId)
                .orElseThrow(() -> new ResourceNotFoundException("FunctionUnit", functionUnitId));
        
        Version targetVersion = getById(versionId);
        
        if (!targetVersion.getFunctionUnit().getId().equals(functionUnitId)) {
            throw new BusinessException("BIZ_VERSION_MISMATCH", "Version does not belong to this function unit");
        }
        
        try {
            // 先创建当前状态的版本作为备份
            String backupVersion = calculateNextVersion(functionUnit.getCurrentVersion());
            Version backup = Version.builder()
                    .functionUnit(functionUnit)
                    .versionNumber(backupVersion)
                    .changeLog("Auto backup before rollback")
                    .snapshotData(createSnapshot(functionUnit))
                    .publishedBy(getCurrentOperator())
                    .build();
            versionRepository.save(backup);
            
            // 恢复目标版本的内容
            Map<String, Object> snapshot = objectMapper.readValue(targetVersion.getSnapshotData(), Map.class);
            restoreFromSnapshot(functionUnit, snapshot);
            
            // 创建回滚后的新版本
            String newVersion = calculateNextVersion(backupVersion);
            Version rollbackVersion = Version.builder()
                    .functionUnit(functionUnit)
                    .versionNumber(newVersion)
                    .changeLog("Rollback to version " + targetVersion.getVersionNumber())
                    .snapshotData(targetVersion.getSnapshotData())
                    .publishedBy(getCurrentOperator())
                    .build();
            versionRepository.save(rollbackVersion);
            
            functionUnit.setCurrentVersion(newVersion);
            functionUnit.setStatus(FunctionUnitStatus.DRAFT);
            
            return functionUnitRepository.save(functionUnit);
        } catch (Exception e) {
            throw new BusinessException("SYS_ROLLBACK_ERROR", "Rollback failed: " + e.getMessage());
        }
    }
    
    @Override
    @Transactional(readOnly = true)
    public byte[] exportVersion(Long versionId) {
        Version version = getById(versionId);
        return version.getSnapshotData();
    }
    
    @Override
    @Transactional(readOnly = true)
    public Version getById(Long id) {
        return versionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Version", id));
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
        snapshot.put("code", functionUnit.getCode());
        snapshot.put("description", functionUnit.getDescription());
        snapshot.put("status", functionUnit.getStatus() != null ? functionUnit.getStatus().name() : null);
        
        if (functionUnit.getProcessDefinition() != null) {
            snapshot.put("processXml", functionUnit.getProcessDefinition().getBpmnXml());
        }
        
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
    
    @SuppressWarnings("unchecked")
    private void restoreFromSnapshot(FunctionUnit functionUnit, Map<String, Object> snapshot) {
        if (snapshot.containsKey("description")) {
            functionUnit.setDescription((String) snapshot.get("description"));
        }
        
        // Restore process XML
        if (snapshot.containsKey("processXml")) {
            String processXml = (String) snapshot.get("processXml");
            if (processXml != null && functionUnit.getProcessDefinition() != null) {
                functionUnit.getProcessDefinition().setBpmnXml(processXml);
            }
        }
        
        // Restore table definitions
        if (snapshot.containsKey("tableDefinitions")) {
            functionUnit.getTableDefinitions().clear();
            List<Map<String, Object>> tableSnapshots = (List<Map<String, Object>>) snapshot.get("tableDefinitions");
            if (tableSnapshots != null) {
                for (Map<String, Object> tableSnap : tableSnapshots) {
                    TableDefinition table = TableDefinition.builder()
                            .functionUnit(functionUnit)
                            .tableName((String) tableSnap.get("tableName"))
                            .tableType(tableSnap.get("tableType") != null ? TableType.valueOf((String) tableSnap.get("tableType")) : null)
                            .tableDisplayName((String) tableSnap.get("tableDisplayName"))
                            .description((String) tableSnap.get("description"))
                            .build();
                    
                    List<Map<String, Object>> fieldSnapshots = (List<Map<String, Object>>) tableSnap.get("fieldDefinitions");
                    if (fieldSnapshots != null) {
                        for (Map<String, Object> fieldSnap : fieldSnapshots) {
                            FieldDefinition field = FieldDefinition.builder()
                                    .tableDefinition(table)
                                    .fieldName((String) fieldSnap.get("fieldName"))
                                    .dataType(fieldSnap.get("dataType") != null ? DataType.valueOf((String) fieldSnap.get("dataType")) : null)
                                    .length(fieldSnap.get("length") != null ? ((Number) fieldSnap.get("length")).intValue() : null)
                                    .precision(fieldSnap.get("precision") != null ? ((Number) fieldSnap.get("precision")).intValue() : null)
                                    .scale(fieldSnap.get("scale") != null ? ((Number) fieldSnap.get("scale")).intValue() : null)
                                    .nullable(fieldSnap.get("nullable") != null ? (Boolean) fieldSnap.get("nullable") : true)
                                    .defaultValue((String) fieldSnap.get("defaultValue"))
                                    .isPrimaryKey(fieldSnap.get("isPrimaryKey") != null ? (Boolean) fieldSnap.get("isPrimaryKey") : false)
                                    .isUnique(fieldSnap.get("isUnique") != null ? (Boolean) fieldSnap.get("isUnique") : false)
                                    .description((String) fieldSnap.get("description"))
                                    .sortOrder(fieldSnap.get("sortOrder") != null ? ((Number) fieldSnap.get("sortOrder")).intValue() : 0)
                                    .build();
                            table.getFieldDefinitions().add(field);
                        }
                    }
                    functionUnit.getTableDefinitions().add(table);
                }
            }
        }
        
        // Restore form definitions
        if (snapshot.containsKey("formDefinitions")) {
            functionUnit.getFormDefinitions().clear();
            List<Map<String, Object>> formSnapshots = (List<Map<String, Object>>) snapshot.get("formDefinitions");
            if (formSnapshots != null) {
                for (Map<String, Object> formSnap : formSnapshots) {
                    FormDefinition form = FormDefinition.builder()
                            .functionUnit(functionUnit)
                            .formName((String) formSnap.get("formName"))
                            .formType(formSnap.get("formType") != null ? FormType.valueOf((String) formSnap.get("formType")) : null)
                            .configJson(formSnap.get("configJson") != null ? (Map<String, Object>) formSnap.get("configJson") : null)
                            .description((String) formSnap.get("description"))
                            .build();
                    functionUnit.getFormDefinitions().add(form);
                }
            }
        }
        
        // Restore action definitions
        if (snapshot.containsKey("actionDefinitions")) {
            functionUnit.getActionDefinitions().clear();
            List<Map<String, Object>> actionSnapshots = (List<Map<String, Object>>) snapshot.get("actionDefinitions");
            if (actionSnapshots != null) {
                for (Map<String, Object> actionSnap : actionSnapshots) {
                    ActionDefinition action = ActionDefinition.builder()
                            .functionUnit(functionUnit)
                            .actionName((String) actionSnap.get("actionName"))
                            .actionType(actionSnap.get("actionType") != null ? ActionType.valueOf((String) actionSnap.get("actionType")) : null)
                            .configJson(actionSnap.get("configJson") != null ? (Map<String, Object>) actionSnap.get("configJson") : null)
                            .icon((String) actionSnap.get("icon"))
                            .buttonColor((String) actionSnap.get("buttonColor"))
                            .description((String) actionSnap.get("description"))
                            .isDefault(actionSnap.get("isDefault") != null ? (Boolean) actionSnap.get("isDefault") : false)
                            .build();
                    functionUnit.getActionDefinitions().add(action);
                }
            }
        }
        
        // Restore decision definitions
        if (snapshot.containsKey("decisionDefinitions")) {
            functionUnit.getDecisionDefinitions().clear();
            List<Map<String, Object>> decisionSnapshots = (List<Map<String, Object>>) snapshot.get("decisionDefinitions");
            if (decisionSnapshots != null) {
                for (Map<String, Object> decisionSnap : decisionSnapshots) {
                    DecisionDefinition decision = DecisionDefinition.builder()
                            .functionUnit(functionUnit)
                            .decisionKey((String) decisionSnap.get("decisionKey"))
                            .decisionName((String) decisionSnap.get("decisionName"))
                            .dmnXml((String) decisionSnap.get("dmnXml"))
                            .hitPolicy((String) decisionSnap.get("hitPolicy"))
                            .description((String) decisionSnap.get("description"))
                            .build();
                    functionUnit.getDecisionDefinitions().add(decision);
                }
            }
        }
    }
    
    private Map<String, Object> findDifferences(Map<String, Object> map1, Map<String, Object> map2) {
        Map<String, Object> differences = new HashMap<>();
        
        for (String key : map1.keySet()) {
            Object value1 = map1.get(key);
            Object value2 = map2.get(key);
            
            if (value2 == null) {
                differences.put(key, Map.of("type", "removed", "oldValue", value1));
            } else if (!value1.equals(value2)) {
                differences.put(key, Map.of("type", "modified", "oldValue", value1, "newValue", value2));
            }
        }
        
        for (String key : map2.keySet()) {
            if (!map1.containsKey(key)) {
                differences.put(key, Map.of("type", "added", "newValue", map2.get(key)));
            }
        }
        
        return differences;
    }
    
}
