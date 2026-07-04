package com.platform.common.jdbc;

import java.util.regex.Pattern;

/**
 * Allowlist validators for SQL <em>identifiers</em> (table / column / qualified names) that are
 * unavoidably concatenated into dynamic SQL in the low-code / dynamic-table paths.
 *
 * <p>SQL <em>values</em> must always use bind parameters ({@code ?}); these helpers cover only the
 * identifier positions that cannot be parameterized. They must be applied <strong>inline,
 * immediately before</strong> the identifier is concatenated into the SQL string, and the returned
 * value used, so the taint (including second-order values read back from catalog / metadata) is
 * broken and no {@code ; DROP}, whitespace, comment or sub-expression can be injected.</p>
 *
 * <p>Legitimate identifiers in this platform are plain snake_case names (e.g. {@code dw_data_12},
 * {@code id_idw}) or {@code quote_ident}-qualified names (e.g. {@code "public"."participants"}), all
 * of which pass; anything else throws {@link IllegalArgumentException}.</p>
 */
public final class SqlIdentifiers {

    /** Plain unquoted identifier: table or column name. */
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,127}");

    /**
     * Schema-qualified and/or {@code quote_ident}-quoted name, e.g. {@code participants},
     * {@code public.participants}, {@code "public"."participants"}. Restricted to the characters
     * that quote_ident output and bare identifiers can contain — no whitespace, semicolons,
     * parentheses, quotes-other-than-double, or comment markers.
     */
    private static final Pattern QUALIFIED_NAME = Pattern.compile("[A-Za-z0-9_.\"]{1,258}");

    private SqlIdentifiers() {
    }

    /**
     * Validates a plain (unquoted) table or column identifier.
     *
     * @return the same value when valid
     * @throws IllegalArgumentException if null or not a bare SQL identifier
     */
    public static String requireIdentifier(String value) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid SQL identifier");
        }
        return value;
    }

    /**
     * Validates a possibly schema-qualified / {@code quote_ident}-quoted table name.
     *
     * @return the same value when valid
     * @throws IllegalArgumentException if null or not a safe qualified name
     */
    public static String requireQualifiedName(String value) {
        if (value == null || !QUALIFIED_NAME.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid SQL qualified name");
        }
        return value;
    }
}
