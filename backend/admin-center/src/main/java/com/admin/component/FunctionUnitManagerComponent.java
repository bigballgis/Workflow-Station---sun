package com.admin.component;

import com.admin.dto.request.FunctionUnitImportRequest;
import com.admin.dto.response.FunctionUnitContentResponse;
import com.admin.dto.response.FunctionUnitContentItemDTO;
import com.admin.dto.response.FunctionUnitInfo;
import com.admin.dto.response.FormContentDTO;
import com.admin.dto.response.ProcessContentDTO;
import com.admin.dto.response.DataTableContentDTO;
import com.admin.dto.response.TableBindingDTO;
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
import com.admin.entity.ActionDefinition;
import com.admin.repository.ActionDefinitionRepository;
import com.admin.repository.FunctionUnitAccessRepository;
import com.admin.repository.FunctionUnitContentRepository;
import com.admin.repository.FunctionUnitDependencyRepository;
import com.admin.repository.FunctionUnitRepository;
import com.platform.common.util.ApiResponseBodyUnwrap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import com.platform.common.version.SemanticVersion;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
    private final FunctionUnitAccessRepository accessRepository;
    private final FunctionUnitValidationComponent validationComponent;
    private final FunctionUnitPackageParser packageParser;
    private final ActionDefinitionRepository actionDefinitionRepository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Value("${user-portal.base-url:http://localhost:8082/api/portal}")
    private String userPortalBaseUrl;

    @Value("${user-portal.internal-api-token:}")
    private String userPortalInternalApiToken;
    
    // 版本号正则表达式（语义化版本）
    private static final Pattern VERSION_PATTERN = Pattern.compile("^\\d+\\.\\d+\\.\\d+(-[a-zA-Z0-9]+)?$");
        
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
            
            // 2. 解析功能包内容（支持 Developer Workstation 导出的 ZIP）
            FunctionUnitPackageParser.ParsedImportPackage parsed = parseImportRequest(request);
            FunctionPackageContent packageContent = parsed.getPackageContent();

            // 3. 检查版本是否已存在
            if (functionUnitRepository.existsByCodeAndVersion(packageContent.getCode(), packageContent.getVersion())) {
                boolean shouldOverwrite = request.isOverwrite()
                        || functionUnitRepository.findByCodeAndVersion(packageContent.getCode(), packageContent.getVersion())
                        .map(u -> u.getStatus() == FunctionUnitStatus.ARCHIVED)
                        .orElse(false);
                if (!shouldOverwrite) {
                    return ImportResult.failure("Function unit version already exists: "
                            + packageContent.getCode() + ":" + packageContent.getVersion()
                            + "（请勾选覆盖或先删除归档版本）");
                }
                deleteExistingVersion(packageContent.getCode(), packageContent.getVersion());
            }

            if (parsed.getIconSvg() != null && request.getIconSvg() == null) {
                request.setIconSvg(parsed.getIconSvg());
            }
            
            // 4. 检测依赖冲突
            List<ImportResult.DependencyConflict> conflicts = detectConflicts(packageContent);
            
            // 5. 创建功能单元（导入后为 DRAFT 状态，未启用，需验证后部署）
            FunctionUnit functionUnit = createFunctionUnit(packageContent, request, importerId);
            
            // 7. 保存依赖关系
            saveDependencies(functionUnit, packageContent.getDependencies());
            
            // 8. 保存内容（流程、表）及表单
            saveContents(functionUnit, packageContent.getContents());
            if (parsed.getForms() != null) {
                saveContents(functionUnit, parsed.getForms());
            }
            saveImportedActions(functionUnit.getId(), parsed.getActions());
            
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
     * 解析导入请求：优先按 ZIP（Base64）解析 Developer Workstation 导出包。
     */
    private FunctionUnitPackageParser.ParsedImportPackage parseImportRequest(FunctionUnitImportRequest request)
            throws IOException {
        if (request.getFileContent() != null && !request.getFileContent().isBlank()
                && request.getFileName() != null
                && request.getFileName().toLowerCase().endsWith(".zip")) {
            try {
                FunctionUnitPackageParser.ParsedImportPackage parsed =
                        packageParser.parseBase64Zip(request.getFileContent());
                FunctionPackageContent content = parsed.getPackageContent();
                if (content.getCode() == null || content.getCode().isBlank()) {
                    content.setCode(extractCodeFromFileName(request.getFileName()));
                }
                if (content.getName() == null || content.getName().isBlank()) {
                    content.setName(request.getName() != null ? request.getName() : content.getCode());
                }
                if (request.getCode() != null && !request.getCode().isBlank()) {
                    content.setCode(request.getCode());
                }
                if (request.getVersion() != null && !request.getVersion().isBlank()) {
                    content.setVersion(request.getVersion());
                }
                if (request.getDescription() != null) {
                    content.setDescription(request.getDescription());
                }
                return parsed;
            } catch (IllegalArgumentException e) {
                log.warn("Base64 zip decode failed, falling back to legacy parser: {}", e.getMessage());
            }
        }
        FunctionPackageContent legacy = parsePackageContentLegacy(request);
        return FunctionUnitPackageParser.ParsedImportPackage.builder()
                .packageContent(legacy)
                .forms(List.of())
                .actions(List.of())
                .iconSvg(request.getIconSvg())
                .build();
    }

    /**
     * 旧版解析（非 ZIP 或纯 BPMN 文本）
     */
    private FunctionPackageContent parsePackageContentLegacy(FunctionUnitImportRequest request) {
        // 优先使用请求中的code，如果没有则从文件名提取
        String code = request.getCode() != null && !request.getCode().isEmpty() 
                ? request.getCode() 
                : extractCodeFromFileName(request.getFileName());
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
                .enabled(false)
                .importedAt(Instant.now())
                .importedBy(importerId)
                .deployedAt(Instant.now())
                .iconSvg(request.getIconSvg())
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
                    .sourceId(content.getSourceId())
                    .build();
            contentRepository.save(unitContent);
        }
    }

    private void saveImportedActions(String functionUnitId, List<Map<String, Object>> actions) {
        if (actions == null || actions.isEmpty()) {
            return;
        }
        actionDefinitionRepository.deleteByFunctionUnitId(functionUnitId);
        for (Map<String, Object> actionData : actions) {
            try {
                String actionName = actionData.get("actionName") != null
                        ? String.valueOf(actionData.get("actionName")) : null;
                String actionType = actionData.get("actionType") != null
                        ? String.valueOf(actionData.get("actionType")) : null;
                if (actionName == null || actionType == null) {
                    continue;
                }
                Map<String, Object> configJson = resolveActionConfigJson(actionData.get("configJson"));
                ActionDefinition actionDef = ActionDefinition.builder()
                        .functionUnitId(functionUnitId)
                        .actionName(actionName)
                        .actionType(actionType)
                        .description(actionData.get("description") != null
                                ? String.valueOf(actionData.get("description")) : null)
                        .configJson(configJson)
                        .icon(actionData.get("icon") != null ? String.valueOf(actionData.get("icon")) : null)
                        .buttonColor(actionData.get("buttonColor") != null
                                ? String.valueOf(actionData.get("buttonColor")) : null)
                        .isDefault(Boolean.TRUE.equals(actionData.get("isDefault")))
                        .build();
                actionDefinitionRepository.save(actionDef);
            } catch (Exception e) {
                log.warn("Failed to save imported action: {}", e.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveActionConfigJson(Object configJsonObj) {
        if (configJsonObj instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        if (configJsonObj instanceof String s && !s.isBlank()) {
            try {
                return objectMapper.readValue(s, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                log.warn("Failed to parse action config_json string: {}", e.getMessage());
            }
        }
        return Map.of();
    }
    
    /**
     * 添加功能单元内容
     */
    @Transactional
    public void addFunctionUnitContent(String functionUnitId, ContentType contentType, 
                                       String contentName, String contentData) {
        addFunctionUnitContent(functionUnitId, contentType, contentName, contentData, null);
    }
    
    /**
     * 添加功能单元内容（带原始ID）
     * @param sourceId 原始内容ID（来自 developer-workstation 的 dw_form_definitions.id 等）
     */
    @Transactional
    public void addFunctionUnitContent(String functionUnitId, ContentType contentType, 
                                       String contentName, String contentData, String sourceId) {
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
                .sourceId(sourceId)
                .build();
        contentRepository.save(unitContent);
        
        log.info("Added content {} of type {} with sourceId {} to function unit {}", contentName, contentType, sourceId, functionUnitId);
    }
    
    /**
     * 删除已存在的版本
     */
    @Transactional
    public void deleteExistingVersion(String code, String version) {
        Optional<FunctionUnit> existing = functionUnitRepository.findByCodeAndVersion(code, version);
        if (existing.isPresent()) {
            FunctionUnit unit = existing.get();
            // 删除相关访问权限配置
            accessRepository.deleteByFunctionUnitId(unit.getId());
            // 删除相关内容
            contentRepository.deleteByFunctionUnitId(unit.getId());
            // 删除相关依赖
            dependencyRepository.deleteByFunctionUnitId(unit.getId());
            actionDefinitionRepository.deleteByFunctionUnitId(unit.getId());
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
     * 根据流程定义Key获取功能单元
     * 通过查找 flowable_process_definition_id 以 processKey: 开头的内容来定位功能单元
     */
    @Transactional(readOnly = true)
    public FunctionUnit getFunctionUnitByProcessKey(String processKey) {
        List<com.admin.entity.FunctionUnitContent> results = contentRepository.findAllByProcessDefinitionKey(processKey);
        if (results.isEmpty()) {
            throw new FunctionUnitNotFoundException("Function unit not found for process definition key: " + processKey);
        }
        // 列表按 content.createdAt DESC：最新一条可能挂在「已禁用」的旧目录版本上。
        // 门户待办/分配仍用 processDefinitionKey 解析目录时，应优先解析到仍启用的目录行，避免误报 disabled。
        for (FunctionUnitContent c : results) {
            FunctionUnit fu = c.getFunctionUnit();
            if (fu != null && Boolean.TRUE.equals(fu.getEnabled())) {
                return fu;
            }
        }
        return results.get(0).getFunctionUnit();
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
     * 获取功能单元内容，按类型过滤。
     * <p>type 为 null 时返回所有类型；type 有效时返回该类型；type 无效时抛出 AdminBusinessException。
     *
     * <p><b>Validates: Requirements 35.1, 35.2, 35.3</b>
     *
     * @param functionUnitId 功能单元 ID
     * @param type           内容类型字符串（可选），如 "FORM", "PROCESS", "DATA_TABLE"
     * @return 内容项 DTO 列表
     */
    @Transactional(readOnly = true)
    public List<FunctionUnitContentItemDTO> getContentsByType(String functionUnitId, String type) {
        List<FunctionUnitContent> contents;
        if (type == null || type.isBlank()) {
            contents = contentRepository.findByFunctionUnitId(functionUnitId);
        } else {
            ContentType requestedType;
            try {
                requestedType = ContentType.valueOf(type.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new AdminBusinessException("INVALID_CONTENT_TYPE", "Invalid content type: " + type);
            }
            contents = contentRepository.findByFunctionUnitIdAndContentType(functionUnitId, requestedType);
        }
        return contents.stream()
                .map(c -> FunctionUnitContentItemDTO.builder()
                        .id(c.getId())
                        .contentType(c.getContentType().name())
                        .contentName(c.getContentName())
                        .contentData(c.getContentData())
                        .sourceId(c.getSourceId())
                        .build())
                .toList();
    }

    /**
     * 组装功能单元完整内容（BPMN 流程、表单定义、数据表等）。
     * <p>业务逻辑包括：Base64 解码 BPMN XML、从 dw_form_definitions 读取最新 config_json、
     * 查询 tableBindings 并附加到表单内容。
     *
     * <p><b>Validates: Requirements 6.1, 6.2, 6.3</b>
     *
     * @param id 功能单元 ID
     * @return 完整内容响应 DTO
     */
    @Transactional(readOnly = true)
    public FunctionUnitContentResponse assembleFunctionUnitContent(String id) {
        FunctionUnit unit = getFunctionUnitById(id);
        List<FunctionUnitContent> contents = contentRepository.findByFunctionUnitId(id);

        List<FormContentDTO> forms = new ArrayList<>();
        List<ProcessContentDTO> processes = new ArrayList<>();
        List<DataTableContentDTO> dataTables = new ArrayList<>();

        for (FunctionUnitContent content : contents) {
            String data = content.getContentData();

            if (content.getContentType() == ContentType.PROCESS && data != null) {
                data = decodeBase64IfNeeded(data);
                String processKey = extractProcessKey(data);
                processes.add(ProcessContentDTO.builder()
                        .id(content.getId())
                        .name(content.getContentName())
                        .sourceId(content.getSourceId())
                        .data(data)
                        .type(ContentType.PROCESS.name())
                        .flowableProcessDefinitionKey(content.getFlowableProcessDefinitionId() != null
                                ? content.getFlowableProcessDefinitionId()
                                : processKey)
                        .build());
            } else if (content.getContentType() == ContentType.FORM) {
                data = fetchLatestConfigJsonOrFallback(content, data);
                forms.add(FormContentDTO.builder()
                        .id(content.getId())
                        .name(content.getContentName())
                        .sourceId(content.getSourceId())
                        .data(data)
                        .type(ContentType.FORM.name())
                        .build());
            } else if (content.getContentType() == ContentType.DATA_TABLE) {
                dataTables.add(DataTableContentDTO.builder()
                        .id(content.getId())
                        .name(content.getContentName())
                        .sourceId(content.getSourceId())
                        .data(data)
                        .type(ContentType.DATA_TABLE.name())
                        .build());
            }
        }

        // Attach tableBindings to each form
        attachTableBindings(forms);

        return FunctionUnitContentResponse.builder()
                .id(unit.getId())
                .name(unit.getName())
                .code(unit.getCode())
                .version(unit.getVersion())
                .description(unit.getDescription())
                .status(unit.getStatus().name())
                .forms(forms)
                .processes(processes)
                .dataTables(dataTables)
                .build();
    }

    /**
     * Attempt Base64 decode; return raw data if not Base64 encoded.
     */
    private String decodeBase64IfNeeded(String data) {
        try {
            byte[] decoded = java.util.Base64.getDecoder().decode(data);
            String result = new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
            log.info("Decoded BPMN XML, length: {}", result.length());
            return result;
        } catch (IllegalArgumentException e) {
            log.info("BPMN data is not Base64 encoded, using raw data");
            return data;
        }
    }

    /**
     * For FORM content, try to fetch the latest config_json from dw_form_definitions
     * (the content_data may be a stale snapshot from import time).
     */
    private String fetchLatestConfigJsonOrFallback(FunctionUnitContent content, String fallbackData) {
        if (content.getSourceId() == null) {
            return fallbackData;
        }
        try {
            Long sourceIdLong = Long.parseLong(content.getSourceId());
            String latestConfigJson = jdbcTemplate.queryForObject(
                    "SELECT config_json::text FROM dw_form_definitions WHERE id = ?",
                    String.class, sourceIdLong);
            if (latestConfigJson != null) {
                log.info("Using latest config_json from dw_form_definitions for form sourceId={}", content.getSourceId());
                return latestConfigJson;
            }
        } catch (NumberFormatException e) {
            log.warn("Invalid sourceId format: {}", content.getSourceId());
        } catch (Exception e) {
            log.warn("Could not fetch latest config_json for form sourceId={}, using content_data: {}",
                    content.getSourceId(), e.getMessage());
        }
        return fallbackData;
    }

    private static List<String> readTextArrayColumn(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        java.sql.Array arr = rs.getArray(column);
        if (arr == null) {
            return Collections.emptyList();
        }
        Object[] raw = (Object[]) arr.getArray();
        if (raw == null) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>(raw.length);
        for (Object o : raw) {
            if (o != null) {
                out.add(o.toString());
            }
        }
        return out;
    }

    private static Long readNullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        Object v = rs.getObject(column);
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Attach tableBindings to each form DTO by querying dw_form_table_bindings.
     * Prefers sourceId match; falls back to form_name match for forms without sourceId.
     */
    private void attachTableBindings(List<FormContentDTO> forms) {
        if (forms.isEmpty()) return;

        try {
            List<String> formSourceIds = forms.stream()
                    .map(FormContentDTO::getSourceId)
                    .filter(sid -> sid != null && !sid.isBlank())
                    .distinct()
                    .toList();

            List<String> formNamesForFallback = forms.stream()
                    .filter(f -> f.getSourceId() == null || f.getSourceId().isBlank())
                    .map(FormContentDTO::getName)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();

            Map<String, List<TableBindingDTO>> bindingsBySourceId = new LinkedHashMap<>();
            Map<String, List<TableBindingDTO>> bindingsByFormName = new LinkedHashMap<>();

            if (!formSourceIds.isEmpty()) {
                String placeholders = formSourceIds.stream().map(n -> "?").collect(Collectors.joining(","));
                // LEFT JOIN both dw_table_definitions (SUB/PRIMARY via table_id) and rt_table_definitions
                // (RELATED via relation_table_id) so designer-configured display names propagate to portal
                // for all binding types — mirrors user-portal ProcessFormComponent.loadSubTableBindingMapsForForm.
                String sql =
                        "SELECT fd.id as form_id, ftb.id as binding_id, ftb.binding_type, ftb.binding_mode, " +
                        "       ftb.sub_mode, ftb.foreign_key_field, ftb.sort_order, " +
                        "       COALESCE(td.id, rt.id) as table_id, " +
                        "       COALESCE(td.table_name, rt.table_name) AS table_name, " +
                        "       COALESCE(td.table_display_name, rt.display_name) AS table_display_name, " +
                        "       COALESCE(td.table_type, 'RELATION') as table_type, " +
                        "       COALESCE(td.description, rt.description) as table_description, " +
                        "       (SELECT array_agg(fd_inner.field_name ORDER BY fd_inner.sort_order NULLS LAST, fd_inner.id) " +
                        "        FROM dw_field_definitions fd_inner " +
                        "        WHERE fd_inner.table_id = td.id AND COALESCE(fd_inner.is_primary_key, false) = true) AS primary_key_fields " +
                        "FROM dw_form_definitions fd " +
                        "JOIN dw_form_table_bindings ftb ON ftb.form_id = fd.id " +
                        "LEFT JOIN dw_table_definitions td ON td.id = ftb.table_id " +
                        "LEFT JOIN rt_table_definitions rt ON rt.id = ftb.relation_table_id " +
                        "WHERE fd.id::text IN (" + placeholders + ") " +
                        "ORDER BY fd.id, ftb.sort_order";
                jdbcTemplate.query(sql, rs -> {
                    String formId = rs.getString("form_id");
                    TableBindingDTO binding = TableBindingDTO.builder()
                            .bindingId(rs.getLong("binding_id"))
                            .tableId(readNullableLong(rs, "table_id"))
                            .bindingType(rs.getString("binding_type"))
                            .bindingMode(rs.getString("binding_mode"))
                            .subMode(rs.getString("sub_mode"))
                            .foreignKeyField(rs.getString("foreign_key_field"))
                            .sortOrder(rs.getInt("sort_order"))
                            .tableName(rs.getString("table_name"))
                            .tableDisplayName(rs.getString("table_display_name"))
                            .tableType(rs.getString("table_type"))
                            .tableDescription(rs.getString("table_description"))
                            .primaryKeyFields(readTextArrayColumn(rs, "primary_key_fields"))
                            .build();
                    bindingsBySourceId.computeIfAbsent(formId, k -> new ArrayList<>()).add(binding);
                }, formSourceIds.toArray());
            }

            if (!formNamesForFallback.isEmpty()) {
                String placeholders = formNamesForFallback.stream().map(n -> "?").collect(Collectors.joining(","));
                String sql =
                        "SELECT latest.form_name, ftb.id as binding_id, ftb.binding_type, ftb.binding_mode, " +
                        "       ftb.sub_mode, ftb.foreign_key_field, ftb.sort_order, " +
                        "       COALESCE(td.id, rt.id) as table_id, " +
                        "       COALESCE(td.table_name, rt.table_name) AS table_name, " +
                        "       COALESCE(td.table_display_name, rt.display_name) AS table_display_name, " +
                        "       COALESCE(td.table_type, 'RELATION') as table_type, " +
                        "       COALESCE(td.description, rt.description) as table_description, " +
                        "       (SELECT array_agg(fd_inner.field_name ORDER BY fd_inner.sort_order NULLS LAST, fd_inner.id) " +
                        "        FROM dw_field_definitions fd_inner " +
                        "        WHERE fd_inner.table_id = td.id AND COALESCE(fd_inner.is_primary_key, false) = true) AS primary_key_fields " +
                        "FROM (SELECT DISTINCT ON (form_name) id, form_name, config_json FROM dw_form_definitions " +
                        "      WHERE form_name IN (" + placeholders + ") ORDER BY form_name, id DESC) latest " +
                        "JOIN dw_form_table_bindings ftb ON ftb.form_id = latest.id " +
                        "LEFT JOIN dw_table_definitions td ON td.id = ftb.table_id " +
                        "LEFT JOIN rt_table_definitions rt ON rt.id = ftb.relation_table_id " +
                        "ORDER BY latest.form_name, ftb.sort_order";
                jdbcTemplate.query(sql, rs -> {
                    String formName = rs.getString("form_name");
                    TableBindingDTO binding = TableBindingDTO.builder()
                            .bindingId(rs.getLong("binding_id"))
                            .tableId(readNullableLong(rs, "table_id"))
                            .bindingType(rs.getString("binding_type"))
                            .bindingMode(rs.getString("binding_mode"))
                            .subMode(rs.getString("sub_mode"))
                            .foreignKeyField(rs.getString("foreign_key_field"))
                            .sortOrder(rs.getInt("sort_order"))
                            .tableName(rs.getString("table_name"))
                            .tableDisplayName(rs.getString("table_display_name"))
                            .tableType(rs.getString("table_type"))
                            .tableDescription(rs.getString("table_description"))
                            .primaryKeyFields(readTextArrayColumn(rs, "primary_key_fields"))
                            .build();
                    bindingsByFormName.computeIfAbsent(formName, k -> new ArrayList<>()).add(binding);
                }, formNamesForFallback.toArray());
            }

            // Attach bindings: prefer sourceId match, fallback to form_name
            for (FormContentDTO form : forms) {
                List<TableBindingDTO> bindings;
                if (form.getSourceId() != null && !form.getSourceId().isBlank()) {
                    bindings = bindingsBySourceId.getOrDefault(form.getSourceId(), Collections.emptyList());
                } else {
                    bindings = bindingsByFormName.getOrDefault(form.getName(), Collections.emptyList());
                }
                form.setTableBindings(bindings);
            }
            log.info("Attached tableBindings to {} forms", forms.size());
        } catch (Exception e) {
            log.warn("Failed to load tableBindings: {}", e.getMessage());
            for (FormContentDTO form : forms) {
                form.setTableBindings(Collections.emptyList());
            }
        }
    }
    
    /**
     * 获取功能单元列表（分页）
     */
    public Page<FunctionUnit> listFunctionUnits(Pageable pageable) {
        return functionUnitRepository.findByStatusNot(FunctionUnitStatus.ARCHIVED, pageable);
    }
    
    /**
     * 获取已归档的功能单元列表（分页）
     */
    public Page<FunctionUnit> listArchivedFunctionUnits(Pageable pageable) {
        return functionUnitRepository.findByStatus(FunctionUnitStatus.ARCHIVED, pageable);
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
     * 验证功能单元：执行结构/依赖/引擎试部署检查，通过后标记为 VALIDATED
     */
    @Transactional
    public ValidationResult validateFunctionUnit(String id, String validatorId) {
        FunctionUnit functionUnit = getFunctionUnitById(id);

        if (!functionUnit.isValidatable()) {
            throw new AdminBusinessException("INVALID_STATUS",
                    "仅草稿状态的功能单元可以验证（当前状态: " + functionUnit.getStatus() + "）");
        }

        ValidationResult result = validationComponent.validate(id);
        result.setFunctionUnitId(id);
        result.setStatus(FunctionUnitStatus.DRAFT.name());

        if (!result.isValid()) {
            return result;
        }

        functionUnit.markAsValidated(validatorId);
        functionUnitRepository.save(functionUnit);
        result.setStatus(FunctionUnitStatus.VALIDATED.name());
        return result;
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
     * 获取功能单元的最新版本（使用语义化版本比较，避免字典序错误如 1.0.9 > 1.0.11）
     */
    public Optional<FunctionUnit> getLatestVersion(String code) {
        List<FunctionUnit> versions = functionUnitRepository.findByCodeOrderByVersionDesc(code);
        if (versions.isEmpty()) return Optional.empty();
        return versions.stream().max((a, b) -> {
            try {
                return SemanticVersion.parse(a.getVersion()).compareTo(SemanticVersion.parse(b.getVersion()));
            } catch (IllegalArgumentException e) {
                return a.getVersion().compareTo(b.getVersion());
            }
        });
    }
    
    /**
     * 获取功能单元的最新稳定版本（已验证或已部署），使用语义化版本比较
     */
    public Optional<FunctionUnit> getLatestStableVersion(String code) {
        List<FunctionUnit> versions = functionUnitRepository.findAllByCodeOrderByVersionDesc(code);
        return versions.stream()
                .filter(v -> v.getStatus() == FunctionUnitStatus.VALIDATED || 
                            v.getStatus() == FunctionUnitStatus.DEPLOYED)
                .max((a, b) -> {
                    try {
                        return SemanticVersion.parse(a.getVersion()).compareTo(SemanticVersion.parse(b.getVersion()));
                    } catch (IllegalArgumentException e) {
                        return a.getVersion().compareTo(b.getVersion());
                    }
                });
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
            throw new AdminBusinessException("INVALID_VERSION", "Invalid version format: " + newVersion);
        }
        
        // 检查新版本是否已存在
        if (functionUnitRepository.existsByCodeAndVersion(source.getCode(), newVersion)) {
            throw new AdminBusinessException("VERSION_EXISTS", "Version already exists: " + source.getCode() + ":" + newVersion);
        }
        
        // 检查版本顺序
        if (compareVersions(source.getVersion(), newVersion) >= 0) {
            throw new AdminBusinessException("INVALID_VERSION", "New version must be greater than source version");
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
            throw new AdminBusinessException("INVALID_STATUS", "Target version status does not allow rollback: " + targetUnit.getStatus());
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
        private String sourceId;
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
     * 归档功能单元（按 code 归档全部版本，并从用户门户移除可见性）
     */
    @Transactional
    public void archiveFunctionUnitByCode(String functionUnitId) {
        FunctionUnit unit = getFunctionUnitById(functionUnitId);

        if (unit.getStatus() == FunctionUnitStatus.ARCHIVED) {
            log.info("Function unit already archived: {}", functionUnitId);
            return;
        }

        if (hasRunningInstances(functionUnitId)) {
            throw new AdminBusinessException("HAS_RUNNING_INSTANCES",
                    "Cannot archive: there are running process instances");
        }

        String code = unit.getCode();
        List<FunctionUnit> allVersions = functionUnitRepository.findAllByCodeOrderByVersionDesc(code);
        log.info("Archiving function unit code {} ({} version(s))", code, allVersions.size());

        for (FunctionUnit version : allVersions) {
            if (version.getStatus() == FunctionUnitStatus.ARCHIVED) {
                continue;
            }
            version.markAsArchived();
            functionUnitRepository.save(version);
        }

        log.info("Function unit archived successfully: code={}", code);
    }

    /**
     * 恢复已归档的功能单元（恢复该 code 下全部 ARCHIVED 版本为 DRAFT）
     */
    @Transactional
    public FunctionUnit restoreFunctionUnit(String functionUnitId) {
        FunctionUnit unit = getFunctionUnitById(functionUnitId);
        if (unit.getStatus() != FunctionUnitStatus.ARCHIVED) {
            throw new AdminBusinessException("INVALID_STATUS", "Only archived function units can be restored");
        }

        String code = unit.getCode();
        List<FunctionUnit> archivedVersions = functionUnitRepository.findByCodeAndStatus(code, FunctionUnitStatus.ARCHIVED);
        if (archivedVersions.isEmpty()) {
            throw new AdminBusinessException("NOT_FOUND", "No archived versions found for code: " + code);
        }

        FunctionUnit toRestore = archivedVersions.stream()
                .max(this::compareBySemver)
                .orElse(unit);
        for (FunctionUnit version : archivedVersions) {
            version.markAsDraft();
            functionUnitRepository.save(version);
        }
        log.info("Restored function unit {} ({} version(s)) to DRAFT", code, archivedVersions.size());
        return functionUnitRepository.findById(toRestore.getId()).orElse(toRestore);
    }

    /**
     * 级联删除功能单元及其所有关联内容（保留供内部/测试使用；对外 DELETE 走归档）
     */
    @Transactional
    public void deleteFunctionUnitCascade(String functionUnitId) {
        FunctionUnit unit = getFunctionUnitById(functionUnitId);
        
        // 检查是否有运行中的流程实例
        if (hasRunningInstances(functionUnitId)) {
            throw new AdminBusinessException("HAS_RUNNING_INSTANCES", 
                    "Cannot delete: there are running process instances");
        }
        
        log.info("Deleting function unit cascade: {} ({})", unit.getName(), functionUnitId);
        
        // 删除访问权限配置
        accessRepository.deleteByFunctionUnitId(functionUnitId);
        
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
        return setEnabled(functionUnitId, enabled, "system", "Manual status change");
    }
    
    /**
     * 设置功能单元启用状态（带操作人和原因）
     * @param functionUnitId 功能单元ID
     * @param enabled 启用状态
     * @param operatorId 操作人ID
     * @param reason 原因
     * @return 更新后的功能单元
     */
    @Transactional
    public FunctionUnit setEnabled(String functionUnitId, boolean enabled, String operatorId, String reason) {
        FunctionUnit unit = getFunctionUnitById(functionUnitId);
        String oldStatus = unit.getEnabled() ? "enabled" : "disabled";
        String newStatus = enabled ? "enabled" : "disabled";

        if (enabled) {
            if (unit.getStatus() != FunctionUnitStatus.DEPLOYED) {
                throw new AdminBusinessException("INVALID_STATUS",
                        "Only DEPLOYED versions can be enabled for portal initiation");
            }
            FunctionUnit maxDeployed = pickMaxSemverAmongDeployed(unit.getCode())
                    .orElseThrow(() -> new AdminBusinessException("NO_DEPLOYED", "No deployed version exists for this code"));
            if (!maxDeployed.getId().equals(unit.getId())) {
                throw new AdminBusinessException("NOT_MAX_DEPLOYED_VERSION",
                        "Only the highest semantic version among deployed versions can be enabled (current highest is " + maxDeployed.getVersion() + ")");
            }
            disableOtherVersions(unit.getCode(), unit.getVersion(), operatorId);
        }

        unit.setEnabled(enabled);
        FunctionUnit saved = functionUnitRepository.save(unit);
        
        log.info("Function unit {} (code: {}, version: {}) status changed from {} to {} by operator: {}, reason: {}, timestamp: {}", 
                functionUnitId, unit.getCode(), unit.getVersion(), oldStatus, newStatus, operatorId, reason, Instant.now());
        
        return saved;
    }
    
    /**
     * 获取已部署且启用的功能单元列表
     */
    public Page<FunctionUnit> listDeployedAndEnabledFunctionUnits(Pageable pageable) {
        return functionUnitRepository.findByStatusAndEnabled(FunctionUnitStatus.DEPLOYED, true, pageable);
    }
    
    /**
     * 获取每个功能单元 code 的最新已部署版本
     * 按 code 分组，使用 SemanticVersion 比较保留每组版本号最高的记录
     */
    public List<FunctionUnit> listLatestDeployedFunctionUnits() {
        List<FunctionUnit> allDeployed = functionUnitRepository.findByStatusAndEnabled(
                FunctionUnitStatus.DEPLOYED, true);
        
        if (allDeployed.isEmpty()) {
            return Collections.emptyList();
        }
        
        // 按 code 分组，每组保留语义化版本号最高的记录
        Map<String, FunctionUnit> latestByCode = new HashMap<>();
        for (FunctionUnit unit : allDeployed) {
            String code = unit.getCode();
            FunctionUnit existing = latestByCode.get(code);
            if (existing == null) {
                latestByCode.put(code, unit);
            } else {
                try {
                    SemanticVersion currentVersion = SemanticVersion.parse(unit.getVersion());
                    SemanticVersion existingVersion = SemanticVersion.parse(existing.getVersion());
                    if (currentVersion.compareTo(existingVersion) > 0) {
                        latestByCode.put(code, unit);
                    }
                } catch (IllegalArgumentException e) {
                    // 版本号格式不合法，降级为字典序比较
                    log.warn("Invalid semantic version format, falling back to lexicographic comparison: {} vs {}", 
                            unit.getVersion(), existing.getVersion());
                    if (unit.getVersion().compareTo(existing.getVersion()) > 0) {
                        latestByCode.put(code, unit);
                    }
                }
            }
        }
        
        return new ArrayList<>(latestByCode.values());
    }

    /**
     * 门户发起流程：当前 code 下「已部署 + 已启用」中语义版本最高的一条目录记录（若无则 empty）
     */
    public Optional<FunctionUnit> getActiveCatalogForPortalStart(String code) {
        List<FunctionUnit> deployed = functionUnitRepository.findByCodeAndStatus(code, FunctionUnitStatus.DEPLOYED);
        List<FunctionUnit> enabledDeployed = deployed.stream().filter(FunctionUnit::isEnabled).toList();
        if (enabledDeployed.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(enabledDeployed.stream().max(this::compareBySemver).orElseThrow());
    }

    private int compareBySemver(FunctionUnit a, FunctionUnit b) {
        try {
            return SemanticVersion.parse(a.getVersion()).compareTo(SemanticVersion.parse(b.getVersion()));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid semver {} / {}, using lexicographic order", a.getVersion(), b.getVersion());
            return a.getVersion().compareTo(b.getVersion());
        }
    }

    private Optional<FunctionUnit> pickMaxSemverAmongDeployed(String code) {
        List<FunctionUnit> deployed = functionUnitRepository.findByCodeAndStatus(code, FunctionUnitStatus.DEPLOYED);
        if (deployed.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(deployed.stream().max(this::compareBySemver).orElseThrow());
    }

    /**
     * 按功能单元目录 ID 清理门户运行数据（并驱动引擎 purge），供回滚/废弃编排
     */
    public Map<String, Object> purgeRuntimeDataForCatalog(String catalogId) {
        if (userPortalInternalApiToken == null || userPortalInternalApiToken.isBlank()) {
            throw new AdminBusinessException("CONFIG", "user-portal.internal-api-token is not configured, cannot invoke portal cleanup for runtime data");
        }
        String base = userPortalBaseUrl != null ? userPortalBaseUrl.replaceAll("/$", "") : "";
        String url = base + "/internal/runtime/purge-by-catalog";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Token", userPortalInternalApiToken);
        Map<String, String> body = Map.of("catalogId", catalogId);
        try {
            ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                throw new AdminBusinessException("PORTAL_PURGE_FAILED", "Portal cleanup returned error: " + resp.getStatusCode());
            }
            return ApiResponseBodyUnwrap.unwrapDataMap(resp.getBody());
        } catch (AdminBusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new AdminBusinessException("PORTAL_PURGE_FAILED", "Failed to invoke portal cleanup: " + e.getMessage(), e);
        }
    }
    
    // ==================== 新增版本管理方法 ====================
    
    /**
     * 禁用指定功能单元代码的其他版本
     * @param code 功能单元代码
     * @param enabledVersion 保持启用的版本号（如果为null则禁用所有版本）
     * @param operatorId 操作人ID
     * @return 被禁用的版本号列表
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public List<String> disableOtherVersions(String code, String enabledVersion, String operatorId) {
        log.info("Disabling other versions for code: {}, keeping enabled: {}, operator: {}", 
                code, enabledVersion, operatorId);
        
        List<String> disabledVersions = new ArrayList<>();
        
        // 查询该代码的所有版本
        List<FunctionUnit> allVersions = functionUnitRepository.findAllByCodeOrderByVersionDesc(code);
        
        for (FunctionUnit unit : allVersions) {
            // 如果不是要保持启用的版本，且当前是启用状态，则禁用
            // 使用 trim 比对：manifest / 路径变量与入库 version 若存在首尾空白，勿误判为「其他版本」而把当前部署行关掉
            if (!shouldKeepVersionEnabled(unit, enabledVersion) && unit.isEnabled()) {
                unit.setEnabled(false);
                functionUnitRepository.save(unit);
                disabledVersions.add(unit.getVersion());
                log.info("Disabled version {} of function unit {}", unit.getVersion(), code);
            }
        }
        
        // 强制刷新到数据库，确保约束检查时旧版本已禁用
        functionUnitRepository.flush();
        
        log.info("Disabled {} versions for function unit {}: {}", 
                disabledVersions.size(), code, disabledVersions);
        
        return disabledVersions;
    }

    /**
     * 在 {@link #disableOtherVersions(String, String, String)} 中判断某条记录是否为「应保持启用」的版本。
     * <p>{@code keepEnabledVersion == null} 表示导入前「关掉同一 code 下所有已启用行」（随后插入新版本）；此时应对每一行返回 false。
     */
    private static boolean shouldKeepVersionEnabled(FunctionUnit unit, String keepEnabledVersion) {
        if (keepEnabledVersion == null) {
            return false;
        }
        String v = unit.getVersion();
        if (v == null) {
            return false;
        }
        return v.trim().equals(keepEnabledVersion.trim());
    }

    /**
     * 工作站「一键部署」链路末尾补齐启用状态：禁用同 code 其他版本后，将<strong>本次部署</strong>的行设为启用。
     * <p>若存在更高「已部署」语义版本，仍启用当前行并记告警（与设计器发布预期一致；门户发起请以业务规则为准）。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FunctionUnit finalizeOneClickDeployEnable(String functionUnitId, String operatorId) {
        FunctionUnit unit = functionUnitRepository.findById(functionUnitId)
                .orElseThrow(() -> new FunctionUnitNotFoundException(functionUnitId));

        if (unit.getStatus() != FunctionUnitStatus.DEPLOYED) {
            throw new AdminBusinessException("INVALID_STATUS",
                    "Cannot finalize enable: function unit is not DEPLOYED (status=" + unit.getStatus() + ")");
        }

        Optional<FunctionUnit> maxDeployed = pickMaxSemverAmongDeployed(unit.getCode());
        if (maxDeployed.isEmpty()) {
            throw new AdminBusinessException("NO_DEPLOYED", "No deployed version exists for code: " + unit.getCode());
        }
        if (!maxDeployed.get().getId().equals(unit.getId())) {
            log.warn(
                    "One-click deploy: enabling {}:{} for operator {} while higher deployed semver exists ({})",
                    unit.getCode(), unit.getVersion(), operatorId, maxDeployed.get().getVersion());
        }

        disableOtherVersions(unit.getCode(), unit.getVersion(), operatorId);
        FunctionUnit fresh = functionUnitRepository.findById(functionUnitId)
                .orElseThrow(() -> new FunctionUnitNotFoundException(functionUnitId));
        fresh.setEnabled(true);
        return functionUnitRepository.save(fresh);
    }
    
    /**
     * 获取当前启用的版本
     * @param code 功能单元代码
     * @return 当前启用的功能单元，如果没有则返回空
     */
    public Optional<FunctionUnit> getEnabledVersion(String code) {
        return functionUnitRepository.findByCodeAndEnabledTrue(code);
    }
    
    /**
     * 激活指定版本（与 {@link #setEnabled(String, boolean, String, String)} 同一套规则）
     * <ul>
     *   <li>目标必须为 {@link FunctionUnitStatus#DEPLOYED}</li>
     *   <li>目标必须为该 code 下全部已部署记录中语义版本最高者</li>
     * </ul>
     */
    @Transactional
    public FunctionUnit activateVersion(String code, String targetVersion, String operatorId) {
        log.info("Activating version {} for code: {}, operator: {}", targetVersion, code, operatorId);

        FunctionUnit targetUnit = functionUnitRepository.findByCodeAndVersion(code, targetVersion)
                .orElseThrow(() -> new FunctionUnitNotFoundException(
                        "Function unit version not found: " + code + ":" + targetVersion));

        if (targetUnit.getStatus() != FunctionUnitStatus.DEPLOYED) {
            throw new AdminBusinessException("INVALID_STATUS",
                    "Only DEPLOYED versions can be activated for portal initiation. Current status: " + targetUnit.getStatus());
        }

        FunctionUnit maxDeployed = pickMaxSemverAmongDeployed(code)
                .orElseThrow(() -> new AdminBusinessException("NO_DEPLOYED", "No deployed version exists for this code"));
        if (!maxDeployed.getId().equals(targetUnit.getId())) {
            throw new AdminBusinessException("NOT_MAX_DEPLOYED_VERSION",
                    "Only the highest semantic version among deployed versions can be activated (current highest is " + maxDeployed.getVersion() + ")");
        }

        disableOtherVersions(code, targetVersion, operatorId);

        FunctionUnit fresh = functionUnitRepository.findByCodeAndVersion(code, targetVersion)
                .orElseThrow(() -> new FunctionUnitNotFoundException(
                        "Function unit version not found: " + code + ":" + targetVersion));
        fresh.setEnabled(true);
        FunctionUnit activated = functionUnitRepository.save(fresh);

        log.info("Successfully activated version {} for function unit {}", targetVersion, code);

        return activated;
    }
    
    /**
     * 获取版本历史（包含启用状态）
     * @param code 功能单元代码
     * @return 版本历史列表
     */
    public List<com.admin.dto.response.VersionHistoryEntry> getVersionHistoryWithStatus(String code) {
        List<FunctionUnit> versions = functionUnitRepository.findAllByCodeOrderByVersionDesc(code);
        List<com.admin.dto.response.VersionHistoryEntry> history = new ArrayList<>();
        
        for (int i = 0; i < versions.size(); i++) {
            FunctionUnit current = versions.get(i);
            
            com.admin.dto.response.VersionHistoryEntry entry = 
                    com.admin.dto.response.VersionHistoryEntry.builder()
                    .version(current.getVersion())
                    .status(current.getStatus())
                    .enabled(current.getEnabled())
                    .createdAt(current.getCreatedAt())
                    .createdBy(current.getCreatedBy())
                    .deployedAt(current.getDeployedAt())
                    .validatedAt(current.getValidatedAt())
                    .validatedBy(current.getValidatedBy())
                    .isLatest(i == 0)
                    .isCurrentlyEnabled(current.isEnabled())
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
     * 从 BPMN XML 中提取 <process id="..."> 属性值
     */
    private String extractProcessKey(String bpmnXml) {
        try {
            int processStart = bpmnXml.indexOf("<bpmn:process");
            if (processStart == -1) {
                processStart = bpmnXml.indexOf("<process");
            }
            if (processStart != -1) {
                int idStart = bpmnXml.indexOf("id=\"", processStart);
                if (idStart != -1) {
                    idStart += 4;
                    int idEnd = bpmnXml.indexOf("\"", idStart);
                    if (idEnd != -1) {
                        return bpmnXml.substring(idStart, idEnd);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to extract process key from BPMN XML: {}", e.getMessage());
        }
        return null;
    }
}
