package com.portal.component;

import com.portal.entity.ProcessInstance;
import com.portal.repository.ProcessInstanceRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Explicit opt-in IT: isolated disposable PostgreSQL only; never run against the platform database. */
class SubTableBatchPostgresTransactionIT {
    private static LocalContainerEntityManagerFactoryBean factory;
    private static JpaTransactionManager transactions;
    private static ProcessInstanceRepository repository;
    private static com.portal.repository.ChangeHistoryRepository auditRepository;

    @BeforeAll
    static void connectToDisposableDatabase() {
        String url = required("BATCH_TEST_JDBC_URL");
        if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/batch_verification")) {
            throw new IllegalArgumentException("Only the loopback disposable batch_verification database is allowed");
        }
        var source = new DriverManagerDataSource(url, required("BATCH_TEST_DB_USER"), required("BATCH_TEST_DB_PASSWORD"));
        factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(source);
        factory.setManagedTypes(PersistenceManagedTypes.of(ProcessInstance.class.getName(), com.portal.entity.ChangeHistory.class.getName()));
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop", "hibernate.show_sql", "false"));
        factory.afterPropertiesSet();
        var entityManagerFactory = java.util.Objects.requireNonNull(factory.getObject());
        transactions = new JpaTransactionManager(entityManagerFactory);
        EntityManager shared = SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory);
        var repositoryFactory = new JpaRepositoryFactory(shared);
        repositoryFactory.addRepositoryProxyPostProcessor((proxy, info) -> proxy.addAdvice(
                new org.springframework.transaction.interceptor.TransactionInterceptor(transactions,
                        new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource())));
        repository = repositoryFactory.getRepository(ProcessInstanceRepository.class);
        auditRepository = repositoryFactory.getRepository(com.portal.repository.ChangeHistoryRepository.class);
    }

    @AfterAll
    static void closeDatabase() {
        if (factory != null) factory.destroy();
    }

    @BeforeEach
    void clearSyntheticInstances() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> auditRepository.deleteAllInBatch());
        new TransactionTemplate(transactions).executeWithoutResult(status -> repository.deleteAllInBatch());
    }

    @Test void latestAuditLookupIsScopedAndKeepsOnlyLatestPairIncludingNullIdentity() {
        java.time.Instant time = java.time.Instant.parse("2026-10-18T00:00:00Z");
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            auditRepository.saveAndFlush(audit("process-one", "lines-one", "row-one", "caption", "first", time));
            auditRepository.saveAndFlush(audit("process-one", "lines-one", "row-one", "caption", "latest-tied", time));
            var otherType = audit("process-one", "lines-one", "row-one", "caption", "other-type", time);
            otherType.setChangeType(com.portal.enums.ChangeType.SUB_TABLE_ROW_ADD);
            auditRepository.saveAndFlush(otherType);
            auditRepository.saveAndFlush(audit("process-one", "lines-one", "row-one", "quantity", "other-field", time));
            auditRepository.saveAndFlush(audit("process-one", "lines-one", null, "caption", "null-row", time));
            auditRepository.saveAndFlush(audit("process-one", "lines-one", "unselected-row", "caption", "excluded", time));
            auditRepository.saveAndFlush(audit("process-one", "lines-one", "synthetic-'quoted-行", "caption", "bound-key", time));
            auditRepository.saveAndFlush(audit("process-two", "lines-one", "row-one", "caption", "other-process", time.plusSeconds(1)));
            auditRepository.saveAndFlush(audit("process-one", "lines-two", "row-one", "caption", "other-table", time.plusSeconds(1)));
        });
        var selected = auditRepository.findLatestSubTableChanges("process-one", "lines-one", List.of("row-one"), false);
        assertEquals(java.util.Set.of("latest-tied", "other-type", "other-field"), selected.stream()
                .map(com.portal.entity.ChangeHistory::getNewValue).collect(java.util.stream.Collectors.toSet()));
        var withNull = auditRepository.findLatestSubTableChanges("process-one", "lines-one", java.util.Arrays.asList("row-one", null), true);
        assertEquals(java.util.Set.of("latest-tied", "other-type", "other-field", "null-row"), withNull.stream()
                .map(com.portal.entity.ChangeHistory::getNewValue).collect(java.util.stream.Collectors.toSet()));
        var nullOnly = auditRepository.findLatestSubTableChanges("process-one", "lines-one", java.util.Collections.singletonList(null), true);
        assertEquals(List.of("null-row"), nullOnly.stream().map(com.portal.entity.ChangeHistory::getNewValue).toList());
        var cell = new com.portal.repository.ChangeHistoryRepository.AuditCell(
                "row-one", "caption", com.portal.enums.ChangeType.SUB_TABLE_ROW_UPDATE);
        var emptyRow = new com.portal.repository.ChangeHistoryRepository.AuditCell(
                null, "caption", com.portal.enums.ChangeType.SUB_TABLE_ROW_UPDATE);
        var cells = auditRepository.findLatestAuditCells("process-one", "lines-one", List.of(cell, cell, emptyRow));
        assertEquals(java.util.Set.of("latest-tied", "null-row"), cells.stream()
                .map(com.portal.repository.ChangeHistoryRepository.AuditCellSnapshot::newValue).collect(java.util.stream.Collectors.toSet()));
        assertEquals(2, cells.size(), "Duplicate lookup keys must not duplicate audit records");
        assertTrue(auditRepository.findLatestAuditCells("process-one", "lines-one", List.of()).isEmpty());
        var quoted = new com.portal.repository.ChangeHistoryRepository.AuditCell(
                "synthetic-'quoted-行", "caption", com.portal.enums.ChangeType.SUB_TABLE_ROW_UPDATE);
        assertEquals(List.of("bound-key"), auditRepository.findLatestAuditCells("process-one", "lines-one", List.of(quoted))
                .stream().map(com.portal.repository.ChangeHistoryRepository.AuditCellSnapshot::newValue).toList());
    }

    private static com.portal.entity.ChangeHistory audit(String process, String table, String row, String field,
                                                         String value, java.time.Instant time) {
        return com.portal.entity.ChangeHistory.builder().processInstanceId(process).userId("synthetic-user")
                .subTableName(table).rowIdentifier(row).fieldName(field).newValue(value).timestamp(time)
                .changeType(com.portal.enums.ChangeType.SUB_TABLE_ROW_UPDATE).build();
    }

    @Test void largeAuditInsertRetainsEveryFieldDeduplicatesAndRollsBackItsOwnFailedBatch() {
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(java.util.Objects.requireNonNull(factory.getDataSource()));
        var component = new ChangeHistoryComponent(auditRepository, repository,
                mock(com.platform.security.repository.UserRepository.class), mock(com.portal.client.WorkflowEngineClient.class),
                jdbc, new com.fasterxml.jackson.databind.ObjectMapper(), mock(UserPortalAuditEnricher.class),
                mock(UserPortalAuditProcessInstanceMatcher.class), transactions);
        var context = com.portal.dto.ChangeHistoryContext.builder().processInstanceId("audit-synthetic")
                .taskInstanceId("task-synthetic").stageId("stage-synthetic").userId("user-synthetic").build();
        var changes = java.util.stream.IntStream.range(0, 401).mapToObj(index -> com.portal.dto.SubTableChange.builder()
                .changeType("ROW_UPDATE").rowIdentifier("row-" + index).oldValues(Map.<String, Object>of("caption", "before"))
                .newValues(Map.<String, Object>of("caption", "after")).build()).toList();
        component.recordSubTableChangesWithName(context, "synthetic-lines", changes);
        var records = auditRepository.findAll();
        assertEquals(401, records.size());
        assertTrue(records.stream().allMatch(row -> "before".equals(row.getOldValue()) && "after".equals(row.getNewValue())
                && "task-synthetic".equals(row.getTaskInstanceId()) && "stage-synthetic".equals(row.getStageId())
                && "user-synthetic".equals(row.getUserId()) && "caption".equals(row.getFieldName())
                && row.getTimestamp() != null && row.getId() != null && !row.getIsConcurrent()
                && row.getChangeType() == com.portal.enums.ChangeType.SUB_TABLE_ROW_UPDATE));
        component.recordSubTableChangesWithName(context, "synthetic-lines", changes);
        assertEquals(401, auditRepository.count(), "An identical repeated save must not append duplicates");
        var invalid = new java.util.ArrayList<>(changes);
        invalid.add(com.portal.dto.SubTableChange.builder().changeType("ROW_UPDATE").rowIdentifier("bad-row")
                .oldValues(Map.of("x".repeat(256), "before")).newValues(Map.of("x".repeat(256), "after")).build());
        component.recordSubTableChangesWithName(com.portal.dto.ChangeHistoryContext.builder()
                .processInstanceId("other-audit-synthetic").userId("user-synthetic").build(), "synthetic-lines", invalid);
        assertEquals(401, auditRepository.count(), "The audit transaction rolls back even earlier JDBC chunks");
    }

    @Test
    void v2DefinitionDigestSurvivesRealJsonbNormalizationAndTaskSave() throws Exception {
        var harness = harness("entryKey", repository);
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var instance = repository.findById("synthetic-process").orElseThrow();
        Map<String, Object> variables = new java.util.LinkedHashMap<>(instance.getVariables());
        Map<String, Object> snapshot = mapper.convertValue(variables.get(SubTableWriteIsolation.BATCH_RUNTIME_FIELD),
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { });
        snapshot.put("schemaVersion", 2);
        snapshot.put("normalizationProbe", List.of(new java.math.BigDecimal("1000.00"), new java.math.BigDecimal("0.00")));
        snapshot.put("definitionDigest", SubTableBatchSnapshotDigest.compute(snapshot, mapper));
        variables.put(SubTableWriteIsolation.BATCH_RUNTIME_FIELD, snapshot);
        instance.setVariables(variables);
        new TransactionTemplate(transactions).executeWithoutResult(status -> repository.saveAndFlush(instance));
        Map<String, Object> reloaded = mapper.convertValue(harness.read().get(SubTableWriteIsolation.BATCH_RUNTIME_FIELD),
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { });
        assertEquals(snapshot.get("definitionDigest"), SubTableBatchSnapshotDigest.compute(reloaded, mapper));
        assertFalse(SubTableBatchRuntimeSnapshot.projectForForm(harness.read(), "form-one", mapper).isEmpty());
        harness.deleteBoth(2);
        assertEquals(reloaded, harness.read().get(SubTableWriteIsolation.BATCH_RUNTIME_FIELD));
    }

    @ParameterizedTest
    @ValueSource(strings = {"entryKey", "renamedBusinessKey"})
    void invalidSecondEditedRowLeavesRealJsonbAndEntityVersionUntouched(String pk) throws Exception {
        var harness = harness(pk, repository);
        harness.enableEditContract();
        Map<String, Object> before = harness.read();
        Long version = repository.findById("synthetic-process").orElseThrow().getLockVersion();
        assertThrows(com.platform.common.exception.BusinessException.class, () -> harness.editBothCaptions(12));
        assertEquals(before, harness.read());
        assertEquals(version, repository.findById("synthetic-process").orElseThrow().getLockVersion());
        harness.editBothCaptions("valid-second-change");
        assertEquals(version + 1, repository.findById("synthetic-process").orElseThrow().getLockVersion());
    }

    @ParameterizedTest
    @ValueSource(strings = {"entryKey", "renamedBusinessKey"})
    void unmarkedInvalidValueCannotBypassMetadataOrIncrementEntityVersion(String pk) throws Exception {
        var harness = harness(pk, repository);
        Map<String, Object> before = harness.read();
        Long version = repository.findById("synthetic-process").orElseThrow().getLockVersion();
        assertThrows(com.platform.common.exception.BusinessException.class, () -> harness.editUnmarkedCaption(12));
        assertEquals(before, harness.read());
        assertEquals(version, repository.findById("synthetic-process").orElseThrow().getLockVersion());
        harness.editUnmarkedCaption("ordinary-field-outside-batch-form");
        assertEquals(version + 1, repository.findById("synthetic-process").orElseThrow().getLockVersion());
    }

    @Test
    void unmarkedAssignmentStageFailureLeavesRealJsonbAndEntityVersionUntouched() throws Exception {
        var harness = harness("renamedBusinessKey", repository);
        harness.enableAssignmentContract(true);
        Map<String, Object> before = harness.read();
        Long version = repository.findById("synthetic-process").orElseThrow().getLockVersion();
        var failure = assertThrows(com.portal.exception.PortalException.class, harness::editUnmarkedCaption);
        assertTrue(failure.getMessage().contains("already started"));
        assertEquals(before, harness.read());
        assertEquals(version, repository.findById("synthetic-process").orElseThrow().getLockVersion());
    }

    @ParameterizedTest
    @ValueSource(strings = {"entryKey", "renamedBusinessKey"})
    void flushedJpaWriteRollsBackJsonbRowsVersionsAndSnapshotAfterLateFailure(String pk) throws Exception {
        var adapter = mock(ProcessInstanceRepository.class, org.mockito.AdditionalAnswers.delegatesTo(repository));
        var harness = harness(pk, adapter);
        Map<String, Object> before = harness.read();
        Long version = repository.findById("synthetic-process").orElseThrow().getLockVersion();
        AtomicInteger flushed = new AtomicInteger();
        doAnswer(invocation -> {
            ProcessInstance written = repository.saveAndFlush(invocation.getArgument(0));
            assertEquals(List.of(), ((Map<?, ?>) written.getVariables().get("__subTables__")).get("dw:lines"));
            assertEquals(2, SubTableBatchDeletedRows.read(written.getVariables()).get("dw:lines").identities().size());
            flushed.incrementAndGet();
            throw new DataIntegrityViolationException("Synthetic failure after PostgreSQL flush");
        }).when(adapter).save(any(ProcessInstance.class));
        assertThrows(DataIntegrityViolationException.class, () -> harness.deleteBoth(2));
        assertEquals(1, flushed.get(), "Real Hibernate flush must complete before failure injection");
        assertEquals(before, harness.read(), "Re-read in a fresh persistence context, not the failed session");
        assertEquals(version, repository.findById("synthetic-process").orElseThrow().getLockVersion());
    }

    @ParameterizedTest
    @ValueSource(strings = {"entryKey", "renamedBusinessKey"})
    void secondRowVersionConflictLeavesPostgresJsonbUnchanged(String pk) throws Exception {
        var harness = harness(pk, repository);
        Map<String, Object> before = harness.read();
        Long version = repository.findById("synthetic-process").orElseThrow().getLockVersion();
        assertThrows(com.portal.exception.PortalException.class, () -> harness.deleteBoth(1));
        assertEquals(before, harness.read());
        assertEquals(version, repository.findById("synthetic-process").orElseThrow().getLockVersion());
    }

    @ParameterizedTest
    @ValueSource(strings = {"entryKey", "renamedBusinessKey"})
    void validDeleteCommitsJsonbAndTombstonesWithOneOptimisticVersionIncrement(String pk) throws Exception {
        var harness = harness(pk, repository);
        Map<String, Object> before = harness.read();
        Long version = repository.findById("synthetic-process").orElseThrow().getLockVersion();
        harness.deleteBoth(2);
        Map<String, Object> saved = harness.read();
        assertEquals(List.of(), ((Map<?, ?>) saved.get("__subTables__")).get("dw:lines"));
        assertEquals(2, SubTableBatchDeletedRows.read(saved).get("dw:lines").identities().size());
        assertEquals(before.get(SubTableWriteIsolation.BATCH_RUNTIME_FIELD), saved.get(SubTableWriteIsolation.BATCH_RUNTIME_FIELD));
        assertEquals(version + 1, repository.findById("synthetic-process").orElseThrow().getLockVersion());
    }

    @Test
    void concurrentTransactionsCannotBothCommitTheSameBaseline() throws Exception {
        var adapter = mock(ProcessInstanceRepository.class, org.mockito.AdditionalAnswers.delegatesTo(repository));
        var harness = harness("renamedBusinessKey", adapter);
        var loaded = new CountDownLatch(2);
        doAnswer(invocation -> {
            var instance = repository.findById(invocation.getArgument(0));
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                loaded.countDown();
                assertTrue(loaded.await(10, TimeUnit.SECONDS), "Both real write transactions must read the same version");
            }
            return instance;
        }).when(adapter).findById("synthetic-process");
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            List<Future<Throwable>> results = workers.invokeAll(List.of(
                    () -> submit(harness), () -> submit(harness)), 20, TimeUnit.SECONDS);
            int committed = 0;
            int conflicted = 0;
            for (Future<Throwable> result : results) {
                Throwable failure = result.get();
                if (failure == null) committed++;
                else {
                    assertInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class, failure);
                    conflicted++;
                }
            }
            assertEquals(1, committed);
            assertEquals(1, conflicted);
            assertEquals(1L, repository.findById("synthetic-process").orElseThrow().getLockVersion());
            assertEquals(2, SubTableBatchDeletedRows.read(harness.read()).get("dw:lines").identities().size());
        } finally {
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private static Throwable submit(BatchTaskFormTransactionHarness harness) {
        try {
            harness.deleteBoth(2);
            return null;
        } catch (RuntimeException failure) {
            return failure; // Test collects both callers' observable outcomes; no production fallback.
        }
    }

    private static BatchTaskFormTransactionHarness harness(String pk, ProcessInstanceRepository adapter) throws Exception {
        return new BatchTaskFormTransactionHarness(pk, false, false,
                new BatchTaskFormTransactionHarness.JpaPersistence(adapter, transactions));
    }

    private static String required(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing disposable test database setting: " + key);
        return value;
    }
}
