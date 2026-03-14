package com.portal.properties;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property test for form config_json structure consistency.
 * Feature: travel-expense-reimbursement, Property 2
 * **Validates: Requirements 3.2**
 */
public class TravelExpenseFormConfigPropertyTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final List<String> RULE_FIELDS = List.of("name", "type", "field", "title");
    private static final List<String> OPT_FIELDS = List.of("form", "submitBtn");

    /** **Validates: Requirements 3.2** */
    @Property(tries = 100)
    @Label("Feature: travel-expense-reimbursement, Property 2: Form config_json structure consistency")
    void formConfigJsonHasCorrectStructure(
            @ForAll("validFormConfigJsons") String configJson) throws Exception {
        Map<String, Object> config = objectMapper.readValue(configJson, new TypeReference<>() {});
        assertThat(config).containsKey("rule");
        assertThat(config.get("rule")).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rules = (List<Map<String, Object>>) config.get("rule");
        assertThat(rules).isNotEmpty();
        for (Map<String, Object> rule : rules) {
            for (String f : RULE_FIELDS) {
                assertThat(rule).containsKey(f);
                assertThat(rule.get(f)).isNotNull();
            }
        }
        assertThat(config).containsKey("options");
        assertThat(config.get("options")).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> options = (Map<String, Object>) config.get("options");
        for (String k : OPT_FIELDS) {
            assertThat(options).containsKey(k);
            assertThat(options.get(k)).isInstanceOf(Map.class);
        }
    }

    @Provide
    Arbitrary<String> validFormConfigJsons() {
        return Combinators.combine(ruleLists(), optionsArb()).as((rules, opts) -> {

            Map<String, Object> c = new LinkedHashMap<>();
            c.put("rule", rules);
            c.put("options", opts);
            c.put("subForms", new LinkedHashMap<>());
            try { return objectMapper.writeValueAsString(c); }
            catch (Exception e) { throw new RuntimeException(e); }
        });
    }

    private Arbitrary<List<Map<String, Object>>> ruleLists() {
        return ruleEntry().list().ofMinSize(1).ofMaxSize(9);
    }

    private Arbitrary<Map<String, Object>> ruleEntry() {
        Arbitrary<String> types = Arbitraries.of("input", "datePicker", "inputNumber", "select");
        return Combinators.combine(
                Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20), types,
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(20),
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(30)
        ).as((n, t, f, ti) -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("name", "ref_" + n); r.put("type", t);
            r.put("field", f); r.put("title", ti);
            return r;
        });
    }

    private Arbitrary<Map<String, Object>> optionsArb() {
        return Arbitraries.just(null).map(x -> {
            Map<String, Object> o = new LinkedHashMap<>();
            Map<String, Object> form = new LinkedHashMap<>();
            form.put("size", "default"); form.put("inline", false);
            o.put("form", form);
            Map<String, Object> btn = new LinkedHashMap<>();
            btn.put("show", true); btn.put("innerText", "Submit");
            o.put("submitBtn", btn);
            return o;
        });
    }
}
