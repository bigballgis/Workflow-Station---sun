package com.developer.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.developer.client.WorkflowEngineClient;
import com.developer.entity.FunctionUnit;
import com.developer.repository.FunctionUnitAccessRepository;
import com.developer.repository.FunctionUnitRepository;
import com.developer.repository.ProcessInstanceRepository;
import com.platform.common.exception.TransactionError;
import net.jqwik.api.*;
import org.mockito.Mock;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * Property-based tests for transaction failure logging.
 * Tests that all transaction failures are properly logged with error level and stack traces.
 */
class TransactionFailureLoggingPropertyTest {
    
    @Mock
    private FunctionUnitRepository functionUnitRepository;
    
    @Mock
    private FunctionUnitAccessRepository accessRepository;
    
    @Mock
    private ProcessInstanceRepository processInstanceRepository;
    
    @Mock
    private VersionService versionService;
    
    @Mock
    private PermissionService permissionService;
    
    @Mock
    private WorkflowEngineClient workflowEngineClient;
    
    private DeploymentService deploymentService;
    private RollbackService rollbackService;
    
    /**
     * Property 33: Transaction Failure Logging
     * 
     * For any transaction (deployment or rollback) that fails, an error log entry should be 
     * created containing the failure reason.
     * 
     * **Validates: Requirements 11.5**
     */
    @Property(tries = 100)
    void deploymentTransactionFailuresAreLogged(
            @ForAll("functionUnitNames") String functionUnitName,
            @ForAll("changeTypes") String changeType) {
        
        // Re-initialize for each iteration
        openMocks(this);
        deploymentService = new DeploymentService(
                functionUnitRepository,
                versionService,
                permissionService,
                workflowEngineClient
        );
        
        // Set up log appender for this iteration
        Logger logger = (Logger) LoggerFactory.getLogger(DeploymentService.class);
        ListAppender<ILoggingEvent> logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
        
        try {
            // Given: A deployment that will fail during BPMN deployment to Flowable
            String version = "1.0.0";
            String bpmnXml = "<bpmn>test</bpmn>";
            Map<String, Object> metadata = new HashMap<>();
            
            // Mock version generation
            when(versionService.generateNextVersion(functionUnitName, changeType))
                    .thenReturn(version);
            
            // Mock version existence check
            when(versionService.versionExists(functionUnitName, version))
                    .thenReturn(false);
            
            // Mock function unit save
            FunctionUnit mockFunctionUnit = FunctionUnit.builder()
                    .id(1L)
                    .name(functionUnitName)
                    .version(version)
                    .isActive(false)
                    .deployedAt(Instant.now())
                    .build();
            when(functionUnitRepository.save(any(FunctionUnit.class)))
                    .thenReturn(mockFunctionUnit);
            
            // Mock Flowable deployment to fail
            RuntimeException flowableError = new RuntimeException("Flowable deployment failed");
            when(workflowEngineClient.deployProcess(anyString(), anyString(), anyString()))
                    .thenThrow(flowableError);
            
            // When: Attempting to deploy the function unit
            // Then: The deployment should fail with TransactionError
            assertThatThrownBy(() -> deploymentService.deployFunctionUnit(
                    functionUnitName, bpmnXml, changeType, metadata))
                    .isInstanceOf(TransactionError.class);
            
            // And: An error log entry should be created
            List<ILoggingEvent> logEvents = logAppender.list;
            assertThat(logEvents).isNotEmpty();
            
            // And: At least one log event should be at ERROR level
            boolean hasErrorLog = logEvents.stream()
                    .anyMatch(event -> event.getLevel() == Level.ERROR);
            assertThat(hasErrorLog)
                    .as("Expected at least one ERROR level log entry for transaction failure")
                    .isTrue();
            
            // And: The error log should contain information about the failure
            boolean hasRelevantErrorLog = logEvents.stream()
                    .filter(event -> event.getLevel() == Level.ERROR)
                    .anyMatch(event -> {
                        String message = event.getFormattedMessage();
                        return message.contains("Failed") || 
                               message.contains("failed") ||
                               message.contains("rolling back");
                    });
            assertThat(hasRelevantErrorLog)
                    .as("Expected error log to contain failure information")
                    .isTrue();
            
            // And: The error log should include the exception (stack trace)
            boolean hasExceptionInLog = logEvents.stream()
                    .filter(event -> event.getLevel() == Level.ERROR)
                    .anyMatch(event -> event.getThrowableProxy() != null);
            assertThat(hasExceptionInLog)
                    .as("Expected error log to include exception/stack trace")
                    .isTrue();
        } finally {
            // Clean up log appender
            logger.detachAppender(logAppender);
        }
    }
    
    /**
     * Property 33: Transaction Failure Logging (Rollback variant)
     * 
     * For any rollback transaction that fails, an error log entry should be created 
     * containing the failure reason.
     * 
     * **Validates: Requirements 11.5**
     */
    @Property(tries = 100)
    void rollbackTransactionFailuresAreLogged(
            @ForAll("versionIds") Long versionId) {
        
        // Re-initialize for each iteration
        openMocks(this);
        rollbackService = new RollbackService(
                functionUnitRepository,
                processInstanceRepository,
                versionService
        );
        
        // Set up log appender for this iteration
        Logger logger = (Logger) LoggerFactory.getLogger(RollbackService.class);
        ListAppender<ILoggingEvent> logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
        
        try {
            // Given: A rollback that will fail due to database error
            RuntimeException databaseError = new RuntimeException("Database connection failed");
            when(functionUnitRepository.findById(versionId))
                    .thenThrow(databaseError);
            
            // When: Attempting to execute the rollback
            // Then: The rollback should fail
            assertThatThrownBy(() -> rollbackService.rollbackToVersion(versionId))
                    .isInstanceOf(RuntimeException.class);
            
            // And: An error log entry should be created
            List<ILoggingEvent> logEvents = logAppender.list;
            assertThat(logEvents).isNotEmpty();
            
            // And: At least one log event should be at ERROR level
            boolean hasErrorLog = logEvents.stream()
                    .anyMatch(event -> event.getLevel() == Level.ERROR);
            assertThat(hasErrorLog)
                    .as("Expected at least one ERROR level log entry for rollback failure")
                    .isTrue();
            
            // And: The error log should contain information about the rollback failure
            boolean hasRelevantErrorLog = logEvents.stream()
                    .filter(event -> event.getLevel() == Level.ERROR)
                    .anyMatch(event -> {
                        String message = event.getFormattedMessage();
                        return message.contains("Rollback") || 
                               message.contains("rollback") ||
                               message.contains("Failed") ||
                               message.contains("failed");
                    });
            assertThat(hasRelevantErrorLog)
                    .as("Expected error log to contain rollback failure information")
                    .isTrue();
            
            // And: The error log should include the exception (stack trace)
            boolean hasExceptionInLog = logEvents.stream()
                    .filter(event -> event.getLevel() == Level.ERROR)
                    .anyMatch(event -> event.getThrowableProxy() != null);
            assertThat(hasExceptionInLog)
                    .as("Expected error log to include exception/stack trace")
                    .isTrue();
        } finally {
            // Clean up log appender
            logger.detachAppender(logAppender);
        }
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
     * Arbitrary for generating valid change types
     */
    @Provide
    Arbitrary<String> changeTypes() {
        return Arbitraries.of("major", "minor", "patch");
    }
    
    /**
     * Arbitrary for generating version IDs
     */
    @Provide
    Arbitrary<Long> versionIds() {
        return Arbitraries.longs().between(1L, 1000L);
    }
}
