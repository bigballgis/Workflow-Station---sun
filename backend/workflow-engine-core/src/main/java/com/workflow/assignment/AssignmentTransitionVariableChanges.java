package com.workflow.assignment;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.platform.common.assignment.AssignmentFailure;
import com.platform.common.assignment.AssignmentVariablePatch;
import com.platform.common.functionunit.RuntimeVersionContext;
import com.platform.common.util.JsonUtils;
import java.util.List;
import java.util.Map;

/** Carries only values changed by synchronous execution, preserving newer, unchanged Portal business values. */
final class AssignmentTransitionVariableChanges {
    private AssignmentTransitionVariableChanges() { }
    static void apply(ObjectNode engineBefore,Map<String,Object> engineAfter,ObjectNode portal) {
        ObjectNode before=engineBefore.deepCopy();
        ObjectNode after=JsonUtils.getObjectMapper().valueToTree(engineAfter);
        for (String key:RuntimeVersionContext.metadataKeys()) {
            if (!java.util.Objects.equals(before.get(key),after.get(key))) throw changed();
        }
        // Per-row call outputs use their declared mappings and complete frozen row identities instead.
        before.remove("__subTables__"); after.remove("__subTables__");
        for (var entry:AssignmentVariablePatch.between(before,after)) write(portal,entry);
    }
    private static void write(ObjectNode target,AssignmentVariablePatch.Entry entry) {
        List<String> path=entry.path();
        for (int index=0;index<path.size()-1;index++) {
            var next=target.get(path.get(index));
            if (next==null) {
                if (!entry.resultPresent()) return;
                next=target.putObject(path.get(index));
            }
            if (!(next instanceof ObjectNode object)) throw changed();
            target=object;
        }
        String field=path.get(path.size()-1);
        if (entry.resultPresent()) target.set(field,entry.resultValue().deepCopy());
        else target.remove(field);
    }
    private static AssignmentFailure changed() { return new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_STATE_CHANGED); }
}
