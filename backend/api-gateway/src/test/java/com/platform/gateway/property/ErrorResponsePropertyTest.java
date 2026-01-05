package com.platform.gateway.property;

import com.platform.common.dto.ErrorResponse;
import com.platform.common.enums.ErrorCode;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.Size;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property tests for error response format consistency.
 * Validates: Property 8 (Error Response Format Consistency)
 */
class ErrorResponsePropertyTest {
    
    // Property 8: Error Response Format Consistency
    // For any API error response, it should contain unified error code,
    // error message, and timestamp fields
    
    @Property(tries = 100)
    void errorResponseShouldContainRequiredFields(
            @ForAll("errorCodes") ErrorCode errorCode,
            @ForAll @AlphaChars @Size(min = 1, max = 100) String message,
            @ForAll @AlphaChars @Size(min = 1, max = 50) String path) {
        
        ErrorResponse response = ErrorResponse.builder()
                .errorCode(errorCode.getCode())
                .message(message)
                .timestamp(LocalDateTime.now())
                .path(path)
                .build();
        
        // Required fields should be present
        assertThat(response.getErrorCode()).isNotNull().isNotBlank();
        assertThat(response.getMessage()).isNotNull().isNotBlank();
        assertThat(response.getTimestamp()).isNotNull();
        assertThat(response.getPath()).isNotNull().isNotBlank();
    }
    
    @Property(tries = 100)
    void errorCodeShouldFollowFormat(
            @ForAll("errorCodes") ErrorCode errorCode) {
        
        String code = errorCode.getCode();
        
        // Error code should be 4 digits
        assertThat(code).matches("\\d{4}");
        
        // First digit indicates category
        int category = Integer.parseInt(code.substring(0, 1));
        assertThat(category).isBetween(1, 5);
    }
    
    @Property(tries = 100)
    void errorResponseWithTraceIdShouldPreserveIt(
            @ForAll("errorCodes") ErrorCode errorCode,
            @ForAll @AlphaChars @Size(min = 32, max = 32) String traceId) {
        
        ErrorResponse response = ErrorResponse.builder()
                .errorCode(errorCode.getCode())
                .message(errorCode.getDefaultMessage())
                .traceId(traceId)
                .timestamp(LocalDateTime.now())
                .path("/api/test")
                .build();
        
        assertThat(response.getTraceId()).isEqualTo(traceId);
    }
    
    @Property(tries = 100)
    void timestampShouldBeRecentPast(
            @ForAll("errorCodes") ErrorCode errorCode) {
        
        LocalDateTime before = LocalDateTime.now();
        
        ErrorResponse response = ErrorResponse.builder()
                .errorCode(errorCode.getCode())
                .message(errorCode.getDefaultMessage())
                .timestamp(LocalDateTime.now())
                .path("/api/test")
                .build();
        
        LocalDateTime after = LocalDateTime.now();
        
        // Timestamp should be between before and after
        assertThat(response.getTimestamp())
                .isAfterOrEqualTo(before)
                .isBeforeOrEqualTo(after);
    }
    
    @Property(tries = 50)
    void authenticationErrorsShouldHave1xxxCode() {
        ErrorCode[] authErrors = {
                ErrorCode.AUTH_TOKEN_INVALID,
                ErrorCode.AUTH_TOKEN_EXPIRED,
                ErrorCode.AUTH_CREDENTIALS_INVALID,
                ErrorCode.AUTH_ACCOUNT_LOCKED
        };
        
        for (ErrorCode error : authErrors) {
            assertThat(error.getCode()).startsWith("1");
        }
    }
    
    @Property(tries = 50)
    void permissionErrorsShouldHave2xxxCode() {
        ErrorCode[] permErrors = {
                ErrorCode.PERMISSION_DENIED,
                ErrorCode.PERMISSION_INSUFFICIENT
        };
        
        for (ErrorCode error : permErrors) {
            assertThat(error.getCode()).startsWith("2");
        }
    }
    
    @Property(tries = 50)
    void systemErrorsShouldHave5xxxCode() {
        ErrorCode[] sysErrors = {
                ErrorCode.INTERNAL_ERROR,
                ErrorCode.SERVICE_UNAVAILABLE,
                ErrorCode.DATABASE_ERROR,
                ErrorCode.CACHE_ERROR,
                ErrorCode.RATE_LIMIT_EXCEEDED
        };
        
        for (ErrorCode error : sysErrors) {
            assertThat(error.getCode()).startsWith("5");
        }
    }
    
    @Provide
    Arbitrary<ErrorCode> errorCodes() {
        return Arbitraries.of(ErrorCode.values());
    }
}
