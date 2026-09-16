package com.portal.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Per-binding write intent carried next to {@code __subTables__}, not inside it.
 *
 * <p>Storage stays one canonical key per table. This object tells the server which binding
 * claimed which rows so a Meeting-attachment binding cannot update a Participant-only row.
 * Must not be placed in {@code formData} — approve/complete copies that map into process
 * variables.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubTableBindingScope {
    private String bindingId;
    private String storeKey;
    private List<Map<String, Object>> rowKeys;
    private Boolean emptied;
}
