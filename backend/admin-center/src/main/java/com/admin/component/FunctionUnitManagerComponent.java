package com.admin.component;

import com.admin.dto.request.FunctionUnitImportRequest;
import com.admin.dto.response.FunctionUnitInfo;
import com.admin.dto.response.ImportResult;
import com.admin.dto.response.ValidationResult;
import com.admin.entity.FunctionUnit;
import com.admin.entity.FunctionUnitContent;
import com.admin.entity.FunctionUnitDependency;
import com.admin.enums.ContentType;
import com.admin.enums.DependencyType;
import com.admin.enums.FunctionUnitStatus;
import com.admin.exception.AdminBusinessException;
import com.admin.exception.FunctionUnitNotFoundException;
import com.admin.exception.InvalidPackageException;
import com.admin.repository.FunctionUnitContentRepository;
import com.admin.repository.FunctionUnitDependencyRepository;
import com.admin.repository.FunctionUnitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 功能单元管理组件
 * 负责功能包的导入、验证、依赖检测和管理
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FunctionUnitManagerComponent {
    
    private final FunctionUnitRepository functionUnitRepository;
    private final FunctionUnitDependencyRepository dependencyRepository;
    private final FunctionUnitContentRepository contentRepository;
    
    // 版本号正则表达式（语义化版本）
    private static final Pattern VERSION_PATTERN = Pattern.compile("^\\d+\\.\\d+\\.\\d+(-[a-zA-Z0-9]+)?$");
    
    // 代码正则表达式
    private static final Pattern CODE_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_-]{2,49}$");
    
    /**
     * 导入功能包
     */
    @Transactional
    public ImportResult importFunctionPackage(FunctionUnitImportRequest request, String importerId) {
        log.info("Importing function package: {}", request.getFileName());
        
        try {
            // 1. 验证文件格式和完整性
            ValidationResult validationResult = validatePackage(request);
            if (!validationResult.isValid()) {
                return ImportResult.validationFailed(validationResult.getErrors());
            }
            
            // 2. 解析功能包内容
            FunctionPackageContent packageContent = parsePackageContent(request);
            
            // 3. 检查版本是否已存在
            if (functionUnitRepository.existsByCodeAndVersion(packageContent.getCode(), packageContent.getVersion())) {
                if (!request.isOverwrite()) {
                    return ImportResult.failure("功能单元版本已存在: " + packageContent.getCode() + ":" + packageContent.getVersion());
                }
                // 删除已存在的版本
                deleteExistingVersion(packageContent.getCode(), packageContent.getVersion());
            }
            
            // 4. 检测依赖冲突
            List<ImportResult.DependencyConflict> conflicts = detectConflicts(packageContent);
            
            // 5. 创建功能单元
            FunctionUnit functionUnit = createFunctionUnit(packageContent, request, importerId);
            
            // 6. 保存依赖关系
            saveDependencies(functionUnit, packageContent.getDependencies());
            
            // 7. 保存内容
            saveContents(functionUnit, packageContent.getContents());
            
            log.info("Function package imported successfully: {}", functionUnit.getId());
            
            FunctionUnitInfo info = FunctionUnitInfo.fromEntity(functionUnit);
            if (!conflicts.isEmpty()) {
                return ImportResult.conflictDetected(info, conflicts);
            }
            return ImportResult.success(info);
            
        } catch (Exception e) {
            log.error("Failed to import function package", e);
            return ImportResult.failure("导入失败: " + e.getMessage());
        }
    }

    
    /**
     * 验证功能包
     */
    public ValidationResult validatePackage(FunctionUnitImportRequest request) {
        log.info("Validating function package: {}", request.getFileName());
        
        ValidationResult result = ValidationResult.builder()
                .valid(true)
                .fileFormatValid(true)
                .integrityValid(true)
                .signatureValid(true)
                .bpmnSyntaxValid(true)
                .dataTableValid(true)
                .formConfigValid(true)
                .errors(new ArrayList<>())
                .warnings(new ArrayList<>())
                .build();
        
        // 1. 验证文件格式
        if (!validateFileFormat(request, result)) {
            result.setFileFormatValid(false);
        }
        
        // 2. 验证完整性
        if (!validateIntegrity(request, result)) {
            result.setIntegrityValid(false);
        }
        
        // 3. 验证数字签名（如果有）
        if (request.getFileContent() != null && !validateDigitalSignature(request, result)) {
            result.setSignatureValid(false);
            result.addWarning("数字签名验证失败，但不影响导入");
        }
        
        return result;
    }
    
    /**
     * 验证文件格式
     */
    private boolean validateFileFormat(FunctionUnitImportRequest request, ValidationResult result) {
        if (request.getFileName() == null || request.getFileName().isEmpty()) {
            result.addError("FILE_FORMAT", "fileName", "文件名不能为空");
            return false;
        }
        
        // 检查文件扩展名
        String fileName = request.getFileName().toLowerCase();
        if (!fileName.endsWith(".zip") && !fileName.endsWith(".fpkg")) {
            result.addError("FILE_FORMAT", "fileName", "不支持的文件格式，仅支持 .zip 或 .fpkg");
            return false;
        }
        
        // 检查文件内容
        if (request.getFileContent() == null && request.getFilePath() == null) {
            result.addError("FILE_FORMAT", "fileContent", "文件内容或文件路径不能为空");
            return false;
        }
        
        return true;
    }
    
    /**
     * 验证完整性
     */
    private boolean validateIntegrity(FunctionUnitImportRequest request, ValidationResult result) {
        // 简化实现：检查文件内容是否为空
        if (request.getFileContent() != null && request.getFileContent().isEmpty()) {
            result.addError("INTEGRITY", "fileContent", "文件内容为空");
            return false;
        }
        return true;
    }
    
    /**
     * 验证数字签名
     */
    private boolean validateDigitalSignature(FunctionUnitImportRequest request, ValidationResult result) {
        // 简化实现：总是返回true
        // 实际实现中应该验证数字签名
        return true;
    }
    
    /**
     * 验证BPMN语法
     */
    public boolean validateBpmnSyntax(String bpmnContent, ValidationResult result) {
        if (bpmnContent == null || bpmnContent.isEmpty()) {
            result.addError("BPMN_SYNTAX", "content", "BPMN内容为空");
            return false;
        }
        
        // 简化实现：检查基本的BPMN结构
        if (!bpmnContent.contains("definitions") || !bpmnContent.contains("process")) {
            result.addError("BPMN_SYNTAX", "content", "无效的BPMN格式");
            return false;
        }
        
        return true;
    }
    
    /**
     * 验证数据表结构
     */
    public boolean validateDataTableStructure(String tableDefinition, ValidationResult result) {
        if (tableDefinition == null || tableDefinition.isEmpty()) {
            return true; // 数据表定义可选
        }
        
        // 简化实现：检查基本的SQL结构
        String upperDef = tableDefinition.toUpperCase();
        if (!upperDef.contains("CREATE TABLE") && !upperDef.contains("ALTER TABLE")) {
            result.addError("DATA_TABLE", "definition", "无效的数据表定义");
            return false;
        }
        
        return true;
    }
    
    /**
     * 验证表单配置
     */
    public boolean validateFormConfig(String formConfig, ValidationResult result) {
        if (formConfig == null || formConfig.isEmpty()) {
            return true; // 表单配置可选
        }
        
        // 简化实现：检查JSON格式
        if (!formConfig.trim().startsWith("{") && !formConfig.trim().startsWith("[")) {
            result.addError("FORM_CONFIG", "config", "无效的表单配置格式");
            return false;
        }
        
        return true;
    }
    
    /**
     * 检测依赖冲突
     */
    public List<ImportResult.DependencyConflict> detectConflicts(FunctionPackageContent packageContent) {
        List<ImportResult.DependencyConflict> conflicts = new ArrayList<>();
        
        for (DependencyInfo dep : packageContent.getDependencies()) {
            // 检查依赖是否存在
            Optional<FunctionUnit> existing = functionUnitRepository.findLatestByCode(dep.getCode());
            if (existing.isPresent()) {
                String existingVersion = existing.get().getVersion();
                if (!isVersionCompatible(dep.getVersion(), existingVersion)) {
                    conflicts.add(ImportResult.DependencyConflict.builder()
                            .dependencyCode(dep.getCode())
                            .requiredVersion(dep.getVersion())
                            .existingVersion(existingVersion)
                            .conflictType("VERSION_MISMATCH")
                            .build());
                }
            } else if (dep.isRequired()) {
                conflicts.add(ImportResult.DependencyConflict.builder()
                        .dependencyCode(dep.getCode())
                        .requiredVersion(dep.getVersion())
                        .existingVersion(null)
                        .conflictType("MISSING_DEPENDENCY")
                        .build());
            }
        }
        
        return conflicts;
    }
    
    /**
     * 解析功能包内容
     */
    private FunctionPackageContent parsePackageContent(FunctionUnitImportRequest request) {
        // 简化实现：从请求中提取元数据
        // 优先使用请求中的 name 作为 code，否则从文件名提取
        String code = request.getName() != null ? request.getName() : extractCodeFromFileName(request.getFileName());
        String version = request.getVersion() != null ? request.getVersion() : "1.0.0";
        String name = request.getName() != null ? request.getName() : code;
        String description = request.getDescription();
        
        List<DependencyInfo> dependencies = new ArrayList<>();
        List<ContentInfo> contents = new ArrayList<>();
        
        // 如果有文件内容，尝试解析
        if (request.getFileContent() != null && !request.getFileContent().isEmpty()) {
            // 简化实现：假设内容是BPMN流程定义
            contents.add(ContentInfo.builder()
                    .contentType(ContentType.PROCESS)
                    .contentName("main-process.bpmn")
                    .contentPath("/processes/main-process.bpmn")
                    .contentData(request.getFileContent())
                    .build());
        }
        
        return FunctionPackageContent.builder()
                .code(code)
                .version(version)
                .name(name)
                .description(description)
                .dependencies(dependencies)
                .contents(contents)
                .build();
    }
    
    /**
     * 从文件名提取代码
     */
    private String extractCodeFromFileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "unknown";
        }
        // 移除扩展名
        String name = fileName;
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            name = fileName.substring(0, dotIndex);
        }
        // 移除版本号（如果有）
        int dashIndex = name.lastIndexOf('-');
        if (dashIndex > 0 && name.substring(dashIndex + 1).matches("\\d+\\.\\d+\\.\\d+.*")) {
            name = name.substring(0, dashIndex);
        }
        return name;
    }
    
    /**
     * 创建功能单元
     */
    private FunctionUnit createFunctionUnit(FunctionPackageContent packageContent, 
                                            FunctionUnitImportRequest request, 
                                            String importerId) {
        String checksum = calculateChecksum(request.getFileContent());
        
        FunctionUnit functionUnit = FunctionUnit.builder()
                .id(UUID.randomUUID().toString())
                .code(packageContent.getCode())
                .name(packageContent.getName())
                .version(packageContent.getVersion())
                .description(packageContent.getDescription())
                .packagePath(request.getFilePath())
                .packageSize(request.getFileContent() != null ? (long) request.getFileContent().length() : 0L)
                .checksum(checksum)
                .status(FunctionUnitStatus.DRAFT)
                .importedAt(Instant.now())
                .importedBy(importerId)
                .build();
        
        return functionUnitRepository.save(functionUnit);
    }
    
    /**
     * 保存依赖关系
     */
    private void saveDependencies(FunctionUnit functionUnit, List<DependencyInfo> dependencies) {
        for (DependencyInfo dep : dependencies) {
            FunctionUnitDependency dependency = FunctionUnitDependency.builder()
                    .id(UUID.randomUUID().toString())
                    .functionUnit(functionUnit)
                    .dependencyCode(dep.getCode())
                    .dependencyVersion(dep.getVersion())
                    .dependencyType(dep.isRequired() ? DependencyType.REQUIRED : DependencyType.OPTIONAL)
                    .build();
            dependencyRepository.save(dependency);
        }
    }
    
    /**
     * 保存内容
     */
    private void saveContents(FunctionUnit functionUnit, List<ContentInfo> contents) {
        for (ContentInfo content : contents) {
            String contentChecksum = calculateChecksum(content.getContentData());
            
            FunctionUnitContent unitContent = FunctionUnitContent.builder()
                    .id(UUID.randomUUID().toString())
                    .functionUnit(functionUnit)
                    .contentType(content.getContentType())
                    .contentName(content.getContentName())
                    .contentPath(content.getContentPath())
                    .contentData(content.getContentData())
                    .checksum(contentChecksum)
                    .build();
            contentRepository.save(unitContent);
        }
    }
    
    /**
     * 添加功能单元内容
     */
    @Transactional
    public void addFunctionUnitContent(String functionUnitId, ContentType contentType, 
                                       String contentName, String contentData) {
        FunctionUnit functionUnit = getFunctionUnitById(functionUnitId);
        
        String contentChecksum = calculateChecksum(contentData);
        String contentPath = "/" + contentType.name().toLowerCase() + "s/" + contentName;
        
        FunctionUnitContent unitContent = FunctionUnitContent.builder()
                .id(UUID.randomUUID().toString())
                .functionUnit(functionUnit)
                .contentType(contentType)
                .contentName(contentName)
                .contentPath(contentPath)
                .contentData(contentData)
                .checksum(contentChecksum)
                .build();
        contentRepository.save(unitContent);
        
        log.info("Added content {} of type {} to function unit {}", contentName, contentType, functionUnitId);
    }
    
    /**
     * 删除已存在的版本
     */
    @Transactional
    public void deleteExistingVersion(String code, String version) {
        Optional<FunctionUnit> existing = functionUnitRepository.findByCodeAndVersion(code, version);
        if (existing.isPresent()) {
            FunctionUnit unit = existing.get();
            // 删除相关内容
            contentRepository.deleteByFunctionUnitId(unit.getId());
            // 删除相关依赖
            dependencyRepository.deleteByFunctionUnitId(unit.getId());
            // 删除功能单元
            functionUnitRepository.delete(unit);
            // 强制刷新，确保删除操作在后续插入之前完成
            functionUnitRepository.flush();
            log.info("Deleted existing function unit version: {}:{}", code, version);
        }
    }
    
    /**
     * 检查版本兼容性
     */
    public boolean isVersionCompatible(String requiredVersion, String existingVersion) {
        if (requiredVersion == null || existingVersion == null) {
            return false;
        }
        
        // 解析版本号
        int[] required = parseVersion(requiredVersion);
        int[] existing = parseVersion(existingVersion);
        
        if (required == null || existing == null) {
            return requiredVersion.equals(existingVersion);
        }
        
        // 主版本号必须相同
        if (required[0] != existing[0]) {
            return false;
        }
        
        // 现有版本的次版本号必须大于等于要求的版本
        if (existing[1] < required[1]) {
            return false;
        }
        
        // 如果次版本号相同，补丁版本号必须大于等于要求的版本
        if (existing[1] == required[1] && existing[2] < required[2]) {
            return false;
        }
        
        return true;
    }
    
    /**
     * 解析版本号
     */
    private int[] parseVersion(String version) {
        if (version == null) {
            return null;
        }
        
        // 移除预发布标签
        String cleanVersion = version.split("-")[0];
        String[] parts = cleanVersion.split("\\.");
        
        if (parts.length < 3) {
            return null;
        }
        
        try {
            return new int[]{
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2])
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    /**
     * 计算校验和
     */
    public String calculateChecksum(String content) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("Failed to calculate checksum", e);
            return null;
        }
    }
    
    /**
     * 根据ID获取功能单元
     */
    public FunctionUnit getFunctionUnitById(String id) {
        return functionUnitRepository.findById(id)
                .orElseThrow(() -> new FunctionUnitNotFoundException("功能单元不存在: " + id));
    }
    
    /**
     * 保存功能单元
     */
    @Transactional
    public FunctionUnit saveFunctionUnit(FunctionUnit functionUnit) {
        return functionUnitRepository.save(functionUnit);
    }
    
    /**
     * 根据代码和版本获取功能单元
     */
    public FunctionUnit getFunctionUnitByCodeAndVersion(String code, String version) {
        return functionUnitRepository.findByCodeAndVersion(code, version)
                .orElseThrow(() -> new FunctionUnitNotFoundException("功能单元不存在: " + code + ":" + version));
    }
    
    /**
     * 获取功能单元的所有内容
     */
    public List<FunctionUnitContent> getFunctionUnitContents(String functionUnitId) {
        return contentRepository.findByFunctionUnitId(functionUnitId);
    }
    
    /**
     * 获取功能单元列表（分页）
     */
    public Page<FunctionUnit> listFunctionUnits(Pageable pageable) {
        return functionUnitRepository.findAll(pageable);
    }
    
    /**
     * 根据状态获取功能单元列表
     */
    public Page<FunctionUnit> listFunctionUnitsByStatus(FunctionUnitStatus status, Pageable pageable) {
        return functionUnitRepository.findByStatus(status, pageable);
    }
    
    /**
     * 获取功能单元的所有版本
     */
    public List<FunctionUnit> getAllVersions(String code) {
        return functionUnitRepository.findAllByCodeOrderByVersionDesc(code);
    }
    
    /**
     * 验证功能单元
     */
    @Transactional
    public FunctionUnit validateFunctionUnit(String id, String validatorId) {
        FunctionUnit functionUnit = getFunctionUnitById(id);
        
        if (functionUnit.getStatus() != FunctionUnitStatus.DRAFT) {
            throw new AdminBusinessException("INVALID_STATUS", "只有草稿状态的功能单元可以验证");
        }
        
        functionUnit.markAsValidated(validatorId);
        return functionUnitRepository.save(functionUnit);
    }
    
    /**
     * 废弃功能单元
     */
    @Transactional
    public FunctionUnit deprecateFunctionUnit(String id) {
        FunctionUnit functionUnit = getFunctionUnitById(id);
        functionUnit.markAsDeprecated();
        return functionUnitRepository.save(functionUnit);
    }
    
    // ==================== 版本管理功能 ====================
    
    /**
     * 验证语义化版本格式
     */
    public boolean isValidSemanticVersion(String version) {
        return version != null && VERSION_PATTERN.matcher(version).matches();
    }
    
    /**
     * 比较两个版本号
     * @return 负数表示v1 < v2，0表示相等，正数表示v1 > v2
     */
    public int compareVersions(String v1, String v2) {
        int[] version1 = parseVersion(v1);
        int[] version2 = parseVersion(v2);
        
        if (version1 == null && version2 == null) {
            return 0;
        }
        if (version1 == null) {
            return -1;
        }
        if (version2 == null) {
            return 1;
        }
        
        // 比较主版本号
        if (version1[0] != version2[0]) {
            return version1[0] - version2[0];
        }
        // 比较次版本号
        if (version1[1] != version2[1]) {
            return version1[1] - version2[1];
        }
        // 比较补丁版本号
        return version1[2] - version2[2];
    }
    
    /**
     * 获取下一个主版本号
     */
    public String getNextMajorVersion(String currentVersion) {
        int[] version = parseVersion(currentVersion);
        if (version == null) {
            return "2.0.0";
        }
        return (version[0] + 1) + ".0.0";
    }
    
    /**
     * 获取下一个次版本号
     */
    public String getNextMinorVersion(String currentVersion) {
        int[] version = parseVersion(currentVersion);
        if (version == null) {
            return "1.1.0";
        }
        return version[0] + "." + (version[1] + 1) + ".0";
    }
    
    /**
     * 获取下一个补丁版本号
     */
    public String getNextPatchVersion(String currentVersion) {
        int[] version = parseVersion(currentVersion);
        if (version == null) {
            return "1.0.1";
        }
        return version[0] + "." + version[1] + "." + (version[2] + 1);
    }
    
    /**
     * 获取功能单元的最新版本
     */
    public Optional<FunctionUnit> getLatestVersion(String code) {
        return functionUnitRepository.findLatestByCode(code);
    }
    
    /**
     * 获取功能单元的最新稳定版本（已验证或已部署）
     */
    public Optional<FunctionUnit> getLatestStableVersion(String code) {
        List<FunctionUnit> versions = functionUnitRepository.findAllByCodeOrderByVersionDesc(code);
        return versions.stream()
                .filter(v -> v.getStatus() == FunctionUnitStatus.VALIDATED || 
                            v.getStatus() == FunctionUnitStatus.DEPLOYED)
                .findFirst();
    }
    
    /**
     * 检查是否可以升级到指定版本
     */
    public VersionUpgradeCheck checkVersionUpgrade(String code, String fromVersion, String toVersion) {
        VersionUpgradeCheck check = new VersionUpgradeCheck();
        check.setFromVersion(fromVersion);
        check.setToVersion(toVersion);
        check.setUpgradable(true);
        check.setWarnings(new ArrayList<>());
        check.setErrors(new ArrayList<>());
        
        // 验证版本格式
        if (!isValidSemanticVersion(fromVersion)) {
            check.addError("源版本格式无效: " + fromVersion);
            check.setUpgradable(false);
        }
        if (!isValidSemanticVersion(toVersion)) {
            check.addError("目标版本格式无效: " + toVersion);
            check.setUpgradable(false);
        }
        
        if (!check.isUpgradable()) {
            return check;
        }
        
        // 检查版本顺序
        int comparison = compareVersions(fromVersion, toVersion);
        if (comparison >= 0) {
            check.addError("目标版本必须大于源版本");
            check.setUpgradable(false);
            return check;
        }
        
        // 检查目标版本是否存在
        Optional<FunctionUnit> targetUnit = functionUnitRepository.findByCodeAndVersion(code, toVersion);
        if (targetUnit.isEmpty()) {
            check.addError("目标版本不存在: " + code + ":" + toVersion);
            check.setUpgradable(false);
            return check;
        }
        
        // 检查目标版本状态
        FunctionUnit target = targetUnit.get();
        if (!target.isDeployable()) {
            check.addError("目标版本状态不允许升级: " + target.getStatus());
            check.setUpgradable(false);
            return check;
        }
        
        // 检查主版本号变化（可能有破坏性变更）
        int[] from = parseVersion(fromVersion);
        int[] to = parseVersion(toVersion);
        if (from != null && to != null && from[0] != to[0]) {
            check.addWarning("主版本号变化，可能存在破坏性变更");
            check.setMajorUpgrade(true);
        }
        
        return check;
    }
    
    /**
     * 获取版本历史
     */
    public List<VersionHistory> getVersionHistory(String code) {
        List<FunctionUnit> versions = functionUnitRepository.findAllByCodeOrderByVersionDesc(code);
        List<VersionHistory> history = new ArrayList<>();
        
        for (int i = 0; i < versions.size(); i++) {
            FunctionUnit current = versions.get(i);
            VersionHistory entry = VersionHistory.builder()
                    .version(current.getVersion())
                    .status(current.getStatus())
                    .createdAt(current.getCreatedAt())
                    .createdBy(current.getCreatedBy())
                    .validatedAt(current.getValidatedAt())
                    .validatedBy(current.getValidatedBy())
                    .isLatest(i == 0)
                    .isStable(current.getStatus() == FunctionUnitStatus.VALIDATED || 
                             current.getStatus() == FunctionUnitStatus.DEPLOYED)
                    .build();
            
            // 计算与前一版本的差异类型
            if (i < versions.size() - 1) {
                FunctionUnit previous = versions.get(i + 1);
                entry.setChangeType(determineChangeType(previous.getVersion(), current.getVersion()));
            } else {
                entry.setChangeType("INITIAL");
            }
            
            history.add(entry);
        }
        
        return history;
    }
    
    /**
     * 确定版本变更类型
     */
    private String determineChangeType(String fromVersion, String toVersion) {
        int[] from = parseVersion(fromVersion);
        int[] to = parseVersion(toVersion);
        
        if (from == null || to == null) {
            return "UNKNOWN";
        }
        
        if (from[0] != to[0]) {
            return "MAJOR";
        }
        if (from[1] != to[1]) {
            return "MINOR";
        }
        if (from[2] != to[2]) {
            return "PATCH";
        }
        return "NONE";
    }
    
    /**
     * 创建新版本（基于现有版本）
     */
    @Transactional
    public FunctionUnit createNewVersion(String sourceId, String newVersion, String creatorId) {
        FunctionUnit source = getFunctionUnitById(sourceId);
        
        // 验证新版本格式
        if (!isValidSemanticVersion(newVersion)) {
            throw new AdminBusinessException("INVALID_VERSION", "无效的版本格式: " + newVersion);
        }
        
        // 检查新版本是否已存在
        if (functionUnitRepository.existsByCodeAndVersion(source.getCode(), newVersion)) {
            throw new AdminBusinessException("VERSION_EXISTS", "版本已存在: " + source.getCode() + ":" + newVersion);
        }
        
        // 检查版本顺序
        if (compareVersions(source.getVersion(), newVersion) >= 0) {
            throw new AdminBusinessException("INVALID_VERSION", "新版本必须大于源版本");
        }
        
        // 创建新版本
        FunctionUnit newUnit = FunctionUnit.builder()
                .id(UUID.randomUUID().toString())
                .code(source.getCode())
                .name(source.getName())
                .version(newVersion)
                .description(source.getDescription())
                .packagePath(source.getPackagePath())
                .packageSize(source.getPackageSize())
                .checksum(source.getChecksum())
                .digitalSignature(source.getDigitalSignature())
                .status(FunctionUnitStatus.DRAFT)
                .createdBy(creatorId)
                .build();
        
        newUnit = functionUnitRepository.save(newUnit);
        
        // 复制依赖关系
        List<FunctionUnitDependency> sourceDeps = dependencyRepository.findByFunctionUnitId(source.getId());
        for (FunctionUnitDependency dep : sourceDeps) {
            FunctionUnitDependency newDep = FunctionUnitDependency.builder()
                    .id(UUID.randomUUID().toString())
                    .functionUnit(newUnit)
                    .dependencyCode(dep.getDependencyCode())
                    .dependencyVersion(dep.getDependencyVersion())
                    .dependencyType(dep.getDependencyType())
                    .build();
            dependencyRepository.save(newDep);
        }
        
        // 复制内容
        List<FunctionUnitContent> sourceContents = contentRepository.findByFunctionUnitId(source.getId());
        for (FunctionUnitContent content : sourceContents) {
            FunctionUnitContent newContent = FunctionUnitContent.builder()
                    .id(UUID.randomUUID().toString())
                    .functionUnit(newUnit)
                    .contentType(content.getContentType())
                    .contentName(content.getContentName())
                    .contentPath(content.getContentPath())
                    .contentData(content.getContentData())
                    .checksum(content.getChecksum())
                    .build();
            contentRepository.save(newContent);
        }
        
        log.info("Created new version {} from {}", newVersion, source.getVersion());
        return newUnit;
    }
    
    /**
     * 回滚到指定版本
     */
    @Transactional
    public FunctionUnit rollbackToVersion(String code, String targetVersion, String operatorId) {
        // 获取目标版本
        FunctionUnit targetUnit = getFunctionUnitByCodeAndVersion(code, targetVersion);
        
        // 检查目标版本状态
        if (!targetUnit.isDeployable()) {
            throw new AdminBusinessException("INVALID_STATUS", "目标版本状态不允许回滚: " + targetUnit.getStatus());
        }
        
        // 废弃所有比目标版本新的版本
        List<FunctionUnit> allVersions = functionUnitRepository.findAllByCodeOrderByVersionDesc(code);
        for (FunctionUnit unit : allVersions) {
            if (compareVersions(unit.getVersion(), targetVersion) > 0) {
                if (unit.getStatus() != FunctionUnitStatus.DEPRECATED) {
                    unit.markAsDeprecated();
                    functionUnitRepository.save(unit);
                    log.info("Deprecated version {} during rollback to {}", unit.getVersion(), targetVersion);
                }
            }
        }
        
        return targetUnit;
    }
    
    // ==================== 版本管理内部类 ====================
    
    /**
     * 版本升级检查结果
     */
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class VersionUpgradeCheck {
        private String fromVersion;
        private String toVersion;
        private boolean upgradable;
        private boolean majorUpgrade;
        private List<String> warnings;
        private List<String> errors;
        
        public void addWarning(String warning) {
            if (warnings == null) {
                warnings = new ArrayList<>();
            }
            warnings.add(warning);
        }
        
        public void addError(String error) {
            if (errors == null) {
                errors = new ArrayList<>();
            }
            errors.add(error);
        }
    }
    
    /**
     * 版本历史记录
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class VersionHistory {
        private String version;
        private FunctionUnitStatus status;
        private Instant createdAt;
        private String createdBy;
        private Instant validatedAt;
        private String validatedBy;
        private String changeType;
        private boolean isLatest;
        private boolean isStable;
    }
    
    // ==================== 内部类 ====================
    
    /**
     * 功能包内容
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class FunctionPackageContent {
        private String code;
        private String version;
        private String name;
        private String description;
        private List<DependencyInfo> dependencies;
        private List<ContentInfo> contents;
    }
    
    /**
     * 依赖信息
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class DependencyInfo {
        private String code;
        private String version;
        private boolean required;
    }
    
    /**
     * 内容信息
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ContentInfo {
        private ContentType contentType;
        private String contentName;
        private String contentPath;
        private String contentData;
    }
    
    // ==================== 删除和启用/禁用功能 ====================
    
    /**
     * 获取删除预览信息
     * 统计将被删除的关联数据数量
     */
    @Transactional(readOnly = true)
    public com.admin.dto.response.DeletePreviewResponse getDeletePreview(String functionUnitId) {
        FunctionUnit unit = getFunctionUnitById(functionUnitId);
        
        // 统计各类关联数据
        List<FunctionUnitContent> contents = contentRepository.findByFunctionUnitId(functionUnitId);
        
        int formCount = 0;
        int processCount = 0;
        int dataTableCount = 0;
        
        for (FunctionUnitContent content : contents) {
            switch (content.getContentType()) {
                case FORM:
                    formCount++;
                    break;
                case PROCESS:
                    processCount++;
                    break;
                case DATA_TABLE:
                    dataTableCount++;
                    break;
                default:
                    break;
            }
        }
        
        int dependencyCount = dependencyRepository.findByFunctionUnitId(functionUnitId).size();
        int deploymentCount = unit.getDeployments() != null ? unit.getDeployments().size() : 0;
        
        // 检查运行中的流程实例（简化实现，实际需要调用流程引擎）
        boolean hasRunningInstances = false;
        int runningInstanceCount = 0;
        
        return com.admin.dto.response.DeletePreviewResponse.builder()
                .functionUnitId(functionUnitId)
                .functionUnitName(unit.getName())
                .functionUnitCode(unit.getCode())
                .formCount(formCount)
                .processCount(processCount)
                .dataTableCount(dataTableCount)
                .accessConfigCount(0) // 将在后续查询
                .deploymentCount(deploymentCount)
                .dependencyCount(dependencyCount)
                .hasRunningInstances(hasRunningInstances)
                .runningInstanceCount(runningInstanceCount)
                .build();
    }
    
    /**
     * 检查是否有运行中的流程实例
     */
    public boolean hasRunningInstances(String functionUnitId) {
        // 简化实现：实际需要调用流程引擎检查
        // 这里返回false，表示没有运行中的实例
        return false;
    }
    
    /**
     * 级联删除功能单元及其所有关联内容
     */
    @Transactional
    public void deleteFunctionUnitCascade(String functionUnitId) {
        FunctionUnit unit = getFunctionUnitById(functionUnitId);
        
        // 检查是否有运行中的流程实例
        if (hasRunningInstances(functionUnitId)) {
            throw new AdminBusinessException("HAS_RUNNING_INSTANCES", 
                    "无法删除：存在运行中的流程实例");
        }
        
        log.info("Deleting function unit cascade: {} ({})", unit.getName(), functionUnitId);
        
        // 删除内容
        contentRepository.deleteByFunctionUnitId(functionUnitId);
        
        // 删除依赖
        dependencyRepository.deleteByFunctionUnitId(functionUnitId);
        
        // 删除功能单元（会级联删除deployments）
        functionUnitRepository.delete(unit);
        
        log.info("Function unit deleted successfully: {}", functionUnitId);
    }
    
    /**
     * 设置功能单元启用状态
     */
    @Transactional
    public FunctionUnit setEnabled(String functionUnitId, boolean enabled) {
        FunctionUnit unit = getFunctionUnitById(functionUnitId);
        unit.setEnabled(enabled);
        FunctionUnit saved = functionUnitRepository.save(unit);
        log.info("Function unit {} enabled status set to: {}", functionUnitId, enabled);
        return saved;
    }
    
    /**
     * 获取已部署且启用的功能单元列表
     */
    public Page<FunctionUnit> listDeployedAndEnabledFunctionUnits(Pageable pageable) {
        return functionUnitRepository.findByStatusAndEnabled(FunctionUnitStatus.DEPLOYED, true, pageable);
    }
}
