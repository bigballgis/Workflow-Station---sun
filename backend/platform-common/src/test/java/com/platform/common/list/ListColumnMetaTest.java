package com.platform.common.list;

import com.platform.common.list.ListColumnMeta.Kind;
import com.platform.common.list.ListColumnMeta.Option;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ListColumnMetaTest {

    @Test
    void openValueKindsAreNeverGroupableByDefault() {
        assertThat(ListColumnMeta.of("title", "Title", Kind.TEXT).groupable()).isFalse();
        assertThat(ListColumnMeta.of("amount", "Amount", Kind.NUMBER).groupable()).isFalse();
        assertThat(ListColumnMeta.of("createdAt", "Created", Kind.DATETIME).groupable()).isFalse();
    }

    @Test
    void closedValueKindsGroupByDefault() {
        assertThat(ListColumnMeta.withOptions("status", "Status", Kind.ENUM,
                List.of(new Option("OPEN", "Open"))).groupable()).isTrue();
        assertThat(ListColumnMeta.of("assignee", "Assignee", Kind.USER).groupable()).isTrue();
        assertThat(ListColumnMeta.of("urgent", "Urgent", Kind.BOOLEAN).groupable()).isTrue();
        assertThat(ListColumnMeta.of("urgent", "Urgent", Kind.BOOLEAN).options())
                .extracting(Option::value)
                .containsExactly("true", "false");
    }

    @Test
    void enumMustBeDeclaredWithOptions() {
        assertThatThrownBy(() -> ListColumnMeta.of("status", "Status", Kind.ENUM))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("withOptions");
    }

    @Test
    void filterableClosedKindWithoutOptionsIsRejected() {
        assertThatThrownBy(() -> new ListColumnMeta(
                "status", "Status", Kind.ENUM, true, true, true,
                ListColumnMeta.operatorsFor(Kind.ENUM), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("without options");
    }

    @Test
    void kindOperatorMatrixCoversNumberAndDatetimeRanges() {
        assertThat(ListColumnMeta.operatorsFor(Kind.NUMBER))
                .contains("gt", "gte", "lt", "lte", "between")
                .doesNotContain("contains", "startsWith");
        assertThat(ListColumnMeta.operatorsFor(Kind.DATETIME))
                .startsWith("today", "yesterday", "last7days", "last30days",
                        "thisWeek", "thisMonth", "thisYear")
                .contains("on", "before", "after", "between")
                .doesNotContain("contains", "gt");
        assertThat(ListColumnMeta.operatorsFor(Kind.TEXT))
                .contains("contains", "startsWith", "endsWith")
                .doesNotContain("gt", "between");
        assertThat(ListColumnMeta.operatorsFor(Kind.BOOLEAN))
                .containsExactly("eq", "ne", "isNull", "isNotNull")
                .isEqualTo(ListColumnMeta.operatorsFor(Kind.ENUM));
    }

    @Test
    void groupableFreeTextColumnIsRejectedAtConstruction() {
        assertThatThrownBy(() -> new ListColumnMeta(
                "title", "Title", Kind.TEXT, true, true, true,
                ListColumnMeta.operatorsFor(Kind.TEXT), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be groupable");
    }

    @Test
    void filterableColumnWithoutOperatorsIsRejected() {
        assertThatThrownBy(() -> new ListColumnMeta(
                "title", "Title", Kind.TEXT, true, true, false, List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("operator whitelist");
    }

    @Test
    void withOptionsRequiresClosedKindAndNonEmptyOptions() {
        ListColumnMeta status = ListColumnMeta.withOptions(
                "status", "Status", Kind.ENUM,
                List.of(new Option("OPEN", "Open"), new Option("DONE", "Done")));
        assertThat(status.options()).hasSize(2);
        assertThat(status.allowsOperator("eq")).isTrue();
        assertThat(status.allowsOperator("contains")).isFalse();

        assertThatThrownBy(() -> ListColumnMeta.withOptions("title", "Title", Kind.TEXT,
                List.of(new Option("A", "A"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not take a closed option list");

        assertThatThrownBy(() -> ListColumnMeta.withOptions("status", "Status", Kind.ENUM, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("without options");
    }

    @Test
    void displayOnlyColumnHasNoCapabilities() {
        ListColumnMeta col = ListColumnMeta.displayOnly("actions", "Actions", Kind.TEXT);
        assertThat(col.filterable()).isFalse();
        assertThat(col.sortable()).isFalse();
        assertThat(col.groupable()).isFalse();
        assertThat(col.operators()).isEmpty();
    }

    @Test
    void displayMappedColumnFiltersByLabelWithoutSorting() {
        ListColumnMeta col = ListColumnMeta.displayMapped("customer_label", "Customer");
        assertThat(col.filterable()).isTrue();
        assertThat(col.sortable()).isFalse();
        assertThat(col.groupable()).isFalse();
        assertThat(col.operators()).contains("contains", "eq", "isNull");
    }
}
