package com.platform.common.exception;

import com.platform.common.enums.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for TransactionError exception class.
 */
@DisplayName("TransactionError")
class TransactionErrorTest {
    
    @Test
    @DisplayName("should create transaction error with operation and cause")
    void shouldCreateWithOperationAndCause() {
        RuntimeException cause = new RuntimeException("Database connection failed");
        TransactionError error = new TransactionError("deployment", cause);
        
        assertThat(error.getOperation()).isEqualTo("deployment");
        assertThat(error.getMessage()).contains("Transaction failed during deployment");
        assertThat(error.getMessage()).contains("Database connection failed");
        assertThat(error.getCause()).isEqualTo(cause);
        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.DATABASE_ERROR);
    }
    
    @Test
    @DisplayName("should create transaction error with operation and message")
    void shouldCreateWithOperationAndMessage() {
        TransactionError error = new TransactionError("rollback", "Constraint violation");
        
        assertThat(error.getOperation()).isEqualTo("rollback");
        assertThat(error.getMessage()).contains("Transaction failed during rollback");
        assertThat(error.getMessage()).contains("Constraint violation");
        assertThat(error.getCause()).isNull();
        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.DATABASE_ERROR);
    }
    
    @Test
    @DisplayName("should create transaction error with operation, message, and cause")
    void shouldCreateWithOperationMessageAndCause() {
        RuntimeException cause = new RuntimeException("Deadlock detected");
        TransactionError error = new TransactionError("version activation", "Failed to update status", cause);
        
        assertThat(error.getOperation()).isEqualTo("version activation");
        assertThat(error.getMessage()).contains("Transaction failed during version activation");
        assertThat(error.getMessage()).contains("Failed to update status");
        assertThat(error.getCause()).isEqualTo(cause);
        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.DATABASE_ERROR);
    }
    
    @Test
    @DisplayName("should format message correctly for different operations")
    void shouldFormatMessageForDifferentOperations() {
        TransactionError deploymentError = new TransactionError("deployment", "Test error");
        TransactionError rollbackError = new TransactionError("rollback", "Test error");
        TransactionError activationError = new TransactionError("version activation", "Test error");
        
        assertThat(deploymentError.getMessage()).startsWith("Transaction failed during deployment");
        assertThat(rollbackError.getMessage()).startsWith("Transaction failed during rollback");
        assertThat(activationError.getMessage()).startsWith("Transaction failed during version activation");
    }
    
    @Test
    @DisplayName("should be instance of PlatformException")
    void shouldBeInstanceOfPlatformException() {
        TransactionError error = new TransactionError("test", "Test message");
        
        assertThat(error).isInstanceOf(PlatformException.class);
        assertThat(error).isInstanceOf(RuntimeException.class);
    }
    
    @Test
    @DisplayName("should preserve cause stack trace")
    void shouldPreserveCauseStackTrace() {
        RuntimeException cause = new RuntimeException("Original error");
        TransactionError error = new TransactionError("deployment", cause);
        
        assertThat(error.getCause()).isNotNull();
        assertThat(error.getCause().getMessage()).isEqualTo("Original error");
        assertThat(error.getCause().getStackTrace()).isNotEmpty();
    }
}
