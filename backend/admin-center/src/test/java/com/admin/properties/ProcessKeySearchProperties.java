package com.admin.properties;

import com.admin.component.FunctionUnitManagerComponent;
import com.admin.entity.FunctionUnit;
import com.admin.entity.FunctionUnitContent;
import com.admin.enums.ContentType;
import com.admin.enums.FunctionUnitStatus;
import com.admin.exception.FunctionUnitNotFoundException;
import com.admin.repository.FunctionUnitAccessRepository;
import com.admin.repository.FunctionUnitContentRepository;
import com.admin.repository.FunctionUnitDependencyRepository;
import com.admin.repository.FunctionUnitRepository;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeTry;
import org.mockito.Mockito;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for Process Key to Function Unit search.
 * 
 * Feature: process-key-function-unit-mapping
 * Property 2: BPMN Process ID Search Correctness
 * 
 * Tests that function units can be correctly found by their BPMN process ID.
 * 
 * **Validates: Requirements 2.2, 2.5, 2.6**
 */
class ProcessKeySearchProperties {

    private FunctionUnitRepository functionUnitRepository;
    private FunctionUnitDependencyRepository dependencyRepository;
    private FunctionUnitContentRepository contentRepository;
    private FunctionUnitAccessRepository accessRepository;
    private FunctionUnitManagerComponent component;

    @BeforeTry
    void setUp() {
        functionUnitRepository = Mockito.mock(FunctionUnitRepository.class);
        dependencyRepository = Mockito.mock(FunctionUnitDependencyRepository.class);
        contentRepository = Mockito.mock(FunctionUnitContentRepository.class);
        accessRepository = Mockito.mock(FunctionUnitAccessRepository.class);
        component = new FunctionUnitManagerComponent(
                functionUnitRepository, dependencyRepository, contentRepository, accessRepository);
    }

    /**
     * Property 2: For any function unit with a valid BPMN XML containing a process ID,
     * searching by that process ID SHALL return the correct function unit.
     * 
     * **Feature: process-key-function-unit-mapping, Property 2: BPMN Process ID Search Correctness**
     * **Validates: Requirements 2.2**
     */
    @Property(tries = 100)
    void searchByProcessKeyReturnsCorrectFunctionUnit(
            @ForAll("processKeys") String processKey,
            @ForAll("functionUnitIds") String functionUnitId,
            @ForAll("functionUnitNames") String functionUnitName) {
        
        // Given: A function unit with content that has a flowable process definition ID
        FunctionUnit expectedUnit = createFunctionUnit(functionUnitId, functionUnitName);
        FunctionUnitContent content = createProcessContent(expectedUnit, processKey);
        
        when(contentRepository.findByProcessDefinitionKey(processKey))
                .thenReturn(Optional.of(content));
        
        // When: Searching by process key
        FunctionUnit result = component.getFunctionUnitByProcessKey(processKey);
        
        // Then: Should return the correct function unit
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(functionUnitId);
        assertThat(result.getName()).isEqualTo(functionUnitName);
    }

    /**
     * Property: When no matching function unit is found, should throw FunctionUnitNotFoundException.
     * 
     * **Validates: Requirements 2.4**
     */
    @Property(tries = 100)
    void searchByNonExistentProcessKeyThrowsException(
            @ForAll("processKeys") String processKey) {
        
        // Given: No content matches the process key
        when(contentRepository.findByProcessDefinitionKey(processKey))
                .thenReturn(Optional.empty());
        
        // When/Then: Should throw FunctionUnitNotFoundException
        assertThatThrownBy(() -> component.getFunctionUnitByProcessKey(processKey))
                .isInstanceOf(FunctionUnitNotFoundException.class)
                .hasMessageContaining(processKey);
    }

    /**
     * Property: Process key search should work with various process key formats.
     * 
     * **Validates: Requirements 2.6**
     */
    @Property(tries = 100)
    void searchWorksWithVariousProcessKeyFormats(
            @ForAll("processKeysWithSpecialChars") String processKey,
            @ForAll("functionUnitIds") String functionUnitId) {
        
        // Given: A function unit with content
        FunctionUnit expectedUnit = createFunctionUnit(functionUnitId, "Test Unit");
        FunctionUnitContent content = createProcessContent(expectedUnit, processKey);
        
        when(contentRepository.findByProcessDefinitionKey(processKey))
                .thenReturn(Optional.of(content));
        
        // When: Searching by process key
        FunctionUnit result = component.getFunctionUnitByProcessKey(processKey);
        
        // Then: Should return the correct function unit
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(functionUnitId);
    }

    /**
     * Property: Search result should be consistent - same input always returns same output.
     */
    @Property(tries = 100)
    void searchIsConsistent(
            @ForAll("processKeys") String processKey,
            @ForAll("functionUnitIds") String functionUnitId) {
        
        // Given: A function unit with content
        FunctionUnit expectedUnit = createFunctionUnit(functionUnitId, "Test Unit");
        FunctionUnitContent content = createProcessContent(expectedUnit, processKey);
        
        when(contentRepository.findByProcessDefinitionKey(processKey))
                .thenReturn(Optional.of(content));
        
        // When: Searching multiple times
        FunctionUnit result1 = component.getFunctionUnitByProcessKey(processKey);
        FunctionUnit result2 = component.getFunctionUnitByProcessKey(processKey);
        
        // Then: Results should be consistent
        assertThat(result1.getId()).isEqualTo(result2.getId());
        assertThat(result1.getName()).isEqualTo(result2.getName());
    }

    // ==================== Helper Methods ====================
    
    private FunctionUnit createFunctionUnit(String id, String name) {
        return FunctionUnit.builder()
                .id(id)
                .name(name)
                .code("test-code-" + id.hashCode())
                .version("1.0.0")
                .status(FunctionUnitStatus.DEPLOYED)
                .enabled(true)
                .deployments(new HashSet<>())
                .build();
    }
    
    private FunctionUnitContent createProcessContent(FunctionUnit functionUnit, String processKey) {
        // Simulate flowable process definition ID format: {processKey}:{version}:{uuid}
        String flowableProcessDefId = processKey + ":1:" + UUID.randomUUID().toString();
        
        FunctionUnitContent content = FunctionUnitContent.builder()
                .id(UUID.randomUUID().toString())
                .functionUnit(functionUnit)
                .contentType(ContentType.PROCESS)
                .contentName("main-process.bpmn")
                .contentPath("/processes/main-process.bpmn")
                .flowableProcessDefinitionId(flowableProcessDefId)
                .build();
        
        return content;
    }

    // ==================== Arbitrary Providers ====================
    
    @Provide
    Arbitrary<String> processKeys() {
        return Arbitraries.of(
                "Process_PurchaseRequest",
                "Process_LeaveRequest",
                "Process_ExpenseReport",
                "SimpleProcess",
                "MyWorkflow",
                "Test_Process_123"
        );
    }
    
    @Provide
    Arbitrary<String> processKeysWithSpecialChars() {
        // Generate process keys with underscores, hyphens, and numbers
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars('_', '-')
                .ofMinLength(3)
                .ofMaxLength(30)
                .filter(s -> Character.isLetter(s.charAt(0)));
    }
    
    @Provide
    Arbitrary<String> functionUnitIds() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(8)
                .ofMaxLength(16)
                .map(s -> "fu-" + s);
    }
    
    @Provide
    Arbitrary<String> functionUnitNames() {
        return Arbitraries.of(
                "Purchase Request",
                "Leave Application",
                "Expense Report",
                "Test Function Unit",
                "采购申请",
                "请假流程"
        );
    }
}
