package com.portal.component;

import com.platform.common.functionunit.CatalogSnapshotReader;
import com.platform.common.functionunit.FunctionUnitVersionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Change-history slices are keyed by physical table name (filter output) or by the
 * history-normalized canonical key ({@code dw:foo} -> {@code dw_foo}). Both must resolve
 * the primary key the request's release declares, otherwise a new row is audited without
 * {@code rowIdentifier} and later rows that share business defaults pair incorrectly.
 */
class ChangeHistoryPrimaryKeySliceLookupTest {

    private static final String RELEASE = "release-1";
    private CatalogSnapshotReader snapshots;

    @BeforeEach
    void setUp() {
        snapshots = mock(CatalogSnapshotReader.class);
        when(snapshots.contents(RELEASE, "DATA_TABLE")).thenReturn(List.of(Map.of(
                "tableName", "item_table", "tableType", "SUB", "fields", List.of(
                        Map.of("fieldName", "line_no", "isPrimaryKey", true, "sortOrder", 2),
                        Map.of("fieldName", "note", "isPrimaryKey", false, "sortOrder", 3),
                        Map.of("fieldName", "item_id", "isPrimaryKey", true, "sortOrder", 1)))));
        when(snapshots.contents(RELEASE, "FORM")).thenReturn(List.of(Map.of(
                "formId", 7, "tableBindings", List.of(Map.of("bindingId", 501, "tableName", "item_table")))));
    }

    @Test
    void canonicalStoreKeyResolvesConfiguredPrimaryKeyInDeclaredOrder() {
        assertThat(ChangeHistoryDesignerPrimaryKeyLookup.resolve(snapshots, RELEASE, "dw:item_table"))
                .containsExactly("item_id", "line_no");
    }

    @Test
    void historyTableNameResolvesConfiguredPrimaryKey() {
        assertThat(ChangeHistoryDesignerPrimaryKeyLookup.resolve(snapshots, RELEASE, "item_table"))
                .containsExactly("item_id", "line_no");
    }

    @Test
    void historyNormalizedCanonicalKeyResolvesConfiguredPrimaryKey() {
        assertThat(ChangeHistoryDesignerPrimaryKeyLookup.tableNameFromHistoryNormalizedCanonicalKey(
                "dw_item_table")).isEqualTo("item_table");
        assertThat(ChangeHistoryDesignerPrimaryKeyLookup.tableNameFromHistoryNormalizedCanonicalKey(
                "rt_item_table")).isEqualTo("item_table");
        assertThat(ChangeHistoryDesignerPrimaryKeyLookup.tableNameFromHistoryNormalizedCanonicalKey(
                "item_table")).isNull();
        assertThat(ChangeHistoryDesignerPrimaryKeyLookup.resolve(snapshots, RELEASE, "dw_item_table"))
                .containsExactly("item_id", "line_no");
    }

    @Test
    void bindingIdSliceResolvesThroughTheReleasesFormBindings() {
        assertThat(ChangeHistoryDesignerPrimaryKeyLookup.resolve(snapshots, RELEASE, "501"))
                .containsExactly("item_id", "line_no");
    }

    @Test
    void unknownSliceReturnsEmptyRatherThanGuessingAColumn() {
        assertThat(ChangeHistoryDesignerPrimaryKeyLookup.resolve(snapshots, RELEASE, "no_such_table")).isEmpty();
        assertThat(ChangeHistoryDesignerPrimaryKeyLookup.resolve(snapshots, RELEASE, "")).isEmpty();
    }

    @Test
    void requestWithoutAPinnedReleaseIsRefusedRatherThanReadFromTheDesigner() {
        assertThatThrownBy(() -> ChangeHistoryDesignerPrimaryKeyLookup.resolve(snapshots, null, "dw:item_table"))
                .isInstanceOf(FunctionUnitVersionException.class);
    }
}
