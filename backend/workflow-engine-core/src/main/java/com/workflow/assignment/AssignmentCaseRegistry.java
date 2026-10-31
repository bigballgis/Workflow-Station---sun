package com.workflow.assignment;

import org.flowable.task.api.Task;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Engine context lifecycle; persistence and policy interpretation remain separate. */
public interface AssignmentCaseRegistry {
    Registration newRoot(String definition, String actor, String startBu);
    Registration newRoot(String definition, String actor, String startBu, String startBuId);
    Registration reopen(String sourceInstance);
    Optional<Registration> instance(String processInstance);
    Optional<Registration> registerTask(Task task);
    void ended(String process, boolean cancelled);
    record Registration(String instance, UUID caseId, UUID roundId, String definition, List<Map<String,Object>> calls) { }
}
