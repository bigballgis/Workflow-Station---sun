package com.workflow.assignment;

import com.platform.common.assignment.AssignmentExclusionPolicy;
import com.platform.common.assignment.AssignmentIdentitySnapshot;
import org.flowable.task.api.Task;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Prepares current source qualification without holding the business case lock across HTTP reads. */
public interface AssignmentQualificationComponent {
    Prepared prepare(Request request);

    record Request(Task task, AssignmentSourceSpec source, AssignmentExclusionPolicy policy,
            AssignmentTaskStateRepository.CaseContext context, List<AssignmentIdentitySnapshot.Reference> additionalReferences,
            java.util.Set<String> excluded, boolean retainBackupHolder) { }
    record Prepared(long authorityRevision, Instant validUntil, List<String> primary, List<String> backup,
            boolean requiresClaim, Map<String,AssignmentIdentitySnapshot.User> identities, List<String> primaryIdentities,Instant nextEvaluationAt) {
        public Prepared(long authorityRevision,Instant validUntil,List<String> primary,List<String> backup,
                boolean requiresClaim,Map<String,AssignmentIdentitySnapshot.User> identities,List<String> primaryIdentities) {
            this(authorityRevision,validUntil,primary,backup,requiresClaim,identities,primaryIdentities,validUntil);
        }
        public Prepared(long authorityRevision, Instant validUntil, List<String> primary, List<String> backup,
                boolean requiresClaim, Map<String,AssignmentIdentitySnapshot.User> identities) {
            this(authorityRevision, validUntil, primary, backup, requiresClaim, identities, primary);
        }
    }
}
