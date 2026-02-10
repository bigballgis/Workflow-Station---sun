package com.developer.service;

import com.developer.dto.FunctionUnitDisplay;
import com.developer.dto.VersionHistoryDisplay;
import com.developer.dto.VersionHistoryEntry;
import com.developer.entity.FunctionUnit;
import com.developer.entity.ProcessDefinition;
import com.developer.repository.FunctionUnitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for UIService.
 * Tests specific examples and edge cases for UI display operations.
 */
@ExtendWith(MockitoExtension.class)
class UIServiceTest {
    
    @Mock
    private FunctionUnitRepository functionUnitRepository;
    
    @Mock
    private ProcessService processService;
    
    private UIService uiService;
    
    @BeforeEach
    void setUp() {
        uiService = new UIService(functionUnitRepository, processService);
    }
    
    @Test
    void getFunctionUnitsForDisplay_withSingleVersion_returnsOneItem() {
        // Given: A function unit with a single version
        FunctionUnit functionUnit = createFunctionUnit(
                1L, "TestFunction", "1.0.0", true, Instant.now());
        
        when(functionUnitRepository.findAll()).thenReturn(Arrays.asList(functionUnit));
        
        // When: Getting function units for display
        List<FunctionUnitDisplay> result = uiService.getFunctionUnitsForDisplay();
        
        // Then: Should return one item
        assertThat(result).hasSize(1);
        
        FunctionUnitDisplay display = result.get(0);
        assertThat(display.getFunctionUnitName()).isEqualTo("TestFunction");
        assertThat(display.getCurrentVersion()).isEqualTo("1.0.0");
        assertThat(display.getVersionCount()).isEqualTo(1);
        assertThat(display.getActiveVersionId()).isEqualTo(1L);
    }
    
    @Test
    void getFunctionUnitsForDisplay_withMultipleVersions_returnsOneItemWithCorrectCount() {
        // Given: A function unit with multiple versions
        Instant now = Instant.now();
        FunctionUnit v1 = createFunctionUnit(1L, "TestFunction", "1.0.0", false, now.minusSeconds(7200));
        FunctionUnit v2 = createFunctionUnit(2L, "TestFunction", "1.1.0", false, now.minusSeconds(3600));
        FunctionUnit v3 = createFunctionUnit(3L, "TestFunction", "1.2.0", true, now);
        
        when(functionUnitRepository.findAll()).thenReturn(Arrays.asList(v1, v2, v3));
        
        // When: Getting function units for display
        List<FunctionUnitDisplay> result = uiService.getFunctionUnitsForDisplay();
        
        // Then: Should return one item with version count of 3
        assertThat(result).hasSize(1);
        
        FunctionUnitDisplay display = result.get(0);
        assertThat(display.getFunctionUnitName()).isEqualTo("TestFunction");
        assertThat(display.getCurrentVersion()).isEqualTo("1.2.0");
        assertThat(display.getVersionCount()).isEqualTo(3);
        assertThat(display.getActiveVersionId()).isEqualTo(3L);
        assertThat(display.getDeployedAt()).isEqualTo(now);
    }
    
    @Test
    void getFunctionUnitsForDisplay_withMultipleFunctionUnits_returnsMultipleItems() {
        // Given: Multiple function units, each with multiple versions
        Instant now = Instant.now();
        
        FunctionUnit func1v1 = createFunctionUnit(1L, "Function1", "1.0.0", false, now.minusSeconds(3600));
        FunctionUnit func1v2 = createFunctionUnit(2L, "Function1", "1.1.0", true, now);
        
        FunctionUnit func2v1 = createFunctionUnit(3L, "Function2", "1.0.0", true, now.minusSeconds(1800));
        
        when(functionUnitRepository.findAll()).thenReturn(Arrays.asList(func1v1, func1v2, func2v1));
        
        // When: Getting function units for display
        List<FunctionUnitDisplay> result = uiService.getFunctionUnitsForDisplay();
        
        // Then: Should return two items, one for each function unit
        assertThat(result).hasSize(2);
        
        // Check Function1
        FunctionUnitDisplay func1Display = result.stream()
                .filter(d -> d.getFunctionUnitName().equals("Function1"))
                .findFirst()
                .orElseThrow();
        assertThat(func1Display.getCurrentVersion()).isEqualTo("1.1.0");
        assertThat(func1Display.getVersionCount()).isEqualTo(2);
        
        // Check Function2
        FunctionUnitDisplay func2Display = result.stream()
                .filter(d -> d.getFunctionUnitName().equals("Function2"))
                .findFirst()
                .orElseThrow();
        assertThat(func2Display.getCurrentVersion()).isEqualTo("1.0.0");
        assertThat(func2Display.getVersionCount()).isEqualTo(1);
    }
    
    @Test
    void getFunctionUnitsForDisplay_withNoActiveVersion_skipsFunction() {
        // Given: A function unit with no active version (edge case)
        FunctionUnit v1 = createFunctionUnit(1L, "TestFunction", "1.0.0", false, Instant.now());
        
        when(functionUnitRepository.findAll()).thenReturn(Arrays.asList(v1));
        
        // When: Getting function units for display
        List<FunctionUnitDisplay> result = uiService.getFunctionUnitsForDisplay();
        
        // Then: Should return empty list (no active version to display)
        assertThat(result).isEmpty();
    }
    
    @Test
    void getFunctionUnitsForDisplay_withEmptyDatabase_returnsEmptyList() {
        // Given: No function units in database
        when(functionUnitRepository.findAll()).thenReturn(new ArrayList<>());
        
        // When: Getting function units for display
        List<FunctionUnitDisplay> result = uiService.getFunctionUnitsForDisplay();
        
        // Then: Should return empty list
        assertThat(result).isEmpty();
    }
    
    @Test
    void getVersionHistoryForUI_withMultipleVersions_returnsAllVersionsOrdered() {
        // Given: Multiple versions of a function unit
        Instant now = Instant.now();
        FunctionUnit v1 = createFunctionUnit(1L, "TestFunction", "1.0.0", false, now.minusSeconds(7200));
        FunctionUnit v2 = createFunctionUnit(2L, "TestFunction", "1.1.0", false, now.minusSeconds(3600));
        FunctionUnit v3 = createFunctionUnit(3L, "TestFunction", "1.2.0", true, now);
        
        // Mock repository to return versions in descending order
        when(functionUnitRepository.findByNameOrderByVersionDesc("TestFunction"))
                .thenReturn(Arrays.asList(v3, v2, v1));
        
        when(processService.countProcessInstancesByVersion(1L)).thenReturn(5L);
        when(processService.countProcessInstancesByVersion(2L)).thenReturn(3L);
        when(processService.countProcessInstancesByVersion(3L)).thenReturn(10L);
        
        // When: Getting version history for UI
        VersionHistoryDisplay result = uiService.getVersionHistoryForUI("TestFunction");
        
        // Then: Should return all versions
        assertThat(result.getFunctionUnitName()).isEqualTo("TestFunction");
        assertThat(result.getVersions()).hasSize(3);
        
        // Check ordering (descending by version)
        assertThat(result.getVersions().get(0).getVersion()).isEqualTo("1.2.0");
        assertThat(result.getVersions().get(1).getVersion()).isEqualTo("1.1.0");
        assertThat(result.getVersions().get(2).getVersion()).isEqualTo("1.0.0");
        
        // Check active status
        assertThat(result.getVersions().get(0).getIsActive()).isTrue();
        assertThat(result.getVersions().get(1).getIsActive()).isFalse();
        assertThat(result.getVersions().get(2).getIsActive()).isFalse();
        
        // Check process counts
        assertThat(result.getVersions().get(0).getProcessInstanceCount()).isEqualTo(10L);
        assertThat(result.getVersions().get(1).getProcessInstanceCount()).isEqualTo(3L);
        assertThat(result.getVersions().get(2).getProcessInstanceCount()).isEqualTo(5L);
        
        // Check canRollback flags
        assertThat(result.getVersions().get(0).getCanRollback()).isFalse(); // Active version
        assertThat(result.getVersions().get(1).getCanRollback()).isTrue();  // Inactive version
        assertThat(result.getVersions().get(2).getCanRollback()).isTrue();  // Inactive version
    }
    
    @Test
    void getVersionHistoryForUI_withSingleVersion_returnsOneVersion() {
        // Given: A function unit with a single version
        FunctionUnit v1 = createFunctionUnit(1L, "TestFunction", "1.0.0", true, Instant.now());
        
        when(functionUnitRepository.findByNameOrderByVersionDesc("TestFunction"))
                .thenReturn(Arrays.asList(v1));
        when(processService.countProcessInstancesByVersion(1L)).thenReturn(0L);
        
        // When: Getting version history for UI
        VersionHistoryDisplay result = uiService.getVersionHistoryForUI("TestFunction");
        
        // Then: Should return one version
        assertThat(result.getFunctionUnitName()).isEqualTo("TestFunction");
        assertThat(result.getVersions()).hasSize(1);
        
        VersionHistoryEntry entry = result.getVersions().get(0);
        assertThat(entry.getVersion()).isEqualTo("1.0.0");
        assertThat(entry.getIsActive()).isTrue();
        assertThat(entry.getProcessInstanceCount()).isEqualTo(0L);
        assertThat(entry.getCanRollback()).isFalse(); // Active version cannot be rolled back
    }
    
    @Test
    void getVersionHistoryForUI_withNoVersions_returnsEmptyList() {
        // Given: No versions exist for the function unit
        when(functionUnitRepository.findByNameOrderByVersionDesc("NonExistent"))
                .thenReturn(new ArrayList<>());
        
        // When: Getting version history for UI
        VersionHistoryDisplay result = uiService.getVersionHistoryForUI("NonExistent");
        
        // Then: Should return empty version list
        assertThat(result.getFunctionUnitName()).isEqualTo("NonExistent");
        assertThat(result.getVersions()).isEmpty();
    }
    
    @Test
    void getVersionHistoryForUI_includesDeploymentTimestamps() {
        // Given: Versions with different deployment timestamps
        Instant time1 = Instant.parse("2024-01-01T10:00:00Z");
        Instant time2 = Instant.parse("2024-01-02T10:00:00Z");
        Instant time3 = Instant.parse("2024-01-03T10:00:00Z");
        
        FunctionUnit v1 = createFunctionUnit(1L, "TestFunction", "1.0.0", false, time1);
        FunctionUnit v2 = createFunctionUnit(2L, "TestFunction", "1.1.0", false, time2);
        FunctionUnit v3 = createFunctionUnit(3L, "TestFunction", "1.2.0", true, time3);
        
        when(functionUnitRepository.findByNameOrderByVersionDesc("TestFunction"))
                .thenReturn(Arrays.asList(v3, v2, v1));
        when(processService.countProcessInstancesByVersion(anyLong())).thenReturn(0L);
        
        // When: Getting version history for UI
        VersionHistoryDisplay result = uiService.getVersionHistoryForUI("TestFunction");
        
        // Then: Each version should have its deployment timestamp
        assertThat(result.getVersions().get(0).getDeployedAt()).isEqualTo(time3);
        assertThat(result.getVersions().get(1).getDeployedAt()).isEqualTo(time2);
        assertThat(result.getVersions().get(2).getDeployedAt()).isEqualTo(time1);
    }
    
    /**
     * Helper method to create a FunctionUnit for testing
     */
    private FunctionUnit createFunctionUnit(Long id, String name, String version, 
                                           boolean isActive, Instant deployedAt) {
        ProcessDefinition processDefinition = ProcessDefinition.builder()
                .id(id)
                .bpmnXml("<bpmn>test</bpmn>")
                .build();
        
        FunctionUnit functionUnit = FunctionUnit.builder()
                .id(id)
                .name(name)
                .version(version)
                .isActive(isActive)
                .deployedAt(deployedAt)
                .processDefinition(processDefinition)
                .build();
        
        // Set bidirectional relationship
        processDefinition.setFunctionUnit(functionUnit);
        
        return functionUnit;
    }
}
