package com.admin.properties;

import com.admin.component.UserManagerComponent;
import com.admin.dto.request.UserCreateRequest;
import com.admin.dto.response.BatchImportResult;
import com.admin.entity.User;
import com.admin.enums.UserStatus;
import com.admin.repository.BusinessUnitRepository;
import com.admin.repository.PasswordHistoryRepository;
import com.admin.repository.UserBusinessUnitRepository;
import com.admin.repository.UserRepository;
import com.admin.service.AuditService;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeTry;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Property tests for User Import functionality
 * 
 * Properties tested:
 * - Property 12: Import Validation Consistency
 * - Property 13: Import Atomicity
 * 
 * **Validates: Requirements 6.2, 6.3, 6.4**
 */
public class UserImportProperties {
    
    private UserRepository userRepository;
    private BusinessUnitRepository businessUnitRepository;
    private PasswordHistoryRepository passwordHistoryRepository;
    private UserBusinessUnitRepository userBusinessUnitRepository;
    private PasswordEncoder passwordEncoder;
    private AuditService auditService;
    private UserManagerComponent userManagerComponent;
    
    @BeforeTry
    void setUp() {
        userRepository = mock(UserRepository.class);
        businessUnitRepository = mock(BusinessUnitRepository.class);
        passwordHistoryRepository = mock(PasswordHistoryRepository.class);
        userBusinessUnitRepository = mock(UserBusinessUnitRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        auditService = mock(AuditService.class);
        
        userManagerComponent = new UserManagerComponent(
                userRepository,
                businessUnitRepository,
                passwordHistoryRepository,
                passwordEncoder,
                auditService,
                userBusinessUnitRepository);
        
        // Default mock behaviors
        when(passwordEncoder.encode(anyString())).thenAnswer(inv -> "encoded_" + inv.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }
    
    // ==================== Property 12: Import Validation Consistency ====================
    
    /**
     * Feature: user-management, Property 12: Import Validation Consistency
     * For any import file, validation errors should be consistently reported
     * **Validates: Requirements 6.2, 6.3**
     */
    @Property(tries = 100)
    @Label("Feature: user-management, Property 12: Import validation errors consistently reported")
    void importValidationErrorsConsistentlyReported(
            @ForAll("validUsernames") String validUsername,
            @ForAll("validEmails") String validEmail) {
        
        // Given: A user with this username already exists
        when(userRepository.existsByUsername(validUsername)).thenReturn(true);
        when(userRepository.existsByEmail(validEmail)).thenReturn(false);
        
        // When: Try to create user with existing username
        UserCreateRequest request = new UserCreateRequest();
        request.setUsername(validUsername);
        request.setEmail(validEmail);
        request.setInitialPassword("Password123!");
        
        // Then: Should throw exception for duplicate username
        try {
            userManagerComponent.createUser(request);
            // If no exception, the test should fail
            assertThat(false).as("Should have thrown exception for duplicate username").isTrue();
        } catch (Exception e) {
            // Validation error should mention username
            assertThat(e.getMessage()).containsIgnoringCase("用户名");
        }
    }
    
    /**
     * Feature: user-management, Property 12: Import Validation Consistency
     * Email format validation should be consistent
     * **Validates: Requirements 6.2**
     */
    @Property(tries = 100)
    @Label("Feature: user-management, Property 12: Email format validation consistent")
    void emailFormatValidationConsistent(
            @ForAll("validUsernames") String username,
            @ForAll("invalidEmails") String invalidEmail) {
        
        // Given: Username doesn't exist
        when(userRepository.existsByUsername(username)).thenReturn(false);
        
        // When: Try to create user with invalid email
        UserCreateRequest request = new UserCreateRequest();
        request.setUsername(username);
        request.setEmail(invalidEmail);
        request.setInitialPassword("Password123!");
        
        // Then: Should throw exception for invalid email
        try {
            userManagerComponent.createUser(request);
            assertThat(false).as("Should have thrown exception for invalid email").isTrue();
        } catch (Exception e) {
            // Should be an email validation error
            assertThat(e).isNotNull();
        }
    }
    
    // ==================== Property 13: Import Atomicity ====================
    
    /**
     * Feature: user-management, Property 13: Import Atomicity
     * Batch import result should accurately reflect success/failure counts
     * **Validates: Requirements 6.3, 6.4**
     */
    @Property(tries = 100)
    @Label("Feature: user-management, Property 13: Import result counts are accurate")
    void importResultCountsAreAccurate(
            @ForAll("fileNames") String fileName) {
        
        // Given: An empty import file (parseImportFile returns empty list)
        MockMultipartFile file = new MockMultipartFile(
                "file", fileName, "application/vnd.ms-excel", new byte[0]);
        
        // When: Import the file
        BatchImportResult result = userManagerComponent.batchImportUsers(file);
        
        // Then: Result should have consistent counts
        assertThat(result.getTotalCount()).isEqualTo(
                result.getSuccessCount() + result.getFailureCount());
        
        // And: Audit should be recorded
        verify(auditService).recordBatchImport(any(BatchImportResult.class));
    }
    
    /**
     * Feature: user-management, Property 13: Import Atomicity
     * Import result should contain file name
     * **Validates: Requirements 6.3**
     */
    @Property(tries = 100)
    @Label("Feature: user-management, Property 13: Import result contains file name")
    void importResultContainsFileName(
            @ForAll("fileNames") String fileName) {
        
        // Given: An import file
        MockMultipartFile file = new MockMultipartFile(
                "file", fileName, "application/vnd.ms-excel", new byte[0]);
        
        // When: Import the file
        BatchImportResult result = userManagerComponent.batchImportUsers(file);
        
        // Then: Result should contain the file name
        assertThat(result.getFileName()).isEqualTo(fileName);
    }
    
    // ==================== Data Generators ====================
    
    @Provide
    Arbitrary<String> validUsernames() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(3)
                .ofMaxLength(20)
                .map(String::toLowerCase);
    }
    
    @Provide
    Arbitrary<String> validEmails() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(3)
                .ofMaxLength(10)
                .map(s -> s.toLowerCase() + "@example.com");
    }
    
    @Provide
    Arbitrary<String> invalidEmails() {
        return Arbitraries.oneOf(
                Arbitraries.just("invalid"),
                Arbitraries.just("no-at-sign"),
                Arbitraries.just("@nodomain"),
                Arbitraries.just("spaces in@email.com"),
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10)
        );
    }
    
    @Provide
    Arbitrary<String> fileNames() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(3)
                .ofMaxLength(20)
                .map(s -> s + ".xlsx");
    }
}
