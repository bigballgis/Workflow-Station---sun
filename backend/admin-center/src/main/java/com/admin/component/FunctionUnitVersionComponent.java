package com.admin.component;

import com.admin.entity.FunctionUnit;
import com.admin.entity.FunctionUnitContent;
import com.admin.entity.FunctionUnitDependency;
import com.admin.enums.FunctionUnitStatus;
import com.admin.exception.AdminBusinessException;
import com.admin.repository.FunctionUnitContentRepository;
import com.admin.repository.FunctionUnitDependencyRepository;
import com.admin.repository.FunctionUnitRepository;
import com.platform.common.i18n.I18nService;
import com.platform.common.version.SemanticVersion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Semantic version rules, version history and version creation/rollback for function units.
 * Collaborator of {@link FunctionUnitManagerComponent}; result types
 * {@link FunctionUnitManagerComponent.VersionUpgradeCheck} / {@link FunctionUnitManagerComponent.VersionHistory}
 * stay on the facade as the published API.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FunctionUnitVersionComponent {

    private final FunctionUnitRepository functionUnitRepository;
    private final FunctionUnitDependencyRepository dependencyRepository;
    private final FunctionUnitContentRepository contentRepository;
    private final FunctionUnitLookup functionUnitLookup;
    private final I18nService i18nService;

    // Semantic version regex
    private static final Pattern VERSION_PATTERN = Pattern.compile("^\\d+\\.\\d+\\.\\d+(-[a-zA-Z0-9]+)?$");

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
     * Semantic version comparator with lexicographic fallback for invalid semver.
     */
    public int compareBySemver(FunctionUnit a, FunctionUnit b) {
        try {
            return SemanticVersion.parse(a.getVersion()).compareTo(SemanticVersion.parse(b.getVersion()));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid semver {} / {}, using lexicographic order", a.getVersion(), b.getVersion());
            return a.getVersion().compareTo(b.getVersion());
        }
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
        return versions.stream().max(this::compareBySemver);
    }

    /**
     * Latest stable version (VALIDATED or DEPLOYED) via semantic compare
     */
    public Optional<FunctionUnit> getLatestStableVersion(String code) {
        List<FunctionUnit> versions = functionUnitRepository.findAllByCodeOrderByVersionDesc(code);
        return versions.stream()
                .filter(v -> v.getStatus() == FunctionUnitStatus.VALIDATED ||
                            v.getStatus() == FunctionUnitStatus.DEPLOYED)
                .max(this::compareBySemver);
    }

    /**
     * Check whether upgrade to target version is allowed
     */
    public FunctionUnitManagerComponent.VersionUpgradeCheck checkVersionUpgrade(
            String code, String fromVersion, String toVersion) {
        FunctionUnitManagerComponent.VersionUpgradeCheck check =
                new FunctionUnitManagerComponent.VersionUpgradeCheck();
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
    public List<FunctionUnitManagerComponent.VersionHistory> getVersionHistory(String code) {
        List<FunctionUnit> versions = functionUnitRepository.findAllByCodeOrderByVersionDesc(code);
        List<FunctionUnitManagerComponent.VersionHistory> history = new ArrayList<>();

        for (int i = 0; i < versions.size(); i++) {
            FunctionUnit current = versions.get(i);
            FunctionUnitManagerComponent.VersionHistory entry =
                    FunctionUnitManagerComponent.VersionHistory.builder()
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
     * Version history including enabled flag.
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
        FunctionUnit source = functionUnitLookup.getById(sourceId);

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
        FunctionUnit targetUnit = functionUnitLookup.getByCodeAndVersion(code, targetVersion);

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
}
