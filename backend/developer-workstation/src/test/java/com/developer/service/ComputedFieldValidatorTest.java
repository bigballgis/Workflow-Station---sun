package com.developer.service;

import com.developer.dto.FieldDefinitionRequest;
import com.developer.entity.FieldDefinition;
import com.developer.entity.TableDefinition;
import com.developer.enums.DataType;
import com.developer.enums.TableType;
import com.developer.exception.DeveloperBusinessException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Design-time validation of computed fields.
 *
 * <p>ASTs are written as JSON here because that is exactly the shape that arrives from the designer
 * and gets stored in {@code computed_field_json}; building them through nested maps in Java would
 * test a different thing than what production sees.
 */
@DisplayName("ComputedFieldValidator")
class ComputedFieldValidatorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ComputedFieldValidator validator = new ComputedFieldValidator();

    @Nested
    @DisplayName("accepts valid definitions")
    class Accepts {

        @Test
        @DisplayName("row formula over existing same-table fields")
        void rowFormula() {
            List<FieldDefinitionRequest> fields = List.of(
                    plain("amount", DataType.DECIMAL),
                    plain("tax_rate", DataType.DECIMAL),
                    computed("total", DataType.DECIMAL, "amount * (1 + tax_rate)", "row",
                            binary("*", field("amount"),
                                    binary("+", number("1"), field("tax_rate"))),
                            List.of("amount", "tax_rate")));

            assertThatCode(() -> validator.validateIncomingFields(
                    mainTable(1L, "purchase_request"), fields, List.of()))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("aggregate formula over a sub-table of the same Function Unit")
        void aggregateFormula() {
            List<FieldDefinitionRequest> fields = List.of(
                    computed("items_total", DataType.DECIMAL, "SUM(request_items.amount)", "aggregate",
                            aggregate("SUM", "request_items", "amount"),
                            List.of("request_items.amount")));

            TableDefinition subTable = subTable(2L, "request_items", "amount");

            assertThatCode(() -> validator.validateIncomingFields(
                    mainTable(1L, "purchase_request"), fields, List.of(subTable)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("sub-table row formula reading a plain MAIN column as table.column")
        void subTableReadsMainColumn() {
            List<FieldDefinitionRequest> fields = List.of(
                    computed("requester", DataType.VARCHAR, "purchase_request.title", "row",
                            parentField("purchase_request", "title"),
                            List.of("purchase_request.title")));

            TableDefinition main = mainTableWithColumns(1L, "purchase_request",
                    FieldDefinition.builder()
                            .fieldName("title")
                            .dataType(DataType.VARCHAR)
                            .sortOrder(0)
                            .build());

            assertThatCode(() -> validator.validateIncomingFields(
                    subTable(2L, "request_items"), fields, List.of(main)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("a table with no computed fields is left entirely alone")
        void ignoresPlainTables() {
            // Guards the AI write path and every existing Function Unit: fields that do not opt in
            // must not be subjected to any new rule, including the PK/unique/default exclusions.
            List<FieldDefinitionRequest> fields = new ArrayList<>();
            FieldDefinitionRequest pk = plain("id", DataType.VARCHAR);
            pk.setIsPrimaryKey(true);
            pk.setIsUnique(true);
            pk.setDefaultValue("seed");
            fields.add(pk);
            fields.add(plain("amount", DataType.DECIMAL));

            assertThatCode(() -> validator.validateIncomingFields(
                    mainTable(1L, "purchase_request"), fields, List.of()))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("a computed field may depend on another computed field")
        void chainedComputedFields() {
            List<FieldDefinitionRequest> fields = List.of(
                    plain("amount", DataType.DECIMAL),
                    computed("subtotal", DataType.DECIMAL, "amount * 2", "row",
                            binary("*", field("amount"), number("2")), List.of("amount")),
                    computed("grand_total", DataType.DECIMAL, "subtotal + 1", "row",
                            binary("+", field("subtotal"), number("1")), List.of("subtotal")));

            assertThatCode(() -> validator.validateIncomingFields(
                    mainTable(1L, "purchase_request"), fields, List.of()))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("rejects")
    class Rejects {

        @Test
        @DisplayName("a formula referencing a field that does not exist")
        void unknownDependency() {
            List<FieldDefinitionRequest> fields = List.of(
                    computed("total", DataType.DECIMAL, "ghost_field * 2", "row",
                            binary("*", field("ghost_field"), number("2")),
                            List.of("ghost_field")));

            expectRejection(fields, List.of(), "COMPUTED_FIELD_UNKNOWN_DEPENDENCY");
        }

        @Test
        @DisplayName("a formula referencing itself")
        void selfReference() {
            List<FieldDefinitionRequest> fields = List.of(
                    computed("total", DataType.DECIMAL, "total + 1", "row",
                            binary("+", field("total"), number("1")), List.of("total")));

            expectRejection(fields, List.of(), "COMPUTED_FIELD_SELF_REFERENCE");
        }

        @Test
        @DisplayName("two formulas depending on each other")
        void circularDependency() {
            List<FieldDefinitionRequest> fields = List.of(
                    computed("a", DataType.DECIMAL, "b + 1", "row",
                            binary("+", field("b"), number("1")), List.of("b")),
                    computed("b", DataType.DECIMAL, "a + 1", "row",
                            binary("+", field("a"), number("1")), List.of("a")));

            expectRejection(fields, List.of(), "COMPUTED_FIELD_CIRCULAR_DEPENDENCY");
        }

        @Test
        @DisplayName("a computed field marked as primary key")
        void computedPrimaryKey() {
            FieldDefinitionRequest total = computed("total", DataType.DECIMAL, "1 + 1", "row",
                    binary("+", number("1"), number("1")), List.of());
            total.setIsPrimaryKey(true);

            expectRejection(List.of(total), List.of(), "COMPUTED_FIELD_CANNOT_BE_PK");
        }

        @Test
        @DisplayName("a computed field carrying a unique constraint")
        void computedUnique() {
            FieldDefinitionRequest total = computed("total", DataType.DECIMAL, "1 + 1", "row",
                    binary("+", number("1"), number("1")), List.of());
            total.setIsUnique(true);

            expectRejection(List.of(total), List.of(), "COMPUTED_FIELD_CANNOT_BE_UNIQUE");
        }

        @Test
        @DisplayName("a computed field with a default value")
        void computedDefaultValue() {
            FieldDefinitionRequest total = computed("total", DataType.DECIMAL, "1 + 1", "row",
                    binary("+", number("1"), number("1")), List.of());
            total.setDefaultValue("0");

            expectRejection(List.of(total), List.of(), "COMPUTED_FIELD_CANNOT_HAVE_DEFAULT");
        }

        @Test
        @DisplayName("an AST calling a function that is not whitelisted")
        void unknownFunction() {
            List<FieldDefinitionRequest> fields = List.of(
                    computed("total", DataType.DECIMAL, "EXFILTRATE(1)", "row",
                            call("EXFILTRATE", number("1")), List.of()));

            expectRejection(fields, List.of(), "COMPUTED_FIELD_INVALID_AST");
        }

        @Test
        @DisplayName("a dependsOn list that disagrees with the AST")
        void tamperedDependsOn() {
            // The client cannot narrow what the server thinks the formula reads.
            List<FieldDefinitionRequest> fields = List.of(
                    plain("amount", DataType.DECIMAL),
                    computed("total", DataType.DECIMAL, "amount * 2", "row",
                            binary("*", field("amount"), number("2")), List.of()));

            expectRejection(fields, List.of(), "COMPUTED_FIELD_DEPENDS_ON_MISMATCH");
        }

        @Test
        @DisplayName("an aggregate declared with row scope")
        void scopeMismatch() {
            List<FieldDefinitionRequest> fields = List.of(
                    computed("items_total", DataType.DECIMAL, "SUM(request_items.amount)", "row",
                            aggregate("SUM", "request_items", "amount"),
                            List.of("request_items.amount")));

            expectRejection(fields, List.of(subTable(2L, "request_items", "amount")),
                    "COMPUTED_FIELD_SCOPE_MISMATCH");
        }

        @Test
        @DisplayName("an aggregate over a table that is not in the Function Unit")
        void unknownSubTable() {
            List<FieldDefinitionRequest> fields = List.of(
                    computed("items_total", DataType.DECIMAL, "SUM(other_items.amount)", "aggregate",
                            aggregate("SUM", "other_items", "amount"),
                            List.of("other_items.amount")));

            expectRejection(fields, List.of(subTable(2L, "request_items", "amount")),
                    "COMPUTED_FIELD_UNKNOWN_SUB_TABLE");
        }

        @Test
        @DisplayName("an aggregate over a column the sub-table does not have")
        void unknownSubTableColumn() {
            List<FieldDefinitionRequest> fields = List.of(
                    computed("items_total", DataType.DECIMAL, "SUM(request_items.ghost)", "aggregate",
                            aggregate("SUM", "request_items", "ghost"),
                            List.of("request_items.ghost")));

            expectRejection(fields, List.of(subTable(2L, "request_items", "amount")),
                    "COMPUTED_FIELD_UNKNOWN_SUB_TABLE_COLUMN");
        }

        @Test
        @DisplayName("a text-producing formula stored in a DECIMAL column")
        void resultTypeMismatch() {
            List<FieldDefinitionRequest> fields = List.of(
                    plain("code", DataType.VARCHAR),
                    computed("total", DataType.DECIMAL, "UPPER(code)", "row",
                            call("UPPER", field("code")), List.of("code")));

            assertThatThrownBy(() -> validator.validateIncomingFields(
                    mainTable(1L, "purchase_request"), fields, List.of()))
                    .isInstanceOf(DeveloperBusinessException.class)
                    .satisfies(thrown -> {
                        DeveloperBusinessException failure = (DeveloperBusinessException) thrown;
                        assertThat(failure.getErrorCode()).isEqualTo("COMPUTED_FIELD_TYPE_MISMATCH");
                        assertThat(failure.getMessage())
                                .contains("produces a text")
                                .contains("DECIMAL")
                                .contains("Change the Data Type of 'total' to VARCHAR, or use a numeric formula");
                    });
        }

        @Test
        @DisplayName("a numeric formula (including date difference) stored in a VARCHAR column")
        void numberFormulaOnVarcharColumn() {
            List<FieldDefinitionRequest> fields = List.of(
                    plain("startdate", DataType.VARCHAR),
                    plain("enddate", DataType.VARCHAR),
                    computed("day", DataType.VARCHAR, "enddate - startdate", "row",
                            binary("-", field("enddate"), field("startdate")),
                            List.of("enddate", "startdate")));

            assertThatThrownBy(() -> validator.validateIncomingFields(
                    mainTable(1L, "date_info"), fields, List.of()))
                    .isInstanceOf(DeveloperBusinessException.class)
                    .satisfies(thrown -> {
                        DeveloperBusinessException failure = (DeveloperBusinessException) thrown;
                        assertThat(failure.getErrorCode()).isEqualTo("COMPUTED_FIELD_TYPE_MISMATCH");
                        assertThat(failure.getMessage())
                                .contains("Computed field 'day'")
                                .contains("produces a number")
                                .contains("VARCHAR")
                                .contains("Change the Data Type of 'day' to INTEGER or DECIMAL")
                                .doesNotContain("startdate")
                                .doesNotContain("enddate");
                    });
        }

        @Test
        @DisplayName("a field flagged computed but carrying no formula")
        void missingDefinition() {
            FieldDefinitionRequest total = plain("total", DataType.DECIMAL);
            total.setIsComputed(true);

            expectRejection(List.of(total), List.of(), "COMPUTED_FIELD_DEFINITION_REQUIRED");
        }

        @Test
        @DisplayName("a definition with no compiled AST")
        void missingAst() {
            FieldDefinitionRequest total = plain("total", DataType.DECIMAL);
            total.setIsComputed(true);
            total.setComputedField(Map.of("version", 1, "scope", "row", "source", "1 + 1"));

            expectRejection(List.of(total), List.of(), "COMPUTED_FIELD_AST_REQUIRED");
        }

        @Test
        @DisplayName("a definition with no source text to redisplay")
        void missingSource() {
            FieldDefinitionRequest total = plain("total", DataType.DECIMAL);
            total.setIsComputed(true);
            total.setComputedField(Map.of(
                    "version", 1,
                    "scope", "row",
                    "ast", json(binary("+", number("1"), number("1")))));

            expectRejection(List.of(total), List.of(), "COMPUTED_FIELD_SOURCE_REQUIRED");
        }

        @Test
        @DisplayName("a qualified table.column on the MAIN table")
        void parentRefOnMainTable() {
            List<FieldDefinitionRequest> fields = List.of(
                    computed("copy", DataType.VARCHAR, "purchase_request.title", "row",
                            parentField("purchase_request", "title"),
                            List.of("purchase_request.title")));

            expectRejection(fields, List.of(), "COMPUTED_FIELD_PARENT_REF_NOT_ALLOWED");
        }

        @Test
        @DisplayName("a sub-table formula naming a table that is not MAIN")
        void unknownParentTable() {
            List<FieldDefinitionRequest> fields = List.of(
                    computed("copy", DataType.VARCHAR, "other.title", "row",
                            parentField("other", "title"),
                            List.of("other.title")));

            TableDefinition main = mainTableWithColumns(1L, "purchase_request",
                    FieldDefinition.builder().fieldName("title").dataType(DataType.VARCHAR)
                            .sortOrder(0).build());

            assertThatThrownBy(() -> validator.validateIncomingFields(
                    subTable(2L, "request_items"), fields, List.of(main)))
                    .isInstanceOf(DeveloperBusinessException.class)
                    .satisfies(thrown -> assertThat(((DeveloperBusinessException) thrown).getErrorCode())
                            .isEqualTo("COMPUTED_FIELD_UNKNOWN_PARENT_TABLE"));
        }

        @Test
        @DisplayName("a sub-table formula naming a MAIN column that does not exist")
        void unknownParentColumn() {
            List<FieldDefinitionRequest> fields = List.of(
                    computed("copy", DataType.VARCHAR, "purchase_request.ghost", "row",
                            parentField("purchase_request", "ghost"),
                            List.of("purchase_request.ghost")));

            TableDefinition main = mainTableWithColumns(1L, "purchase_request",
                    FieldDefinition.builder().fieldName("title").dataType(DataType.VARCHAR)
                            .sortOrder(0).build());

            assertThatThrownBy(() -> validator.validateIncomingFields(
                    subTable(2L, "request_items"), fields, List.of(main)))
                    .isInstanceOf(DeveloperBusinessException.class)
                    .satisfies(thrown -> assertThat(((DeveloperBusinessException) thrown).getErrorCode())
                            .isEqualTo("COMPUTED_FIELD_UNKNOWN_PARENT_COLUMN"));
        }

        @Test
        @DisplayName("a sub-table formula reading a computed MAIN column")
        void parentComputedColumn() {
            List<FieldDefinitionRequest> fields = List.of(
                    computed("copy", DataType.VARCHAR, "purchase_request.display", "row",
                            parentField("purchase_request", "display"),
                            List.of("purchase_request.display")));

            TableDefinition main = mainTableWithColumns(1L, "purchase_request",
                    FieldDefinition.builder().fieldName("display").dataType(DataType.VARCHAR)
                            .isComputed(true).sortOrder(0).build());

            assertThatThrownBy(() -> validator.validateIncomingFields(
                    subTable(2L, "request_items"), fields, List.of(main)))
                    .isInstanceOf(DeveloperBusinessException.class)
                    .satisfies(thrown -> assertThat(((DeveloperBusinessException) thrown).getErrorCode())
                            .isEqualTo("COMPUTED_FIELD_PARENT_COMPUTED_DEPENDENCY"));
        }
    }

    private void expectRejection(List<FieldDefinitionRequest> fields,
                                 List<TableDefinition> allTables,
                                 String expectedCode) {
        assertThatThrownBy(() -> validator.validateIncomingFields(
                mainTable(1L, "purchase_request"), fields, allTables))
                .isInstanceOf(DeveloperBusinessException.class)
                .satisfies(thrown -> assertThat(((DeveloperBusinessException) thrown).getErrorCode())
                        .isEqualTo(expectedCode));
    }

    // ----- fixtures -------------------------------------------------------------------------

    private static TableDefinition mainTable(Long id, String name) {
        return TableDefinition.builder()
                .id(id)
                .tableName(name)
                .tableType(TableType.MAIN)
                .fieldDefinitions(new ArrayList<>())
                .build();
    }

    private static TableDefinition mainTableWithColumns(Long id, String name, FieldDefinition... columns) {
        TableDefinition table = mainTable(id, name);
        table.setFieldDefinitions(new ArrayList<>(List.of(columns)));
        return table;
    }

    private static TableDefinition subTable(Long id, String name, String... columns) {
        List<FieldDefinition> fields = new ArrayList<>();
        for (String column : columns) {
            fields.add(FieldDefinition.builder()
                    .fieldName(column)
                    .dataType(DataType.DECIMAL)
                    .sortOrder(fields.size())
                    .build());
        }
        return TableDefinition.builder()
                .id(id)
                .tableName(name)
                .tableType(TableType.SUB)
                .fieldDefinitions(fields)
                .build();
    }

    private static FieldDefinitionRequest plain(String name, DataType type) {
        return FieldDefinitionRequest.builder()
                .fieldName(name)
                .dataType(type)
                .build();
    }

    private static FieldDefinitionRequest computed(String name,
                                                   DataType type,
                                                   String source,
                                                   String scope,
                                                   String astJson,
                                                   List<String> dependsOn) {
        FieldDefinitionRequest request = plain(name, type);
        request.setIsComputed(true);
        request.setComputedField(Map.of(
                "version", 1,
                "scope", scope,
                "source", source,
                "ast", json(astJson),
                "dependsOn", dependsOn,
                "onError", "fail"));
        return request;
    }

    private static Map<String, Object> json(String raw) {
        try {
            return MAPPER.readValue(raw, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            throw new IllegalArgumentException("Bad AST fixture: " + raw, e);
        }
    }

    private static String number(String text) {
        return "{\"type\":\"number\",\"text\":\"" + text + "\"}";
    }

    private static String field(String name) {
        return "{\"type\":\"field\",\"name\":\"" + name + "\"}";
    }

    private static String parentField(String table, String name) {
        return "{\"type\":\"field\",\"table\":\"" + table + "\",\"name\":\"" + name + "\"}";
    }

    private static String binary(String op, String left, String right) {
        return "{\"type\":\"binary\",\"op\":\"" + op + "\",\"left\":" + left + ",\"right\":" + right + "}";
    }

    private static String aggregate(String fn, String table, String column) {
        return "{\"type\":\"aggregate\",\"fn\":\"" + fn + "\",\"table\":\"" + table
                + "\",\"column\":\"" + column + "\"}";
    }

    private static String call(String fn, String... args) {
        return "{\"type\":\"call\",\"fn\":\"" + fn + "\",\"args\":[" + String.join(",", args) + "]}";
    }
}
