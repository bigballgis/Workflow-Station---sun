package com.workflow.recordnote;

import org.flowable.bpmn.model.*;

/** Nearest real MI scope, including nested subprocesses and standalone MI user tasks. */
final class RecordNoteTaskScope {
    private RecordNoteTaskScope() {}
    static Activity activity(FlowElement element) {
        FlowElement current=element;
        while(current!=null) {
            if(current instanceof Activity a && a.getLoopCharacteristics() instanceof MultiInstanceLoopCharacteristics) return a;
            current=current.getParentContainer() instanceof SubProcess p?p:null;
        }
        return null;
    }
    static String key(FlowElement element) {
        Activity scope=activity(element);
        if(scope==null) throw new IllegalArgumentException("Notification target has no multi-instance scope");
        return scope.getId();
    }
    static String elementVariable(FlowElement element) {
        Activity scope=activity(element);
        if(scope==null) return null;
        String name=((MultiInstanceLoopCharacteristics)scope.getLoopCharacteristics()).getElementVariable();
        return name==null || name.isBlank()?"currentItem":name;
    }
}
