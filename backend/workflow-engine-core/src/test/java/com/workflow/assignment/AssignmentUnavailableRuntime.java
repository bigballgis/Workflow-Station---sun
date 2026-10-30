package com.workflow.assignment;

/** Simulates an older/incomplete Engine for fail-closed compatibility tests. */
final class AssignmentUnavailableRuntime extends AssignmentRuntimeSupport {
    @Override public Capability capability() { return new Capability(0,false); }
}
