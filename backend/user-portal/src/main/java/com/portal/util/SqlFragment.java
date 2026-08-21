package com.portal.util;

import java.util.ArrayList;
import java.util.List;

/**
 * A compiled WHERE fragment and the values it binds, in the order its placeholders appear.
 *
 * <p>The two travel together because they are only correct together: appending the text without
 * its parameters, or in a different order than the surrounding clause, silently binds one
 * condition's value to another's placeholder.
 */
public record SqlFragment(String sql, List<Object> params) {

    /** Constrains nothing. */
    public static final SqlFragment EMPTY = new SqlFragment("", List.of());

    public SqlFragment {
        params = List.copyOf(params);
    }

    public boolean isEmpty() {
        return sql.isEmpty();
    }

    /** Appends another fragment, preserving placeholder order. */
    public SqlFragment and(SqlFragment other) {
        if (other == null || other.isEmpty()) {
            return this;
        }
        if (isEmpty()) {
            return other;
        }
        List<Object> combined = new ArrayList<>(params);
        combined.addAll(other.params());
        return new SqlFragment(sql + other.sql(), combined);
    }
}
