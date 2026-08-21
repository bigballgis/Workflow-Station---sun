package com.admin.component;

import com.admin.dto.request.FunctionUnitImportRequest;
import com.admin.dto.response.FunctionUnitInfo;
import com.admin.dto.response.ImportResult;
import com.admin.dto.response.ValidationResult;
import com.admin.entity.ActionDefinition;
import com.admin.entity.FunctionUnit;
import com.admin.entity.FunctionUnitContent;
import com.admin.entity.FunctionUnitDependency;
import com.admin.enums.ContentType;
import com.admin.enums.DependencyType;
import com.admin.enums.FunctionUnitStatus;
import com.admin.repository.ActionDefinitionRepository;
import com.admin.repository.FunctionUnitAccessRepository;
import com.admin.repository.FunctionUnitContentRepository;
import com.admin.repository.FunctionUnitDependencyRepository;
import com.admin.repository.FunctionUnitRepository;
import com.admin.service.FunctionUnitAccessService;
import com.admin.service.FunctionUnitAuditAccessService;
import com.admin.util.ChecksumUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.i18n.I18nService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Function package import: file/package validation, dependency conflict detection,
 * parsing (Developer Workstation ZIP or legacy BPMN) and persistence of the imported unit.
 * Collaborator of {@link FunctionUnitManagerComponent}; package-level inner types
 * {@link FunctionUnitManagerComponent.FunctionPackageContent} etc. stay on the facade.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FunctionUnitImportComponent {

    private final FunctionUnitRepository functionUnitRepository;
    private final FunctionUnitDependencyRepository dependencyRepository;
    private final FunctionUnitContentRepository contentRepository;
    private final FunctionUnitAccessRepository accessRepository;
    private final FunctionUnitAccessService functionUnitAccessService;
    private final FunctionUnitAuditAccessService functionUnitAuditAccessService;
    private final FunctionUnitPackageParser packageParser;
    private final ActionDefinitionRepository actionDefinitionRepository;
    private final FunctionUnitVersionComponent versionComponent;
    private final RelationTableStructureImporter relationTableStructureImporter;
    private final ObjectMapper objectMapper;
    private final I18nService i18nService;

    /** Import function package */
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
            FunctionUnitManagerComponent.FunctionPackageContent packageContent = parsed.getPackageContent();

            // 3. Catalog import rules:
            //    - Same name + same (code, version) → overwrite that row in place (re-import)
            //    - Same name + new semver from DW publish → new catalog row (portal sees new version)
            //    - Name absent → new unit; (code, version) taken under another name → next free patch
            FunctionUnit existingByName = functionUnitRepository.findLatestByName(packageContent.getName()).orElse(null);
            FunctionUnit overwriteTarget = null;
            boolean versioned = false;

            if (existingByName != null) {
                packageContent.setCode(existingByName.getCode());
                String incomingVersion = packageContent.getVersion();
                Optional<FunctionUnit> existingSameVersion = functionUnitRepository.findByCodeAndVersion(
                        existingByName.getCode(), incomingVersion);
                versioned = true;
                if (existingSameVersion.isPresent()) {
                    overwriteTarget = existingSameVersion.get();
                } else {
                    log.info("Importing new catalog version {} for code {} (latest by name was {})",
                            incomingVersion, existingByName.getCode(), existingByName.getVersion());
                }
            } else if (packageContent.getCode() != null
                    && functionUnitRepository.existsByCodeAndVersion(
                            packageContent.getCode(), packageContent.getVersion())) {
                packageContent.setVersion(nextAvailableVersion(packageContent.getCode()));
            }

            if (parsed.getIconSvg() != null && request.getIconSvg() == null) {
                request.setIconSvg(parsed.getIconSvg());
            }

            // 4. Detect dependency conflicts
            List<ImportResult.DependencyConflict> conflicts = detectConflicts(packageContent);

            // 5. Create a new unit, or overwrite the matching (code, version) row in place.
            FunctionUnit functionUnit = overwriteTarget != null
                    ? overwriteFunctionUnit(overwriteTarget, packageContent, request, importerId)
                    : createFunctionUnit(packageContent, request, importerId);

            if (overwriteTarget == null && existingByName != null && packageContent.getCode() != null) {
                functionUnitAccessService.copyAccessFromSiblingVersions(
                        packageContent.getCode(), functionUnit.getId());
                // Audit grants live on the catalog row too; without this a redeploy
                // silently strips reviewers of access.
                functionUnitAuditAccessService.copyAuditAccessFromSiblingVersions(
                        packageContent.getCode(), functionUnit.getId());
            }

            // 7. Save dependencies
            saveDependencies(functionUnit, packageContent.getDependencies());

            // 8. Save contents (process, tables) and forms
            saveContents(functionUnit, packageContent.getContents());
            if (parsed.getForms() != null) {
                saveContents(functionUnit, parsed.getForms());
            }
            saveImportedActions(functionUnit.getId(), parsed.getActions());

            // Import relation-table (rt_) structures: absent name → INIT (version 1),
            // existing name → UPDATED with version+1.
            relationTableStructureImporter.importRelationTables(parsed.getRelationTables(), importerId);

            log.info("Function package imported successfully: {}", functionUnit.getId());

            FunctionUnitInfo info = FunctionUnitInfo.fromEntity(functionUnit);
            if (!conflicts.isEmpty()) {
                ImportResult conflictResult = ImportResult.conflictDetected(info, conflicts);
                conflictResult.setVersioned(versioned);
                return conflictResult;
            }
            return ImportResult.success(info, versioned);

        } catch (Exception e) {
            log.error("Failed to import function package", e);
            return ImportResult.failure(i18nService.getMessage("admin.fu.import_failed", e.getMessage()));
        }
    }

    /** Validate function package */
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

    /** Validate file format */
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

    /** Validate integrity */
    private boolean validateIntegrity(FunctionUnitImportRequest request, ValidationResult result) {
        // Simplified: reject empty file content
        if (request.getFileContent() != null && request.getFileContent().isEmpty()) {
            result.addError("INTEGRITY", "fileContent", i18nService.getMessage("admin.fu.file_content_empty"));
            return false;
        }
        return true;
    }

    /** Validate digital signature */
    private boolean validateDigitalSignature(FunctionUnitImportRequest request, ValidationResult result) {
        // Simplified: always returns true
        // Production should verify digital signature
        return true;
    }

    /** Validate BPMN syntax */
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

    /** Validate data table structure */
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

    /** Validate form configuration */
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

    /** Detect dependency conflicts */
    public List<ImportResult.DependencyConflict> detectConflicts(
            FunctionUnitManagerComponent.FunctionPackageContent packageContent) {
        List<ImportResult.DependencyConflict> conflicts = new ArrayList<>();

        for (FunctionUnitManagerComponent.DependencyInfo dep : packageContent.getDependencies()) {
            // Check dependencies exist
            Optional<FunctionUnit> existing = functionUnitRepository.findLatestByCode(dep.getCode());
            if (existing.isPresent()) {
                String existingVersion = existing.get().getVersion();
                if (!versionComponent.isVersionCompatible(dep.getVersion(), existingVersion)) {
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

    /** Parse import request: prefer ZIP (Base64) from Developer Workstation export. */
    private FunctionUnitPackageParser.ParsedImportPackage parseImportRequest(FunctionUnitImportRequest request)
            throws IOException {
        if (request.getFileContent() != null && !request.getFileContent().isBlank()
                && request.getFileName() != null
                && request.getFileName().toLowerCase().endsWith(".zip")) {
            try {
                FunctionUnitPackageParser.ParsedImportPackage parsed =
                        packageParser.parseBase64Zip(request.getFileContent());
                FunctionUnitManagerComponent.FunctionPackageContent content = parsed.getPackageContent();
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
        FunctionUnitManagerComponent.FunctionPackageContent legacy = parsePackageContentLegacy(request);
        return FunctionUnitPackageParser.ParsedImportPackage.builder()
                .packageContent(legacy)
                .forms(List.of())
                .actions(List.of())
                .relationTables(List.of())
                .iconSvg(request.getIconSvg())
                .build();
    }

    /** Legacy parse (non-ZIP or raw BPMN text) */
    private FunctionUnitManagerComponent.FunctionPackageContent parsePackageContentLegacy(
            FunctionUnitImportRequest request) {
        // Prefer request code; else derive from file name
        String code = request.getCode() != null && !request.getCode().isEmpty()
                ? request.getCode()
                : extractCodeFromFileName(request.getFileName());
        String version = request.getVersion() != null ? request.getVersion() : "1.0.0";
        String name = request.getName() != null ? request.getName() : code;
        String description = request.getDescription();

        List<FunctionUnitManagerComponent.DependencyInfo> dependencies = new ArrayList<>();
        List<FunctionUnitManagerComponent.ContentInfo> contents = new ArrayList<>();

        // If file content present, attempt parse
        if (request.getFileContent() != null && !request.getFileContent().isEmpty()) {
            // Simplified: assume content is BPMN process definition
            contents.add(FunctionUnitManagerComponent.ContentInfo.builder()
                    .contentType(ContentType.PROCESS)
                    .contentName("main-process.bpmn")
                    .contentPath("/processes/main-process.bpmn")
                    .contentData(request.getFileContent())
                    .build());
        }

        return FunctionUnitManagerComponent.FunctionPackageContent.builder()
                .code(code)
                .version(version)
                .name(name)
                .description(description)
                .dependencies(dependencies)
                .contents(contents)
                .build();
    }

    /** Extract code from file name */
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
     * Next free version for a code when adding a version on same-name import.
     * Bumps the latest existing version's patch number, then walks up until the
     * (code, version) pair is free (defensive against gaps/duplicates).
     */
    private String nextAvailableVersion(String code) {
        String latest = functionUnitRepository.findLatestByCode(code)
                .map(FunctionUnit::getVersion)
                .orElse("1.0.0");
        String candidate = bumpPatch(latest);
        while (functionUnitRepository.existsByCodeAndVersion(code, candidate)) {
            candidate = bumpPatch(candidate);
        }
        return candidate;
    }

    /** Increment the patch component of a MAJOR.MINOR.PATCH version; falls back to 1.0.0 on bad input. */
    private String bumpPatch(String version) {
        if (version == null || version.isBlank()) {
            return "1.0.0";
        }
        String clean = version.split("-")[0];
        String[] parts = clean.split("\\.");
        if (parts.length < 3) {
            return "1.0.0";
        }
        try {
            int patch = Integer.parseInt(parts[2]) + 1;
            return parts[0] + "." + parts[1] + "." + patch;
        } catch (NumberFormatException e) {
            return "1.0.0";
        }
    }

    /**
     * Overwrite an existing (code, version) row in place: clear its old child content
     * (contents/dependencies; actions are cleared by saveImportedActions) and refresh metadata,
     * keeping the same id, code, version, and Admin Center Access config.
     */
    private FunctionUnit overwriteFunctionUnit(FunctionUnit existing,
                                               FunctionUnitManagerComponent.FunctionPackageContent packageContent,
                                               FunctionUnitImportRequest request,
                                               String importerId) {
        // Clear old child content so the imported package fully replaces it (Access is admin-managed, preserve it).
        contentRepository.deleteByFunctionUnitId(existing.getId());
        dependencyRepository.deleteByFunctionUnitId(existing.getId());
        functionUnitRepository.flush();

        existing.setDescription(packageContent.getDescription());
        // Adopt the published version from the export manifest so the deployed row (and the portal
        // catalog, which dedupes by code keeping the highest semver) reflects the real version.
        if (packageContent.getVersion() != null && !packageContent.getVersion().isBlank()) {
            existing.setVersion(packageContent.getVersion());
        }
        existing.setPackagePath(request.getFilePath());
        existing.setPackageSize(request.getFileContent() != null ? (long) request.getFileContent().length() : 0L);
        existing.setChecksum(ChecksumUtils.sha256Hex(request.getFileContent()));
        existing.setStatus(FunctionUnitStatus.DRAFT);
        existing.setImportedAt(Instant.now());
        existing.setImportedBy(importerId);
        if (request.getIconSvg() != null) {
            existing.setIconSvg(request.getIconSvg());
        }
        return functionUnitRepository.save(existing);
    }

    /** Create function unit */
    private FunctionUnit createFunctionUnit(FunctionUnitManagerComponent.FunctionPackageContent packageContent,
                                            FunctionUnitImportRequest request,
                                            String importerId) {
        String checksum = ChecksumUtils.sha256Hex(request.getFileContent());

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

    /** Save dependencies */
    private void saveDependencies(FunctionUnit functionUnit,
                                  List<FunctionUnitManagerComponent.DependencyInfo> dependencies) {
        for (FunctionUnitManagerComponent.DependencyInfo dep : dependencies) {
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

    /** Save contents */
    private void saveContents(FunctionUnit functionUnit,
                              List<FunctionUnitManagerComponent.ContentInfo> contents) {
        for (FunctionUnitManagerComponent.ContentInfo content : contents) {
            String contentChecksum = ChecksumUtils.sha256Hex(content.getContentData());

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

    /** Delete existing version */
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
}
