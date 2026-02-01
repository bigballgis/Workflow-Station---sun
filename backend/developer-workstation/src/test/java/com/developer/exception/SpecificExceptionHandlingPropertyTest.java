package com.developer.exception;

import com.developer.dto.ErrorResponse;
import net.jqwik.api.*;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.Size;
import net.jqwik.api.lifecycle.BeforeTry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;

import static org.mockito.Mockito.*;

/**
 * Property-based tests for specific exception handling framework.
 * 
 * **Feature: technical-debt-remediation, Property 9: Specific Exception Handling**
 * **Validates: Requirements 3.1, 3.2, 3.3**
 */
class SpecificExceptionHandlingPropertyTest {
    
    private GlobalExceptionHandler exceptionHandler;
    private WebRequest mockRequest;
    
    @BeforeTry
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        mockRequest = mock(WebRequest.class);
        when(mockRequest.getDescription(false)).thenReturn("uri=/test/path");
    }
    
    /**
     * Property 9: Specific Exception Handling
     * 
     * For any exception that occurs, the error handler should catch and process it 
     * with appropriate specificity rather than generic exception catching, maintaining 
     * error context and providing meaningful messages.
     * 
     * **Validates: Requirements 3.1, 3.2, 3.3**
     */
    @Property(tries = 100)
    @Label("Specific Exception Handling - Exceptions are handled with appropriate specificity")
    void specificExceptionHandlingProperty(
            @ForAll("applicationExceptions") ApplicationException exception) {
        
        // Act: Handle the exception through the global exception handler
        ResponseEntity<ErrorResponse> response = handleExceptionByType(exception);
        
        // Assert: Exception handling should be specific and appropriate
        
        // 1. Response should never be null
        assert response != null : "Exception handling should always return a response";
        
        // 2. Response should have appropriate HTTP status based on exception type
        assert response.getStatusCode() != null : "Response should have HTTP status";
        
        // 3. Error response should contain proper error information
        ErrorResponse errorResponse = response.getBody();
        assert errorResponse != null : "Error response should not be null";
        assert errorResponse.getCode() != null : "Error code should not be null";
        assert errorResponse.getMessage() != null : "Error message should not be null";
        assert errorResponse.getTimestamp() != null : "Timestamp should not be null";
        
        // 4. Error handling should be specific to exception type
        if (exception instanceof SecurityException secEx) {
            assert errorResponse.getCode().equals(secEx.getErrorCode()) : 
                "Security exception should preserve error code";
            // Security exceptions should return generic messages for safety
            assert errorResponse.getMessage().equals("A security error occurred") : 
                "Security exceptions should return safe generic messages";
        } else if (exception instanceof ValidationException valEx) {
            assert errorResponse.getCode().equals(valEx.getErrorCode()) : 
                "Validation exception should preserve error code";
            assert errorResponse.getMessage().equals(valEx.getMessage()) : 
                "Validation exceptions should preserve original message";
        } else if (exception instanceof DataAccessException dataEx) {
            assert errorResponse.getCode().equals(dataEx.getErrorCode()) : 
                "Data access exception should preserve error code";
            // Data access exceptions should return generic messages to avoid exposing internals
            assert errorResponse.getMessage().equals("A data access error occurred") : 
                "Data access exceptions should return safe generic messages";
        }
        
        // 5. Error context should be maintained (verified through logging)
        assert exception.getContext() != null : "Exception context should be maintained";
        
        // 6. HTTP status should match exception category
        verifyHttpStatusMatchesCategory(exception, response);
    }
    
    /**
     * Property: Error Context Preservation
     * 
     * For any application exception, error context and metadata should be preserved
     * throughout the handling process.
     * 
     * **Validates: Requirements 3.2, 3.3**
     */
    @Property(tries = 50)
    @Label("Error Context Preservation - Context is maintained during handling")
    void errorContextPreservationProperty(
            @ForAll @NotBlank @Size(min = 5, max = 100) String operation,
            @ForAll @NotBlank @Size(min = 5, max = 50) String component,
            @ForAll @NotBlank @Size(min = 10, max = 200) String message) {
        
        // Arrange: Create exception with specific context
        ErrorContext context = ErrorContext.of(operation, component);
        ValidationException exception = new ValidationException(message, 
            java.util.List.of(), context);
        
        // Act: Handle the exception
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleValidation(exception, mockRequest);
        
        // Assert: Context should be preserved
        
        // 1. Response should contain error information
        assert response != null : "Response should not be null";
        ErrorResponse errorResponse = response.getBody();
        assert errorResponse != null : "Error response should not be null";
        
        // 2. Original exception context should be accessible
        assert exception.getContext().getOperation().equals(operation) : 
            "Operation context should be preserved";
        assert exception.getContext().getComponent().equals(component) : 
            "Component context should be preserved";
        
        // 3. Error message should be preserved for validation exceptions
        assert errorResponse.getMessage().equals(message) : 
            "Error message should be preserved for validation exceptions";
        
        // 4. Error code should be consistent
        assert errorResponse.getCode().equals(exception.getErrorCode()) : 
            "Error code should be consistent";
    }
    
    /**
     * Property: Exception Hierarchy Compliance
     * 
     * For any exception type, the system should handle it according to proper 
     * exception hierarchies with appropriate categorization.
     * 
     * **Validates: Requirements 3.4**
     */
    @Property(tries = 50)
    @Label("Exception Hierarchy Compliance - Exceptions follow proper hierarchy")
    void exceptionHierarchyComplianceProperty(
            @ForAll("exceptionCategories") ErrorCategory category) {
        
        // Arrange: Create exception of specific category
        ApplicationException exception = createExceptionByCategory(category);
        
        // Act: Verify exception properties
        ErrorCategory actualCategory = exception.getCategory();
        ErrorSeverity severity = exception.getSeverity();
        
        // Assert: Exception should follow hierarchy rules
        
        // 1. Category should match expected
        assert actualCategory == category : 
            "Exception category should match expected";
        
        // 2. Severity should be appropriate for category
        assert severity != null : "Exception should have severity";
        
        // 3. Category-specific rules should be followed
        switch (category) {
            case SECURITY:
                assert severity == ErrorSeverity.CRITICAL || severity == ErrorSeverity.ERROR : 
                    "Security exceptions should have high severity";
                assert exception instanceof SecurityException : 
                    "Security category should use SecurityException";
                break;
            case VALIDATION:
                assert severity == ErrorSeverity.WARN || severity == ErrorSeverity.ERROR : 
                    "Validation exceptions should have appropriate severity";
                assert exception instanceof ValidationException : 
                    "Validation category should use ValidationException";
                break;
            case DATA_ACCESS:
                assert severity == ErrorSeverity.ERROR : 
                    "Data access exceptions should have error severity";
                assert exception instanceof DataAccessException : 
                    "Data access category should use DataAccessException";
                break;
            case BUSINESS_LOGIC:
                assert severity == ErrorSeverity.WARN : 
                    "Business logic exceptions should have warning severity";
                assert exception instanceof BusinessLogicException : 
                    "Business logic category should use BusinessLogicException";
                break;
        }
        
        // 4. Exception should have proper error code format
        assert exception.getErrorCode() != null : "Exception should have error code";
        assert exception.getErrorCode().length() > 3 : "Error code should be meaningful";
    }
    
    // Helper methods
    
    private ResponseEntity<ErrorResponse> handleExceptionByType(ApplicationException exception) {
        if (exception instanceof SecurityException secEx) {
            return exceptionHandler.handleSecurity(secEx, mockRequest);
        } else if (exception instanceof ValidationException valEx) {
            return exceptionHandler.handleValidation(valEx, mockRequest);
        } else if (exception instanceof DataAccessException dataEx) {
            return exceptionHandler.handleDataAccess(dataEx, mockRequest);
        } else if (exception instanceof BusinessLogicException bizEx) {
            return exceptionHandler.handleBusinessLogicException(bizEx, mockRequest);
        } else {
            // For other ApplicationException types, use the generic handler
            return exceptionHandler.handleException(exception, mockRequest);
        }
    }
    
    private void verifyHttpStatusMatchesCategory(ApplicationException exception, 
                                               ResponseEntity<ErrorResponse> response) {
        switch (exception.getCategory()) {
            case VALIDATION:
                assert response.getStatusCode().is4xxClientError() : 
                    "Validation errors should return 4xx status";
                break;
            case SECURITY:
                assert response.getStatusCode().is4xxClientError() : 
                    "Security errors should return 4xx status";
                break;
            case DATA_ACCESS:
                assert response.getStatusCode().is5xxServerError() : 
                    "Data access errors should return 5xx status";
                break;
            case BUSINESS_LOGIC:
                assert response.getStatusCode().is4xxClientError() : 
                    "Business logic errors should return 4xx status";
                break;
            default:
                assert response.getStatusCode().is5xxServerError() : 
                    "Unknown errors should return 5xx status";
                break;
        }
    }
    
    private ApplicationException createExceptionByCategory(ErrorCategory category) {
        ErrorContext context = ErrorContext.of("test_operation", "TestComponent");
        
        switch (category) {
            case SECURITY:
                return SecurityException.injectionAttempt("Test security error", context);
            case VALIDATION:
                return new ValidationException("Test validation error", 
                    java.util.List.of(), context);
            case DATA_ACCESS:
                return DataAccessException.queryFailure("Test data error", context, 
                    new RuntimeException("DB error"));
            case BUSINESS_LOGIC:
                // Create a custom BusinessLogicException that extends ApplicationException
                return new BusinessLogicException("BIZ_TEST", "Test business error", 
                    "test_rule", context);
            default:
                return new ValidationException("Test error", java.util.List.of(), context);
        }
    }
    
    // Data generators
    
    @Provide
    Arbitrary<ApplicationException> applicationExceptions() {
        return Arbitraries.oneOf(
            securityExceptions(),
            validationExceptions(),
            dataAccessExceptions(),
            businessExceptions()
        );
    }
    
    @Provide
    Arbitrary<SecurityException> securityExceptions() {
        return Combinators.combine(
            Arbitraries.of(SecurityThreat.values()),
            Arbitraries.strings().withCharRange('a', 'z').ofMinLength(10).ofMaxLength(100)
        ).as((threat, message) -> {
            ErrorContext context = ErrorContext.of("security_check", "SecurityService");
            return new SecurityException("SEC_TEST", message, threat, context);
        });
    }
    
    @Provide
    Arbitrary<ValidationException> validationExceptions() {
        return Arbitraries.strings().withCharRange('a', 'z').ofMinLength(10).ofMaxLength(100)
            .map(message -> {
                ErrorContext context = ErrorContext.of("validation", "ValidationService");
                return new ValidationException(message, java.util.List.of(), context);
            });
    }
    
    @Provide
    Arbitrary<DataAccessException> dataAccessExceptions() {
        return Combinators.combine(
            Arbitraries.of("query", "connection", "transaction"),
            Arbitraries.strings().withCharRange('a', 'z').ofMinLength(10).ofMaxLength(100)
        ).as((operation, message) -> {
            ErrorContext context = ErrorContext.of("data_access", "DataService");
            return new DataAccessException("DATA_TEST", message, operation, context);
        });
    }
    
    @Provide
    Arbitrary<BusinessLogicException> businessExceptions() {
        return Combinators.combine(
            Arbitraries.strings().withCharRange('a', 'z').ofMinLength(5).ofMaxLength(50),
            Arbitraries.strings().withCharRange('a', 'z').ofMinLength(10).ofMaxLength(100)
        ).as((rule, message) -> {
            ErrorContext context = ErrorContext.of("business_logic", "BusinessService");
            return new BusinessLogicException("BIZ_TEST", message, rule, context);
        });
    }
    
    @Provide
    Arbitrary<ErrorCategory> exceptionCategories() {
        return Arbitraries.of(
            ErrorCategory.SECURITY,
            ErrorCategory.VALIDATION,
            ErrorCategory.DATA_ACCESS,
            ErrorCategory.BUSINESS_LOGIC
        );
    }
}