package com.developer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Result of repairing a pasted form config against target Function Unit bindings.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormConfigPasteRepairResponse {

    private Map<String, Object> configJson;

    /** stale bindingId → target bindingId (string keys for JSON stability). */
    @Builder.Default
    private Map<String, String> bindingIdMapping = new LinkedHashMap<>();

    /** stale rt tableId → target relationTableId. */
    @Builder.Default
    private Map<String, String> relationTableIdMapping = new LinkedHashMap<>();

    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    /** True when pasted config already referenced some of the target form's binding ids. */
    private boolean mixedSource;

    private boolean applied;

    /** Table names created during createMissingTables provisioning. */
    @Builder.Default
    private List<String> createdTableNames = new ArrayList<>();
}
