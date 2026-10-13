package com.portal.dto;

import lombok.Data;

import java.util.List;

/**
 * Transport-only declaration for a SubTable batch workspace change.
 *
 * <p>This is not a second data source: the canonical rows remain in
 * {@code formData.__subTables__}.  The declaration lets the server verify that
 * a change made by the batch UI belongs to the frozen instance capability before
 * the normal write path persists that canonical payload.</p>
 */
@Data
public class SubTableBatchChange {
    private String batchControlId;
    private String bindingId;
    /** Currently EDIT; ADD/DELETE/ASSIGN remain unavailable until their own guards land. */
    private String operation;
    private List<RowPatch> rowPatches;

    @Data
    public static class RowPatch {
        private String rowId;
        private List<String> fields;
    }
}
