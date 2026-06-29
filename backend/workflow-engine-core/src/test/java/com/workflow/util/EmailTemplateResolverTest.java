package com.workflow.util;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EmailTemplateResolverTest {

    @Test
    void resolve_replacesTopLevelVariable() {
        Map<String, Object> vars = Map.of("remark", "hello");
        String result = EmailTemplateResolver.resolve("<p>${remark}</p>", vars);
        assertThat(result).isEqualTo("<p>hello</p>");
    }

    @Test
    void resolve_replacesSubTableHtmlWithRows() {
        Map<String, Object> vars = Map.of(
                "__subTables__", Map.of(
                        "66", List.of(
                                Map.of("id", "r1", "name", "Item A", "qty", 2),
                                Map.of("id", "r2", "name", "Item B", "qty", 5)
                        )
                )
        );

        String result = EmailTemplateResolver.resolve("<h3>Lines</h3>${subTableHtml:66}", vars);

        assertThat(result).contains("<table");
        assertThat(result).contains("Item A");
        assertThat(result).contains("Item B");
        assertThat(result).doesNotContain("${subTableHtml:66}");
    }

    @Test
    void resolve_subTableHtml_emptyBindingShowsNoData() {
        Map<String, Object> vars = Map.of("__subTables__", Map.of("66", List.of()));
        String result = EmailTemplateResolver.resolve("${subTableHtml:66}", vars);
        assertThat(result).contains("No data");
    }

    @Test
    void resolve_subTableHtml_unknownBindingShowsNoData() {
        Map<String, Object> vars = Map.of("__subTables__", Map.of("66", List.of(Map.of("id", "1"))));
        String result = EmailTemplateResolver.resolve("${subTableHtml:99}", vars);
        assertThat(result).contains("No data");
    }

    @Test
    void resolve_combinesMainFieldAndSubTable() {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("title", "Order");
        vars.put("__subTables__", Map.of("10", List.of(Map.of("id", "1", "amount", 100))));

        String template = "<p>Title: ${title}</p>${subTableHtml:10}";
        String result = EmailTemplateResolver.resolve(template, vars);

        assertThat(result).contains("Title: Order");
        assertThat(result).contains("<td style=").contains(">100</td>");
    }

    @Test
    void resolve_escapesHtmlInCellValues() {
        Map<String, Object> vars = Map.of(
                "__subTables__", Map.of(
                        "1", List.of(Map.of("id", "1", "note", "<script>alert(1)</script>"))
                )
        );
        String result = EmailTemplateResolver.resolve("${subTableHtml:1}", vars);
        assertThat(result).doesNotContain("<script>");
        assertThat(result).contains("&lt;script&gt;");
    }

    @Test
    void resolve_nullTemplateReturnsNull() {
        assertThat(EmailTemplateResolver.resolve(null, Map.of())).isNull();
    }

    @Test
    void resolve_subTableFieldBindingAndField() {
        Map<String, Object> vars = Map.of(
                "__subTables__", Map.of(
                        "271", List.of(Map.of("card_number", "12", "row_id", "r1"))
                )
        );
        String result = EmailTemplateResolver.resolve("Card: ${subTableField:271:card_number}", vars);
        assertThat(result).isEqualTo("Card: 12");
    }

    @Test
    void resolve_bareSubTableFieldFallback() {
        Map<String, Object> vars = Map.of(
                "case_number", "CASE-001",
                "__subTables__", Map.of(
                        "271", List.of(Map.of("card_number", "12"))
                )
        );
        String result = EmailTemplateResolver.resolve("case ${case_number}, card ${card_number}", vars);
        assertThat(result).isEqualTo("case CASE-001, card 12");
    }

    @Test
    void resolve_subTableFieldJoinsMultipleRows() {
        Map<String, Object> vars = Map.of(
                "__subTables__", Map.of(
                        "271", List.of(
                                Map.of("card_number", "12"),
                                Map.of("card_number", "34")
                        )
                )
        );
        String result = EmailTemplateResolver.resolve("${subTableField:271:card_number}", vars);
        assertThat(result).isEqualTo("12, 34");
    }

    @Test
    void resolve_subTableHtmlSelectedColumnsWithHeaders() {
        Map<String, Object> vars = Map.of(
                "__subTables__", Map.of(
                        "271", List.of(
                                new LinkedHashMap<>(Map.of("card_number", "1", "case_type", "A", "row_id", "x1")),
                                new LinkedHashMap<>(Map.of("card_number", "2", "case_type", "B", "row_id", "x2"))
                        )
                )
        );

        String result = EmailTemplateResolver.resolve(
                "${subTableHtml:271:card_number=Card Number,case_type=Card Type}", vars);

        // Custom headers rendered, row_id excluded, one row per record
        assertThat(result).contains("<th").contains("Card Number").contains("Card Type");
        assertThat(result).doesNotContain("row_id").doesNotContain("x1").doesNotContain("x2");
        assertThat(result).contains(">1</td>").contains(">A</td>");
        assertThat(result).contains(">2</td>").contains(">B</td>");
    }

    @Test
    void resolve_subTableHtmlSelectedColumnsDefaultHeaderToFieldName() {
        Map<String, Object> vars = Map.of(
                "__subTables__", Map.of(
                        "271", List.of(Map.of("card_number", "9", "case_type", "Z"))
                )
        );
        // Only card_number requested; case_type must not appear
        String result = EmailTemplateResolver.resolve("${subTableHtml:271:card_number}", vars);
        assertThat(result).contains("card_number").contains(">9</td>");
        assertThat(result).doesNotContain("case_type").doesNotContain(">Z</td>");
    }
}
