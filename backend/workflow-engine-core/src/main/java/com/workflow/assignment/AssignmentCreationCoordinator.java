package com.workflow.assignment;

import org.flowable.task.api.Task;

public interface AssignmentCreationCoordinator {
    void created(Task task);
}
