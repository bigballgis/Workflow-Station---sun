package com.developer.service;

import com.developer.dto.FunctionUnitDisplay;
import com.developer.dto.VersionHistoryDisplay;
import com.developer.dto.VersionHistoryEntry;
import com.developer.entity.FunctionUnit;
import com.developer.repository.FunctionUnitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service class for UI display operations.
 * Provides consolidated views of function units and version history for UI consumption.
 * 
 * Requirements: 3.1, 3.2, 3.3, 3.4, 3.5
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UIService {
    
    private final FunctionUnitRepository functionUnitRepository;
    private final ProcessService processService;
    
    /**
     * Get function units for display in the UI.
     * Returns only active versions, with each function unit shown as a single item
     * regardless of how many versions exist.
     * 
     * Requirements: 3.1 - WHEN displaying Function_Units in the UI, THE System SHALL show 
     *                     each Function_Unit as a single item regardless of version count
     *               3.2 - WHEN displaying a Function_Unit, THE System SHALL show the 
     *                     Active_Version information
     * 
     * @return list of function unit display objects
     */
    @Transactional(readOnly = true)
    public List<FunctionUnitDisplay> getFunctionUnitsForDisplay() {
        log.info("Retrieving function units for UI display");
        
        try {
            // Get all function units
            List<FunctionUnit> allFunctionUnits = functionUnitRepository.findAll();
            
            // Group by function unit name
            Map<String, List<FunctionUnit>> groupedByName = allFunctionUnits.stream()
                    .collect(Collectors.groupingBy(
                            FunctionUnit::getName,
                            LinkedHashMap::new,
                            Collectors.toList()
                    ));
            
            log.debug("Found {} unique function units", groupedByName.size());
            
            // Create display objects for each function unit
            List<FunctionUnitDisplay> displayList = new ArrayList<>();
            
            for (Map.Entry<String, List<FunctionUnit>> entry : groupedByName.entrySet()) {
                String functionUnitName = entry.getKey();
                List<FunctionUnit> versions = entry.getValue();
                
                // Find the active version
                FunctionUnit activeVersion = versions.stream()
                        .filter(FunctionUnit::getIsActive)
                        .findFirst()
                        .orElse(null);
                
                if (activeVersion != null) {
                    FunctionUnitDisplay display = FunctionUnitDisplay.builder()
                            .functionUnitName(functionUnitName)
                            .currentVersion(activeVersion.getVersion())
                            .deployedAt(activeVersion.getDeployedAt())
                            .versionCount(versions.size())
                            .activeVersionId(activeVersion.getId())
                            .build();
                    
                    displayList.add(display);
                    log.debug("Added display entry for function unit: {} (version: {}, total versions: {})",
                            functionUnitName, activeVersion.getVersion(), versions.size());
                } else {
                    log.warn("No active version found for function unit: {}", functionUnitName);
                }
            }
            
            log.info("Retrieved {} function units for UI display", displayList.size());
            return displayList;
            
        } catch (Exception e) {
            log.error("Failed to retrieve function units for UI display", e);
            throw new RuntimeException(
                "Failed to retrieve function units for display: " + e.getMessage(), e
            );
        }
    }
    
    /**
     * Get version history for a specific function unit for UI display.
     * Returns all versions ordered by version number descending, with detailed information
     * including active status, deployment timestamp, process count, and rollback capability.
     * 
     * Requirements: 3.3 - WHEN a user requests version history, THE System SHALL display 
     *                     all versions of that Function_Unit ordered by version number
     *               3.4 - WHEN displaying version history, THE System SHALL indicate which 
     *                     version is currently active
     *               3.5 - WHEN displaying version history, THE System SHALL show the 
     *                     deployment timestamp for each version
     * 
     * @param functionUnitName the name of the function unit
     * @return version history display object with all versions
     */
    @Transactional(readOnly = true)
    public VersionHistoryDisplay getVersionHistoryForUI(String functionUnitName) {
        log.info("Retrieving version history for UI: {}", functionUnitName);
        
        try {
            // Query all versions for the function unit, ordered by version descending
            List<FunctionUnit> versions = functionUnitRepository
                    .findByNameOrderByVersionDesc(functionUnitName);
            
            if (versions.isEmpty()) {
                log.warn("No versions found for function unit: {}", functionUnitName);
                return VersionHistoryDisplay.builder()
                        .functionUnitName(functionUnitName)
                        .versions(new ArrayList<>())
                        .build();
            }
            
            log.debug("Found {} versions for function unit: {}", versions.size(), functionUnitName);
            
            // Convert to version history entries
            List<VersionHistoryEntry> entries = new ArrayList<>();
            
            for (FunctionUnit version : versions) {
                // Count process instances bound to this version
                long processCount = processService.countProcessInstancesByVersion(version.getId());
                
                // Calculate canRollback flag
                // Active version cannot be rolled back to (it's already active)
                // Inactive versions can be rolled back to
                boolean canRollback = !version.getIsActive();
                
                VersionHistoryEntry entry = VersionHistoryEntry.builder()
                        .version(version.getVersion())
                        .isActive(version.getIsActive())
                        .deployedAt(version.getDeployedAt())
                        .processInstanceCount(processCount)
                        .canRollback(canRollback)
                        .versionId(version.getId())
                        .build();
                
                entries.add(entry);
                
                log.debug("Added version history entry: version={}, active={}, processes={}, canRollback={}",
                        version.getVersion(), version.getIsActive(), processCount, canRollback);
            }
            
            VersionHistoryDisplay display = VersionHistoryDisplay.builder()
                    .functionUnitName(functionUnitName)
                    .versions(entries)
                    .build();
            
            log.info("Retrieved version history for function unit: {} ({} versions)",
                    functionUnitName, entries.size());
            
            return display;
            
        } catch (Exception e) {
            log.error("Failed to retrieve version history for function unit: {}", 
                    functionUnitName, e);
            throw new RuntimeException(
                "Failed to retrieve version history: " + e.getMessage(), e
            );
        }
    }
}
