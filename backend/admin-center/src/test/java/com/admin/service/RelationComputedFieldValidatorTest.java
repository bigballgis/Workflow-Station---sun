package com.admin.service;

import com.admin.exception.AdminBusinessException;
import com.admin.service.RelationComputedFieldValidator.IncomingField;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.enums.RelationDataType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Design-time validation of computed fields on Relation Tables.
 *
 * <p>ASTs are written as JSON here because that is exactly the shape that arrives from the designer
 * and gets stored in {@code computed_field_json}.
 */
@DisplayName("RelationComputedFieldValidator")
class RelationComputedFieldValidatorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RelationComputedFieldValidator validator = new RelationComputedFieldValidator();

    @Nested
    @DisplayName("accepts")
    class Accepts {

        @Test
        @DisplayName("row formula over existing columns of the same table")
        void rowFormula() {
            List<IncomingField> fields = List.of(
                    plain("quantity", RelationDataType.DECIMAL),
                    plain("unit_price", RelationDataType.DECIMAL),
                    computed("line_total", RelationDataType.DECIMAL, "quantity * unit_price", "row",
                            binary("*", field("quantity"), field("unit_price")),
                            List.of("quantity", "unit_price")));

            assertThatCode(() -> validator.validateIncomingFields(fields))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("a table with no computed fields is left entirely alone")
        void ignoresPlainTables() {
            // Guards every existing Relation Table: columns that do not opt in must not be
            // subjected to any new rule, including the PK and default-value exclusions.
            List<IncomingField> fields = List.of(
                    new IncomingField("id", RelationDataType.VARCHAR, false, null,
                            true, false, "seed", Map.of("strategy", "SEQUENCE")),
                    plain("quantity", RelationDataType.DECIMAL));

            assertThatCode(() -> validator.validateIncomingFields(fields))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("rejects")
    class Rejects {

        @Test
        @DisplayName("aggregate scope, because a Relation Table has no sub-tables")
        void aggregateScope() {
            List<IncomingField> fields = List.of(
                    computed("items_total", RelationDataType.DECIMAL, "SUM(items.amount)",
                            "aggregate", aggregate("SUM", "items", "amount"),
                            List.of("items.amount")));

            assertThatThrownBy(() -> validator.validateIncomingFields(fields))
                    .isInstanceOf(AdminBusinessException.class)
                    .hasMessageContaining("has no sub-tables to aggregate over");
        }

        @Test
        @DisplayName("a computed column that is also a primary key")
        void computedPrimaryKey() {
            List<IncomingField> fields = List.of(
                    plain("quantity", RelationDataType.DECIMAL),
                    new IncomingField("line_total", RelationDataType.DECIMAL, true,
                            definition("quantity * 2", "row",
                                    binary("*", field("quantity"), number("2")),
                                    List.of("quantity")),
                            true, false, null, null));

            assertThatThrownBy(() -> validator.validateIncomingFields(fields))
                    .isInstanceOf(AdminBusinessException.class)
                    .hasMessageContaining("cannot be a primary key");
        }

        @Test
        @DisplayName("a formula referencing a column the table does not have")
        void unknownDependency() {
            List<IncomingField> fields = List.of(
                    computed("line_total", RelationDataType.DECIMAL, "quantity * 2", "row",
                            binary("*", field("quantity"), number("2")), List.of("quantity")));

            assertThatThrownBy(() -> validator.validateIncomingFields(fields))
                    .isInstanceOf(AdminBusinessException.class)
                    .hasMessageContaining("is not a field of this table");
        }

        @Test
        @DisplayName("a formula whose result cannot be stored in the declared column type")
        void typeMismatch() {
            List<IncomingField> fields = List.of(
                    plain("quantity", RelationDataType.DECIMAL),
                    computed("label", RelationDataType.VARCHAR, "quantity * 2", "row",
                            binary("*", field("quantity"), number("2")), List.of("quantity")));

            assertThatThrownBy(() -> validator.validateIncomingFields(fields))
                    .isInstanceOf(AdminBusinessException.class)
                    .hasMessageContaining("produces a number")
                    .hasMessageContaining("VARCHAR")
                    .hasMessageContaining("Change the Data Type of 'label' to INTEGER or DECIMAL");
        }

        @Test
        @DisplayName("a column marked computed without any formula")
        void missingDefinition() {
            List<IncomingField> fields = List.of(
                    new IncomingField("line_total", RelationDataType.DECIMAL, true, null,
                            false, false, null, null));

            assertThatThrownBy(() -> validator.validateIncomingFields(fields))
                    .isInstanceOf(AdminBusinessException.class)
                    .hasMessageContaining("carries no formula definition");
        }

        @Test
        @DisplayName("two computed columns that depend on each other")
        void circularDependency() {
            List<IncomingField> fields = List.of(
                    computed("a", RelationDataType.DECIMAL, "b + 1", "row",
                            binary("+", field("b"), number("1")), List.of("b")),
                    computed("b", RelationDataType.DECIMAL, "a + 1", "row",
                            binary("+", field("a"), number("1")), List.of("a")));

            assertThatThrownBy(() -> validator.validateIncomingFields(fields))
                    .isInstanceOf(AdminBusinessException.class)
                    .hasMessageContaining("dependency cycle");
        }

        @Test
        @DisplayName("a qualified table.column, because a Relation Table has no parent row")
        void parentFieldRef() {
            List<IncomingField> fields = List.of(
                    computed("copy", RelationDataType.VARCHAR, "leave_request.name", "row",
                            parentField("leave_request", "name"),
                            List.of("leave_request.name")));

            assertThatThrownBy(() -> validator.validateIncomingFields(fields))
                    .isInstanceOf(AdminBusinessException.class)
                    .hasMessageContaining("only a sub-table formula may read the main table");
        }
    }

    private static IncomingField plain(String name, RelationDataType type) {
        return new IncomingField(name, type, false, null, false, false, null, null);
    }

    private static IncomingField computed(String name,
                                          RelationDataType type,
                                          String source,
                                          String scope,
                                          String astJson,
                                          List<String> dependsOn) {
        return new IncomingField(name, type, true,
                definition(source, scope, astJson, dependsOn), false, false, null, null);
    }

    private static Map<String, Object> definition(String source,
                                                  String scope,
                                                  String astJson,
                                                  List<String> dependsOn) {
        return Map.of(
                "version", 1,
                "scope", scope,
                "source", source,
                "ast", json(astJson),
                "dependsOn", dependsOn,
                "onError", "fail");
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
}
