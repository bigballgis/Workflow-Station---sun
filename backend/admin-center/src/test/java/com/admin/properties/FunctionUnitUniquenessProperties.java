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
 * Property-based tests for Multiple Function Unit Uniqueness.
 * 
 * Feature: process-key-function-unit-mapping
 * Property 4: Multiple Function Unit Uniqueness
 * 
 * For any set of function units with distinct BPMN process IDs, searching by any of those 
 * process IDs SHALL return exactly one matching function unit, and no two function units 
 * SHALL share the same BPMN process ID.
 * 
 * **Validates: Requirements 5.3**
 */
class FunctionUnitUniquenessProperties {

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
     * Property 4: For any set of function units with distinct BPMN process IDs,
     * searching by any of those process IDs SHALL return exactly one matching function unit.
     * 
     * **Feature: process-key-function-unit-mapping, Property 4: Multiple Function Unit Uniqueness**
     * **Validates: Requirements 5.3**
     */
    @Property(tries = 100)
    void eachDistinctProcessKeyReturnsExactlyOneFunctionUnit(
            @ForAll("distinctProcessKeyPairs") List<ProcessKeyFunctionUnitPair> pairs) {
        
        Assume.that(pairs != null && !pairs.isEmpty());
        
        // Given: Multiple function units with distinct process keys
        Map<String, FunctionUnitContent> processKeyToContent = new HashMap<>();
        
        for (ProcessKeyFunctionUnitPair pair : pairs) {
            FunctionUnit unit = createFunctionUnit(pair.functionUnitId, pair.functionUnitName);
            FunctionUnitContent content = createProcessContent(unit, pair.processKey);
            processKeyToContent.put(pair.processKey, content);
        }
        
        // Setup mock to return correct content for each process key
        for (Map.Entry<String, FunctionUnitContent> entry : processKeyToContent.entrySet()) {
            when(contentRepository.findByProcessDefinitionKey(entry.getKey()))
                    .thenReturn(Optional.of(entry.getValue()));
        }
        
        // When/Then: Searching by each process key returns exactly one function unit
        for (ProcessKeyFunctionUnitPair pair : pairs) {
            FunctionUnit result = component.getFunctionUnitByProcessKey(pair.processKey);
            
            // Should return exactly one function unit
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(pair.functionUnitId);
            
            // Verify the result is unique (same process key always returns same function unit)
            FunctionUnit result2 = component.getFunctionUnitByProcessKey(pair.processKey);
            assertThat(result.getId()).isEqualTo(result2.getId());
        }
    }

    /**
     * Property: No two function units should share the same BPMN process ID.
     * When a process key is searched, it should map to exactly one function unit.
     * 
     * **Validates: Requirements 5.3**
     */
    @Property(tries = 100)
    void processKeyMapsToExactlyOneFunctionUnit(
            @ForAll("processKeys") String processKey,
            @ForAll("functionUnitIds") String functionUnitId) {
        
        // Given: A function unit with a specific process key
        FunctionUnit unit = createFunctionUnit(functionUnitId, "Test Unit");
        FunctionUnitContent content = createProcessContent(unit, processKey);
        
        when(contentRepository.findByProcessDefinitionKey(processKey))
                .thenReturn(Optional.of(content));
        
        // When: Searching by process key
        FunctionUnit result = component.getFunctionUnitByProcessKey(processKey);
        
        // Then: Should return exactly one function unit with the correct ID
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(functionUnitId);
    }

    /**
     * Property: Different process keys should map to different function units.
     * 
     * **Validates: Requirements 5.3**
     */
    @Property(tries = 100)
    void differentProcessKeysMayMapToDifferentFunctionUnits(
            @ForAll("processKeys") String processKey1,
            @ForAll("processKeys") String processKey2,
            @ForAll("functionUnitIds") String functionUnitId1,
            @ForAll("functionUnitIds") String functionUnitId2) {
        
        Assume.that(!processKey1.equals(processKey2));
        Assume.that(!functionUnitId1.equals(functionUnitId2));
        
        // Given: Two different function units with different process keys
        FunctionUnit unit1 = createFunctionUnit(functionUnitId1, "Unit 1");
        FunctionUnit unit2 = createFunctionUnit(functionUnitId2, "Unit 2");
        FunctionUnitContent content1 = createProcessContent(unit1, processKey1);
        FunctionUnitContent content2 = createProcessContent(unit2, processKey2);
        
        when(contentRepository.findByProcessDefinitionKey(processKey1))
                .thenReturn(Optional.of(content1));
        when(contentRepository.findByProcessDefinitionKey(processKey2))
                .thenReturn(Optional.of(content2));
        
        // When: Searching by each process key
        FunctionUnit result1 = component.getFunctionUnitByProcessKey(processKey1);
        FunctionUnit result2 = component.getFunctionUnitByProcessKey(processKey2);
        
        // Then: Each should return its corresponding function unit
        assertThat(result1.getId()).isEqualTo(functionUnitId1);
        assertThat(result2.getId()).isEqualTo(functionUnitId2);
        assertThat(result1.getId()).isNotEqualTo(result2.getId());
    }

    /**
     * Property: Search results are deterministic - same input always produces same output.
     */
    @Property(tries = 100)
    void searchResultsAreDeterministic(
            @ForAll("processKeys") String processKey,
            @ForAll("functionUnitIds") String functionUnitId,
            @ForAll("positiveIntegers") int searchCount) {
        
        Assume.that(searchCount >= 2 && searchCount <= 10);
        
        // Given: A function unit with a process key
        FunctionUnit unit = createFunctionUnit(functionUnitId, "Test Unit");
        FunctionUnitContent content = createProcessContent(unit, processKey);
        
        when(contentRepository.findByProcessDefinitionKey(processKey))
                .thenReturn(Optional.of(content));
        
        // When: Searching multiple times
        Set<String> resultIds = new HashSet<>();
        for (int i = 0; i < searchCount; i++) {
            FunctionUnit result = component.getFunctionUnitByProcessKey(processKey);
            resultIds.add(result.getId());
        }
        
        // Then: All searches should return the same function unit
        assertThat(resultIds).hasSize(1);
        assertThat(resultIds).contains(functionUnitId);
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
        
        return FunctionUnitContent.builder()
                .id(UUID.randomUUID().toString())
                .functionUnit(functionUnit)
                .contentType(ContentType.PROCESS)
                .contentName("main-process.bpmn")
                .contentPath("/processes/main-process.bpmn")
                .flowableProcessDefinitionId(flowableProcessDefId)
                .build();
    }

    // ==================== Arbitrary Providers ====================
    
    @Provide
    Arbitrary<String> processKeys() {
        return Arbitraries.of(
                "Process_PurchaseRequest",
                "Process_LeaveRequest",
                "Process_ExpenseReport",
                "Process_TravelRequest",
                "Process_Onboarding",
                "Process_Offboarding",
                "SimpleProcess",
                "MyWorkflow",
                "Test_Process_123",
                "HR_Approval_Flow"
        );
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
    Arbitrary<Integer> positiveIntegers() {
        return Arbitraries.integers().between(2, 10);
    }
    
    @Provide
    Arbitrary<List<ProcessKeyFunctionUnitPair>> distinctProcessKeyPairs() {
        return Arbitraries.integers().between(2, 5).flatMap(count -> {
            // Generate 'count' distinct pairs
            return Arbitraries.of(
                    "Process_PurchaseRequest",
                    "Process_LeaveRequest", 
                    "Process_ExpenseReport",
                    "Process_TravelRequest",
                    "Process_Onboarding"
            ).list().ofSize(count).uniqueElements().flatMap(processKeys -> {
                List<Arbitrary<ProcessKeyFunctionUnitPair>> pairArbitraries = new ArrayList<>();
                for (int i = 0; i < processKeys.size(); i++) {
                    String pk = processKeys.get(i);
                    int index = i;
                    pairArbitraries.add(
                            functionUnitIds().map(fuId -> 
                                    new ProcessKeyFunctionUnitPair(pk, fuId + "-" + index, "Unit " + index))
                    );
                }
                return Combinators.combine(pairArbitraries).as(pairs -> pairs);
            });
        });
    }
    
    // ==================== Helper Classes ====================
    
    /**
     * Represents a pair of process key and function unit for testing.
     */
    static class ProcessKeyFunctionUnitPair {
        final String processKey;
        final String functionUnitId;
        final String functionUnitName;
        
        ProcessKeyFunctionUnitPair(String processKey, String functionUnitId, String functionUnitName) {
            this.processKey = processKey;
            this.functionUnitId = functionUnitId;
            this.functionUnitName = functionUnitName;
        }
    }
}
