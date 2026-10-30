package com.workflow.assignment;

import org.flowable.bpmn.model.Activity;
import org.flowable.bpmn.model.CallActivity;

/** Protected MI positions require an explicit row source; arbitrary scalar participant loops have no stable slot contract. */
public final class AssignmentMiPublication {
    private AssignmentMiPublication() { }
    public static void validate(Activity activity) {
        var loop=activity.getLoopCharacteristics();
        if (loop==null) return;
        if (loop.getElementVariable()==null || loop.getElementVariable().isBlank()) throw invalid();
        if (AssignmentMiRowSource.table(activity)==null) throw invalid();
        // The first-party call compiler constructs its collection from this explicitly configured table.
        if (activity instanceof CallActivity) return;
        String collection=loop.getInputDataItem();
        if (collection==null || collection.isBlank()) collection=loop.getCollectionString();
        if (collection==null || !collection.matches("(?:[A-Za-z_][A-Za-z0-9_]*|\\$\\{[A-Za-z_][A-Za-z0-9_]*})")) throw invalid();
    }
    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Protected multi-instance activities require a configured row source, collection and item variable");
    }
}
