package com.platform.common.relationtable;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Structural equality check for a Relation Table's importable metadata (display name, description,
 * field list), used by both admin-center's {@code RelationTableStructureImporter} and
 * developer-workstation's {@code RelationTableStructurePortability} to decide whether an import
 * actually changes anything before flipping status to {@code UPDATED} and bumping the version.
 *
 * <p>Both callers normalize their "current" and "incoming" state into the same
 * {@code Map<String, Object>} shape (one map per field, keyed the same way the export/import
 * payload already uses: fieldName, dataType, length, precision, scale, nullable, isPrimaryKey,
 * defaultValue, displayName, isForeignKey, refTableName, refPrimaryKeyFields, pkGenerationJson,
 * fkDisplayMode, sortOrder, isComputed, computedField) so a single comparison rule applies to both.
 */
public final class RelationTableStructureDiff {

    /** Field metadata keys that participate in the equality check, compared via Object#equals. */
    private static final List<String> FIELD_KEYS = List.of(
            "fieldName", "dataType", "length", "precision", "scale", "nullable", "isPrimaryKey",
            "defaultValue", "displayName", "isForeignKey", "refTableName", "refPrimaryKeyFields",
            "pkGenerationJson", "fkDisplayMode", "isComputed", "computedField");

    private RelationTableStructureDiff() {
    }

    /**
     * @param currentDisplayName  display name currently stored
     * @param currentDescription  description currently stored
     * @param currentFields       current fields, one map per field (see key list above)
     * @param incomingDisplayName display name from the import payload
     * @param incomingDescription description from the import payload
     * @param incomingFields      incoming fields, one map per field (same shape)
     * @return true if nothing importable actually changed
     */
    public static boolean unchanged(
            String currentDisplayName, String currentDescription, List<Map<String, Object>> currentFields,
            String incomingDisplayName, String incomingDescription, List<Map<String, Object>> incomingFields) {
        if (!Objects.equals(normalize(currentDisplayName), normalize(incomingDisplayName))) {
            return false;
        }
        if (!Objects.equals(normalize(currentDescription), normalize(incomingDescription))) {
            return false;
        }
        return fieldsEqual(currentFields, incomingFields);
    }

    private static boolean fieldsEqual(List<Map<String, Object>> a, List<Map<String, Object>> b) {
        List<Map<String, Object>> left = sortedByFieldName(a);
        List<Map<String, Object>> right = sortedByFieldName(b);
        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            if (!fieldEqual(left.get(i), right.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean fieldEqual(Map<String, Object> a, Map<String, Object> b) {
        for (String key : FIELD_KEYS) {
            if (!Objects.equals(normalize(a.get(key)), normalize(b.get(key)))) {
                return false;
            }
        }
        return true;
    }

    private static List<Map<String, Object>> sortedByFieldName(List<Map<String, Object>> fields) {
        return fields.stream()
                .sorted(Comparator.comparing(f -> String.valueOf(f.get("fieldName"))))
                .toList();
    }

    /** Treats null and blank string as equivalent so absent-vs-empty payload differences don't false-positive. */
    private static Object normalize(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof String s) {
            return s.isBlank() ? null : s;
        }
        return v;
    }
}
