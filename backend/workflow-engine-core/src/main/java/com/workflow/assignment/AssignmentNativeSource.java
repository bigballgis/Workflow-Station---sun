package com.workflow.assignment;

import com.workflow.util.BpmnExtensionUtils;
import org.flowable.bpmn.model.UserTask;
import java.util.List;
import java.util.regex.Pattern;

/** Decode only an exact native variable reference; arbitrary EL is not a refreshable personnel source. */
public final class AssignmentNativeSource {
    private static final Pattern VARIABLE = Pattern.compile("[#$]\\{\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*}");
    private AssignmentNativeSource() { }

    public static AssignmentSourceSpec dynamic(UserTask task) {
        String configured=BpmnExtensionUtils.getExtensionProperty(task,"assigneeType");
        if (configured!=null) {
            if (com.workflow.enums.AssigneeType.fromCode(configured)==null) {
                throw new IllegalArgumentException("Protected task requires a supported explicit platform assignment source");
            }
            return null;
        }
        boolean dynamicCandidates=task.getCandidateUsers().stream().anyMatch(AssignmentNativeSource::expression)
                || task.getCandidateGroups().stream().anyMatch(AssignmentNativeSource::expression);
        if (dynamicCandidates) throw new IllegalArgumentException("Native dynamic candidates require an explicit platform assignment source");
        String assignee=task.getAssignee();
        if (!expression(assignee)) return null;
        var matcher=VARIABLE.matcher(assignee);
        if (!matcher.matches() || !task.getCandidateUsers().isEmpty() || !task.getCandidateGroups().isEmpty()) {
            throw new IllegalArgumentException("Native assignment expression requires one explicit refreshable platform source");
        }
        String variable=matcher.group(1);
        return "initiator".equals(variable)
                ? AssignmentSourceSpec.resolver(new AssignmentSourceSpec.Resolver("INITIATOR",List.of(),null,null,null,null))
                : AssignmentSourceSpec.variable(variable);
    }

    private static boolean expression(String value) {
        return value!=null && (value.contains("${") || value.contains("#{"));
    }
}
