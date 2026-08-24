package com.admin.dto.list;

/**
 * One group of a whole list result set. {@code count} is {@code COUNT(*) GROUP BY} over the same
 * predicate the page was drawn from — never the number of rows on the current page.
 */
public record AdminListGroup(String label, long count) {

    public AdminListGroup {
        if (count < 0) {
            throw new IllegalArgumentException("group count cannot be negative");
        }
    }
}
