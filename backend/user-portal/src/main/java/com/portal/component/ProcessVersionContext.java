package com.portal.component;

import com.platform.common.functionunit.RuntimeVersionContext;
import com.portal.entity.ProcessInstance;
import java.util.Map;

/** The persisted instance association is authoritative on every write path. */
public final class ProcessVersionContext {
    private ProcessVersionContext() { }

    public static RuntimeVersionContext of(ProcessInstance instance) {
        return new RuntimeVersionContext(instance.getFunctionUnitCatalogId(), instance.getFunctionUnitCode(),
                instance.getFunctionUnitVersionLabel(), instance.getProcessDefinitionId());
    }

    public static void apply(ProcessInstance instance, Map<String, Object> variables) {
        of(instance).applyTo(variables);
    }
}
