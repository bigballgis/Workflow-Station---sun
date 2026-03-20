package com.developer.service;

import com.developer.dto.AiGeneratedData;
import com.developer.entity.*;
import com.developer.enums.*;
import com.developer.repository.FunctionUnitRepository;
import com.developer.repository.IconRepository;
import com.developer.service.impl.AiWriteServiceImpl;
import jakarta.persistence.EntityManager;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;
import org.junit.jupiter.api.Tag;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for AiWriteService full replacement write correctness.
 *
 * <p>Tests verify that after calling applyGeneratedData:
 * - MODIFY mode clears old data and writes only new data (no residual old data)
 * - NEW mode writes data correctly
 * - ForeignKey entity references are correctly resolved
 * - FormDefinition boundTable backward compat is set correctly</p>
 *
 * <p><b>Validates: Requirements 10.3, 10.4</b></p>
 */
@Tag("Feature: ai-function-unit-generation, Property 10: 全量替换写入正确性")
class AiFullReplacementWriteProperties {

    // --- Reuse test doubles from AiTransactionAtomicityProperties ---

    static class FixedFunctionUnitRepository extends AiTransactionAtomicityProperties.EmptyFunctionUnitRepository {
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

    // --- Property Tests ---

    /**
     * Property 10a: MODIFY mode clears old data and writes new data.
     *
     * <p>For any FunctionUnit with pre-existing tableDefinitions/formDefinitions/actionDefinitions/processDefinition,
     * after applyGeneratedData, the FunctionUnit should contain ONLY the new data from AiGeneratedData.</p>
     *
     * <p><b>Validates: Requirements 10.3, 10.4</b></p>
     */
    @Property(tries = 50)
    void modifyModeClearsOldDataAndWritesNewData(
            @ForAll @LongRange(min = 1, max = 1000) Long functionUnitId,
            @ForAll("validGeneratedDataWithTables") AiGeneratedData newData) {

        // Create FunctionUnit with pre-existing data (triggers MODIFY mode)
        FunctionUnit fu = buildFunctionUnitWithExistingData(functionUnitId);

        AiWriteServiceImpl writeService = new AiWriteServiceImpl(
                new FixedFunctionUnitRepository(fu),
                new AiTransactionAtomicityProperties.StubIconRepository(),
                new AiTransactionAtomicityProperties.NoOpEntityManager()
        );

        writeService.applyGeneratedData(functionUnitId, newData);

        // Verify old data is gone and new data is present
        int expectedTableCount = newData.getTableDefinitions() != null ? newData.getTableDefinitions().size() : 0;
        int expectedFormCount = newData.getFormDefinitions() != null ? newData.getFormDefinitions().size() : 0;
        int expectedActionCount = newData.getActionDefinitions() != null ? newData.getActionDefinitions().size() : 0;

        assertThat(fu.getTableDefinitions()).hasSize(expectedTableCount);
        assertThat(fu.getFormDefinitions()).hasSize(expectedFormCount);
        assertThat(fu.getActionDefinitions()).hasSize(expectedActionCount);

        // Verify table names match new data
        if (newData.getTableDefinitions() != null) {
            Set<String> expectedTableNames = new HashSet<>();
            for (Map<String, Object> td : newData.getTableDefinitions()) {
                expectedTableNames.add((String) td.get("tableName"));
            }
            Set<String> actualTableNames = new HashSet<>();
            for (TableDefinition td : fu.getTableDefinitions()) {
                actualTableNames.add(td.getTableName());
            }
            assertThat(actualTableNames).isEqualTo(expectedTableNames);
        }

        // Verify no old table names remain
        assertThat(fu.getTableDefinitions().stream()
                .map(TableDefinition::getTableName)
                .filter(n -> n.startsWith("old_")))
                .isEmpty();

        // Verify no old form names remain
        assertThat(fu.getFormDefinitions().stream()
                .map(FormDefinition::getFormName)
                .filter(n -> n.startsWith("old_")))
                .isEmpty();

        // Verify no old action names remain
        assertThat(fu.getActionDefinitions().stream()
                .map(ActionDefinition::getActionName)
                .filter(n -> n.startsWith("old_")))
                .isEmpty();

        // Verify processDefinition matches new data
        if (newData.getProcessDefinition() != null && newData.getProcessDefinition().get("bpmnXml") != null
                && !((String) newData.getProcessDefinition().get("bpmnXml")).isBlank()) {
            assertThat(fu.getProcessDefinition()).isNotNull();
            assertThat(fu.getProcessDefinition().getBpmnXml())
                    .isEqualTo(newData.getProcessDefinition().get("bpmnXml"));
        }
    }

    /**
     * Property 10b: NEW mode writes data without clearing.
     *
     * <p>For any FunctionUnit with no existing data, applyGeneratedData should write the new data correctly.</p>
     *
     * <p><b>Validates: Requirements 10.3, 10.4</b></p>
     */
    @Property(tries = 50)
    void newModeWritesDataCorrectly(
            @ForAll @LongRange(min = 1, max = 1000) Long functionUnitId,
            @ForAll("validGeneratedDataWithTables") AiGeneratedData newData) {

        // Create empty FunctionUnit (triggers NEW mode)
        FunctionUnit fu = FunctionUnit.builder()
                .id(functionUnitId)
                .code("fu-new-" + functionUnitId)
                .name("New FU")
                .tableDefinitions(new ArrayList<>())
                .formDefinitions(new ArrayList<>())
                .actionDefinitions(new ArrayList<>())
                .versions(new ArrayList<>())
                .build();

        AiWriteServiceImpl writeService = new AiWriteServiceImpl(
                new FixedFunctionUnitRepository(fu),
                new AiTransactionAtomicityProperties.StubIconRepository(),
                new AiTransactionAtomicityProperties.NoOpEntityManager()
        );

        writeService.applyGeneratedData(functionUnitId, newData);

        int expectedTableCount = newData.getTableDefinitions() != null ? newData.getTableDefinitions().size() : 0;
        int expectedFormCount = newData.getFormDefinitions() != null ? newData.getFormDefinitions().size() : 0;
        int expectedActionCount = newData.getActionDefinitions() != null ? newData.getActionDefinitions().size() : 0;

        assertThat(fu.getTableDefinitions()).hasSize(expectedTableCount);
        assertThat(fu.getFormDefinitions()).hasSize(expectedFormCount);
        assertThat(fu.getActionDefinitions()).hasSize(expectedActionCount);

        // Verify each table has correct fields
        if (newData.getTableDefinitions() != null) {
            for (int i = 0; i < newData.getTableDefinitions().size(); i++) {
                Map<String, Object> expectedTable = newData.getTableDefinitions().get(i);
                TableDefinition actualTable = fu.getTableDefinitions().stream()
                        .filter(t -> t.getTableName().equals(expectedTable.get("tableName")))
                        .findFirst().orElse(null);
                assertThat(actualTable).isNotNull();
                assertThat(actualTable.getFunctionUnit()).isSameAs(fu);

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> expectedFields = (List<Map<String, Object>>) expectedTable.get("fieldDefinitions");
                if (expectedFields != null) {
                    assertThat(actualTable.getFieldDefinitions()).hasSize(expectedFields.size());
                }
            }
        }
    }

    /**
     * Property 10c: ForeignKey entity references are correctly resolved.
     *
     * <p>For any AiGeneratedData with foreignKeys referencing tables/fields by name,
     * after write, the ForeignKey entities should have correct entity references (not null).</p>
     *
     * <p><b>Validates: Requirements 10.3, 10.4</b></p>
     */
    @Property(tries = 50)
    void foreignKeyEntityReferencesAreCorrectlyResolved(
            @ForAll @LongRange(min = 1, max = 1000) Long functionUnitId) {

        FunctionUnit fu = FunctionUnit.builder()
                .id(functionUnitId)
                .code("fu-fk-" + functionUnitId)
                .name("FK Test FU")
                .tableDefinitions(new ArrayList<>())
                .formDefinitions(new ArrayList<>())
                .actionDefinitions(new ArrayList<>())
                .versions(new ArrayList<>())
                .build();

        // Build generated data with a main table and a sub table with a foreign key
        AiGeneratedData data = buildDataWithForeignKeys();

        AiWriteServiceImpl writeService = new AiWriteServiceImpl(
                new FixedFunctionUnitRepository(fu),
                new AiTransactionAtomicityProperties.StubIconRepository(),
                new AiTransactionAtomicityProperties.NoOpEntityManager()
        );

        writeService.applyGeneratedData(functionUnitId, data);

        // Find the sub table and verify its foreign key references
        TableDefinition subTable = fu.getTableDefinitions().stream()
                .filter(t -> "order_items".equals(t.getTableName()))
                .findFirst().orElse(null);
        assertThat(subTable).isNotNull();
        assertThat(subTable.getForeignKeys()).hasSize(1);

        ForeignKey fk = subTable.getForeignKeys().get(0);
        assertThat(fk.getTableDefinition()).isSameAs(subTable);
        assertThat(fk.getFieldDefinition()).isNotNull();
        assertThat(fk.getFieldDefinition().getFieldName()).isEqualTo("order_id");
        assertThat(fk.getRefTableDefinition()).isNotNull();
        assertThat(fk.getRefTableDefinition().getTableName()).isEqualTo("orders");
        assertThat(fk.getRefFieldDefinition()).isNotNull();
        assertThat(fk.getRefFieldDefinition().getFieldName()).isEqualTo("id");
    }

    /**
     * Property 10d: FormDefinition boundTable backward compat.
     *
     * <p>For any FormDefinition with a PRIMARY binding, the boundTable field should be set
     * to the PRIMARY binding's TableDefinition.</p>
     *
     * <p><b>Validates: Requirements 10.3, 10.4</b></p>
     */
    @Property(tries = 50)
    void formDefinitionBoundTableBackwardCompat(
            @ForAll @LongRange(min = 1, max = 1000) Long functionUnitId,
            @ForAll("validTableName") String tableName) {

        FunctionUnit fu = FunctionUnit.builder()
                .id(functionUnitId)
                .code("fu-bt-" + functionUnitId)
                .name("BoundTable Test FU")
                .tableDefinitions(new ArrayList<>())
                .formDefinitions(new ArrayList<>())
                .actionDefinitions(new ArrayList<>())
                .versions(new ArrayList<>())
                .build();

        // Build data with a table and a form with PRIMARY binding to that table
        AiGeneratedData data = AiGeneratedData.builder()
                .tableDefinitions(List.of(Map.of(
                        "tableName", tableName,
                        "tableType", "MAIN",
                        "fieldDefinitions", List.of(Map.of(
                                "fieldName", "id",
                                "dataType", "INTEGER",
                                "isPrimaryKey", true,
                                "sortOrder", 1
                        ))
                )))
                .formDefinitions(List.of(Map.of(
                        "formName", "main_form",
                        "formType", "MAIN",
                        "configJson", Map.of("layout", "default"),
                        "tableBindings", List.of(Map.of(
                                "tableName", tableName,
                                "bindingType", "PRIMARY",
                                "bindingMode", "EDITABLE",
                                "sortOrder", 1
                        ))
                )))
                .build();

        AiWriteServiceImpl writeService = new AiWriteServiceImpl(
                new FixedFunctionUnitRepository(fu),
                new AiTransactionAtomicityProperties.StubIconRepository(),
                new AiTransactionAtomicityProperties.NoOpEntityManager()
        );

        writeService.applyGeneratedData(functionUnitId, data);

        assertThat(fu.getFormDefinitions()).hasSize(1);
        FormDefinition form = fu.getFormDefinitions().get(0);
        assertThat(form.getBoundTable()).isNotNull();
        assertThat(form.getBoundTable().getTableName()).isEqualTo(tableName);
    }

    // --- Helper Methods ---

    private FunctionUnit buildFunctionUnitWithExistingData(Long id) {
        FunctionUnit fu = FunctionUnit.builder()
                .id(id)
                .code("fu-existing-" + id)
                .name("Existing FU")
                .tableDefinitions(new ArrayList<>())
                .formDefinitions(new ArrayList<>())
                .actionDefinitions(new ArrayList<>())
                .versions(new ArrayList<>())
                .build();

        // Add old table
        TableDefinition oldTable = TableDefinition.builder()
                .functionUnit(fu)
                .tableName("old_table")
                .tableType(TableType.MAIN)
                .fieldDefinitions(new ArrayList<>())
                .foreignKeys(new ArrayList<>())
                .build();
        FieldDefinition oldField = FieldDefinition.builder()
                .tableDefinition(oldTable)
                .fieldName("old_field")
                .dataType(DataType.VARCHAR)
                .length(100)
                .isPrimaryKey(true)
                .sortOrder(1)
                .build();
        oldTable.getFieldDefinitions().add(oldField);
        fu.getTableDefinitions().add(oldTable);

        // Add old form
        FormDefinition oldForm = FormDefinition.builder()
                .functionUnit(fu)
                .formName("old_form")
                .formType(FormType.MAIN)
                .configJson(Map.of("layout", "old"))
                .tableBindings(new ArrayList<>())
                .build();
        fu.getFormDefinitions().add(oldForm);

        // Add old action
        ActionDefinition oldAction = ActionDefinition.builder()
                .functionUnit(fu)
                .actionName("old_action")
                .actionType(ActionType.APPROVE)
                .configJson(Map.of("key", "old"))
                .build();
        fu.getActionDefinitions().add(oldAction);

        // Add old process definition
        ProcessDefinition oldProcess = ProcessDefinition.builder()
                .functionUnit(fu)
                .functionUnitVersionId(id)
                .bpmnXml("<old-bpmn/>")
                .build();
        fu.setProcessDefinition(oldProcess);

        return fu;
    }

    private AiGeneratedData buildDataWithForeignKeys() {
        return AiGeneratedData.builder()
                .tableDefinitions(List.of(
                        Map.of(
                                "tableName", "orders",
                                "tableType", "MAIN",
                                "fieldDefinitions", List.of(
                                        Map.of("fieldName", "id", "dataType", "INTEGER",
                                                "isPrimaryKey", true, "sortOrder", 1)
                                )
                        ),
                        Map.of(
                                "tableName", "order_items",
                                "tableType", "SUB",
                                "fieldDefinitions", List.of(
                                        Map.of("fieldName", "id", "dataType", "INTEGER",
                                                "isPrimaryKey", true, "sortOrder", 1),
                                        Map.of("fieldName", "order_id", "dataType", "INTEGER",
                                                "isPrimaryKey", false, "sortOrder", 2)
                                ),
                                "foreignKeys", List.of(
                                        Map.of("fieldName", "order_id",
                                                "refTableName", "orders",
                                                "refFieldName", "id")
                                )
                        )
                ))
                .build();
    }

    // --- Arbitrary Providers ---

    @Provide
    Arbitrary<AiGeneratedData> validGeneratedDataWithTables() {
        Arbitrary<String> tableNames = Arbitraries.of("users", "products", "orders", "invoices", "tasks", "reports");
        Arbitrary<String> tableTypes = Arbitraries.of("MAIN", "SUB");
        Arbitrary<String> dataTypes = Arbitraries.of("VARCHAR", "INTEGER", "TEXT", "BOOLEAN", "DATE");
        Arbitrary<String> formTypes = Arbitraries.of("MAIN", "SUB");
        Arbitrary<String> actionTypes = Arbitraries.of("APPROVE", "REJECT", "SAVE", "CANCEL");

        // Generate 1-3 tables, each with 1-3 fields
        Arbitrary<List<Map<String, Object>>> tables = Combinators.combine(tableNames, tableTypes, dataTypes)
                .as((name, type, dt) -> {
                    List<Map<String, Object>> fields = new ArrayList<>();
                    fields.add(Map.of(
                            "fieldName", "id",
                            "dataType", "INTEGER",
                            "isPrimaryKey", true,
                            "sortOrder", 1
                    ));
                    fields.add(Map.of(
                            "fieldName", name + "_name",
                            "dataType", dt.equals("VARCHAR") ? "VARCHAR" : dt,
                            "isPrimaryKey", false,
                            "sortOrder", 2,
                            "length", 100
                    ));
                    Map<String, Object> table = new LinkedHashMap<>();
                    table.put("tableName", name);
                    table.put("tableType", type);
                    table.put("fieldDefinitions", fields);
                    return table;
                })
                .list().ofMinSize(1).ofMaxSize(3)
                .map(list -> {
                    // Ensure unique table names
                    Map<String, Map<String, Object>> unique = new LinkedHashMap<>();
                    for (Map<String, Object> t : list) {
                        unique.putIfAbsent((String) t.get("tableName"), t);
                    }
                    return new ArrayList<>(unique.values());
                });

        Arbitrary<List<Map<String, Object>>> forms = formTypes.map(ft -> {
            Map<String, Object> form = new LinkedHashMap<>();
            form.put("formName", "form_" + ft.toLowerCase());
            form.put("formType", ft);
            form.put("configJson", Map.of("layout", "default"));
            return form;
        }).list().ofMinSize(0).ofMaxSize(2)
                .map(list -> {
                    Map<String, Map<String, Object>> unique = new LinkedHashMap<>();
                    for (Map<String, Object> f : list) {
                        unique.putIfAbsent((String) f.get("formName"), f);
                    }
                    return new ArrayList<>(unique.values());
                });

        Arbitrary<List<Map<String, Object>>> actions = actionTypes.map(at -> {
            Map<String, Object> action = new LinkedHashMap<>();
            action.put("actionName", "action_" + at.toLowerCase());
            action.put("actionType", at);
            action.put("configJson", Map.of("enabled", true));
            return action;
        }).list().ofMinSize(0).ofMaxSize(2)
                .map(list -> {
                    Map<String, Map<String, Object>> unique = new LinkedHashMap<>();
                    for (Map<String, Object> a : list) {
                        unique.putIfAbsent((String) a.get("actionName"), a);
                    }
                    return new ArrayList<>(unique.values());
                });

        Arbitrary<Map<String, Object>> process = Arbitraries.of(
                null,
                Map.<String, Object>of("bpmnXml", "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"/>")
        );

        return Combinators.combine(tables, forms, actions, process)
                .as((t, f, a, p) -> AiGeneratedData.builder()
                        .tableDefinitions(t)
                        .formDefinitions(f)
                        .actionDefinitions(a)
                        .processDefinition(p)
                        .build());
    }

    @Provide
    Arbitrary<String> validTableName() {
        return Arbitraries.of("customers", "products", "orders", "invoices", "tasks",
                "employees", "departments", "projects", "reports", "settings");
    }
}
