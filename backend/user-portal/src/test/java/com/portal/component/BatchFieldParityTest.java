package com.portal.component;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class BatchFieldParityTest {
    private static Map<String, Object> form(Map<String, Object> rule) { return Map.of("rule", List.of(rule)); }
    private static Map<String, Object> input(Map<String, Object> validator) {
        return Map.of("type", "input", "field", "contact", "validate", List.of(validator));
    }
    @Test void validatesEmailAndPreservesDesignerMessage() {
        var form = form(input(Map.of("type", "email", "message", "Enter a valid email")));
        for (String email : List.of("person@example.invalid", "\"Test Person\"@example.invalid", "person+tag@example.invalid")) {
            assertThatCode(() -> SubTableBatchFormRules.validateRow(form, Map.of("contact", email))).doesNotThrowAnyException();
        }
        for (String email : List.of("invalid", "person..test@example.invalid", "person@example.c")) {
            assertThatThrownBy(() -> SubTableBatchFormRules.validateRow(form, Map.of("contact", email))).hasMessageContaining("Enter a valid email");
        }
    }
    @Test void validatesPatternAndDesignerModes() {
        assertThatCode(() -> SubTableBatchFormRules.validateRow(form(input(Map.of("mode", "pattern", "pattern", "^A"))), Map.of("contact", "ABC"))).doesNotThrowAnyException();
        assertThatThrownBy(() -> SubTableBatchFormRules.validateRow(form(input(Map.of("pattern", "^A"))), Map.of("contact", "BCD"))).hasMessageContaining("pattern");
        assertThatThrownBy(() -> SubTableBatchFormRules.validateRow(form(input(Map.of("mode", "email"))), Map.of("contact", "invalid"))).hasMessageContaining("invalid");
    }
    @Test void dateValidatorAcceptsDateTimeButRejectsInvalidCalendarDates() {
        var form = form(input(Map.of("type", "date")));
        assertThatCode(() -> SubTableBatchFormRules.validateRow(form, Map.of("contact", "2026-11-04 12:13:14"))).doesNotThrowAnyException();
        assertThatThrownBy(() -> SubTableBatchFormRules.validateRow(form, Map.of("contact", "invalid"))).hasMessageContaining("invalid");
    }
    @Test void multiselectAcceptsListsAndEnforcesExplicitEnumOnEachItem() {
        var rule = Map.<String, Object>of("type", "select", "field", "contact", "props", Map.of("multiple", true), "options", List.of(Map.of("value", "a")));
        assertThatCode(() -> SubTableBatchFormRules.validateRow(form(rule), Map.of("contact", List.of("a")))).doesNotThrowAnyException();
        assertThatThrownBy(() -> SubTableBatchFormRules.validateRow(form(rule), Map.of("contact", "a"))).hasMessageContaining("list");
        assertThatCode(() -> SubTableBatchFormRules.validateRow(form(input(Map.of("type", "array", "min", 1))), Map.of("contact", List.of("a")))).doesNotThrowAnyException();
    }
    @Test void optionsArePresentationStateAndExplicitEnumsRemainConstraints() {
        var rule = Map.<String, Object>of("type", "select", "field", "contact", "options", List.of(Map.of("value", "old")));
        assertThatCode(() -> SubTableBatchFormRules.validateRow(form(rule), Map.of("contact", "dynamic"))).doesNotThrowAnyException();
        assertThatThrownBy(() -> SubTableBatchFormRules.validateRow(form(input(Map.of("type", "enum", "enum", List.of("old")))), Map.of("contact", "dynamic"))).hasMessageContaining("option");
    }
    @Test void requiresNonemptyRangeAndRequiredLists() {
        var rule = Map.<String, Object>of("type", "datePicker", "field", "contact", "props", Map.of("type", "daterange"));
        assertThatCode(() -> SubTableBatchFormRules.validateRow(form(rule), Map.of("contact", List.of("2026-11-01", "2026-11-04")))).doesNotThrowAnyException();
        assertThatThrownBy(() -> SubTableBatchFormRules.validateRow(form(rule), Map.of("contact", List.of("2026-11-01")))).hasMessageContaining("two values");
        assertThatThrownBy(() -> SubTableBatchFormRules.validateRow(form(input(Map.of("mode", "required"))), Map.of("contact", List.of()))).hasMessageContaining("required");
    }
    @Test void acceptsTrailingZerosWithoutRoundingAndRejectsRealPrecisionLoss() {
        var field = Map.of("fieldName", "amount", "dataType", "DECIMAL", "precision", 18, "scale", 2);
        for (String value : List.of("0.5000000000", "1000.0000", "0.0000000000")) {
            assertThatCode(() -> SubTableBatchAddedRowGuard.validateField(field, Map.of("amount", new BigDecimal(value)))).doesNotThrowAnyException();
        }
        assertThatThrownBy(() -> SubTableBatchAddedRowGuard.validateField(field, Map.of("amount", new BigDecimal("0.501")))).hasMessageContaining("precision");
        assertThatThrownBy(() -> SubTableBatchAddedRowGuard.validateField(field, Map.of("amount", new BigDecimal("10000000000000000")))).hasMessageContaining("precision");
        assertThatCode(() -> SubTableBatchAddedRowGuard.validateField(Map.of("fieldName", "amount", "dataType", "DECIMAL", "precision", 2, "scale", 2), Map.of("amount", BigDecimal.ZERO))).doesNotThrowAnyException();
    }
    @Test void lengthBoundsAcceptWholeDecimalsAndNumericStrings() {
        for (Object length : List.of(3, 3.0, "3", "3.0")) {
            var form = form(Map.of("type", "input", "field", "contact", "props", Map.of("maxLength", length)));
            assertThatCode(() -> SubTableBatchFormRules.validateRow(form, Map.of("contact", "abc"))).doesNotThrowAnyException();
            assertThatThrownBy(() -> SubTableBatchFormRules.validateRow(form, Map.of("contact", "long"))).hasMessageContaining("too long");
        }
        for (Object length : List.of(0, -1, "")) {
            var form = form(Map.of("type", "input", "field", "contact", "props", Map.of("maxlength", length)));
            assertThatCode(() -> SubTableBatchFormRules.validateRow(form, Map.of("contact", "abc"))).doesNotThrowAnyException();
        }
    }
}
