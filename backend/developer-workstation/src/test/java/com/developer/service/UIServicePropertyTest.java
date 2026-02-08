package com.developer.service;

import com.developer.dto.FunctionUnitDisplay;
import com.developer.dto.VersionHistoryDisplay;
import com.developer.dto.VersionHistoryEntry;
import com.developer.entity.FunctionUnit;
import com.developer.entity.ProcessDefinition;
import com.developer.repository.FunctionUnitRepository;
import net.jqwik.api.*;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * Property-based tests for UIService.
 * Tests universal properties that should hold for all UI display operations.
 */
class UIServicePropertyTest {
    
    @Mock
    private FunctionUnitRepository functionUnitRepository;
    
    @Mock
    private ProcessService processService;
    
    private UIService uiService;
    
    @BeforeEach
    void setUp() {
        openMocks(this);
        uiService = new UIService(functionUnitRepository, processService);
    }
    
    /**
     * Property 8: UI Display Consolidation
     * 
     * For any set of function units, the UI display query should return exactly one item 
     * per unique function unit name, regardless of how many versions exist.
     * 
     * **Validates: Requirements 3.1**
     */
    @Property(tries = 100)
    void uiDisplayShowsOneItemPerFunctionUnit(
            @ForAll("functionUnitNames") String functionUnitName,
            @ForAll("versionCounts") int versionCount) {
        
        // Limit version count to reasonable range
        Assume.that(versionCount > 0 && versionCount <= 20);
        
        // Initialize mocks for each property test iteration
        openMocks(this);
        uiService = new UIService(functionUnitRepository, processService);
        
        // Given: Multiple versions of the same function unit
        List<FunctionUnit> allVersions = new ArrayList<>();
        
        for (int i = 0; i < versionCount; i++) {
            boolean isActive = (i == versionCount - 1); // Last version is active
            String version = String.format("1.0.%d", i);
            
            FunctionUnit functionUnit = createMockFunctionUnit(
                    (long) (i + 1), 
                    functionUnitName, 
                    version, 
                    isActive);
            
            allVersions.add(functionUnit);
        }
        
        when(functionUnitRepository.findAll()).thenReturn(allVersions);
        when(processService.countProcessInstancesByVersion(anyLong())).thenReturn(0L);
        
        // When: Getting function units for display
        List<FunctionUnitDisplay> displayList = uiService.getFunctionUnitsForDisplay();
        
        // Then: Should return exactly one item for the function unit
        assertThat(displayList).hasSize(1);
        
        // And: The item should represent the function unit
        FunctionUnitDisplay display = displayList.get(0);
        assertThat(display.getFunctionUnitName()).isEqualTo(functionUnitName);
        
        // And: The version count should match the total number of versions
        assertThat(display.getVersionCount()).isEqualTo(versionCount);
        
        // And: Should show the active version
        assertThat(display.getCurrentVersion()).isEqualTo(String.format("1.0.%d", versionCount - 1));
    }
    
    /**
     * Property 8 (Extended): UI Display Consolidation with Multiple Function Units
     * 
     * For any set of function units with multiple names, the UI display should return 
     * exactly one item per unique function unit name.
     * 
     * **Validates: Requirements 3.1**
     */
    @Property(tries = 100)
    void uiDisplayConsolidatesMultipleFunctionUnits(
            @ForAll("functionUnitNameLists") List<String> functionUnitNames,
            @ForAll("versionCounts") int versionsPerUnit) {
        
        // Limit to reasonable ranges
        Assume.that(functionUnitNames.size() > 0 && functionUnitNames.size() <= 10);
        Assume.that(versionsPerUnit > 0 && versionsPerUnit <= 5);
        
        // Initialize mocks for each property test iteration
        openMocks(this);
        uiService = new UIService(functionUnitRepository, processService);
        
        // Given: Multiple function units, each with multiple versions
        List<FunctionUnit> allVersions = new ArrayList<>();
        Set<String> uniqueNames = new HashSet<>(functionUnitNames);
        
        long idCounter = 1L;
        for (String name : uniqueNames) {
            for (int v = 0; v < versionsPerUnit; v++) {
                boolean isActive = (v == versionsPerUnit - 1);
                String version = String.format("1.0.%d", v);
                
                FunctionUnit functionUnit = createMockFunctionUnit(
                        idCounter++, name, version, isActive);
                allVersions.add(functionUnit);
            }
        }
        
        when(functionUnitRepository.findAll()).thenReturn(allVersions);
        when(processService.countProcessInstancesByVersion(anyLong())).thenReturn(0L);
        
        // When: Getting function units for display
        List<FunctionUnitDisplay> displayList = uiService.getFunctionUnitsForDisplay();
        
        // Then: Should return exactly one item per unique function unit name
        assertThat(displayList).hasSize(uniqueNames.size());
        
        // And: Each function unit name should appear exactly once
        Set<String> displayedNames = displayList.stream()
                .map(FunctionUnitDisplay::getFunctionUnitName)
                .collect(Collectors.toSet());
        assertThat(displayedNames).isEqualTo(uniqueNames);
        
        // And: Each display item should have the correct version count
        for (FunctionUnitDisplay display : displayList) {
            assertThat(display.getVersionCount()).isEqualTo(versionsPerUnit);
        }
    }
    
    /**
     * Property 9: UI Shows Active Version
     * 
     * For any function unit displayed in the UI, the data shown should match 
     * the active version's data.
     * 
     * **Validates: Requirements 3.2**
     */
    @Property(tries = 100)
    void uiDisplayShowsActiveVersionData(
            @ForAll("functionUnitNames") String functionUnitName,
            @ForAll("versions") String activeVersion,
            @ForAll("versions") String inactiveVersion) {
        
        // Skip if versions are the same
        Assume.that(!activeVersion.equals(inactiveVersion));
        
        // Initialize mocks for each property test iteration
        openMocks(this);
        uiService = new UIService(functionUnitRepository, processService);
        
        // Given: A function unit with an active and inactive version
        Instant activeDeployedAt = Instant.now();
        Instant inactiveDeployedAt = Instant.now().minusSeconds(3600);
        
        FunctionUnit activeFunctionUnit = createMockFunctionUnit(
                2L, functionUnitName, activeVersion, true);
        activeFunctionUnit.setDeployedAt(activeDeployedAt);
        
        FunctionUnit inactiveFunctionUnit = createMockFunctionUnit(
                1L, functionUnitName, inactiveVersion, false);
        inactiveFunctionUnit.setDeployedAt(inactiveDeployedAt);
        
        List<FunctionUnit> allVersions = Arrays.asList(inactiveFunctionUnit, activeFunctionUnit);
        
        when(functionUnitRepository.findAll()).thenReturn(allVersions);
        when(processService.countProcessInstancesByVersion(anyLong())).thenReturn(0L);
        
        // When: Getting function units for display
        List<FunctionUnitDisplay> displayList = uiService.getFunctionUnitsForDisplay();
        
        // Then: Should return one item
        assertThat(displayList).hasSize(1);
        
        FunctionUnitDisplay display = displayList.get(0);
        
        // And: Should show the active version's data, not the inactive version's data
        assertThat(display.getCurrentVersion()).isEqualTo(activeVersion);
        assertThat(display.getCurrentVersion()).isNotEqualTo(inactiveVersion);
        
        // And: Should show the active version's deployment timestamp
        assertThat(display.getDeployedAt()).isEqualTo(activeDeployedAt);
        assertThat(display.getDeployedAt()).isNotEqualTo(inactiveDeployedAt);
        
        // And: Should reference the active version's ID
        assertThat(display.getActiveVersionId()).isEqualTo(2L);
        assertThat(display.getActiveVersionId()).isNotEqualTo(1L);
    }
    
    /**
     * Property 11: Version History Completeness
     * 
     * For any function unit version history, each version entry should include 
     * the active status flag and deployment timestamp.
     * 
     * **Validates: Requirements 3.4, 3.5**
     */
    @Property(tries = 100)
    void versionHistoryIncludesCompleteInformation(
            @ForAll("functionUnitNames") String functionUnitName,
            @ForAll("versionCounts") int versionCount) {
        
        // Limit version count to reasonable range
        Assume.that(versionCount > 0 && versionCount <= 20);
        
        // Initialize mocks for each property test iteration
        openMocks(this);
        uiService = new UIService(functionUnitRepository, processService);
        
        // Given: Multiple versions of a function unit
        List<FunctionUnit> versions = new ArrayList<>();
        
        for (int i = 0; i < versionCount; i++) {
            boolean isActive = (i == versionCount - 1); // Last version is active
            String version = String.format("1.0.%d", i);
            Instant deployedAt = Instant.now().minusSeconds(3600L * (versionCount - i));
            
            FunctionUnit functionUnit = createMockFunctionUnit(
                    (long) (i + 1), 
                    functionUnitName, 
                    version, 
                    isActive);
            functionUnit.setDeployedAt(deployedAt);
            
            versions.add(functionUnit);
        }
        
        // Reverse to get descending order (newest first)
        Collections.reverse(versions);
        
        when(functionUnitRepository.findByNameOrderByVersionDesc(functionUnitName))
                .thenReturn(versions);
        when(processService.countProcessInstancesByVersion(anyLong())).thenReturn(5L);
        
        // When: Getting version history for UI
        VersionHistoryDisplay historyDisplay = uiService.getVersionHistoryForUI(functionUnitName);
        
        // Then: Should return all versions
        assertThat(historyDisplay.getVersions()).hasSize(versionCount);
        
        // And: Each version entry should include complete information
        for (int i = 0; i < versionCount; i++) {
            VersionHistoryEntry entry = historyDisplay.getVersions().get(i);
            
            // Should have version number
            assertThat(entry.getVersion()).isNotNull();
            assertThat(entry.getVersion()).isNotEmpty();
            
            // Should have active status flag
            assertThat(entry.getIsActive()).isNotNull();
            
            // Should have deployment timestamp
            assertThat(entry.getDeployedAt()).isNotNull();
            
            // Should have process instance count
            assertThat(entry.getProcessInstanceCount()).isNotNull();
            assertThat(entry.getProcessInstanceCount()).isEqualTo(5L);
            
            // Should have canRollback flag
            assertThat(entry.getCanRollback()).isNotNull();
            
            // Should have version ID
            assertThat(entry.getVersionId()).isNotNull();
        }
        
        // And: Exactly one version should be marked as active
        long activeCount = historyDisplay.getVersions().stream()
                .filter(VersionHistoryEntry::getIsActive)
                .count();
        assertThat(activeCount).isEqualTo(1);
        
        // And: The active version should have canRollback = false
        VersionHistoryEntry activeEntry = historyDisplay.getVersions().stream()
                .filter(VersionHistoryEntry::getIsActive)
                .findFirst()
                .orElseThrow();
        assertThat(activeEntry.getCanRollback()).isFalse();
        
        // And: All inactive versions should have canRollback = true
        List<VersionHistoryEntry> inactiveEntries = historyDisplay.getVersions().stream()
                .filter(entry -> !entry.getIsActive())
                .collect(Collectors.toList());
        for (VersionHistoryEntry entry : inactiveEntries) {
            assertThat(entry.getCanRollback()).isTrue();
        }
    }
    
    /**
     * Helper method to create a mock FunctionUnit
     */
    private FunctionUnit createMockFunctionUnit(Long id, String name, String version, boolean isActive) {
        ProcessDefinition processDefinition = ProcessDefinition.builder()
                .id(id)
                .bpmnXml("<bpmn>test</bpmn>")
                .build();
        
        FunctionUnit functionUnit = FunctionUnit.builder()
                .id(id)
                .name(name)
                .version(version)
                .isActive(isActive)
                .deployedAt(Instant.now())
                .processDefinition(processDefinition)
                .build();
        
        // Set bidirectional relationship
        processDefinition.setFunctionUnit(functionUnit);
        
        return functionUnit;
    }
    
    /**
     * Arbitrary for generating valid function unit names
     */
    @Provide
    Arbitrary<String> functionUnitNames() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .withChars('-', '_')
                .ofMinLength(3)
                .ofMaxLength(50)
                .filter(s -> !s.isEmpty() && Character.isLetter(s.charAt(0)));
    }
    
    /**
     * Arbitrary for generating lists of function unit names
     */
    @Provide
    Arbitrary<List<String>> functionUnitNameLists() {
        return functionUnitNames().list().ofMinSize(1).ofMaxSize(10);
    }
    
    /**
     * Arbitrary for generating valid semantic versions
     */
    @Provide
    Arbitrary<String> versions() {
        return Combinators.combine(
                Arbitraries.integers().between(0, 10),
                Arbitraries.integers().between(0, 20),
                Arbitraries.integers().between(0, 50)
        ).as((major, minor, patch) -> String.format("%d.%d.%d", major, minor, patch));
    }
    
    /**
     * Arbitrary for generating version counts
     */
    @Provide
    Arbitrary<Integer> versionCounts() {
        return Arbitraries.integers().between(1, 20);
    }
}
