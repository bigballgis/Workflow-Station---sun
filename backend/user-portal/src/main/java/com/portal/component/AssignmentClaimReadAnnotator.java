package com.portal.component;

import com.platform.common.assignment.AssignmentFailure;
import com.platform.common.assignment.AssignmentTaskReadSnapshot;
import com.platform.common.assignment.AssignmentTaskReadSnapshot.*;
import com.portal.client.AssignmentTaskReadClient;
import com.portal.dto.TaskInfo;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.*;

/** Reapplies an extra veto on every response, including cached Mine rows, before pagination. */
@Component
public class AssignmentClaimReadAnnotator {
    private final AssignmentTaskReadClient client;
    public AssignmentClaimReadAnnotator(AssignmentTaskReadClient client) { this.client=client; }
    public void annotate(List<TaskInfo> tasks,String actor) {
        var byId=new LinkedHashMap<String,List<TaskInfo>>();
        for (var task:tasks) {
            if (task.getTaskId()==null || task.getTaskId().isBlank()) { task.setClaimable(false); continue; }
            byId.computeIfAbsent(task.getTaskId(),key->new ArrayList<>()).add(task);
        }
        var ids=new ArrayList<>(byId.keySet());
        var decisions=new HashMap<String,Entry>();
        for (int offset=0;offset<ids.size();offset+=1000) {
            var batch=ids.subList(offset,Math.min(offset+1000,ids.size()));
            Map<String,Entry> entries;
            try { entries=validate(client.read(List.copyOf(batch),actor),batch,actor); }
            catch (AssignmentFailure unavailable) {
                // FALLBACK(ux): unavailable claim information disables claiming; visibility is unchanged.
                State state=AssignmentFailure.Reason.ASSIGNMENT_DEPENDENCY_UNAVAILABLE.name().equals(unavailable.getDetails().get("reason"))
                        ?State.DEPENDENCY_UNAVAILABLE:State.CONTEXT_UNAVAILABLE;
                batch.forEach(id->decisions.put(id,new Entry(id,null,state,new Claim(false,null,null),null)));
                continue;
            }
            decisions.putAll(entries);
        }
        var now=Instant.now();
        byId.forEach((id,rows)->rows.forEach(task->{
            task.setAssignmentClaimAccess(decisions.get(id));
            task.setClaimable(task.isClaimable() && allows(decisions.get(id),now));
        }));
    }
    void enforce(TaskInfo task) {
        task.setClaimable(task.isClaimable() && allows(task.getAssignmentClaimAccess(),Instant.now()));
    }
    private Map<String,Entry> validate(AssignmentTaskReadSnapshot snapshot,List<String> ids,String actor) {
        if (snapshot==null || snapshot.contractVersion()!=1 || !Objects.equals(actor,snapshot.actor())
                || snapshot.observedAt()==null || snapshot.authorityRevision()<0 || snapshot.tasks()==null) throw unavailable();
        var entries=new HashMap<String,Entry>();
        for (var entry:snapshot.tasks()) {
            if (entry==null || !ids.contains(entry.taskId()) || entry.state()==null || entry.claim()==null || entry.stamp()==null
                    || entry.stamp().authorityRevision()!=snapshot.authorityRevision() || entries.putIfAbsent(entry.taskId(),entry)!=null) throw unavailable();
        }
        if (entries.size()!=ids.size()) throw unavailable();
        return entries;
    }
    private boolean allows(Entry entry,Instant now) {
        if (entry==null) return false;
        if (!entry.claim().exclusionAllowsClaim()) return false;
        if (entry.state()==State.LEGACY) return Boolean.FALSE.equals(entry.tracked());
        return entry.state()==State.CURRENT && Boolean.TRUE.equals(entry.tracked()) && entry.stamp().caseRevision()!=null
                && entry.stamp().validUntil()!=null && entry.stamp().validUntil().isAfter(now);
    }
    private AssignmentFailure unavailable() { return new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE); }
}
