package com.portal.dto;

import com.portal.dto.PortalListColumnMeta.Kind;
import com.portal.dto.PortalListColumnMeta.Option;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PortalListColumnMetaTest {

    @Test
    void openValueKindsAreNeverGroupableByDefault() {
        assertThat(PortalListColumnMeta.of("title", "Title", Kind.TEXT).groupable()).isFalse();
        assertThat(PortalListColumnMeta.of("amount", "Amount", Kind.NUMBER).groupable()).isFalse();
        assertThat(PortalListColumnMeta.of("createdAt", "Created", Kind.DATETIME).groupable()).isFalse();
    }

    @Test
    void closedValueKindsGroupByDefault() {
        assertThat(PortalListColumnMeta.withOptions("status", "Status", Kind.ENUM,
                List.of(new Option("OPEN", "Open"))).groupable()).isTrue();
        assertThat(PortalListColumnMeta.of("assignee", "Assignee", Kind.USER).groupable()).isTrue();
        assertThat(PortalListColumnMeta.of("urgent", "Urgent", Kind.BOOLEAN).groupable()).isTrue();
        assertThat(PortalListColumnMeta.of("urgent", "Urgent", Kind.BOOLEAN).options())
                .extracting(Option::value)
                .containsExactly("true", "false");
    }

    @Test
    void enumMustBeDeclaredWithOptions() {
        assertThatThrownBy(() -> PortalListColumnMeta.of("status", "Status", Kind.ENUM))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("withOptions");
    }

    @Test
    void filterableClosedKindWithoutOptionsIsRejected() {
        assertThatThrownBy(() -> new PortalListColumnMeta(
                "status", "Status", Kind.ENUM, true, true, true,
                PortalListColumnMeta.operatorsFor(Kind.ENUM), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("without options");
    }

    @Test
    void kindOperatorMatrixCoversNumberAndDatetimeRanges() {
        assertThat(PortalListColumnMeta.operatorsFor(Kind.NUMBER))
                .contains("gt", "gte", "lt", "lte", "between")
                .doesNotContain("contains", "startsWith");
        assertThat(PortalListColumnMeta.operatorsFor(Kind.DATETIME))
                .startsWith("today", "yesterday", "last7days", "last30days",
                        "thisWeek", "thisMonth", "thisYear")
                .contains("on", "before", "after", "between")
                .doesNotContain("contains", "gt");
        assertThat(PortalListColumnMeta.operatorsFor(Kind.TEXT))
                .contains("contains", "startsWith", "endsWith")
                .doesNotContain("gt", "between");
        assertThat(PortalListColumnMeta.operatorsFor(Kind.BOOLEAN))
                .containsExactly("eq", "ne", "isNull", "isNotNull")
                .isEqualTo(PortalListColumnMeta.operatorsFor(Kind.ENUM));
    }

    @Test
    void groupableFreeTextColumnIsRejectedAtConstruction() {
        assertThatThrownBy(() -> new PortalListColumnMeta(
                "title", "Title", Kind.TEXT, true, true, true,
                PortalListColumnMeta.operatorsFor(Kind.TEXT), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be groupable");
    }

    @Test
    void filterableColumnWithoutOperatorsIsRejected() {
        assertThatThrownBy(() -> new PortalListColumnMeta(
                "title", "Title", Kind.TEXT, true, true, false, List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("operator whitelist");
    }

    @Test
    void withOptionsRequiresClosedKindAndNonEmptyOptions() {
        PortalListColumnMeta status = PortalListColumnMeta.withOptions(
                "status", "Status", Kind.ENUM,
                List.of(new Option("OPEN", "Open"), new Option("DONE", "Done")));
        assertThat(status.options()).hasSize(2);
        assertThat(status.allowsOperator("eq")).isTrue();
        assertThat(status.allowsOperator("contains")).isFalse();

        assertThatThrownBy(() -> PortalListColumnMeta.withOptions("title", "Title", Kind.TEXT,
                List.of(new Option("A", "A"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not take a closed option list");

        assertThatThrownBy(() -> PortalListColumnMeta.withOptions("status", "Status", Kind.ENUM, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("without options");
    }

    @Test
    void displayOnlyColumnHasNoCapabilities() {
        PortalListColumnMeta col = PortalListColumnMeta.displayOnly("actions", "Actions", Kind.TEXT);
        assertThat(col.filterable()).isFalse();
        assertThat(col.sortable()).isFalse();
        assertThat(col.groupable()).isFalse();
        assertThat(col.operators()).isEmpty();
    }

    @Test
    void displayMappedColumnFiltersByLabelWithoutSorting() {
        PortalListColumnMeta col = PortalListColumnMeta.displayMapped("customer_label", "Customer");
        assertThat(col.filterable()).isTrue();
        assertThat(col.sortable()).isFalse();
        assertThat(col.groupable()).isFalse();
        assertThat(col.operators()).contains("contains", "eq", "isNull");
    }
}
