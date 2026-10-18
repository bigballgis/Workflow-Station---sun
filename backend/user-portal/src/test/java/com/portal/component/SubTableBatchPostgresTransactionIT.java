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

    @BeforeAll
    static void connectToDisposableDatabase() {
        String url = required("BATCH_TEST_JDBC_URL");
        if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/batch_verification")) {
            throw new IllegalArgumentException("Only the loopback disposable batch_verification database is allowed");
        }
        var source = new DriverManagerDataSource(url, required("BATCH_TEST_DB_USER"), required("BATCH_TEST_DB_PASSWORD"));
        factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(source);
        factory.setManagedTypes(PersistenceManagedTypes.of(ProcessInstance.class.getName()));
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
    }

    @AfterAll
    static void closeDatabase() {
        if (factory != null) factory.destroy();
    }

    @BeforeEach
    void clearSyntheticInstances() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> repository.deleteAllInBatch());
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
