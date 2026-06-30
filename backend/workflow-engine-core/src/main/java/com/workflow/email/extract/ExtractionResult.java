package com.workflow.email.extract;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Output of {@link EmailFieldExtractor}: main-table {@code fields}, sub-table rows keyed by
 * bindingId, and the list of required targets that produced no value.
 *
 * <p>{@code missingRequired} is the runtime quality gate: when non-empty, the caller must NOT
 * auto-create the main record and should instead route to a manual-review user task.
 */
public final class ExtractionResult {

    private final Map<String, Object> fields = new LinkedHashMap<>();
    private final Map<String, List<Map<String, Object>>> subTables = new LinkedHashMap<>();
    private final List<String> missingRequired = new ArrayList<>();

    public Map<String, Object> getFields() {
        return fields;
    }

    public Map<String, List<Map<String, Object>>> getSubTables() {
        return subTables;
    }

    public List<String> getMissingRequired() {
        return missingRequired;
    }

    public boolean hasMissingRequired() {
        return !missingRequired.isEmpty();
    }
}
