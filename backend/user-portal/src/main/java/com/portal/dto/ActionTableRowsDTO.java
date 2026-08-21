package com.portal.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Read-only row data for one ACTION-type table binding (e.g. FORM_POPUP "Meeting Remark"),
 * scoped to the current request via {@code foreign_key_field}. See
 * {@link com.portal.component.ActionTableReadComponent}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActionTableRowsDTO {
    private Long bindingId;
    private List<Map<String, Object>> rows;
}
