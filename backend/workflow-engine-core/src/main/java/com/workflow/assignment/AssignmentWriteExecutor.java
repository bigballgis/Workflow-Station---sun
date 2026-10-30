package com.workflow.assignment;

import java.util.function.Supplier;

public interface AssignmentWriteExecutor {
    <T> T execute(AssignmentWriteGuard.Command command, Supplier<T> write);
}
