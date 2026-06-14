package com.admin.service;

import com.admin.entity.RelationTableAuditLog;
import com.admin.repository.RelationTableAuditLogRepository;
import com.admin.repository.UserRepository;
import com.admin.service.impl.RelationTableAuditServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.enums.RelationAuditAction;
import com.platform.security.util.SecurityContextUtils;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeTry;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Relation Table 审计日志属性测试
 *
 * Feature: relation-tables, Property 9: 审计日志完整性
 *
 * Validates: Requirements 13.1, 13.2, 13.3, 13.4
 */
class RelationTableAuditPropertyTest {

    private RelationTableAuditLogRepository auditLogRepository;
    private UserRepository userRepository;
    private ObjectMapper objectMapper;
    private RelationTableAuditServiceImpl auditService;

    @BeforeTry
    void setUp() {
        auditLogRepository = mock(RelationTableAuditLogRepository.class);
        userRepository = mock(UserRepository.class);
        when(userRepository.findById(any())).thenReturn(Optional.empty());
        objectMapper = new ObjectMapper();
        auditService = new RelationTableAuditServiceImpl(auditLogRepository, objectMapper, userRepository);
    }

    // ==================== Arbitraries ====================

    @Provide
    Arbitrary<Long> tableIds() {
        return Arbitraries.longs().between(1L, 10000L);
    }

    @Provide
    Arbitrary<String> tableNames() {
        return Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(20)
                .map(String::toLowerCase)
                .map(s -> "rt_" + s);
    }

    @Provide
    Arbitrary<String> rowIds() {
        return Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(20);
    }

    @Provide
    Arbitrary<Map<String, Object>> dataMaps() {
        return Arbitraries.maps(
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10).map(String::toLowerCase),
                Arbitraries.oneOf(
                        Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(30).map(s -> (Object) s),
                        Arbitraries.integers().between(0, 99999).map(i -> (Object) i),
                        Arbitraries.of(true, false).map(b -> (Object) b)
                )
        ).ofMinSize(1).ofMaxSize(5);
    }

    @Provide
    Arbitrary<String> operatorIds() {
        return Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(15).map(String::toLowerCase);
    }

    @Provide
    Arbitrary<String> operatorNames() {
        return Arbitraries.strings().alpha().ofMinLength(2).ofMaxLength(20);
    }

    @Provide
    Arbitrary<String> statuses() {
        return Arbitraries.of("Active", "Inactive");
    }

    @Provide
    Arbitrary<RelationAuditAction> auditActions() {
        return Arbitraries.of(RelationAuditAction.values());
    }

    // ==================== Property 9: 审计日志完整性 ====================

    /**
     * Property 9: 审计日志完整性 - ADD 操作
     *
     * For any ADD operation, the system should create an audit log with:
     * - action = ADD
     * - newValue containing the new data as JSON
     * - oldValue = null
     * - correct operator info and timestamp
     *
     * Feature: relation-tables, Property 9: 审计日志完整性
     * Validates: Requirements 13.1
     */
    @Property(tries = 100)
    @Tag("Feature: relation-tables, Property 9: 审计日志完整性")
    void addOperationCreatesCorrectAuditLog(
            @ForAll("tableIds") Long tableId,
            @ForAll("tableNames") String tableName,
            @ForAll("rowIds") String rowId,
            @ForAll("dataMaps") Map<String, Object> newData,
            @ForAll("operatorIds") String operatorId,
            @ForAll("operatorNames") String operatorName
    ) {
        try (MockedStatic<SecurityContextUtils> securityMock = mockStatic(SecurityContextUtils.class)) {
            securityMock.when(SecurityContextUtils::getCurrentUserId)
                    .thenReturn(Optional.of(operatorId));
            securityMock.when(SecurityContextUtils::getCurrentUsername)
                    .thenReturn(Optional.of(operatorName));

            ArgumentCaptor<RelationTableAuditLog> captor =
                    ArgumentCaptor.forClass(RelationTableAuditLog.class);
            when(auditLogRepository.save(captor.capture()))
                    .thenAnswer(inv -> inv.getArgument(0));

            auditService.logAdd(tableId, tableName, rowId, newData);

            verify(auditLogRepository, times(1)).save(any(RelationTableAuditLog.class));
            RelationTableAuditLog saved = captor.getValue();

            // Verify action type
            assertThat(saved.getAction())
                    .as("Action should be ADD")
                    .isEqualTo(RelationAuditAction.ADD.getCode());

            // Verify old_value is null for ADD
            assertThat(saved.getOldValue())
                    .as("old_value should be null for ADD operation")
                    .isNull();

            // Verify new_value contains the new data
            assertThat(saved.getNewValue())
                    .as("new_value should not be null for ADD operation")
                    .isNotNull();
            Map<String, Object> parsedNewValue = parseJson(saved.getNewValue());
            assertThat(parsedNewValue.keySet())
                    .as("new_value keys should match input data keys")
                    .containsExactlyInAnyOrderElementsOf(newData.keySet());

            // Verify operator info
            assertThat(saved.getOperatorId()).isEqualTo(operatorId);
            assertThat(saved.getOperatorName()).isEqualTo(operatorName);

            // Verify table info
            assertThat(saved.getTableId()).isEqualTo(tableId);
            assertThat(saved.getTableName()).isEqualTo(tableName);
            assertThat(saved.getRowId()).isEqualTo(rowId);

            // Verify timestamp is set
            assertThat(saved.getOperatedAt())
                    .as("operatedAt should be set")
                    .isNotNull();
        }
    }

    /**
     * Property 9: 审计日志完整性 - UPDATE 操作
     *
     * For any UPDATE operation, the system should create an audit log with:
     * - action = UPDATE
     * - oldValue reflecting the data before change
     * - newValue reflecting the data after change
     * - correct operator info and timestamp
     *
     * Feature: relation-tables, Property 9: 审计日志完整性
     * Validates: Requirements 13.2
     */
    @Property(tries = 100)
    @Tag("Feature: relation-tables, Property 9: 审计日志完整性")
    void updateOperationCreatesCorrectAuditLog(
            @ForAll("tableIds") Long tableId,
            @ForAll("tableNames") String tableName,
            @ForAll("rowIds") String rowId,
            @ForAll("dataMaps") Map<String, Object> oldData,
            @ForAll("dataMaps") Map<String, Object> newData,
            @ForAll("operatorIds") String operatorId,
            @ForAll("operatorNames") String operatorName
    ) {
        try (MockedStatic<SecurityContextUtils> securityMock = mockStatic(SecurityContextUtils.class)) {
            securityMock.when(SecurityContextUtils::getCurrentUserId)
                    .thenReturn(Optional.of(operatorId));
            securityMock.when(SecurityContextUtils::getCurrentUsername)
                    .thenReturn(Optional.of(operatorName));

            ArgumentCaptor<RelationTableAuditLog> captor =
                    ArgumentCaptor.forClass(RelationTableAuditLog.class);
            when(auditLogRepository.save(captor.capture()))
                    .thenAnswer(inv -> inv.getArgument(0));

            auditService.logUpdate(tableId, tableName, rowId, oldData, newData);

            verify(auditLogRepository, times(1)).save(any(RelationTableAuditLog.class));
            RelationTableAuditLog saved = captor.getValue();

            // Verify action type
            assertThat(saved.getAction())
                    .as("Action should be UPDATE")
                    .isEqualTo(RelationAuditAction.UPDATE.getCode());

            // Verify old_value contains old data
            assertThat(saved.getOldValue())
                    .as("old_value should not be null for UPDATE operation")
                    .isNotNull();
            Map<String, Object> parsedOldValue = parseJson(saved.getOldValue());
            assertThat(parsedOldValue.keySet())
                    .as("old_value keys should match old data keys")
                    .containsExactlyInAnyOrderElementsOf(oldData.keySet());

            // Verify new_value contains new data
            assertThat(saved.getNewValue())
                    .as("new_value should not be null for UPDATE operation")
                    .isNotNull();
            Map<String, Object> parsedNewValue = parseJson(saved.getNewValue());
            assertThat(parsedNewValue.keySet())
                    .as("new_value keys should match new data keys")
                    .containsExactlyInAnyOrderElementsOf(newData.keySet());

            // Verify operator info
            assertThat(saved.getOperatorId()).isEqualTo(operatorId);
            assertThat(saved.getOperatorName()).isEqualTo(operatorName);

            // Verify table info and timestamp
            assertThat(saved.getTableId()).isEqualTo(tableId);
            assertThat(saved.getTableName()).isEqualTo(tableName);
            assertThat(saved.getRowId()).isEqualTo(rowId);
            assertThat(saved.getOperatedAt()).isNotNull();
        }
    }

    /**
     * Property 9: 审计日志完整性 - DELETE 操作
     *
     * For any DELETE operation, the system should create an audit log with:
     * - action = DELETE
     * - oldValue containing the deleted data
     * - newValue = null
     * - correct operator info and timestamp
     *
     * Feature: relation-tables, Property 9: 审计日志完整性
     * Validates: Requirements 13.4
     */
    @Property(tries = 100)
    @Tag("Feature: relation-tables, Property 9: 审计日志完整性")
    void deleteOperationCreatesCorrectAuditLog(
            @ForAll("tableIds") Long tableId,
            @ForAll("tableNames") String tableName,
            @ForAll("rowIds") String rowId,
            @ForAll("dataMaps") Map<String, Object> oldData,
            @ForAll("operatorIds") String operatorId,
            @ForAll("operatorNames") String operatorName
    ) {
        try (MockedStatic<SecurityContextUtils> securityMock = mockStatic(SecurityContextUtils.class)) {
            securityMock.when(SecurityContextUtils::getCurrentUserId)
                    .thenReturn(Optional.of(operatorId));
            securityMock.when(SecurityContextUtils::getCurrentUsername)
                    .thenReturn(Optional.of(operatorName));

            ArgumentCaptor<RelationTableAuditLog> captor =
                    ArgumentCaptor.forClass(RelationTableAuditLog.class);
            when(auditLogRepository.save(captor.capture()))
                    .thenAnswer(inv -> inv.getArgument(0));

            auditService.logDelete(tableId, tableName, rowId, oldData);

            verify(auditLogRepository, times(1)).save(any(RelationTableAuditLog.class));
            RelationTableAuditLog saved = captor.getValue();

            // Verify action type
            assertThat(saved.getAction())
                    .as("Action should be DELETE")
                    .isEqualTo(RelationAuditAction.DELETE.getCode());

            // Verify old_value contains deleted data
            assertThat(saved.getOldValue())
                    .as("old_value should not be null for DELETE operation")
                    .isNotNull();
            Map<String, Object> parsedOldValue = parseJson(saved.getOldValue());
            assertThat(parsedOldValue.keySet())
                    .as("old_value keys should match deleted data keys")
                    .containsExactlyInAnyOrderElementsOf(oldData.keySet());

            // Verify new_value is null for DELETE
            assertThat(saved.getNewValue())
                    .as("new_value should be null for DELETE operation")
                    .isNull();

            // Verify operator info
            assertThat(saved.getOperatorId()).isEqualTo(operatorId);
            assertThat(saved.getOperatorName()).isEqualTo(operatorName);

            // Verify table info and timestamp
            assertThat(saved.getTableId()).isEqualTo(tableId);
            assertThat(saved.getTableName()).isEqualTo(tableName);
            assertThat(saved.getRowId()).isEqualTo(rowId);
            assertThat(saved.getOperatedAt()).isNotNull();
        }
    }

    /**
     * Property 9: 审计日志完整性 - STATUS_CHANGE 操作
     *
     * For any STATUS_CHANGE operation, the system should create an audit log with:
     * - action = STATUS_CHANGE
     * - oldValue containing the old status
     * - newValue containing the new status
     * - correct operator info and timestamp
     *
     * Feature: relation-tables, Property 9: 审计日志完整性
     * Validates: Requirements 13.3
     */
    @Property(tries = 100)
    @Tag("Feature: relation-tables, Property 9: 审计日志完整性")
    void statusChangeOperationCreatesCorrectAuditLog(
            @ForAll("tableIds") Long tableId,
            @ForAll("tableNames") String tableName,
            @ForAll("rowIds") String rowId,
            @ForAll("statuses") String oldStatus,
            @ForAll("statuses") String newStatus,
            @ForAll("operatorIds") String operatorId,
            @ForAll("operatorNames") String operatorName
    ) {
        try (MockedStatic<SecurityContextUtils> securityMock = mockStatic(SecurityContextUtils.class)) {
            securityMock.when(SecurityContextUtils::getCurrentUserId)
                    .thenReturn(Optional.of(operatorId));
            securityMock.when(SecurityContextUtils::getCurrentUsername)
                    .thenReturn(Optional.of(operatorName));

            ArgumentCaptor<RelationTableAuditLog> captor =
                    ArgumentCaptor.forClass(RelationTableAuditLog.class);
            when(auditLogRepository.save(captor.capture()))
                    .thenAnswer(inv -> inv.getArgument(0));

            auditService.logStatusChange(tableId, tableName, rowId, oldStatus, newStatus);

            verify(auditLogRepository, times(1)).save(any(RelationTableAuditLog.class));
            RelationTableAuditLog saved = captor.getValue();

            // Verify action type
            assertThat(saved.getAction())
                    .as("Action should be STATUS_CHANGE")
                    .isEqualTo(RelationAuditAction.STATUS_CHANGE.getCode());

            // Verify old_value contains old status
            assertThat(saved.getOldValue())
                    .as("old_value should not be null for STATUS_CHANGE operation")
                    .isNotNull();
            Map<String, Object> parsedOldValue = parseJson(saved.getOldValue());
            assertThat(parsedOldValue).containsEntry("status", oldStatus);

            // Verify new_value contains new status
            assertThat(saved.getNewValue())
                    .as("new_value should not be null for STATUS_CHANGE operation")
                    .isNotNull();
            Map<String, Object> parsedNewValue = parseJson(saved.getNewValue());
            assertThat(parsedNewValue).containsEntry("status", newStatus);

            // Verify operator info
            assertThat(saved.getOperatorId()).isEqualTo(operatorId);
            assertThat(saved.getOperatorName()).isEqualTo(operatorName);

            // Verify table info and timestamp
            assertThat(saved.getTableId()).isEqualTo(tableId);
            assertThat(saved.getTableName()).isEqualTo(tableName);
            assertThat(saved.getRowId()).isEqualTo(rowId);
            assertThat(saved.getOperatedAt()).isNotNull();
        }
    }

    // ==================== Property 16: 审计日志过滤正确性 ====================

    @Provide
    Arbitrary<Instant> instants() {
        long base = Instant.parse("2024-01-01T00:00:00Z").getEpochSecond();
        long end = Instant.parse("2025-01-01T00:00:00Z").getEpochSecond();
        return Arbitraries.longs().between(base, end).map(Instant::ofEpochSecond);
    }

    @Provide
    Arbitrary<List<RelationTableAuditLog>> auditLogLists() {
        Arbitrary<RelationTableAuditLog> logArbitrary = Combinators.combine(
                tableIds(),
                tableNames(),
                rowIds(),
                Arbitraries.of(RelationAuditAction.values()).map(RelationAuditAction::getCode),
                operatorIds(),
                operatorNames(),
                instants()
        ).as((tableId, tableName, rowId, action, opId, opName, time) ->
                RelationTableAuditLog.builder()
                        .id(UUID.randomUUID().toString())
                        .tableId(tableId)
                        .tableName(tableName)
                        .rowId(rowId)
                        .action(action)
                        .operatorId(opId)
                        .operatorName(opName)
                        .operatedAt(time)
                        .build()
        );
        return logArbitrary.list().ofMinSize(1).ofMaxSize(20);
    }

    /**
     * Property 16: 审计日志过滤正确性
     *
     * For any audit log query with filter conditions (tableId, action, operatorId,
     * startTime, endTime), all returned log records must satisfy the specified filter conditions.
     *
     * Feature: relation-tables, Property 16: 审计日志过滤正确性
     * Validates: Requirements 13.5
     */
    @Property(tries = 100)
    @Tag("Feature: relation-tables, Property 16: 审计日志过滤正确性")
    void auditLogFilterReturnsOnlyMatchingRecords(
            @ForAll("auditLogLists") List<RelationTableAuditLog> allLogs,
            @ForAll("tableIds") Long filterTableId,
            @ForAll("auditActions") RelationAuditAction filterAction,
            @ForAll("operatorIds") String filterOperatorId,
            @ForAll("instants") Instant filterStartTime
    ) {
        Instant filterEndTime = filterStartTime.plus(30, ChronoUnit.DAYS);
        String filterActionCode = filterAction.getCode();

        // Compute the expected filtered subset from allLogs
        List<RelationTableAuditLog> expectedFiltered = allLogs.stream()
                .filter(log -> log.getTableId().equals(filterTableId))
                .filter(log -> log.getAction().equals(filterActionCode))
                .filter(log -> log.getOperatorId().equals(filterOperatorId))
                .filter(log -> !log.getOperatedAt().isBefore(filterStartTime)
                        && !log.getOperatedAt().isAfter(filterEndTime))
                .collect(Collectors.toList());

        Pageable pageable = PageRequest.of(0, 50);
        Page<RelationTableAuditLog> mockPage = new PageImpl<>(expectedFiltered, pageable, expectedFiltered.size());

        when(auditLogRepository.findByFilters(
                eq(filterTableId), eq(filterActionCode), eq(filterOperatorId),
                eq(filterStartTime), eq(filterEndTime), eq(pageable)
        )).thenReturn(mockPage);

        // Call the service
        Page<RelationTableAuditLog> result = auditService.queryAuditLogs(
                filterTableId, filterActionCode, filterOperatorId,
                filterStartTime, filterEndTime, pageable);

        // Verify: all returned logs satisfy every filter condition
        for (RelationTableAuditLog log : result.getContent()) {
            assertThat(log.getTableId())
                    .as("Returned log tableId should match filter")
                    .isEqualTo(filterTableId);
            assertThat(log.getAction())
                    .as("Returned log action should match filter")
                    .isEqualTo(filterActionCode);
            assertThat(log.getOperatorId())
                    .as("Returned log operatorId should match filter")
                    .isEqualTo(filterOperatorId);
            assertThat(log.getOperatedAt())
                    .as("Returned log operatedAt should be >= startTime")
                    .isAfterOrEqualTo(filterStartTime);
            assertThat(log.getOperatedAt())
                    .as("Returned log operatedAt should be <= endTime")
                    .isBeforeOrEqualTo(filterEndTime);
        }

        // Verify the service correctly delegates to repository with exact filter params
        verify(auditLogRepository).findByFilters(
                filterTableId, filterActionCode, filterOperatorId,
                filterStartTime, filterEndTime, pageable);
    }

    // ==================== Helper Methods ====================

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse JSON: " + json, e);
        }
    }
}
