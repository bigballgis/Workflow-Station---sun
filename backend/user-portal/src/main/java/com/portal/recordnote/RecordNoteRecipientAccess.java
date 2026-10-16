package com.portal.recordnote;

import com.portal.component.ProcessApplicationQueryComponent;
import com.portal.dto.ProcessInstanceInfo;
import com.portal.repository.ProcessInstanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/** Reuses the same read permission as Record Note opening, without hydrating or rewriting the Request. */
@Component
@RequiredArgsConstructor
public class RecordNoteRecipientAccess {
    private final ProcessInstanceRepository requests;
    private final ProcessApplicationQueryComponent access;
    private final RecordNoteParticipantRepository participants;
    // Only read probes run here; all notification writes stay on the transaction's calling thread.
    private final java.util.concurrent.ExecutorService permissionReads=new java.util.concurrent.ThreadPoolExecutor(
            4,4,0,java.util.concurrent.TimeUnit.MILLISECONDS,new java.util.concurrent.ArrayBlockingQueue<>(32),
            runnable -> { Thread thread=new Thread(runnable,"record-note-permission"); thread.setDaemon(true); return thread; });

    @jakarta.annotation.PreDestroy
    public void close() { permissionReads.shutdownNow(); }

    public Set<String> allowed(String processId,Set<String> users) {
        if(users.isEmpty()) return Set.of();
        var request=requests.findById(processId).orElseThrow(() -> new IllegalStateException("NOTE_REQUEST_UNRESOLVED"));
        var detail=ProcessInstanceInfo.builder().id(processId).functionUnitCode(request.getFunctionUnitCode())
                .startUserId(request.getStartUserId()).currentAssignee(request.getCurrentAssignee())
                .candidateUsers(request.getCandidateUsers()).variables(request.getVariables()).build();
        Set<String> allowed=new HashSet<>(participants.assignedReaders(processId,users));
        if(request.getStartUserId()!=null && users.contains(request.getStartUserId())) allowed.add(request.getStartUserId());
        var pending=new java.util.ArrayList<>(users);
        pending.removeAll(allowed);
        for(int offset=0;offset<pending.size();offset+=16) {
            var batch=pending.subList(offset,Math.min(offset+16,pending.size()));
            var probes=batch.stream().map(user -> java.util.concurrent.CompletableFuture.supplyAsync(
                    () -> access.canAuditProcessDetail(user,detail),permissionReads)).toList();
            for(int i=0;i<batch.size();i++) if(probes.get(i).join()) allowed.add(batch.get(i));
        }
        return allowed;
    }
}
