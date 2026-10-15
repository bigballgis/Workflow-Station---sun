package com.developer.component.impl;

import com.developer.dto.ValidationResult;
import com.developer.entity.FormDefinition;
import com.developer.entity.FunctionUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class FunctionUnitBatchValidationTest {
    @Test
    void publicationRejectsUnsupportedBatchSchemaButAcceptsLegacyForms() {
        FunctionUnit unit = new FunctionUnit();
        FormDefinition form = new FormDefinition();
        form.setConfigJson(Map.of("rule", List.of()));
        unit.getFormDefinitions().add(form);
        FunctionUnitValidator validator = new FunctionUnitValidator();
        ValidationResult legacy = new ValidationResult();
        validator.validate(unit, legacy);
        assertThat(legacy.isValid()).isTrue();
        form.setConfigJson(Map.of("rule", List.of(), "batchForms", Map.of("101", Map.of(
                "schemaVersion", 99, "rule", List.of(Map.of("type", "input", "field", "value"))))));
        ValidationResult invalid = new ValidationResult();
        validator.validate(unit, invalid);
        assertThat(invalid.isValid()).isFalse();
        assertThat(invalid.getErrors()).anySatisfy(error -> assertThat(error.getCode()).isEqualTo("INVALID_BATCH_DEFINITION"));
    }
}
