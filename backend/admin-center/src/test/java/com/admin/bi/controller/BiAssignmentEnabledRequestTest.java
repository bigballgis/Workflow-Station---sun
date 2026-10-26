package com.admin.bi.controller;

import com.admin.bi.dto.request.AssignmentEnabledRequest;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BiAssignmentEnabledRequestTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @ParameterizedTest
    @ValueSource(strings = {"\"true\"", "\"false\"", "1", "0", "\"\"", "[]", "{}", "\"abc\""})
    void rejectsNonBooleanJsonTokens(String value) {
        assertThatThrownBy(() -> mapper.readValue("{\"enabled\":" + value + "}", AssignmentEnabledRequest.class))
                .isInstanceOf(JsonMappingException.class);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void acceptsJsonBooleans(boolean value) throws Exception {
        assertThat(mapper.readValue("{\"enabled\":" + value + "}", AssignmentEnabledRequest.class).enabled())
                .isEqualTo(value);
    }

    @Test
    void missingAndNullRemainRequired() throws Exception {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            for (String body : new String[]{"{}", "{\"enabled\":null}"}) {
                assertThat(factory.getValidator().validate(mapper.readValue(body, AssignmentEnabledRequest.class)))
                        .anyMatch(violation -> violation.getPropertyPath().toString().equals("enabled"));
            }
        }
    }
}
