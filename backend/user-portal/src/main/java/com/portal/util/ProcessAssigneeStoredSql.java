package com.portal.util;

/**
 * Current Assignee list cell: claimed user and candidate pool as one
 * comma-separated identity string. Pool / BU:Role rows live in
 * {@code candidate_users} with {@code current_assignee} null.
 */
public final class ProcessAssigneeStoredSql {

    private ProcessAssigneeStoredSql() {
    }

    public static final String EXPRESSION =
            "concat_ws(',', NULLIF(btrim(pi.current_assignee), ''), NULLIF(btrim(pi.candidate_users), ''))";
}
