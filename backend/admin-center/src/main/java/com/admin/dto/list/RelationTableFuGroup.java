package com.admin.dto.list;

/**
 * Left-rail Function Unit group on the Table Structure page. {@code key} is the Function Unit
 * id, or {@code __common__} for tables with no link. Count is tables in that group (a table
 * linked to two units appears in both).
 */
public record RelationTableFuGroup(String key, String label, long count) {
}
