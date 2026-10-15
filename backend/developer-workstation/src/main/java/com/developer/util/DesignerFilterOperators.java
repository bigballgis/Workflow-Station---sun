package com.developer.util;

import java.util.Set;

/** Operators shared by Main Table View filters and Action post-email send conditions. */
public final class DesignerFilterOperators {

    public static final Set<String> ALL = Set.of(
            "eq", "ne", "contains", "notContains", "startsWith", "notStartsWith",
            "endsWith", "notEndsWith", "isNull", "isNotNull", "gt", "lt");

    public static final Set<String> SYSTEM_FILTER_FIELDS = Set.of(
            "process_status", "start_time", "initiator", "current_step");

    public static final int MAX_NEST_DEPTH = 3;
    public static final int MAX_POST_EMAIL_RULES = 20;

    private DesignerFilterOperators() {}
}
