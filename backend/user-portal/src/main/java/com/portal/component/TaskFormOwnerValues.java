package com.portal.component;

import com.portal.entity.ProcessInstance;
import java.util.Map;

/** Owner values use the freshly reloaded instance and the trusted task row scope inside the fenced save. */
final class TaskFormOwnerValues {
    private TaskFormOwnerValues() { }
    static void apply(OwnerFieldComponent owner, Context context, Map<String,Object> updated) {
        SubTableBatchTaskWriteContext.applyCurrentItem(context.writeContext(), updated,
                context.previous().containsKey(SubTableWriteIsolation.BATCH_RUNTIME_FIELD));
        var instance = context.instance();
        owner.applyOnSubmit(instance.getFunctionUnitCode(), new OwnerFieldComponent.OwnerWriteContext(context.actor(),
                instance.getStartUserId(), instance.getCurrentAssignee(), instance.getCandidateUsers(), context.previous(), context.outer()), updated);
        OwnerFieldComponent.stripProcessWideCurrentItem(updated);
    }
    record Context(ProcessInstance instance, String actor, Map<String,Object> previous, Map<String,Object> writeContext,
            MiOuterStepResolver.OuterLookup outer) { }
}
