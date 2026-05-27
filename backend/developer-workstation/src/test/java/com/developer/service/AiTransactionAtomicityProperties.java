package com.developer.service;

import com.developer.dto.AiGeneratedData;
import com.developer.entity.FunctionUnit;
import com.developer.entity.Icon;
import com.developer.enums.IconCategory;
import com.developer.exception.AiGenerationException;
import com.developer.repository.FunctionUnitRepository;
import com.developer.repository.IconRepository;
import com.developer.service.impl.AiWriteServiceImpl;
import jakarta.persistence.EntityManager;
import net.jqwik.api.*;
import net.jqwik.api.constraints.LongRange;
import org.junit.jupiter.api.Tag;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Property-based tests for AiWriteService transaction atomicity.
 *
 * <p>Tests verify that AiWriteServiceImpl propagates exceptions (not swallowing them),
 * which ensures Spring @Transactional will trigger rollback on any failure.
 * This guarantees no partial writes occur.</p>
 *
 * <p><b>Validates: Requirements 10.5, 10.8</b></p>
 */
@Tag("Feature: ai-function-unit-generation, Property 9: 事务原子性")
class AiTransactionAtomicityProperties {

    // --- Manual test doubles ---

    /**
     * A FunctionUnitRepository stub that returns empty for any findById call,
     * simulating "FunctionUnit not found".
     */
    static class EmptyFunctionUnitRepository implements FunctionUnitRepository {
        @Override public Optional<FunctionUnit> findById(Long id) { return Optional.empty(); }
        @Override public List<Long> findIdsByCreatedBy(String username) { return List.of(); }

        // --- Unused methods stubbed to satisfy interface ---
        @Override public boolean existsByName(String name) { return false; }
        @Override public boolean existsByNameAndIdNot(String name, Long id) { return false; }
        @Override public boolean existsByCode(String code) { return false; }
        @Override public Optional<FunctionUnit> findByName(String name) { return Optional.empty(); }
        @Override public Optional<FunctionUnit> findByCode(String code) { return Optional.empty(); }
        @Override public org.springframework.data.domain.Page<FunctionUnit> findByStatus(com.developer.enums.FunctionUnitStatus status, org.springframework.data.domain.Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
        @Override public org.springframework.data.domain.Page<FunctionUnit> findAllWithRelations(org.springframework.data.domain.Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
        @Override public Optional<FunctionUnit> findByIdWithRelations(Long id) { return Optional.empty(); }
        @Override public void flush() {}
        @Override public <S extends FunctionUnit> S saveAndFlush(S entity) { return entity; }
        @Override public <S extends FunctionUnit> List<S> saveAllAndFlush(Iterable<S> entities) { return List.of(); }
        @Override public void deleteAllInBatch(Iterable<FunctionUnit> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<Long> ids) {}
        @Override public void deleteAllInBatch() {}
        @Override public FunctionUnit getOne(Long id) { return null; }
        @Override public FunctionUnit getById(Long id) { return null; }
        @Override public FunctionUnit getReferenceById(Long id) { return null; }
        @Override public <S extends FunctionUnit> Optional<S> findOne(org.springframework.data.domain.Example<S> example) { return Optional.empty(); }
        @Override public <S extends FunctionUnit> List<S> findAll(org.springframework.data.domain.Example<S> example) { return List.of(); }
        @Override public <S extends FunctionUnit> List<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Sort sort) { return List.of(); }
        @Override public <S extends FunctionUnit> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
        @Override public <S extends FunctionUnit> long count(org.springframework.data.domain.Example<S> example) { return 0; }
        @Override public <S extends FunctionUnit> boolean exists(org.springframework.data.domain.Example<S> example) { return false; }
        @Override public <S extends FunctionUnit, R> R findBy(org.springframework.data.domain.Example<S> example, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
        @Override public <S extends FunctionUnit> S save(S entity) { return entity; }
        @Override public <S extends FunctionUnit> List<S> saveAll(Iterable<S> entities) { return List.of(); }
        @Override public boolean existsById(Long id) { return false; }
        @Override public List<FunctionUnit> findAll() { return List.of(); }
        @Override public List<FunctionUnit> findAllById(Iterable<Long> ids) { return List.of(); }
        @Override public long count() { return 0; }
        @Override public void deleteById(Long id) {}
        @Override public void delete(FunctionUnit entity) {}
        @Override public void deleteAllById(Iterable<? extends Long> ids) {}
        @Override public void deleteAll(Iterable<? extends FunctionUnit> entities) {}
        @Override public void deleteAll() {}
        @Override public List<FunctionUnit> findAll(org.springframework.data.domain.Sort sort) { return List.of(); }
        @Override public org.springframework.data.domain.Page<FunctionUnit> findAll(org.springframework.data.domain.Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
        @Override public Optional<FunctionUnit> findOne(org.springframework.data.jpa.domain.Specification<FunctionUnit> spec) { return Optional.empty(); }
        @Override public List<FunctionUnit> findAll(org.springframework.data.jpa.domain.Specification<FunctionUnit> spec) { return List.of(); }
        @Override public org.springframework.data.domain.Page<FunctionUnit> findAll(org.springframework.data.jpa.domain.Specification<FunctionUnit> spec, org.springframework.data.domain.Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
        @Override public List<FunctionUnit> findAll(org.springframework.data.jpa.domain.Specification<FunctionUnit> spec, org.springframework.data.domain.Sort sort) { return List.of(); }
        @Override public long count(org.springframework.data.jpa.domain.Specification<FunctionUnit> spec) { return 0; }
        @Override public boolean exists(org.springframework.data.jpa.domain.Specification<FunctionUnit> spec) { return false; }
        @Override public long delete(org.springframework.data.jpa.domain.Specification<FunctionUnit> spec) { return 0; }
        @Override public <S extends FunctionUnit, R> R findBy(org.springframework.data.jpa.domain.Specification<FunctionUnit> spec, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
    }

    /**
     * A FunctionUnitRepository stub that returns a given FunctionUnit for findById.
     */
    static class FixedFunctionUnitRepository extends EmptyFunctionUnitRepository {
        private final FunctionUnit functionUnit;

        FixedFunctionUnitRepository(FunctionUnit functionUnit) {
            this.functionUnit = functionUnit;
        }

        @Override
        public Optional<FunctionUnit> findById(Long id) {
            return Objects.equals(functionUnit.getId(), id) ? Optional.of(functionUnit) : Optional.empty();
        }

        @Override
        public <S extends FunctionUnit> S save(S entity) { return entity; }
    }

    /** Minimal IconRepository stub */
    static class StubIconRepository implements IconRepository {
        @Override public Optional<Icon> findByName(String name) { return Optional.empty(); }
        @Override public List<Icon> findByCategory(IconCategory category) { return List.of(); }
        @Override public org.springframework.data.domain.Page<Icon> findByCategory(IconCategory category, org.springframework.data.domain.Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
        @Override public boolean existsByName(String name) { return false; }
        @Override public void flush() {}
        @Override public <S extends Icon> S saveAndFlush(S entity) { return entity; }
        @Override public <S extends Icon> List<S> saveAllAndFlush(Iterable<S> entities) { return List.of(); }
        @Override public void deleteAllInBatch(Iterable<Icon> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<Long> ids) {}
        @Override public void deleteAllInBatch() {}
        @Override public Icon getOne(Long id) { return null; }
        @Override public Icon getById(Long id) { return null; }
        @Override public Icon getReferenceById(Long id) { return null; }
        @Override public <S extends Icon> Optional<S> findOne(org.springframework.data.domain.Example<S> example) { return Optional.empty(); }
        @Override public <S extends Icon> List<S> findAll(org.springframework.data.domain.Example<S> example) { return List.of(); }
        @Override public <S extends Icon> List<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Sort sort) { return List.of(); }
        @Override public <S extends Icon> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
        @Override public <S extends Icon> long count(org.springframework.data.domain.Example<S> example) { return 0; }
        @Override public <S extends Icon> boolean exists(org.springframework.data.domain.Example<S> example) { return false; }
        @Override public <S extends Icon, R> R findBy(org.springframework.data.domain.Example<S> example, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
        @Override public <S extends Icon> S save(S entity) { return entity; }
        @Override public <S extends Icon> List<S> saveAll(Iterable<S> entities) { return List.of(); }
        @Override public Optional<Icon> findById(Long id) { return Optional.empty(); }
        @Override public boolean existsById(Long id) { return false; }
        @Override public List<Icon> findAll() { return List.of(); }
        @Override public List<Icon> findAllById(Iterable<Long> ids) { return List.of(); }
        @Override public long count() { return 0; }
        @Override public void deleteById(Long id) {}
        @Override public void delete(Icon entity) {}
        @Override public void deleteAllById(Iterable<? extends Long> ids) {}
        @Override public void deleteAll(Iterable<? extends Icon> entities) {}
        @Override public void deleteAll() {}
        @Override public List<Icon> findAll(org.springframework.data.domain.Sort sort) { return List.of(); }
        @Override public org.springframework.data.domain.Page<Icon> findAll(org.springframework.data.domain.Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
        @Override public Optional<Icon> findOne(org.springframework.data.jpa.domain.Specification<Icon> spec) { return Optional.empty(); }
        @Override public List<Icon> findAll(org.springframework.data.jpa.domain.Specification<Icon> spec) { return List.of(); }
        @Override public org.springframework.data.domain.Page<Icon> findAll(org.springframework.data.jpa.domain.Specification<Icon> spec, org.springframework.data.domain.Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
        @Override public List<Icon> findAll(org.springframework.data.jpa.domain.Specification<Icon> spec, org.springframework.data.domain.Sort sort) { return List.of(); }
        @Override public long count(org.springframework.data.jpa.domain.Specification<Icon> spec) { return 0; }
        @Override public boolean exists(org.springframework.data.jpa.domain.Specification<Icon> spec) { return false; }
        @Override public long delete(org.springframework.data.jpa.domain.Specification<Icon> spec) { return 0; }
        @Override public <S extends Icon, R> R findBy(org.springframework.data.jpa.domain.Specification<Icon> spec, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
    }

    /** No-op EntityManager stub for unit tests */
    static class NoOpEntityManager implements EntityManager {
        @Override public void persist(Object entity) {}
        @Override public <T> T merge(T entity) { return entity; }
        @Override public void remove(Object entity) {}
        @Override public <T> T find(Class<T> entityClass, Object primaryKey) { return null; }
        @Override public <T> T find(Class<T> entityClass, Object primaryKey, java.util.Map<String, Object> properties) { return null; }
        @Override public <T> T find(Class<T> entityClass, Object primaryKey, jakarta.persistence.LockModeType lockMode) { return null; }
        @Override public <T> T find(Class<T> entityClass, Object primaryKey, jakarta.persistence.LockModeType lockMode, java.util.Map<String, Object> properties) { return null; }
        @Override public <T> T getReference(Class<T> entityClass, Object primaryKey) { return null; }
        @Override public void flush() {}
        @Override public void setFlushMode(jakarta.persistence.FlushModeType flushMode) {}
        @Override public jakarta.persistence.FlushModeType getFlushMode() { return jakarta.persistence.FlushModeType.AUTO; }
        @Override public void lock(Object entity, jakarta.persistence.LockModeType lockMode) {}
        @Override public void lock(Object entity, jakarta.persistence.LockModeType lockMode, java.util.Map<String, Object> properties) {}
        @Override public void refresh(Object entity) {}
        @Override public void refresh(Object entity, java.util.Map<String, Object> properties) {}
        @Override public void refresh(Object entity, jakarta.persistence.LockModeType lockMode) {}
        @Override public void refresh(Object entity, jakarta.persistence.LockModeType lockMode, java.util.Map<String, Object> properties) {}
        @Override public void clear() {}
        @Override public void detach(Object entity) {}
        @Override public boolean contains(Object entity) { return false; }
        @Override public jakarta.persistence.LockModeType getLockMode(Object entity) { return null; }
        @Override public void setProperty(String propertyName, Object value) {}
        @Override public java.util.Map<String, Object> getProperties() { return Map.of(); }
        @Override public jakarta.persistence.Query createQuery(String qlString) { return null; }
        @Override public <T> jakarta.persistence.TypedQuery<T> createQuery(jakarta.persistence.criteria.CriteriaQuery<T> criteriaQuery) { return null; }
        @Override public jakarta.persistence.Query createQuery(jakarta.persistence.criteria.CriteriaUpdate updateQuery) { return null; }
        @Override public jakarta.persistence.Query createQuery(jakarta.persistence.criteria.CriteriaDelete deleteQuery) { return null; }
        @Override public <T> jakarta.persistence.TypedQuery<T> createQuery(String qlString, Class<T> resultClass) { return null; }
        @Override public jakarta.persistence.Query createNamedQuery(String name) { return null; }
        @Override public <T> jakarta.persistence.TypedQuery<T> createNamedQuery(String name, Class<T> resultClass) { return null; }
        @Override public jakarta.persistence.Query createNativeQuery(String sqlString) { return null; }
        @Override public jakarta.persistence.Query createNativeQuery(String sqlString, Class resultClass) { return null; }
        @Override public jakarta.persistence.Query createNativeQuery(String sqlString, String resultSetMapping) { return null; }
        @Override public jakarta.persistence.StoredProcedureQuery createNamedStoredProcedureQuery(String name) { return null; }
        @Override public jakarta.persistence.StoredProcedureQuery createStoredProcedureQuery(String procedureName) { return null; }
        @Override public jakarta.persistence.StoredProcedureQuery createStoredProcedureQuery(String procedureName, Class... resultClasses) { return null; }
        @Override public jakarta.persistence.StoredProcedureQuery createStoredProcedureQuery(String procedureName, String... resultSetMappings) { return null; }
        @Override public void joinTransaction() {}
        @Override public boolean isJoinedToTransaction() { return false; }
        @Override public <T> T unwrap(Class<T> cls) { return null; }
        @Override public Object getDelegate() { return null; }
        @Override public void close() {}
        @Override public boolean isOpen() { return true; }
        @Override public jakarta.persistence.EntityTransaction getTransaction() { return null; }
        @Override public jakarta.persistence.EntityManagerFactory getEntityManagerFactory() { return null; }
        @Override public jakarta.persistence.criteria.CriteriaBuilder getCriteriaBuilder() { return null; }
        @Override public jakarta.persistence.metamodel.Metamodel getMetamodel() { return null; }
        @Override public <T> jakarta.persistence.EntityGraph<T> createEntityGraph(Class<T> rootType) { return null; }
        @Override public jakarta.persistence.EntityGraph<?> createEntityGraph(String graphName) { return null; }
        @Override public jakarta.persistence.EntityGraph<?> getEntityGraph(String graphName) { return null; }
        @Override public <T> List<jakarta.persistence.EntityGraph<? super T>> getEntityGraphs(Class<T> entityClass) { return List.of(); }
    }

    // --- Property Tests ---

    /**
     * Property 9a: applyGeneratedData throws AiGenerationException when FunctionUnit not found.
     *
     * <p>For any functionUnitId that doesn't exist in the repository, applyGeneratedData
     * should throw AiGenerationException, ensuring no partial writes occur.</p>
     *
     * <p><b>Validates: Requirements 10.5, 10.8</b></p>
     */
    @Property(tries = 100)
    void applyGeneratedDataThrowsWhenFunctionUnitNotFound(
            @ForAll @LongRange(min = 1, max = 100000) Long functionUnitId,
            @ForAll("anyGeneratedData") AiGeneratedData generatedData) {

        AiWriteServiceImpl writeService = new AiWriteServiceImpl(
                new EmptyFunctionUnitRepository(),
                new StubIconRepository(),
                new NoOpEntityManager()
        );

        assertThatThrownBy(() -> writeService.applyGeneratedData(functionUnitId, generatedData, null))
                .isInstanceOf(AiGenerationException.class)
                .hasMessageContaining(String.valueOf(functionUnitId));
    }

    /**
     * Property 9b: Invalid enum values in generated data are gracefully skipped.
     *
     * <p>For any AiGeneratedData containing invalid enum strings (e.g., invalid TableType),
     * the write should skip the invalid entry and complete without exception.</p>
     *
     * <p><b>Validates: Requirements 10.5, 10.8</b></p>
     */
    @Property(tries = 100)
    void invalidEnumValuesInGeneratedDataAreGracefullySkipped(
            @ForAll @LongRange(min = 1, max = 1000) Long functionUnitId,
            @ForAll("invalidEnumString") String invalidEnum) {

        FunctionUnit fu = FunctionUnit.builder()
                .id(functionUnitId)
                .code("fu-test")
                .name("Test FU")
                .tableDefinitions(new ArrayList<>())
                .formDefinitions(new ArrayList<>())
                .actionDefinitions(new ArrayList<>())
                .decisionDefinitions(new ArrayList<>())
                .tableRelations(new ArrayList<>())
                .versions(new ArrayList<>())
                .build();

        AiWriteServiceImpl writeService = new AiWriteServiceImpl(
                new FixedFunctionUnitRepository(fu),
                new StubIconRepository(),
                new NoOpEntityManager()
        );

        // Invalid tableType should be skipped (logged as WARN), not throw
        AiGeneratedData dataWithInvalidTableType = AiGeneratedData.builder()
                .tableDefinitions(List.of(Map.of(
                        "tableName", "bad_table",
                        "tableType", invalidEnum,
                        "fieldDefinitions", List.of(Map.of(
                                "fieldName", "id",
                                "dataType", "INTEGER",
                                "isPrimaryKey", true,
                                "sortOrder", 1
                        ))
                )))
                .build();

        // Should complete without exception — invalid table type is skipped
        writeService.applyGeneratedData(functionUnitId, dataWithInvalidTableType, null);

        // The invalid table should have been skipped
        assertThat(fu.getTableDefinitions()).isEmpty();
    }

    /**
     * Property 9c: Null/empty generated data collections don't corrupt existing FunctionUnit.
     *
     * <p>For any FunctionUnit, applying generated data with all null collections
     * should not throw and should not corrupt the entity.</p>
     *
     * <p><b>Validates: Requirements 10.5, 10.8</b></p>
     */
    @Property(tries = 100)
    void nullGeneratedDataCollectionsDoNotCorruptFunctionUnit(
            @ForAll @LongRange(min = 1, max = 1000) Long functionUnitId,
            @ForAll("optionalName") String name,
            @ForAll("optionalDescription") String description) {

        FunctionUnit fu = FunctionUnit.builder()
                .id(functionUnitId)
                .code("fu-test-" + functionUnitId)
                .name("Original Name")
                .description("Original Description")
                .tableDefinitions(new ArrayList<>())
                .formDefinitions(new ArrayList<>())
                .actionDefinitions(new ArrayList<>())
                .versions(new ArrayList<>())
                .build();

        AiWriteServiceImpl writeService = new AiWriteServiceImpl(
                new FixedFunctionUnitRepository(fu),
                new StubIconRepository(),
                new NoOpEntityManager()
        );

        // All collections null — should not throw
        AiGeneratedData emptyData = AiGeneratedData.builder()
                .tableDefinitions(null)
                .formDefinitions(null)
                .actionDefinitions(null)
                .processDefinition(null)
                .icon(null)
                .name(name)
                .description(description)
                .build();

        // Should complete without exception
        writeService.applyGeneratedData(functionUnitId, emptyData, null);

        // FunctionUnit should still be valid (not corrupted)
        assertThat(fu.getId()).isEqualTo(functionUnitId);
        assertThat(fu.getTableDefinitions()).isNotNull();
        assertThat(fu.getFormDefinitions()).isNotNull();
        assertThat(fu.getActionDefinitions()).isNotNull();

        // Name/description updated only if non-blank
        if (name != null && !name.isBlank()) {
            assertThat(fu.getName()).isEqualTo(name);
        }
        if (description != null && !description.isBlank()) {
            assertThat(fu.getDescription()).isEqualTo(description);
        }
    }

    // --- Arbitrary Providers ---

    @Provide
    Arbitrary<AiGeneratedData> anyGeneratedData() {
        return Arbitraries.of(
                AiGeneratedData.builder().build(),
                AiGeneratedData.builder()
                        .tableDefinitions(List.of())
                        .formDefinitions(List.of())
                        .actionDefinitions(List.of())
                        .build(),
                AiGeneratedData.builder()
                        .name("test")
                        .description("test desc")
                        .build()
        );
    }

    @Provide
    Arbitrary<String> invalidEnumString() {
        // Generate strings that are NOT valid TableType enum values
        java.util.Set<String> validTableTypes = java.util.Set.of("MAIN", "SUB", "ACTION", "RELATION");
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20)
                .filter(s -> !validTableTypes.contains(s));
    }

    @Provide
    Arbitrary<String> optionalName() {
        return Arbitraries.oneOf(
                Arbitraries.just(null),
                Arbitraries.just(""),
                Arbitraries.just("   "),
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(50)
        );
    }

    @Provide
    Arbitrary<String> optionalDescription() {
        return Arbitraries.oneOf(
                Arbitraries.just(null),
                Arbitraries.just(""),
                Arbitraries.just("   "),
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(200)
        );
    }
}
