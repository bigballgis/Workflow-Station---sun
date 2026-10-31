package com.workflow.assignment;

import com.workflow.service.TaskAssigneeResolver;
import org.flowable.task.api.Task;

/** Reuses the original assignment resolver against current source data. */
public interface AssignmentSourceResolver {
    Resolution resolve(Task task, AssignmentSourceSpec source,AssignmentTaskStateRepository.CaseContext context);
    record Resolution(TaskAssigneeResolver.ResolveResult assignment, String singleBusinessUnitCode,
            java.util.Map<String,com.platform.common.assignment.AssignmentIdentitySnapshot.Kind> identityKinds) {
        public Resolution(TaskAssigneeResolver.ResolveResult assignment, String singleBusinessUnitCode) {
            this(assignment, singleBusinessUnitCode, java.util.Map.of());
        }
        public static Resolution canonical(TaskAssigneeResolver.ResolveResult assignment, String bu) {
            var kinds = new java.util.LinkedHashMap<String,com.platform.common.assignment.AssignmentIdentitySnapshot.Kind>();
            if (assignment.getAssignee() != null && !assignment.getAssignee().isBlank()) {
                kinds.put(assignment.getAssignee(), com.platform.common.assignment.AssignmentIdentitySnapshot.Kind.ID);
            }
            if (assignment.getCandidateUsers() != null) assignment.getCandidateUsers().forEach(id ->
                    kinds.put(id, com.platform.common.assignment.AssignmentIdentitySnapshot.Kind.ID));
            return new Resolution(assignment, bu, java.util.Map.copyOf(kinds));
        }
    }
}
