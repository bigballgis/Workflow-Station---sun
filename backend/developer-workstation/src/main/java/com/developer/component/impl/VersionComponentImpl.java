package com.developer.component.impl;

import com.developer.component.VersionComponent;
import com.developer.entity.FunctionUnit;
import com.developer.entity.Version;
import com.developer.enums.FunctionUnitStatus;
import com.developer.exception.BusinessException;
import com.developer.exception.ResourceNotFoundException;
import com.developer.repository.FunctionUnitRepository;
import com.developer.repository.VersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 版本管理组件实现
 */
@Component
@Slf4j
public class VersionComponentImpl implements VersionComponent {
    
    private final VersionRepository versionRepository;
    private final FunctionUnitRepository functionUnitRepository;
    private final ObjectMapper objectMapper;
    
    /**
     * 简化构造函数，用于测试
     */
    public VersionComponentImpl(VersionRepository versionRepository) {
        this(versionRepository, null, new ObjectMapper());
    }
    
    /**
     * 完整构造函数
     */
    public VersionComponentImpl(
            VersionRepository versionRepository,
            FunctionUnitRepository functionUnitRepository,
            ObjectMapper objectMapper) {
        this.versionRepository = versionRepository;
        this.functionUnitRepository = functionUnitRepository;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
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
                    .publishedBy("system") // TODO: 从安全上下文获取
                    .build();
            
            version = versionRepository.save(version);
            
            functionUnit.setCurrentVersion(newVersion);
            functionUnitRepository.save(functionUnit);
            
            return version;
        } catch (Exception e) {
            throw new BusinessException("SYS_SNAPSHOT_ERROR", "创建版本快照失败");
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
            result.put("differences", Map.of("error", "无法比较版本"));
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
            throw new BusinessException("BIZ_VERSION_MISMATCH", "版本不属于该功能单元");
        }
        
        try {
            // 先创建当前状态的版本作为备份
            String backupVersion = calculateNextVersion(functionUnit.getCurrentVersion());
            Version backup = Version.builder()
                    .functionUnit(functionUnit)
                    .versionNumber(backupVersion)
                    .changeLog("回滚前自动备份")
                    .snapshotData(createSnapshot(functionUnit))
                    .publishedBy("system")
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
                    .changeLog("回滚到版本 " + targetVersion.getVersionNumber())
                    .snapshotData(targetVersion.getSnapshotData())
                    .publishedBy("system")
                    .build();
            versionRepository.save(rollbackVersion);
            
            functionUnit.setCurrentVersion(newVersion);
            functionUnit.setStatus(FunctionUnitStatus.DRAFT);
            
            return functionUnitRepository.save(functionUnit);
        } catch (Exception e) {
            throw new BusinessException("SYS_ROLLBACK_ERROR", "回滚失败: " + e.getMessage());
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
        snapshot.put("description", functionUnit.getDescription());
        snapshot.put("status", functionUnit.getStatus().name());
        
        if (functionUnit.getProcessDefinition() != null) {
            snapshot.put("processXml", functionUnit.getProcessDefinition().getBpmnXml());
        }
        
        // TODO: 添加表、表单、动作的快照
        
        return objectMapper.writeValueAsBytes(snapshot);
    }
    
    private void restoreFromSnapshot(FunctionUnit functionUnit, Map<String, Object> snapshot) {
        if (snapshot.containsKey("description")) {
            functionUnit.setDescription((String) snapshot.get("description"));
        }
        
        // TODO: 恢复表、表单、动作
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
