package com.portal.util;

/** Local server-owned metadata, excluded from ordinary form/variable response maps. */
public final class SubTableBatchMetadata {
    public static final String RUNTIME_FIELD = "__subTableBatchRuntime__";
    public static final String DELETED_ROWS_FIELD = "__subTableBatchDeletedRows__";
    private SubTableBatchMetadata() { }
}
