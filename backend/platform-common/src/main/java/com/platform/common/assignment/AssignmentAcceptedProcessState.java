package com.platform.common.assignment;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Engine-owned projection captured after completion, inside the same transaction as the accepted receipt. */
public record AssignmentAcceptedProcessState(String instanceId,String definitionId,String processKey,String processName,
        String parentInstanceId,String callActivityId,String state,String businessKey,String name,
        Instant startTime,Instant endTime,Map<String,Object> variables,List<Task> tasks) {
    public static final String EFFECT_KEY="engineProcessStates";
    public record Task(String id,String node,String name,String assignee,List<String> candidateUsers,String instanceId,String ownerOuterStep) {
        public Task { candidateUsers=List.copyOf(candidateUsers); }
    }
    public AssignmentAcceptedProcessState {
        if (instanceId==null || definitionId==null || state==null || variables==null || tasks==null) {
            throw new IllegalArgumentException("Incomplete accepted Engine projection");
        }
        tasks=List.copyOf(tasks);
        variables=java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(variables));
    }
}
