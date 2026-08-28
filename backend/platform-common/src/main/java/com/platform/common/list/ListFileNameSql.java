package com.platform.common.list;

import java.util.List;

/**
 * FILE-cell filenames for list filter, sort, and keyword search.
 * The extraction order is the same contract as
 * {@code frontend/shared/src/list/fileNames.ts} ({@code extractFileLinks}).
 */
public final class ListFileNameSql {

    private static final String UPLOAD_URL = "'/(api/v[0-9]+/)?upload/files/'";

    private ListFileNameSql() {
    }

    /**
     * JSON member refs are stored as {@code col->>'field'} (text). FILE values are objects
     * or arrays, so the predicate must read {@code col->'field'} (jsonb).
     */
    public static String jsonbOf(String textRef) {
        if (textRef == null || textRef.isBlank()) {
            throw new IllegalArgumentException("FILE column expression is required");
        }
        if (textRef.contains("->>'")) {
            return textRef.replace("->>'", "->'");
        }
        return "to_jsonb(" + textRef + ")";
    }

    public static String emptinessPredicate(String textRef, boolean wantEmpty) {
        String exists = "EXISTS (SELECT 1 FROM " + namesFrom(textRef) + " file_names)";
        return wantEmpty ? "(NOT " + exists + ")" : exists;
    }

    public static String namePredicate(String textRef, String operator, String value,
                                       List<Object> outParams) {
        String names = namesFrom(textRef);
        return switch (operator) {
            case "contains" -> existsIlike(names, outParams, "%" + ListFilterSql.escapeLike(value) + "%");
            case "notContains" -> "(NOT " + existsIlike(names, outParams,
                    "%" + ListFilterSql.escapeLike(value) + "%") + ")";
            case "startsWith" -> existsIlike(names, outParams, ListFilterSql.escapeLike(value) + "%");
            case "endsWith" -> existsIlike(names, outParams, "%" + ListFilterSql.escapeLike(value));
            case "eq" -> {
                outParams.add(value);
                yield "EXISTS (SELECT 1 FROM " + names + " file_names WHERE file_names.n = ?)";
            }
            case "ne" -> {
                outParams.add(value);
                yield "(NOT EXISTS (SELECT 1 FROM " + names + " file_names WHERE file_names.n = ?))";
            }
            default -> throw new IllegalArgumentException(
                    "Operator " + operator + " is not supported on a FILE column");
        };
    }

    public static String sortExpression(String textRef) {
        return "(SELECT min(file_names.n) FROM " + namesFrom(textRef) + " file_names)";
    }

    /** One bind of {@code ILIKE ? ESCAPE '\'} against any extracted filename. */
    public static String anyNameIlike(String textRef) {
        return "EXISTS (SELECT 1 FROM " + namesFrom(textRef)
                + " file_names WHERE file_names.n" + ListFilterSql.ILIKE + ")";
    }

    private static String existsIlike(String names, List<Object> outParams, String pattern) {
        outParams.add(pattern);
        return "EXISTS (SELECT 1 FROM " + names + " file_names WHERE file_names.n"
                + ListFilterSql.ILIKE + ")";
    }

    /**
     * One-column subquery aliased by callers as {@code file_names(n)}.
     * Bare strings participate only when they are upload URLs; objects with a URL field
     * always participate (same as {@code extractFileLinks}).
     */
    static String namesFrom(String textRef) {
        String cell = jsonbOf(textRef);
        return "(SELECT n.filename AS n FROM jsonb_array_elements(" + asArray(cell) + ") AS elem"
                + " CROSS JOIN LATERAL (" + sourceFields() + ") src"
                + " CROSS JOIN LATERAL (" + filenameFromSource() + ") n"
                + " WHERE n.filename IS NOT NULL AND n.filename <> '')";
    }

    private static String asArray(String jsonb) {
        return "CASE WHEN " + jsonb + " IS NULL OR " + jsonb + " = 'null'::jsonb THEN '[]'::jsonb"
                + " WHEN jsonb_typeof(" + jsonb + ") = 'array' THEN " + jsonb
                + " ELSE jsonb_build_array(" + jsonb + ") END";
    }

    private static String sourceFields() {
        String url = "COALESCE("
                + "NULLIF(BTRIM(elem->>'url'), ''),"
                + " NULLIF(BTRIM(elem->>'fileUrl'), ''),"
                + " NULLIF(BTRIM(elem->>'path'), ''),"
                + " NULLIF(BTRIM(elem->>'downloadUrl'), ''))";
        return "SELECT CASE"
                + " WHEN jsonb_typeof(elem) = 'object' THEN " + url
                + " WHEN jsonb_typeof(elem) = 'string' THEN NULLIF(BTRIM(elem #>> '{}'), '')"
                + " ELSE NULL END AS url,"
                + " CASE WHEN jsonb_typeof(elem) = 'object'"
                + " THEN NULLIF(BTRIM(elem->>'name'), '') ELSE NULL END AS object_name,"
                + " CASE WHEN jsonb_typeof(elem) = 'string'"
                + " THEN (elem #>> '{}') ~ " + UPLOAD_URL
                + " WHEN jsonb_typeof(elem) = 'object' THEN true"
                + " ELSE false END AS eligible";
    }

    private static String filenameFromSource() {
        String lastSeg = "regexp_replace(split_part(split_part(src.url, '?', 1), '#', 1), '^.*/', '')";
        String fromPath = "CASE WHEN NULLIF(" + lastSeg + ", '') IS NOT NULL"
                + " THEN " + percentDecode(lastSeg)
                + " ELSE " + percentDecode("src.url") + " END";
        return "SELECT CASE WHEN (NOT src.eligible) OR src.url IS NULL THEN NULL"
                + " WHEN src.object_name IS NOT NULL THEN src.object_name"
                + " ELSE COALESCE(" + queryParamName() + ", " + fromPath + ") END AS filename";
    }

    private static String queryParamName() {
        String query = "NULLIF(split_part(split_part(src.url, '?', 2), '#', 1), '')";
        String decoded = percentDecode("q.v");
        return "(SELECT " + decoded
                + " FROM unnest(string_to_array(" + query + ", '&')) AS kv(kv)"
                + " CROSS JOIN LATERAL (SELECT split_part(kv.kv, '=', 1) AS k,"
                + " CASE WHEN strpos(kv.kv, '=') > 0"
                + " THEN substr(kv.kv, strpos(kv.kv, '=') + 1) ELSE NULL END AS v) q"
                + " WHERE q.k IN ('originalName', 'fileName', 'filename', 'name')"
                + " AND NULLIF(BTRIM(q.v), '') IS NOT NULL"
                + " ORDER BY CASE q.k WHEN 'originalName' THEN 1 WHEN 'fileName' THEN 2"
                + " WHEN 'filename' THEN 3 ELSE 4 END"
                + " LIMIT 1)";
    }

    /**
     * application/x-www-form-urlencoded ({@code +} → space) then {@code %XX} UTF-8,
     * matching {@code URLSearchParams} + {@code decodeURIComponent}.
     */
    static String percentDecode(String expr) {
        return "COALESCE(convert_from(decode(regexp_replace(replace((" + expr
                + "), '+', ' '), '%([0-9A-Fa-f]{2})', E'\\\\x\\\\1', 'g'), 'escape'), 'UTF8'), ("
                + expr + "))";
    }
}
