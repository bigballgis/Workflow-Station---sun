package com.workflow.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LookupFieldResolverTest {

    @Test
    void resolve_readsAttributeFromEmbeddedRow() {
        Map<String, Object> vars = Map.of(
                "user", Map.of("id", "u1", "name", "Alice"));
        assertThat(LookupFieldResolver.resolve(vars, "user", "name")).isEqualTo("Alice");
    }

    @Test
    void resolve_skipsNestedMapValues() {
        Map<String, Object> vars = Map.of(
                "user", Map.of("id", "u1", "profile", Map.of("x", 1)));
        assertThat(LookupFieldResolver.resolve(vars, "user", "profile")).isEqualTo("");
    }
}
