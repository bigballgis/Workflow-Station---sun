package com.platform.common.enums;

/**
 * Permission level granted to a role on a Relation Table.
 *
 * <ul>
 *   <li>{@link #READONLY} — view + export only.</li>
 *   <li>{@link #READ_WRITE} — full CRUD (add / edit / inactive) + import + export.</li>
 * </ul>
 *
 * Stored as {@code rt_table_access.permission_level}. Values are kept as plain strings on
 * the entity/DTO for JPA + JSON simplicity; use these constants and helpers to avoid typos.
 */
public final class RelationPermissionLevel {

    public static final String READONLY = "READONLY";
    public static final String READ_WRITE = "READ_WRITE";

    private RelationPermissionLevel() {
    }

    /** Normalize an arbitrary input to a valid level; falls back to READ_WRITE when blank/unknown. */
    public static String normalize(String level) {
        if (level == null) {
            return READ_WRITE;
        }
        String upper = level.trim().toUpperCase();
        return READONLY.equals(upper) ? READONLY : READ_WRITE;
    }

    /** Whether the given level grants write (add/edit/inactive/import) access. */
    public static boolean canWrite(String level) {
        return READ_WRITE.equals(normalize(level));
    }

    /** Whether the value is one of the recognized levels. */
    public static boolean isValid(String level) {
        return READONLY.equals(level) || READ_WRITE.equals(level);
    }
}
