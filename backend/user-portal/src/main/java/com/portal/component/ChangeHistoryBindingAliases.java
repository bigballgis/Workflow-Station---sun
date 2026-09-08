package com.portal.component;

import java.util.List;
import java.util.Map;

record ChangeHistoryBindingAliases(
        Map<String, String> aliasToBinding,
        Map<String, String> bindingToHistoryName,
        Map<String, Integer> aliasPriorities,
        Map<String, List<String>> bindingToPrimaryKeyFields) {

    ChangeHistoryBindingAliases {
        if (bindingToPrimaryKeyFields == null) {
            bindingToPrimaryKeyFields = Map.of();
        }
    }

    List<String> primaryKeyFields(String bindingId) {
        if (bindingId == null || bindingToPrimaryKeyFields.isEmpty()) {
            return List.of();
        }
        List<String> fields = bindingToPrimaryKeyFields.get(bindingId);
        return fields == null ? List.of() : fields;
    }
}
