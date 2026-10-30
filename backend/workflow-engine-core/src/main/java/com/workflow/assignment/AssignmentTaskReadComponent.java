package com.workflow.assignment;

import com.platform.common.assignment.*;
import com.platform.common.assignment.AssignmentTaskReadSnapshot.*;
import org.springframework.stereotype.Component;
import java.util.*;

/** Batch projection read is an extra claim veto, never a reusable business-write authorization. */
@Component
public class AssignmentTaskReadComponent {
    private final AssignmentTaskReadContexts contexts;
    private final AssignmentReadContextClassifier classifier;
    private final AssignmentTaskReadProjectionRepository projections;
    private final AssignmentIdentityClient identities;
    private final AssignmentDefinitionPolicy definitions;
    public AssignmentTaskReadComponent(AssignmentTaskReadContexts contexts,AssignmentReadContextClassifier classifier,
            AssignmentTaskReadProjectionRepository projections,AssignmentIdentityClient identities,AssignmentDefinitionPolicy definitions) {
        this.contexts=contexts; this.classifier=classifier; this.projections=projections; this.identities=identities; this.definitions=definitions;
    }
    public AssignmentTaskReadSnapshot read(Request request) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE);
        }
        var actual=contexts.read(request.taskIds()); var kinds=classifier.classify(actual);
        AssignmentIdentitySnapshot actor=null;
        if (kinds.containsValue(AssignmentReadContextClassifier.Kind.TRACKED)) {
            try { actor=identities.resolve(List.of(new AssignmentIdentitySnapshot.Reference(request.actor(),AssignmentIdentitySnapshot.Kind.ID))); }
            catch (AssignmentFailure unavailable) {
                // FALLBACK(ux): only the informational read becomes unavailable; legacy authorization is never inferred.
                actor=null;
            }
        }
        var snapshot=projections.read(request.taskIds(),request.actor());
        var entries=new ArrayList<Entry>();
        for (String id:request.taskIds()) entries.add(entry(id,request.actor(),actual.get(id),kinds.get(id),new Decision(snapshot,actor)));
        return new AssignmentTaskReadSnapshot(1,request.actor(),snapshot.authority(),snapshot.observedAt(),List.copyOf(entries));
    }
    private Entry entry(String id,String actor,AssignmentTaskReadContexts.Context context,AssignmentReadContextClassifier.Kind kind,Decision decision) {
        var snapshot=decision.projection(); var row=snapshot.rows().get(id);
        if (context==null || row==null) return result(id,null,State.NOT_ACTIVE,null,snapshot);
        var position=context.subject().position();
        if (!Objects.equals(position.instance(),row.live().instance()) || !Objects.equals(position.definition(),row.live().definition())
                || !Objects.equals(position.node(),row.live().node())) return result(id,null,State.CONTEXT_UNAVAILABLE,row,snapshot);
        if (kind==AssignmentReadContextClassifier.Kind.LEGACY && !row.binding().statePresent() && !row.binding().instanceRegistered()) {
            return result(id,false,State.LEGACY,row,snapshot);
        }
        if (kind!=AssignmentReadContextClassifier.Kind.TRACKED || !row.binding().bound()) return result(id,null,State.CONTEXT_UNAVAILABLE,row,snapshot);
        State state=state(actor,row,decision);
        return result(id,true,state,row,snapshot);
    }
    private State state(String actor,AssignmentTaskReadProjectionRepository.Row row,Decision decision) {
        var snapshot=decision.projection(); var identity=decision.actor(); var versions=row.versions();
        if (!row.binding().active()) return State.NOT_ACTIVE;
        if (row.synchronizationPending()) return State.SYNC_PENDING;
        if (identity==null || identity.users()==null) return State.DEPENDENCY_UNAVAILABLE;
        if (identity.authorityRevision()!=snapshot.authority()) return State.RECONCILING;
        var user=identity.users().get(actor);
        if (user==null || !user.active() || !actor.equals(user.id())) return State.ACTOR_INACTIVE;
        if (!row.binding().hasSource() || versions.syncedRevision()!=versions.caseRevision() || versions.authority()!=snapshot.authority()
                || versions.validUntil()==null || !versions.validUntil().isAfter(snapshot.observedAt())) return State.RECONCILING;
        return switch (row.pool().state()) {
            case "ACTIVE" -> State.CURRENT;
            case "BLOCKED_NO_ELIGIBLE_USER" -> State.BLOCKED_NO_ELIGIBLE_USER;
            default -> State.RECONCILING;
        };
    }
    private Entry result(String id,Boolean tracked,State state,AssignmentTaskReadProjectionRepository.Row row,
            AssignmentTaskReadProjectionRepository.Snapshot snapshot) {
        boolean allowed=state==State.LEGACY || state==State.CURRENT && row!=null && (row.live().holder()==null || row.live().holder().isBlank())
                && (!definitions.node(row.live().definition(),row.live().node()).enabled() || row.pool().containsActor());
        return new Entry(id,tracked,state,new Claim(allowed,row==null?null:row.pool().source(),row==null?null:row.pool().holderSource(),
                row==null?null:new Holding(row.live().holder())),
                new Stamp(row!=null && row.binding().bound()?row.versions().caseRevision():null,snapshot.authority(),row==null?null:row.versions().validUntil()));
    }
    private record Decision(AssignmentTaskReadProjectionRepository.Snapshot projection,AssignmentIdentitySnapshot actor) { }
}
