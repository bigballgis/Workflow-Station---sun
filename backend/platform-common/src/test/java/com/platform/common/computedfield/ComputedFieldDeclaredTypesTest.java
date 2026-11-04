package com.platform.common.computedfield;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class ComputedFieldDeclaredTypesTest {
    @Test void declaredTextPreservesIdentifierDigitsWhileSchemaFreeSamplesRemainCompatible() {
        String identifier = "00123456789012345678901234";
        assertThat(ComputedValues.fromRowValue(identifier, "VARCHAR")).isEqualTo(ComputedValue.of(identifier));
        assertThat(ComputedValues.fromRowValue(identifier)).isInstanceOf(ComputedValue.Number.class);
        assertThat(ComputedValues.fromRowValue(" ", "TEXT")).isEqualTo(ComputedValue.BLANK);
        assertThat(ComputedValues.fromRowValue(null, "VARCHAR")).isEqualTo(ComputedValue.BLANK);
    }

    @Test void parentReferenceAndConditionalRetainExactText() {
        var context = new ComputedFieldContext(Map.of(), Map.of(), Map.of("parent", Map.of("identifier", "000123")),
                Map.of(), Map.of("parent", Map.of("identifier", "CHAR")));
        Map<String, Object> reference = Map.of("type", "field", "table", "Parent", "name", "identifier");
        assertThat(ComputedFieldEvaluator.evaluate(reference, context)).isEqualTo(EvalOutcome.ok(ComputedValue.of("000123")));
    }

    @Test void numericColumnsStillCalculateAndTextRequiresExplicitValue() {
        Map<String, Object> multiply = Map.of("type", "binary", "op", "*", "left", Map.of("type", "field", "name", "amount"),
                "right", Map.of("type", "number", "text", "2"));
        var numeric = new ComputedFieldContext(Map.of("amount", "001.20"), Map.of(), Map.of(), Map.of("amount", "DECIMAL"), Map.of());
        assertThat(((EvalOutcome.Success) ComputedFieldEvaluator.evaluate(multiply, numeric)).value())
                .isEqualTo(ComputedValue.of(new BigDecimal("2.40")));
        var text = new ComputedFieldContext(numeric.row(), Map.of(), Map.of(), Map.of("amount", "VARCHAR"), Map.of());
        assertThat(ComputedFieldEvaluator.evaluate(multiply, text)).isInstanceOf(EvalOutcome.Failure.class);
        assertThat(ComputedFieldEvaluator.evaluate(Map.of("type", "call", "fn", "VALUE", "args", java.util.List.of(
                Map.of("type", "field", "name", "amount"))), text)).isInstanceOf(EvalOutcome.Success.class);
    }

    @Test void outputColumnConversionIsExplicitAndRejectsInvalidNumericOrBooleanResults() {
        assertThat(ComputedValues.forField(ComputedValue.of(new BigDecimal("12.50")), "VARCHAR"))
                .isEqualTo(EvalOutcome.ok(ComputedValue.of("12.50")));
        assertThat(ComputedValues.forField(ComputedValue.of("001.20"), "DECIMAL"))
                .isEqualTo(EvalOutcome.ok(ComputedValue.of(new BigDecimal("1.20"))));
        assertThat(ComputedValues.forField(ComputedValue.of("wrong"), "DECIMAL")).isInstanceOf(EvalOutcome.Failure.class);
        assertThat(ComputedValues.forField(ComputedValue.of(new BigDecimal("1.5")), "INTEGER")).isInstanceOf(EvalOutcome.Failure.class);
        assertThat(ComputedValues.forField(ComputedValue.of("true"), "BOOLEAN")).isInstanceOf(EvalOutcome.Failure.class);
        assertThat(ComputedValues.forField(ComputedValue.BLANK, "VARCHAR")).isEqualTo(EvalOutcome.ok(ComputedValue.BLANK));
    }
}
