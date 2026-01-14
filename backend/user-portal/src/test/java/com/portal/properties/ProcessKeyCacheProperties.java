package com.portal.properties;

import com.portal.component.FunctionUnitAccessComponent;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.mockito.Mockito;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for FunctionUnitAccessComponent cache consistency.
 * 
 * Feature: process-key-function-unit-mapping
 * Property 3: Cache Consistency
 * 
 * Tests that process key to function unit ID mappings are correctly cached
 * and subsequent requests use the cache without making additional API calls.
 * 
 * **Validates: Requirements 3.3**
 */
class ProcessKeyCacheProperties {

    private FunctionUnitAccessComponent component;
    private RestTemplate mockRestTemplate;
    private AtomicInteger apiCallCount;

    @BeforeProperty
    void setUp() {
        mockRestTemplate = Mockito.mock(RestTemplate.class);
        component = new FunctionUnitAccessComponent(mockRestTemplate);
        ReflectionTestUtils.setField(component, "adminCenterUrl", "http://localhost:8090");
        apiCallCount = new AtomicInteger(0);
        
        // Clear any existing cache
        component.clearAllCache();
    }

    /**
     * Property 3: Cache Consistency
     * 
     * *For any* process key that has been successfully resolved to a function unit ID,
     * subsequent resolution requests for the same process key SHALL return the same
     * function unit ID from cache without making additional API calls.
     * 
     * **Feature: process-key-function-unit-mapping, Property 3: Cache Consistency**
     * **Validates: Requirements 3.3**
     */
    @Property(tries = 100)
    void resolvedProcessKeyIsCachedAndReused(
            @ForAll("validProcessKeys") String processKey,
            @ForAll("functionUnitIds") String functionUnitId) {
        
        // Reset call counter and cache for each test
        apiCallCount.set(0);
        component.clearAllCache();
        
        // Setup mock to return function unit ID via process key API
        setupMockForProcessKeyResolution(processKey, functionUnitId);
        
        // First resolution - should call API
        String firstResult = component.resolveFunctionUnitId(processKey);
        int callsAfterFirst = apiCallCount.get();
        
        // Second resolution - should use cache, no additional API call
        String secondResult = component.resolveFunctionUnitId(processKey);
        int callsAfterSecond = apiCallCount.get();
        
        // Verify results are the same
        assertThat(firstResult).isEqualTo(functionUnitId);
        assertThat(secondResult).isEqualTo(functionUnitId);
        
        // Verify cache was used (no additional API calls for second resolution)
        assertThat(callsAfterSecond).isEqualTo(callsAfterFirst);
        
        // Verify the key is in cache
        assertThat(component.isProcessKeyCached(processKey)).isTrue();
    }

    /**
     * Property: Multiple different process keys can be cached independently.
     * 
     * **Validates: Requirements 3.3**
     */
    @Property(tries = 50)
    void multipleDifferentProcessKeysAreCachedIndependently(
            @ForAll("validProcessKeys") String processKey1,
            @ForAll("validProcessKeys") String processKey2,
            @ForAll("functionUnitIds") String functionUnitId1,
            @ForAll("functionUnitIds") String functionUnitId2) {
        
        // Skip if keys are the same
        Assume.that(!processKey1.equals(processKey2));
        
        // Reset for each test
        apiCallCount.set(0);
        component.clearAllCache();
        
        // Setup mocks for both process keys
        setupMockForProcessKeyResolution(processKey1, functionUnitId1);
        setupMockForProcessKeyResolution(processKey2, functionUnitId2);
        
        // Resolve both keys
        String result1 = component.resolveFunctionUnitId(processKey1);
        String result2 = component.resolveFunctionUnitId(processKey2);
        
        // Verify both are cached
        assertThat(component.isProcessKeyCached(processKey1)).isTrue();
        assertThat(component.isProcessKeyCached(processKey2)).isTrue();
        
        // Verify cache size
        assertThat(component.getProcessKeyCacheSize()).isGreaterThanOrEqualTo(2);
        
        // Resolve again - should use cache
        int callsBeforeResolve = apiCallCount.get();
        String result1Again = component.resolveFunctionUnitId(processKey1);
        String result2Again = component.resolveFunctionUnitId(processKey2);
        int callsAfterResolve = apiCallCount.get();
        
        // Verify results are consistent
        assertThat(result1Again).isEqualTo(result1);
        assertThat(result2Again).isEqualTo(result2);
        
        // Verify no additional API calls were made
        assertThat(callsAfterResolve).isEqualTo(callsBeforeResolve);
    }

    /**
     * Property: Clearing cache for a specific key removes only that key.
     * 
     * **Validates: Requirements 3.3**
     */
    @Property(tries = 50)
    void clearingSpecificCacheKeyRemovesOnlyThatKey(
            @ForAll("validProcessKeys") String processKey1,
            @ForAll("validProcessKeys") String processKey2,
            @ForAll("functionUnitIds") String functionUnitId1,
            @ForAll("functionUnitIds") String functionUnitId2) {
        
        // Skip if keys are the same
        Assume.that(!processKey1.equals(processKey2));
        
        // Reset for each test
        component.clearAllCache();
        
        // Setup mocks and resolve both keys
        setupMockForProcessKeyResolution(processKey1, functionUnitId1);
        setupMockForProcessKeyResolution(processKey2, functionUnitId2);
        
        component.resolveFunctionUnitId(processKey1);
        component.resolveFunctionUnitId(processKey2);
        
        // Verify both are cached
        assertThat(component.isProcessKeyCached(processKey1)).isTrue();
        assertThat(component.isProcessKeyCached(processKey2)).isTrue();
        
        // Clear only the first key
        component.clearProcessKeyCache(processKey1);
        
        // Verify first key is removed, second key remains
        assertThat(component.isProcessKeyCached(processKey1)).isFalse();
        assertThat(component.isProcessKeyCached(processKey2)).isTrue();
    }

    /**
     * Property: Clearing all cache removes all cached keys.
     * 
     * **Validates: Requirements 3.3**
     */
    @Property(tries = 50)
    void clearingAllCacheRemovesAllKeys(
            @ForAll("validProcessKeys") String processKey1,
            @ForAll("validProcessKeys") String processKey2,
            @ForAll("functionUnitIds") String functionUnitId1,
            @ForAll("functionUnitIds") String functionUnitId2) {
        
        // Skip if keys are the same
        Assume.that(!processKey1.equals(processKey2));
        
        // Reset for each test
        component.clearAllCache();
        
        // Setup mocks and resolve both keys
        setupMockForProcessKeyResolution(processKey1, functionUnitId1);
        setupMockForProcessKeyResolution(processKey2, functionUnitId2);
        
        component.resolveFunctionUnitId(processKey1);
        component.resolveFunctionUnitId(processKey2);
        
        // Verify both are cached
        assertThat(component.getProcessKeyCacheSize()).isGreaterThanOrEqualTo(2);
        
        // Clear all cache
        component.clearAllCache();
        
        // Verify all keys are removed
        assertThat(component.isProcessKeyCached(processKey1)).isFalse();
        assertThat(component.isProcessKeyCached(processKey2)).isFalse();
        assertThat(component.getProcessKeyCacheSize()).isEqualTo(0);
    }

    // ==================== Helper Methods ====================

    private void setupMockForProcessKeyResolution(String processKey, String functionUnitId) {
        // Mock the code lookup to fail (404)
        String codeUrl = "http://localhost:8090/api/v1/admin/function-units/code/" + 
                         encodeUrl(processKey) + "/latest";
        when(mockRestTemplate.exchange(
                eq(codeUrl),
                eq(HttpMethod.GET),
                isNull(),
                any(ParameterizedTypeReference.class)
        )).thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));
        
        // Mock the process key lookup to succeed
        String processKeyUrl = "http://localhost:8090/api/v1/admin/function-units/by-process-key/" + 
                               encodeUrl(processKey);
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("id", functionUnitId);
        responseBody.put("code", processKey);
        responseBody.put("name", "Test Function Unit");
        
        when(mockRestTemplate.exchange(
                eq(processKeyUrl),
                eq(HttpMethod.GET),
                isNull(),
                any(ParameterizedTypeReference.class)
        )).thenAnswer(invocation -> {
            apiCallCount.incrementAndGet();
            return ResponseEntity.ok(responseBody);
        });
    }

    private String encodeUrl(String value) {
        try {
            return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }

    // ==================== Arbitrary Providers ====================

    @Provide
    Arbitrary<String> validProcessKeys() {
        // Generate process keys like: Process_PurchaseRequest, LeaveRequest, etc.
        return Arbitraries.of(
                "Process_PurchaseRequest",
                "Process_LeaveRequest",
                "Process_ExpenseReport",
                "SimpleProcess",
                "MyWorkflow",
                "Test_Process_123",
                "Process_HR_Onboarding",
                "Process_IT_Request",
                "Process_Finance_Approval",
                "Process_Sales_Order"
        );
    }

    @Provide
    Arbitrary<String> functionUnitIds() {
        // Generate UUID-like function unit IDs
        return Arbitraries.strings()
                .withCharRange('a', 'f')
                .withCharRange('0', '9')
                .ofLength(32)
                .map(s -> s.substring(0, 8) + "-" + s.substring(8, 12) + "-" + 
                         s.substring(12, 16) + "-" + s.substring(16, 20) + "-" + 
                         s.substring(20, 32));
    }
}
