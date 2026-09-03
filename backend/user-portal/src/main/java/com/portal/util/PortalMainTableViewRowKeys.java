package com.portal.util;

import java.util.List;

/**
 * Exact lookup for a Main Table View {@code rowKey} as the list page already issued it.
 *
 * <p>MAIN keys are the process instance id. SUB keys are {@code instanceId|field=value},
 * matching {@code SubTableRowIdentity.identityOf}. The SQL identity column stores the value
 * only, so this strips the {@code field=} prefix before comparing.
 *
 * <p>Must run on the page query (the outer {@code FROM (...) pi}), where {@code pi.row_identity}
 * is the expanded row's identity — not inside the LATERAL expansion.
 */
public final class PortalMainTableViewRowKeys {

    private PortalMainTableViewRowKeys() {
    }

    public static SqlFragment exactMatch(String rowKey, boolean subView) {
        if (rowKey == null || rowKey.isBlank()) {
            return SqlFragment.EMPTY;
        }
        int pipe = rowKey.indexOf('|');
        if (!subView) {
            String instanceId = (pipe < 0 ? rowKey : rowKey.substring(0, pipe)).trim();
            if (instanceId.isEmpty()) {
                throw new IllegalArgumentException("rowKey must include a process instance id");
            }
            return new SqlFragment(" AND pi.id = ?", List.of(instanceId));
        }
        if (pipe < 0) {
            throw new IllegalArgumentException("A sub-table row key must include the row identity");
        }
        String instanceId = rowKey.substring(0, pipe).trim();
        String identity = rowKey.substring(pipe + 1).trim();
        if (instanceId.isEmpty() || identity.isEmpty()) {
            throw new IllegalArgumentException("A sub-table row key must include the row identity");
        }
        return new SqlFragment(
                " AND pi.id = ? AND pi.row_identity = ?",
                List.of(instanceId, identityValue(identity)));
    }

    private static String identityValue(String identity) {
        int eq = identity.indexOf('=');
        if (eq < 1 || eq == identity.length() - 1) {
            throw new IllegalArgumentException("A sub-table row key identity must be field=value");
        }
        String value = identity.substring(eq + 1).trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("A sub-table row key identity must be field=value");
        }
        return value;
    }
}
