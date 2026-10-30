package com.workflow.assignment;

import com.workflow.dto.request.StartProcessRequest;
import org.flowable.engine.runtime.ProcessInstance;

public interface AssignmentRootStarter {
    boolean supports(String definition);
    ProcessInstance start(StartProcessRequest request, String definition, java.util.function.Consumer<ProcessInstance> created);
}
