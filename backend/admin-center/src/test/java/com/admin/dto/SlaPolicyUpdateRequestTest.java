package com.admin.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** AC3: the lead time accepts whole numbers 1..3650 only; nothing is coerced. */
class SlaPolicyUpdateRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @ParameterizedTest
    @ValueSource(strings = {"1", "30", "3650"})
    void acceptsWholeDaysInRange(String days) throws Exception {
        assertThat(validator.validate(parse(days))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-5", "3651", "1.5", "30.0001", "null"})
    void rejectsFractionsOutOfRangeAndBlank(String days) throws Exception {
        assertThat(validator.validate(parse(days))).isNotEmpty();
    }

    private SlaPolicyUpdateRequest parse(String days) throws Exception {
        return objectMapper.readValue("{\"leadTimeDays\":" + days + "}", SlaPolicyUpdateRequest.class);
    }
}
