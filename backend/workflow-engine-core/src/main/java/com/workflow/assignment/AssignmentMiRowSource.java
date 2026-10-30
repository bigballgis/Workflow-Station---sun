package com.workflow.assignment;

import com.workflow.util.BpmnExtensionUtils;
import org.flowable.bpmn.model.*;
import java.util.LinkedHashSet;

/** One configuration rule for publishing and resolving an MI scope's stable row identity. */
public final class AssignmentMiRowSource {
    private AssignmentMiRowSource() { }
    public static String table(Activity activity) {
        var tables=new LinkedHashSet<String>();
        String own=BpmnExtensionUtils.getExtensionProperty(activity,activity instanceof CallActivity?"callRowsTable":"subTableName");
        if (own!=null && !own.isBlank()) tables.add(own);
        if (activity instanceof SubProcess subprocess) for (var element:subprocess.getFlowElements()) {
            if (!(element instanceof UserTask task)) continue;
            String table=BpmnExtensionUtils.getExtensionProperty(task,"subTableName");
            if (table!=null && !table.isBlank()) tables.add(table);
        }
        if (tables.size()>1) throw new IllegalArgumentException("MI scope has conflicting configured row sources");
        return tables.isEmpty()?null:tables.iterator().next();
    }
}
