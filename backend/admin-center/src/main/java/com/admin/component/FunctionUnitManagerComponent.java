package com.admin.component;

import com.admin.dto.request.FunctionUnitImportRequest;
import com.admin.dto.response.DeletePreviewResponse;
import com.admin.dto.response.FunctionUnitContentItemDTO;
import com.admin.dto.response.FunctionUnitContentResponse;
import com.admin.dto.response.ImportResult;
import com.admin.dto.response.ValidationResult;
import com.admin.dto.response.VersionHistoryEntry;
import com.admin.entity.FunctionUnit;
import com.admin.entity.FunctionUnitContent;
import com.admin.enums.ContentType;
import com.admin.enums.FunctionUnitStatus;
import com.admin.util.ChecksumUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.admin.repository.FunctionUnitRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Function unit management facade.
 * <p>Keeps the published API (controllers, parser, tests) stable and delegates to the
 * domain collaborators:
 * <ul>
 *   <li>{@link FunctionUnitImportComponent} — package import/validation/conflict detection</li>
 *   <li>{@link FunctionUnitVersionComponent} — semantic version rules, history, create/rollback</li>
 *   <li>{@link FunctionUnitContentComponent} — content add/query/assembly (+ cache)</li>
 *   <li>{@link FunctionUnitLifecycleComponent} — validate/deprecate/archive/restore/delete/enable/activate</li>
 *   <li>{@link PortalRuntimePurgeClient} — portal runtime purge REST call</li>
 * </ul>
 * Inner types ({@link FunctionPackageContent}, {@link ContentInfo}, {@link DependencyInfo},
 * {@link VersionUpgradeCheck}, {@link VersionHistory}) remain here as the published API.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FunctionUnitManagerComponent {

    private final FunctionUnitRepository functionUnitRepository;
    private final FunctionUnitLookup functionUnitLookup;
    private final FunctionUnitImportComponent importComponent;
    private final FunctionUnitVersionComponent versionComponent;
    private final FunctionUnitContentComponent contentComponent;
    private final FunctionUnitLifecycleComponent lifecycleComponent;
    private final PortalRuntimePurgeClient portalRuntimePurgeClient;

    // ==================== Import and package validation ====================

    /** Import function package */
    public ImportResult importFunctionPackage(FunctionUnitImportRequest request, String importerId) {
        return importComponent.importFunctionPackage(request, importerId);
    }

    /** Validate function package */
    public ValidationResult validatePackage(FunctionUnitImportRequest request) {
        return importComponent.validatePackage(request);
    }

    /** Validate BPMN syntax */
    public boolean validateBpmnSyntax(String bpmnContent, ValidationResult result) {
        return importComponent.validateBpmnSyntax(bpmnContent, result);
    }

    /** Validate data table structure */
    public boolean validateDataTableStructure(String tableDefinition, ValidationResult result) {
        return importComponent.validateDataTableStructure(tableDefinition, result);
    }

    /** Validate form configuration */
    public boolean validateFormConfig(String formConfig, ValidationResult result) {
        return importComponent.validateFormConfig(formConfig, result);
    }

    /** Detect dependency conflicts */
    public List<ImportResult.DependencyConflict> detectConflicts(FunctionPackageContent packageContent) {
        return importComponent.detectConflicts(packageContent);
    }

    /** Delete existing version */
    public void deleteExistingVersion(String code, String version) {
        importComponent.deleteExistingVersion(code, version);
    }

    // ==================== Content ====================

    /** Add function unit content */
    public void addFunctionUnitContent(String functionUnitId, ContentType contentType,
                                       String contentName, String contentData) {
        contentComponent.addFunctionUnitContent(functionUnitId, contentType, contentName, contentData, null);
    }

    /**
     * Add function unit content(with source id)
     * @param sourceId Source content id (e.g. developer-workstation dw_form_definitions.id)
     */
    public void addFunctionUnitContent(String functionUnitId, ContentType contentType,
                                       String contentName, String contentData, String sourceId) {
        contentComponent.addFunctionUnitContent(functionUnitId, contentType, contentName, contentData, sourceId);
    }

    /** Get all contents for function unit */
    public List<FunctionUnitContent> getFunctionUnitContents(String functionUnitId) {
        return contentComponent.getFunctionUnitContents(functionUnitId);
    }

    /**
     * Get function unit contents filtered by type (null type returns all; invalid type throws).
     *
     * <p><b>Validates: Requirements 35.1, 35.2, 35.3</b>
     */
    public List<FunctionUnitContentItemDTO> getContentsByType(String functionUnitId, String type) {
        return contentComponent.getContentsByType(functionUnitId, type);
    }

    /**
     * Assemble full function unit content (BPMN, forms with table bindings, data tables).
     *
     * <p><b>Validates: Requirements 6.1, 6.2, 6.3</b>
     */
    public FunctionUnitContentResponse assembleFunctionUnitContent(String id) {
        return contentComponent.assembleFunctionUnitContent(id);
    }

    // ==================== Basic queries ====================

    /** Compute checksum */
    public String calculateChecksum(String content) {
        return ChecksumUtils.sha256Hex(content);
    }

    /** Get function unit by id */
    public FunctionUnit getFunctionUnitById(String id) {
        return functionUnitLookup.getById(id);
    }

    /** Get function unit by process definition key */
    public FunctionUnit getFunctionUnitByProcessKey(String processKey) {
        return contentComponent.getFunctionUnitByProcessKey(processKey);
    }

    /** Save function unit */
    @Transactional
    public FunctionUnit saveFunctionUnit(FunctionUnit functionUnit) {
        return functionUnitRepository.save(functionUnit);
    }

    /** Get function unit by code and version */
    public FunctionUnit getFunctionUnitByCodeAndVersion(String code, String version) {
        return functionUnitLookup.getByCodeAndVersion(code, version);
    }

    /** List function units (paged) */
    public Page<FunctionUnit> listFunctionUnits(Pageable pageable) {
        return functionUnitRepository.findByStatusNot(FunctionUnitStatus.ARCHIVED, pageable);
    }

    /** List archived function units (paged) */
    public Page<FunctionUnit> listArchivedFunctionUnits(Pageable pageable) {
        return functionUnitRepository.findByStatus(FunctionUnitStatus.ARCHIVED, pageable);
    }

    /** List function units by status */
    public Page<FunctionUnit> listFunctionUnitsByStatus(FunctionUnitStatus status, Pageable pageable) {
        return functionUnitRepository.findByStatus(status, pageable);
    }

    /** List all versions of function unit */
    public List<FunctionUnit> getAllVersions(String code) {
        return functionUnitRepository.findAllByCodeOrderByVersionDesc(code);
    }

    // ==================== Lifecycle ====================

    /** Validate function unit: structure/dependency/trial deploy; mark VALIDATED on success */
    public ValidationResult validateFunctionUnit(String id, String validatorId) {
        return lifecycleComponent.validateFunctionUnit(id, validatorId);
    }

    /** Deprecate function unit */
    public FunctionUnit deprecateFunctionUnit(String id) {
        return lifecycleComponent.deprecateFunctionUnit(id);
    }

    // ==================== Version management ====================

    /** Check version compatibility */
    public boolean isVersionCompatible(String requiredVersion, String existingVersion) {
        return versionComponent.isVersionCompatible(requiredVersion, existingVersion);
    }

    /** Validate semantic version format */
    public boolean isValidSemanticVersion(String version) {
        return versionComponent.isValidSemanticVersion(version);
    }

    /**
     * Compare two version strings
     * @return negative if v1 < v2, zero if equal, positive if v1 > v2
     */
    public int compareVersions(String v1, String v2) {
        return versionComponent.compareVersions(v1, v2);
    }

    /** Next major version string */
    public String getNextMajorVersion(String currentVersion) {
        return versionComponent.getNextMajorVersion(currentVersion);
    }

    /** Next minor version string */
    public String getNextMinorVersion(String currentVersion) {
        return versionComponent.getNextMinorVersion(currentVersion);
    }

    /** Next patch version string */
    public String getNextPatchVersion(String currentVersion) {
        return versionComponent.getNextPatchVersion(currentVersion);
    }

    /** Latest function unit version (semantic compare; avoids lexicographic errors like 1.0.9 > 1.0.11) */
    public Optional<FunctionUnit> getLatestVersion(String code) {
        return versionComponent.getLatestVersion(code);
    }

    /** Latest stable version (VALIDATED or DEPLOYED) via semantic compare */
    public Optional<FunctionUnit> getLatestStableVersion(String code) {
        return versionComponent.getLatestStableVersion(code);
    }

    /** Check whether upgrade to target version is allowed */
    public VersionUpgradeCheck checkVersionUpgrade(String code, String fromVersion, String toVersion) {
        return versionComponent.checkVersionUpgrade(code, fromVersion, toVersion);
    }

    /** Version history */
    public List<VersionHistory> getVersionHistory(String code) {
        return versionComponent.getVersionHistory(code);
    }

    /** Version history including enabled flag. */
    public List<VersionHistoryEntry> getVersionHistoryWithStatus(String code) {
        return versionComponent.getVersionHistoryWithStatus(code);
    }

    /** Create new version from existing */
    public FunctionUnit createNewVersion(String sourceId, String newVersion, String creatorId) {
        return versionComponent.createNewVersion(sourceId, newVersion, creatorId);
    }

    /** Rollback to target version */
    public FunctionUnit rollbackToVersion(String code, String targetVersion, String operatorId) {
        return versionComponent.rollbackToVersion(code, targetVersion, operatorId);
    }

    // ==================== Delete and enable/disable ====================

    /** Delete preview: count associated data that would be removed */
    public DeletePreviewResponse getDeletePreview(String functionUnitId) {
        return lifecycleComponent.getDeletePreview(functionUnitId);
    }

    /** Whether running process instances exist */
    public boolean hasRunningInstances(String functionUnitId) {
        return lifecycleComponent.hasRunningInstances(functionUnitId);
    }

    /** Archive function unit by code (all versions; remove portal visibility) */
    public void archiveFunctionUnitByCode(String functionUnitId) {
        lifecycleComponent.archiveFunctionUnitByCode(functionUnitId);
    }

    /** Restore archived function unit (all ARCHIVED versions under code → DRAFT) */
    public FunctionUnit restoreFunctionUnit(String functionUnitId) {
        return lifecycleComponent.restoreFunctionUnit(functionUnitId);
    }

    /** Cascade-delete function unit and related data (internal/test; public DELETE uses archive) */
    public void deleteFunctionUnitCascade(String functionUnitId) {
        lifecycleComponent.deleteFunctionUnitCascade(functionUnitId);
    }

    /** Set function unit enabled flag */
    public FunctionUnit setEnabled(String functionUnitId, boolean enabled) {
        return lifecycleComponent.setEnabled(functionUnitId, enabled, "system", "Manual status change");
    }

    /** Set function unit enabled flag (with operator and reason). */
    public FunctionUnit setEnabled(String functionUnitId, boolean enabled, String operatorId, String reason) {
        return lifecycleComponent.setEnabled(functionUnitId, enabled, operatorId, reason);
    }

    /** List deployed and enabled function units */
    public Page<FunctionUnit> listDeployedAndEnabledFunctionUnits(Pageable pageable) {
        return lifecycleComponent.listDeployedAndEnabledFunctionUnits(pageable);
    }

    /** Latest deployed version per function unit code */
    public List<FunctionUnit> listLatestDeployedFunctionUnits() {
        return lifecycleComponent.listLatestDeployedFunctionUnits();
    }

    /** Portal start: highest semantic version among deployed+enabled for code (empty if none) */
    public Optional<FunctionUnit> getActiveCatalogForPortalStart(String code) {
        return lifecycleComponent.getActiveCatalogForPortalStart(code);
    }

    /** Purge portal runtime data by catalog id (engine purge) for rollback/deprecate flows */
    public Map<String, Object> purgeRuntimeDataForCatalog(String catalogId) {
        return portalRuntimePurgeClient.purgeRuntimeDataForCatalog(catalogId);
    }

    /**
     * Disable other versions for function unit code
     * @param enabledVersion version to keep enabled (null disables all)
     * @return list of disabled version strings
     */
    public List<String> disableOtherVersions(String code, String enabledVersion, String operatorId) {
        return lifecycleComponent.disableOtherVersions(code, enabledVersion, operatorId);
    }

    /**
     * End of workstation one-click deploy: after disabling other versions for the code,
     * enable the row deployed in this run.
     */
    public FunctionUnit finalizeOneClickDeployEnable(String functionUnitId, String operatorId) {
        return lifecycleComponent.finalizeOneClickDeployEnable(functionUnitId, operatorId);
    }

    /** Currently enabled version for a code. */
    public Optional<FunctionUnit> getEnabledVersion(String code) {
        return lifecycleComponent.getEnabledVersion(code);
    }

    /** Activate a specific version (same rules as {@link #setEnabled(String, boolean, String, String)}). */
    public FunctionUnit activateVersion(String code, String targetVersion, String operatorId) {
        return lifecycleComponent.activateVersion(code, targetVersion, operatorId);
    }

    // ==================== Version management inner types ====================

    /** Version upgrade check result */
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

    /** Version history entry */
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

    /** Function package content */
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

    /** Dependency info */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class DependencyInfo {
        private String code;
        private String version;
        private boolean required;
    }

    /** Content info */
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
}
