package com.admin.component;

import com.admin.dto.request.FunctionUnitImportRequest;
import com.admin.dto.response.FunctionUnitContentResponse;
import com.admin.dto.response.FunctionUnitContentItemDTO;
import com.admin.dto.response.FunctionUnitInfo;
import com.admin.dto.response.FormContentDTO;
import com.admin.dto.response.ProcessContentDTO;
import com.admin.dto.response.DataTableContentDTO;
import com.admin.dto.response.TableBindingDTO;
import com.admin.dto.response.TableFieldDefinitionDTO;
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
import com.platform.common.i18n.I18nService;
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
 * Function unit management component.
 * Handles function package import, validation, dependency checks, and management.
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
    private final I18nService i18nService;

    @Value("${user-portal.base-url:http://localhost:8082/api/portal}")
    private String userPortalBaseUrl;

    @Value("${user-portal.internal-api-token:}")
    private String userPortalInternalApiToken;
    
    // Semantic version regex
    private static final Pattern VERSION_PATTERN = Pattern.compile("^\\d+\\.\\d+\\.\\d+(-[a-zA-Z0-9]+)?$");
        
    /**
     * Import function package
     */
    @Transactional
    public ImportResult importFunctionPackage(FunctionUnitImportRequest request, String importerId) {
        log.info("Importing function package: {}", request.getFileName());
        
        try {
            // 1. Validate file format and integrity
            ValidationResult validationResult = validatePackage(request);
            if (!validationResult.isValid()) {
                return ImportResult.validationFailed(validationResult.getErrors());
            }
            
            // 2. Parse package (Developer Workstation ZIP export)
            FunctionUnitPackageParser.ParsedImportPackage parsed = parseImportRequest(request);
            FunctionPackageContent packageContent = parsed.getPackageContent();

            // 3. Check whether version already exists
            if (functionUnitRepository.existsByCodeAndVersion(packageContent.getCode(), packageContent.getVersion())) {
                boolean shouldOverwrite = request.isOverwrite()
                        || functionUnitRepository.findByCodeAndVersion(packageContent.getCode(), packageContent.getVersion())
                        .map(u -> u.getStatus() == FunctionUnitStatus.ARCHIVED)
                        .orElse(false);
                if (!shouldOverwrite) {
                    return ImportResult.failure("Function unit version already exists: "
                            + packageContent.getCode() + ":" + packageContent.getVersion()
                            + i18nService.getMessage("admin.fu.version_exists_suffix"));
                }
                deleteExistingVersion(packageContent.getCode(), packageContent.getVersion());
            }

            if (parsed.getIconSvg() != null && request.getIconSvg() == null) {
                request.setIconSvg(parsed.getIconSvg());
            }
            
            // 4. Detect dependency conflicts
            List<ImportResult.DependencyConflict> conflicts = detectConflicts(packageContent);
            
            // 5. Create function unit(DRAFT after import; enable after validation/deploy)
            FunctionUnit functionUnit = createFunctionUnit(packageContent, request, importerId);
            
            // 7. Save dependencies
            saveDependencies(functionUnit, packageContent.getDependencies());
            
            // 8. Save contents (process, tables) and forms
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
            return ImportResult.failure(i18nService.getMessage("admin.fu.import_failed", e.getMessage()));
        }
    }

    
    /**
     * Validate function package
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
        
        // 1. Validate file format
        if (!validateFileFormat(request, result)) {
            result.setFileFormatValid(false);
        }
        
        // 2. Validate integrity
        if (!validateIntegrity(request, result)) {
            result.setIntegrityValid(false);
        }
        
        // 3. Validate digital signature (if present)
        if (request.getFileContent() != null && !validateDigitalSignature(request, result)) {
            result.setSignatureValid(false);
            result.addWarning(i18nService.getMessage("admin.fu.signature_warning"));
        }
        
        return result;
    }
    
    /**
     * Validate file format
     */
    private boolean validateFileFormat(FunctionUnitImportRequest request, ValidationResult result) {
        if (request.getFileName() == null || request.getFileName().isEmpty()) {
            result.addError("FILE_FORMAT", "fileName", i18nService.getMessage("admin.fu.file_name_required"));
            return false;
        }
        
        // Check file extension
        String fileName = request.getFileName().toLowerCase();
        if (!fileName.endsWith(".zip") && !fileName.endsWith(".fpkg")) {
            result.addError("FILE_FORMAT", "fileName", i18nService.getMessage("admin.fu.file_format_unsupported"));
            return false;
        }
        
        // Check file content
        if (request.getFileContent() == null && request.getFilePath() == null) {
            result.addError("FILE_FORMAT", "fileContent", i18nService.getMessage("admin.fu.file_content_required"));
            return false;
        }
        
        return true;
    }
    
    /**
     * Validate integrity
     */
    private boolean validateIntegrity(FunctionUnitImportRequest request, ValidationResult result) {
        // Simplified: reject empty file content
        if (request.getFileContent() != null && request.getFileContent().isEmpty()) {
            result.addError("INTEGRITY", "fileContent", i18nService.getMessage("admin.fu.file_content_empty"));
            return false;
        }
        return true;
    }
    
    /**
     * Validate digital signature
     */
    private boolean validateDigitalSignature(FunctionUnitImportRequest request, ValidationResult result) {
        // Simplified: always returns true
        // Production should verify digital signature
        return true;
    }
    
    /**
     * Validate BPMN syntax
     */
    public boolean validateBpmnSyntax(String bpmnContent, ValidationResult result) {
        if (bpmnContent == null || bpmnContent.isEmpty()) {
            result.addError("BPMN_SYNTAX", "content", i18nService.getMessage("admin.fu.bpmn_empty"));
            return false;
        }
        
        // Simplified: check basic BPMN structure
        if (!bpmnContent.contains("definitions") || !bpmnContent.contains("process")) {
            result.addError("BPMN_SYNTAX", "content", i18nService.getMessage("admin.fu.bpmn_invalid"));
            return false;
        }
        
        return true;
    }
    
    /**
     * Validate data table structure
     */
    public boolean validateDataTableStructure(String tableDefinition, ValidationResult result) {
        if (tableDefinition == null || tableDefinition.isEmpty()) {
            return true; // data table definition optional
        }
        
        // Simplified: check basic SQL structure
        String upperDef = tableDefinition.toUpperCase();
        if (!upperDef.contains("CREATE TABLE") && !upperDef.contains("ALTER TABLE")) {
            result.addError("DATA_TABLE", "definition", i18nService.getMessage("admin.fu.data_table_invalid"));
            return false;
        }
        
        return true;
    }
    
    /**
     * Validate form configuration
     */
    public boolean validateFormConfig(String formConfig, ValidationResult result) {
        if (formConfig == null || formConfig.isEmpty()) {
            return true; // form configuration optional
        }
        
        // Simplified: check JSON format
        if (!formConfig.trim().startsWith("{") && !formConfig.trim().startsWith("[")) {
            result.addError("FORM_CONFIG", "config", i18nService.getMessage("admin.fu.form_config_invalid"));
            return false;
        }
        
        return true;
    }
    
    /**
     * Detect dependency conflicts
     */
    public List<ImportResult.DependencyConflict> detectConflicts(FunctionPackageContent packageContent) {
        List<ImportResult.DependencyConflict> conflicts = new ArrayList<>();
        
        for (DependencyInfo dep : packageContent.getDependencies()) {
            // Check dependencies exist
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
     * Parse import request: prefer ZIP (Base64) from Developer Workstation export.
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
     * Legacy parse (non-ZIP or raw BPMN text)
     */
    private FunctionPackageContent parsePackageContentLegacy(FunctionUnitImportRequest request) {
        // Prefer request code; else derive from file name
        String code = request.getCode() != null && !request.getCode().isEmpty() 
                ? request.getCode() 
                : extractCodeFromFileName(request.getFileName());
        String version = request.getVersion() != null ? request.getVersion() : "1.0.0";
        String name = request.getName() != null ? request.getName() : code;
        String description = request.getDescription();
        
        List<DependencyInfo> dependencies = new ArrayList<>();
        List<ContentInfo> contents = new ArrayList<>();
        
        // If file content present, attempt parse
        if (request.getFileContent() != null && !request.getFileContent().isEmpty()) {
            // Simplified: assume content is BPMN process definition
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
     * Extract code from file name
     */
    private String extractCodeFromFileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "unknown";
        }
        // Strip extension
        String name = fileName;
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            name = fileName.substring(0, dotIndex);
        }
        // Strip version suffix if present
        int dashIndex = name.lastIndexOf('-');
        if (dashIndex > 0 && name.substring(dashIndex + 1).matches("\\d+\\.\\d+\\.\\d+.*")) {
            name = name.substring(0, dashIndex);
        }
        return name;
    }
    
    /**
     * Create function unit
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
     * Save dependencies
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
     * Save contents
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
     * Add function unit content
     */
    @Transactional
    public void addFunctionUnitContent(String functionUnitId, ContentType contentType, 
                                       String contentName, String contentData) {
        addFunctionUnitContent(functionUnitId, contentType, contentName, contentData, null);
    }
    
    /**
     * Add function unit content(with source id)
     * @param sourceId Source content id (e.g. developer-workstation dw_form_definitions.id)
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
     * Delete existing version
     */
    @Transactional
    public void deleteExistingVersion(String code, String version) {
        Optional<FunctionUnit> existing = functionUnitRepository.findByCodeAndVersion(code, version);
        if (existing.isPresent()) {
            FunctionUnit unit = existing.get();
            // Delete related access permissions
            accessRepository.deleteByFunctionUnitId(unit.getId());
            // Delete related contents
            contentRepository.deleteByFunctionUnitId(unit.getId());
            // Delete related dependencies
            dependencyRepository.deleteByFunctionUnitId(unit.getId());
            actionDefinitionRepository.deleteByFunctionUnitId(unit.getId());
            // Delete function unit
            functionUnitRepository.delete(unit);
            // Flush so deletes complete before subsequent inserts
            functionUnitRepository.flush();
            log.info("Deleted existing function unit version: {}:{}", code, version);
        }
    }
    
    /**
     * Check version compatibility
     */
    public boolean isVersionCompatible(String requiredVersion, String existingVersion) {
        if (requiredVersion == null || existingVersion == null) {
            return false;
        }
        
        // Parse version number
        int[] required = parseVersion(requiredVersion);
        int[] existing = parseVersion(existingVersion);
        
        if (required == null || existing == null) {
            return requiredVersion.equals(existingVersion);
        }
        
        // Major version must match
        if (required[0] != existing[0]) {
            return false;
        }
        
        // Existing minor must be >= required minor
        if (existing[1] < required[1]) {
            return false;
        }
        
        // If minor equal, patch must be >= required patch
        if (existing[1] == required[1] && existing[2] < required[2]) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Parse version number
     */
    private int[] parseVersion(String version) {
        if (version == null) {
            return null;
        }
        
        // Strip pre-release tag
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
     * Compute checksum
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
     * Get function unit by id
     */
    public FunctionUnit getFunctionUnitById(String id) {
        return functionUnitRepository.findById(id)
                .orElseThrow(() -> new FunctionUnitNotFoundException(
                        i18nService.getMessage("admin.fu.not_found_by_id", id)));
    }
    
    /**
     * Get function unit by process definition key
     * Locate via content whose flowable_process_definition_id starts with processKey:
     */
    @Transactional(readOnly = true)
    public FunctionUnit getFunctionUnitByProcessKey(String processKey) {
        List<com.admin.entity.FunctionUnitContent> results = contentRepository.findAllByProcessDefinitionKey(processKey);
        if (results.isEmpty()) {
            throw new FunctionUnitNotFoundException("Function unit not found for process definition key: " + processKey);
        }
        // List ordered by content.createdAt DESC; newest row may be on a disabled catalog version.
        // For portal tasks/assignment by processDefinitionKey, prefer enabled catalog row to avoid false disabled.
        for (FunctionUnitContent c : results) {
            FunctionUnit fu = c.getFunctionUnit();
            if (fu != null && Boolean.TRUE.equals(fu.getEnabled())) {
                return fu;
            }
        }
        return results.get(0).getFunctionUnit();
    }
    
    /**
     * Save function unit
     */
    @Transactional
    public FunctionUnit saveFunctionUnit(FunctionUnit functionUnit) {
        return functionUnitRepository.save(functionUnit);
    }
    
    /**
     * Get function unit by code and version
     */
    public FunctionUnit getFunctionUnitByCodeAndVersion(String code, String version) {
        return functionUnitRepository.findByCodeAndVersion(code, version)
                .orElseThrow(() -> new FunctionUnitNotFoundException(
                        i18nService.getMessage("admin.fu.not_found_by_code", code, version)));
    }
    
    /**
     * Get all contents for function unit
     */
    public List<FunctionUnitContent> getFunctionUnitContents(String functionUnitId) {
        return contentRepository.findByFunctionUnitId(functionUnitId);
    }

    /**
     * Get function unit contents filtered by type.
     * <p>null type returns all; valid type filters; invalid type throws AdminBusinessException.
     *
     * <p><b>Validates: Requirements 35.1, 35.2, 35.3</b>
     *
     * @param functionUnitId Function unit id
     * @param type           Content type string (optional), e.g. "FORM", "PROCESS", "DATA_TABLE"
     * @return List of content item DTOs
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
     * Assemble full function unit content (BPMN, forms, data tables, etc.).
     * <p>Includes Base64 BPMN decode, latest config_json from dw_form_definitions,
     * load tableBindings and attach to form content.
     *
     * <p><b>Validates: Requirements 6.1, 6.2, 6.3</b>
     *
     * @param id Function unit id
     * @return Full content response DTO
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

    private void enrichBindingsWithFieldDefinitions(List<TableBindingDTO> bindings) {
        if (bindings == null || bindings.isEmpty()) {
            return;
        }
        Set<Long> dwTableIds = new HashSet<>();
        Set<Long> rtTableIds = new HashSet<>();
        for (TableBindingDTO binding : bindings) {
            if (binding.getTableId() == null) {
                continue;
            }
            if ("RELATION".equalsIgnoreCase(binding.getTableType())) {
                rtTableIds.add(binding.getTableId());
            } else {
                dwTableIds.add(binding.getTableId());
            }
        }
        Map<Long, List<TableFieldDefinitionDTO>> dwFields = loadDwFieldDefinitions(dwTableIds);
        Map<Long, List<TableFieldDefinitionDTO>> rtFields = loadRtFieldDefinitions(rtTableIds);
        for (TableBindingDTO binding : bindings) {
            if (binding.getTableId() == null) {
                binding.setFieldDefinitions(Collections.emptyList());
                continue;
            }
            if ("RELATION".equalsIgnoreCase(binding.getTableType())) {
                binding.setFieldDefinitions(rtFields.getOrDefault(binding.getTableId(), Collections.emptyList()));
            } else {
                binding.setFieldDefinitions(dwFields.getOrDefault(binding.getTableId(), Collections.emptyList()));
            }
        }
    }

    private Map<Long, List<TableFieldDefinitionDTO>> loadDwFieldDefinitions(Set<Long> tableIds) {
        return loadFieldDefinitionsFromTable("dw_field_definitions", tableIds);
    }

    private Map<Long, List<TableFieldDefinitionDTO>> loadRtFieldDefinitions(Set<Long> tableIds) {
        return loadFieldDefinitionsFromTable("rt_field_definitions", tableIds);
    }

    private Map<Long, List<TableFieldDefinitionDTO>> loadFieldDefinitionsFromTable(String table, Set<Long> tableIds) {
        if (tableIds == null || tableIds.isEmpty()) {
            return Collections.emptyMap();
        }
        String placeholders = tableIds.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql =
                "SELECT table_id, field_name, is_primary_key, is_foreign_key, ref_table_id, " +
                "       ref_primary_key_fields, pk_generation_json, fk_display_mode " +
                "FROM " + table + " WHERE table_id IN (" + placeholders + ") " +
                "ORDER BY table_id, sort_order NULLS LAST, id";
        Map<Long, List<TableFieldDefinitionDTO>> byTable = new LinkedHashMap<>();
        jdbcTemplate.query(sql, rs -> {
            Long tableId = readNullableLong(rs, "table_id");
            if (tableId == null) {
                return;
            }
            TableFieldDefinitionDTO field = TableFieldDefinitionDTO.builder()
                    .fieldName(rs.getString("field_name"))
                    .isPrimaryKey(rs.getBoolean("is_primary_key"))
                    .isForeignKey(rs.getBoolean("is_foreign_key"))
                    .refTableId(readNullableLong(rs, "ref_table_id"))
                    .refPrimaryKeyFields(readJsonStringList(rs, "ref_primary_key_fields"))
                    .pkGeneration(readJsonMap(rs, "pk_generation_json"))
                    .fkDisplayMode(rs.getString("fk_display_mode"))
                    .build();
            byTable.computeIfAbsent(tableId, k -> new ArrayList<>()).add(field);
        }, tableIds.toArray());
        return byTable;
    }

    private List<String> readJsonStringList(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        String json = rs.getString(column);
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            log.warn("Failed to parse JSON list column {}: {}", column, e.getMessage());
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readJsonMap(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        String json = rs.getString(column);
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            log.warn("Failed to parse JSON map column {}: {}", column, e.getMessage());
            return Collections.emptyMap();
        }
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
                        "       ftb.sub_mode, ftb.foreign_key_field, ftb.binding_link_mode, ftb.sort_order, " +
                        "       COALESCE(td.id, rt.id) as table_id, " +
                        "       COALESCE(td.table_name, rt.table_name) AS table_name, " +
                        "       COALESCE(td.table_display_name, rt.display_name) AS table_display_name, " +
                        "       COALESCE(td.table_type, 'RELATION') as table_type, " +
                        "       COALESCE(td.display_name, rt.description) as table_description, " +
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
                            .bindingLinkMode(rs.getString("binding_link_mode"))
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
                        "       ftb.sub_mode, ftb.foreign_key_field, ftb.binding_link_mode, ftb.sort_order, " +
                        "       COALESCE(td.id, rt.id) as table_id, " +
                        "       COALESCE(td.table_name, rt.table_name) AS table_name, " +
                        "       COALESCE(td.table_display_name, rt.display_name) AS table_display_name, " +
                        "       COALESCE(td.table_type, 'RELATION') as table_type, " +
                        "       COALESCE(td.display_name, rt.description) as table_description, " +
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
                            .bindingLinkMode(rs.getString("binding_link_mode"))
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

            for (List<TableBindingDTO> list : bindingsBySourceId.values()) {
                enrichBindingsWithFieldDefinitions(list);
            }
            for (List<TableBindingDTO> list : bindingsByFormName.values()) {
                enrichBindingsWithFieldDefinitions(list);
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
     * List function units (paged)
     */
    public Page<FunctionUnit> listFunctionUnits(Pageable pageable) {
        return functionUnitRepository.findByStatusNot(FunctionUnitStatus.ARCHIVED, pageable);
    }
    
    /**
     * List archived function units (paged)
     */
    public Page<FunctionUnit> listArchivedFunctionUnits(Pageable pageable) {
        return functionUnitRepository.findByStatus(FunctionUnitStatus.ARCHIVED, pageable);
    }
    
    /**
     * List function units by status
     */
    public Page<FunctionUnit> listFunctionUnitsByStatus(FunctionUnitStatus status, Pageable pageable) {
        return functionUnitRepository.findByStatus(status, pageable);
    }
    
    /**
     * List all versions of function unit
     */
    public List<FunctionUnit> getAllVersions(String code) {
        return functionUnitRepository.findAllByCodeOrderByVersionDesc(code);
    }
    
    /**
     * Validate function unit: structure/dependency/trial deploy; mark VALIDATED on success
     */
    @Transactional
    public ValidationResult validateFunctionUnit(String id, String validatorId) {
        FunctionUnit functionUnit = getFunctionUnitById(id);

        if (!functionUnit.isValidatable()) {
            throw new AdminBusinessException("INVALID_STATUS",
                    i18nService.getMessage("admin.fu.validate_draft_only", functionUnit.getStatus()));
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
     * Deprecate function unit
     */
    @Transactional
    public FunctionUnit deprecateFunctionUnit(String id) {
        FunctionUnit functionUnit = getFunctionUnitById(id);
        functionUnit.markAsDeprecated();
        return functionUnitRepository.save(functionUnit);
    }
    
    // ==================== Version management ====================
    
    /**
     * Validate semantic version format
     */
    public boolean isValidSemanticVersion(String version) {
        return version != null && VERSION_PATTERN.matcher(version).matches();
    }
    
    /**
     * Compare two version strings
     * @return negative if v1 < v2, zero if equal, positive if v1 > v2
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
        
        // Compare major version
        if (version1[0] != version2[0]) {
            return version1[0] - version2[0];
        }
        // Compare minor version
        if (version1[1] != version2[1]) {
            return version1[1] - version2[1];
        }
        // Compare patch version
        return version1[2] - version2[2];
    }
    
    /**
     * Next major version string
     */
    public String getNextMajorVersion(String currentVersion) {
        int[] version = parseVersion(currentVersion);
        if (version == null) {
            return "2.0.0";
        }
        return (version[0] + 1) + ".0.0";
    }
    
    /**
     * Next minor version string
     */
    public String getNextMinorVersion(String currentVersion) {
        int[] version = parseVersion(currentVersion);
        if (version == null) {
            return "1.1.0";
        }
        return version[0] + "." + (version[1] + 1) + ".0";
    }
    
    /**
     * Next patch version string
     */
    public String getNextPatchVersion(String currentVersion) {
        int[] version = parseVersion(currentVersion);
        if (version == null) {
            return "1.0.1";
        }
        return version[0] + "." + version[1] + "." + (version[2] + 1);
    }
    
    /**
     * Latest function unit version (semantic compare; avoids lexicographic errors like 1.0.9 > 1.0.11)
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
     * Latest stable version (VALIDATED or DEPLOYED) via semantic compare
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
     * Check whether upgrade to target version is allowed
     */
    public VersionUpgradeCheck checkVersionUpgrade(String code, String fromVersion, String toVersion) {
        VersionUpgradeCheck check = new VersionUpgradeCheck();
        check.setFromVersion(fromVersion);
        check.setToVersion(toVersion);
        check.setUpgradable(true);
        check.setWarnings(new ArrayList<>());
        check.setErrors(new ArrayList<>());
        
        // Validate version format
        if (!isValidSemanticVersion(fromVersion)) {
            check.addError(i18nService.getMessage("admin.fu.source_version_invalid", fromVersion));
            check.setUpgradable(false);
        }
        if (!isValidSemanticVersion(toVersion)) {
            check.addError(i18nService.getMessage("admin.fu.target_version_invalid", toVersion));
            check.setUpgradable(false);
        }
        
        if (!check.isUpgradable()) {
            return check;
        }
        
        // Check version ordering
        int comparison = compareVersions(fromVersion, toVersion);
        if (comparison >= 0) {
            check.addError(i18nService.getMessage("admin.fu.target_must_gt_source"));
            check.setUpgradable(false);
            return check;
        }
        
        // Check target version exists
        Optional<FunctionUnit> targetUnit = functionUnitRepository.findByCodeAndVersion(code, toVersion);
        if (targetUnit.isEmpty()) {
            check.addError(i18nService.getMessage("admin.fu.target_not_found", code, toVersion));
            check.setUpgradable(false);
            return check;
        }
        
        // Check target version status
        FunctionUnit target = targetUnit.get();
        if (!target.isDeployable()) {
            check.addError(i18nService.getMessage("admin.fu.target_status_invalid", target.getStatus()));
            check.setUpgradable(false);
            return check;
        }
        
        // Check major version change (possible breaking change)
        int[] from = parseVersion(fromVersion);
        int[] to = parseVersion(toVersion);
        if (from != null && to != null && from[0] != to[0]) {
            check.addWarning(i18nService.getMessage("admin.fu.major_version_warning"));
            check.setMajorUpgrade(true);
        }
        
        return check;
    }
    
    /**
     * Version history
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
            
            // Compute change type vs previous version
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
     * Determine version change type
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
     * Create new version from existing
     */
    @Transactional
    public FunctionUnit createNewVersion(String sourceId, String newVersion, String creatorId) {
        FunctionUnit source = getFunctionUnitById(sourceId);
        
        // Validate new version format
        if (!isValidSemanticVersion(newVersion)) {
            throw new AdminBusinessException("INVALID_VERSION", "Invalid version format: " + newVersion);
        }
        
        // Check new version does not already exist
        if (functionUnitRepository.existsByCodeAndVersion(source.getCode(), newVersion)) {
            throw new AdminBusinessException("VERSION_EXISTS", "Version already exists: " + source.getCode() + ":" + newVersion);
        }
        
        // Check version ordering
        if (compareVersions(source.getVersion(), newVersion) >= 0) {
            throw new AdminBusinessException("INVALID_VERSION", "New version must be greater than source version");
        }
        
        // Create new version record
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
        
        // Copy dependencies
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
        
        // Copy contents
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
     * Rollback to target version
     */
    @Transactional
    public FunctionUnit rollbackToVersion(String code, String targetVersion, String operatorId) {
        // Load target version
        FunctionUnit targetUnit = getFunctionUnitByCodeAndVersion(code, targetVersion);
        
        // Check target version status
        if (!targetUnit.isDeployable()) {
            throw new AdminBusinessException("INVALID_STATUS", "Target version status does not allow rollback: " + targetUnit.getStatus());
        }
        
        // Deprecate versions newer than target
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
    
    // ==================== Version management inner types ====================
    
    /**
     * Version upgrade check result
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
     * Version history entry
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
    
    // ==================== Inner types ====================
    
    /**
     * Function package content
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
     * Dependency info
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
     * Content info
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
    
    // ==================== Delete and enable/disable ====================
    
    /**
     * Delete preview
     * Count associated data that would be removed
     */
    @Transactional(readOnly = true)
    public com.admin.dto.response.DeletePreviewResponse getDeletePreview(String functionUnitId) {
        FunctionUnit unit = getFunctionUnitById(functionUnitId);
        
        // Count associated entities by type
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
        
        // Check running instances (simplified; production should call engine)
        boolean hasRunningInstances = false;
        int runningInstanceCount = 0;
        
        return com.admin.dto.response.DeletePreviewResponse.builder()
                .functionUnitId(functionUnitId)
                .functionUnitName(unit.getName())
                .functionUnitCode(unit.getCode())
                .formCount(formCount)
                .processCount(processCount)
                .dataTableCount(dataTableCount)
                .accessConfigCount(0) // filled in by a later query
                .deploymentCount(deploymentCount)
                .dependencyCount(dependencyCount)
                .hasRunningInstances(hasRunningInstances)
                .runningInstanceCount(runningInstanceCount)
                .build();
    }
    
    /**
     * Whether running process instances exist
     */
    public boolean hasRunningInstances(String functionUnitId) {
        // Simplified: should call process engine
        // Returns false here (no running instances)
        return false;
    }
    
    /**
     * Archive function unit by code (all versions; remove portal visibility)
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
     * Restore archived function unit (all ARCHIVED versions under code → DRAFT)
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
     * Cascade-delete function unit and related data (internal/test; public DELETE uses archive)
     */
    @Transactional
    public void deleteFunctionUnitCascade(String functionUnitId) {
        FunctionUnit unit = getFunctionUnitById(functionUnitId);
        
        // Whether running process instances exist
        if (hasRunningInstances(functionUnitId)) {
            throw new AdminBusinessException("HAS_RUNNING_INSTANCES", 
                    "Cannot delete: there are running process instances");
        }
        
        log.info("Deleting function unit cascade: {} ({})", unit.getName(), functionUnitId);
        
        // Delete access permissions
        accessRepository.deleteByFunctionUnitId(functionUnitId);
        
        // Delete contents
        contentRepository.deleteByFunctionUnitId(functionUnitId);
        
        // Delete dependencies
        dependencyRepository.deleteByFunctionUnitId(functionUnitId);
        
        // Delete function unit (cascades deployments)
        functionUnitRepository.delete(unit);
        
        log.info("Function unit deleted successfully: {}", functionUnitId);
    }
    
    /**
     * Set function unit enabled flag
     */
    @Transactional
    public FunctionUnit setEnabled(String functionUnitId, boolean enabled) {
        return setEnabled(functionUnitId, enabled, "system", "Manual status change");
    }
    
    /**
     * Set function unit enabled flag (with operator and reason).
     * @param functionUnitId function unit id
     * @param enabled enabled flag
     * @param operatorId operator id
     * @param reason reason
     * @return updated function unit
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
     * List deployed and enabled function units
     */
    public Page<FunctionUnit> listDeployedAndEnabledFunctionUnits(Pageable pageable) {
        return functionUnitRepository.findByStatusAndEnabled(FunctionUnitStatus.DEPLOYED, true, pageable);
    }
    
    /**
     * Latest deployed version per function unit code
     * Group by code; keep highest SemanticVersion per group
     */
    public List<FunctionUnit> listLatestDeployedFunctionUnits() {
        List<FunctionUnit> allDeployed = functionUnitRepository.findByStatusAndEnabled(
                FunctionUnitStatus.DEPLOYED, true);
        
        if (allDeployed.isEmpty()) {
            return Collections.emptyList();
        }
        
        // Group by code; keep highest semantic version per group
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
                    // Invalid semver; fall back to lexicographic compare
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
     * Portal start: highest semantic version among deployed+enabled for code (empty if none)
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
     * Purge portal runtime data by catalog id (engine purge) for rollback/deprecate flows
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
    
    // ==================== Additional version management APIs ====================
    
    /**
     * Disable other versions for function unit code
     * @param code function unit code
     * @param enabledVersion version to keep enabled (null disables all)
     * @param operatorId operator id
     * @return list of disabled version strings
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public List<String> disableOtherVersions(String code, String enabledVersion, String operatorId) {
        log.info("Disabling other versions for code: {}, keeping enabled: {}, operator: {}", 
                code, enabledVersion, operatorId);
        
        List<String> disabledVersions = new ArrayList<>();
        
        // Load all versions for code
        List<FunctionUnit> allVersions = functionUnitRepository.findAllByCodeOrderByVersionDesc(code);
        
        for (FunctionUnit unit : allVersions) {
            // Disable when not the version to keep and currently enabled
            // Trim compare: leading/trailing whitespace on manifest/path vs stored version must not disable the wrong deployed row
            if (!shouldKeepVersionEnabled(unit, enabledVersion) && unit.isEnabled()) {
                unit.setEnabled(false);
                functionUnitRepository.save(unit);
                disabledVersions.add(unit.getVersion());
                log.info("Disabled version {} of function unit {}", unit.getVersion(), code);
            }
        }
        
        // Flush so DB sees disabled rows before constraint checks
        functionUnitRepository.flush();
        
        log.info("Disabled {} versions for function unit {}: {}", 
                disabledVersions.size(), code, disabledVersions);
        
        return disabledVersions;
    }

    /**
     * In {@link #disableOtherVersions(String, String, String)}, whether a row is the version that should stay enabled.
     * <p>{@code keepEnabledVersion == null} means pre-import: disable all enabled rows for the code (before inserting the new version); always false per row.
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
     * End of workstation one-click deploy: after disabling other versions for the code, enable the <strong>row deployed in this run</strong>.
     * <p>If a higher deployed semantic version exists, still enable this row and log a warning (matches designer publish expectation; portal start follows business rules).
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
     * Currently enabled version for a code.
     * @param code function unit code
     * @return enabled function unit, or empty if none
     */
    public Optional<FunctionUnit> getEnabledVersion(String code) {
        return functionUnitRepository.findByCodeAndEnabledTrue(code);
    }
    
    /**
     * Activate a specific version (same rules as {@link #setEnabled(String, boolean, String, String)}).
     * <ul>
     *   <li>Target must be {@link FunctionUnitStatus#DEPLOYED}</li>
     *   <li>Target must be the highest semantic version among all deployed rows for the code</li>
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
     * Version history including enabled flag.
     * @param code function unit code
     * @return version history entries
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
            
            // Compute change type vs previous version
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
     * Extract {@code <process id="...">} attribute value from BPMN XML.
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
