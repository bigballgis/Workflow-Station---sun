package com.portal.component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.client.WorkflowEngineClient;
import com.portal.dto.SubTableBatchChange;
import com.portal.dto.SubTableBindingScope;
import com.portal.entity.ProcessInstance;
import com.portal.repository.ProcessInstanceRepository;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real JDBC transaction; only the repository adapter and unrelated metadata/engine I/O are stubbed. */
final class BatchTaskFormTransactionHarness {
    final ObjectMapper mapper = new ObjectMapper();
    final JdbcTemplate persistence;
    final AtomicInteger writes = new AtomicInteger();
    final TaskFormComponent component;
    final String pk;
    final Map<String, Object> baseline;
    private final JpaPersistence jpa;

    record JpaPersistence(ProcessInstanceRepository repository,
                          org.springframework.transaction.PlatformTransactionManager transactionManager) { }

    BatchTaskFormTransactionHarness(String pk, boolean failAfterWrite) throws Exception {
        this(pk, failAfterWrite, false);
    }

    BatchTaskFormTransactionHarness(String pk, boolean failAfterWrite, boolean readonlyCaption) throws Exception {
        this(pk, failAfterWrite, readonlyCaption, null);
    }

    BatchTaskFormTransactionHarness(String pk, boolean failAfterWrite, boolean readonlyCaption, JpaPersistence jpa) throws Exception {
        this.pk = pk;
        this.jpa = jpa;
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:batch_tx_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        persistence = new JdbcTemplate(source);
        baseline = variables();
        ProcessInstanceRepository repository;
        if (jpa == null) {
            persistence.execute("CREATE TABLE batch_tx_instance (id VARCHAR(64) PRIMARY KEY, variables CLOB)");
            persistence.update("INSERT INTO batch_tx_instance VALUES (?, ?)", "synthetic-process", mapper.writeValueAsString(baseline));
            repository = repository(failAfterWrite);
        } else {
            repository = jpa.repository();
            repository.saveAndFlush(ProcessInstance.builder().id("synthetic-process").processDefinitionKey("synthetic-key")
                    .startUserId("synthetic-user").status("RUNNING").functionUnitCode("synthetic-unit")
                    .variables(mapper.convertValue(baseline, new TypeReference<Map<String, Object>>() { })).build());
        }
        WorkflowEngineClient engine = mock(WorkflowEngineClient.class);
        when(engine.isAvailable()).thenReturn(true);
        when(engine.getTaskById("synthetic-task")).thenReturn(Optional.of(Map.of(
                "taskDefinitionKey", "review", "processInstanceId", "synthetic-process")));
        JdbcTemplate metadata = mock(JdbcTemplate.class);
        component = new TaskFormComponent(mock(ProcessFormComponent.class), mock(ChangeHistoryComponent.class),
                repository, engine, mock(RestTemplate.class), mapper, metadata,
                jpa == null ? new DataSourceTransactionManager(source) : jpa.transactionManager());
        TaskFormDefinitionLoader loader = mock(TaskFormDefinitionLoader.class);
        when(loader.fetchTaskFormByStageId(eq("review"), eq("synthetic-process"), any())).thenReturn(
                Map.of("sourceId", "form-one", "fieldPermissions", Map.of("101:caption", "EDITABLE"), "configJson", Map.of(),
                        "publishedFieldPermissions", Map.of("101:caption", readonlyCaption ? "READONLY" : "EDITABLE"),
                        "tableBindings", List.of(Map.of("bindingId", "101", "tableName", "lines", "bindingMode", "EDITABLE"))));
        ReflectionTestUtils.setField(component, "formDefinitionLoader", loader);
        ReflectionTestUtils.setField(component, "requestIdEnricher", mock(RequestIdEnricher.class));
        SubTableWriteDesign design = mock(SubTableWriteDesign.class);
        when(design.resolve(any(), any())).thenReturn(Map.of());
        ReflectionTestUtils.setField(component, "subTableWriteIsolation", new SubTableWriteIsolation(
                new MiSubTaskSubTableRowMerger(metadata), new SubTableBindingScopeGuard(metadata), design));
    }

    private ProcessInstanceRepository repository(boolean failAfterWrite) {
        ProcessInstanceRepository repository = mock(ProcessInstanceRepository.class);
        when(repository.findById("synthetic-process")).thenAnswer(invocation -> Optional.of(ProcessInstance.builder()
                .id("synthetic-process").functionUnitCode("synthetic-unit").variables(read()).build()));
        when(repository.save(any(ProcessInstance.class))).thenAnswer(invocation -> {
            ProcessInstance instance = invocation.getArgument(0);
            persistence.update("UPDATE batch_tx_instance SET variables=? WHERE id=?",
                    mapper.writeValueAsString(instance.getVariables()), instance.getId());
            writes.incrementAndGet();
            if (failAfterWrite) throw new DataIntegrityViolationException("Synthetic failure after JDBC write");
            return instance;
        });
        return repository;
    }

    Map<String, Object> read() throws Exception {
        if (jpa != null) return jpa.repository().findById("synthetic-process").orElseThrow().getVariables();
        return mapper.readValue(persistence.queryForObject("SELECT variables FROM batch_tx_instance WHERE id=?",
                String.class, "synthetic-process"), new TypeReference<Map<String, Object>>() { });
    }

    void deleteBoth(int secondVersion) {
        SubTableBatchChange change = new SubTableBatchChange();
        change.setBindingId("101"); change.setBatchControlId("grid"); change.setOperation("DELETE");
        change.setRowPatches(List.of(patch("one"), patch("two")));
        SubTableBindingScope scope = new SubTableBindingScope("101", "dw:lines", List.of(), true,
                List.of(Map.of(pk, "one", "_wsRowVersion", 2), Map.of(pk, "two", "_wsRowVersion", secondVersion)));
        Map<String, Object> payload = new HashMap<>(Map.of("caseKey", "parent-one", "__subTables__",
                new HashMap<>(Map.of("dw:lines", List.of()))));
        component.submitTaskForm("synthetic-task", "synthetic-user", payload, null, List.of("dw:lines"),
                List.of(scope), List.of(change));
    }

    void editUnmarkedCaption() throws Exception {
        Map<String, Object> payload = read();
        Map<String, Object> tables = mapper.convertValue(payload.get("__subTables__"), new TypeReference<Map<String, Object>>() { });
        List<Map<String, Object>> rows = mapper.convertValue(tables.get("dw:lines"), new TypeReference<List<Map<String, Object>>>() { });
        rows.get(0).put("caption", "MUST-NOT-WRITE");
        tables.put("dw:lines", rows);
        component.submitTaskForm("synthetic-task", "synthetic-user", Map.of("caseKey", "parent-one", "__subTables__", tables));
    }

    private SubTableBatchChange.RowPatch patch(String value) {
        SubTableBatchChange.RowPatch patch = new SubTableBatchChange.RowPatch();
        patch.setRowId(value); patch.setRowKey(Map.of(pk, value)); patch.setFields(List.of());
        return patch;
    }

    private Map<String, Object> variables() {
        Map<String, Object> binding = Map.of("tableName", "lines", "bindingType", "SUB", "bindingMode", "EDITABLE",
                "bindingLinkMode", "structuralFk", "foreignKeyField", "parentRef", "primaryKeyFields", List.of(pk),
                "fieldDefinitions", List.of(Map.of("fieldName", "parentRef", "isForeignKey", true,
                        "refTableId", 10, "refPrimaryKeyFields", List.of("caseKey"))));
        Map<String, Object> design = Map.of("binding", binding, "dependencies", List.of(), "fields", List.of(
                Map.of("fieldName", pk, "isPrimaryKey", true), Map.of("fieldName", "parentRef", "isForeignKey", true),
                Map.of("fieldName", "caption", "dataType", "TEXT")), "parentTablesById",
                Map.of("10", Map.of("tableName", "cases", "tableType", "MAIN")));
        Map<String, Object> control = Map.of("batchControlId", "grid", "bindingId", "101", "rowDesign", design,
                "capabilities", Map.of("allowBatchDelete", true), "originalCrud", Map.of("allowDelete", true));
        return Map.of("caseKey", "parent-one", "__subTables__", Map.of("dw:lines", List.of(
                Map.of(pk, "one", "parentRef", "parent-one", "caption", "first-original", "_wsRowVersion", 2),
                Map.of(pk, "two", "parentRef", "parent-one", "caption", "second-original", "_wsRowVersion", 2))),
                SubTableWriteIsolation.BATCH_RUNTIME_FIELD,
                Map.of("schemaVersion", 1, "forms", Map.of("form-one", Map.of("controls", List.of(control)))));
    }
}
