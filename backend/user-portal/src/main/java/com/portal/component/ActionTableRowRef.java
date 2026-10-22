package com.portal.component;

/**
 * One FORM_POPUP submission row in {@code dw_table_data_rows}. Versioned writes key the row by
 * {@code runtime_table_id} + {@code process_instance_id} + {@code row_id}, not designer {@code table_id}.
 */
public record ActionTableRowRef(String rowId) {}
