package com.workflow.assignment;

import com.platform.common.assignment.AssignmentFailure;
import com.platform.common.assignment.AssignmentIdentitySnapshot;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Scheduling hints only. Every selected task still passes fresh qualification and the original case transaction. */
@Component
public class AssignmentReconciliationQueue {
    private final AssignmentTaskStateRepository states;
    private final AssignmentTaskReadContexts tasks;
    private final AssignmentIdentityClient identities;
    public AssignmentReconciliationQueue(AssignmentTaskStateRepository states,AssignmentTaskReadContexts tasks,AssignmentIdentityClient identities) {
        this.states=states;this.tasks=tasks;this.identities=identities;
    }
    public List<String> due(long revision) {
        var ordered=states.dueTasks(revision,1000);
        if (ordered.isEmpty()) return ordered;
        var current=tasks.read(ordered);
        var holders=current.values().stream().map(value->value.subject().holder())
                .filter(value->value!=null && !value.isBlank()).distinct().toList();
        if (holders.isEmpty()) return ordered;
        var snapshot=identities.resolve(holders.stream().map(id->new AssignmentIdentitySnapshot.Reference(id,AssignmentIdentitySnapshot.Kind.ID)).toList());
        if (snapshot.authorityRevision()!=revision) throw new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_STATE_CHANGED);
        Set<String> lost=new HashSet<>(snapshot.missingReferences());
        snapshot.users().forEach((id,user)->{if (!user.active()) lost.add(id);});
        var prioritized=new ArrayList<>(ordered);
        // List.sort is stable: unrelated work retains the recorded fair order.
        prioritized.sort(java.util.Comparator.comparingInt(id->current.containsKey(id)
                && lost.contains(current.get(id).subject().holder())?0:1));
        return List.copyOf(prioritized);
    }
}
