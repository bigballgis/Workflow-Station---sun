package com.platform.common.assignment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Exact identity resolution used by deployment/runtime, never a display-name or keyword search. */
public record AssignmentIdentitySnapshot(long authorityRevision, Map<String, User> users, Set<String> missingReferences,TimeWindow timeWindow) {
    public AssignmentIdentitySnapshot(long authorityRevision,Map<String,User> users,Set<String> missingReferences) {
        this(authorityRevision,users,missingReferences,new TimeWindow(null,java.time.Instant.now().plusSeconds(5),java.time.Instant.now()));
    }
    public record TimeWindow(java.time.Instant previousBoundary,java.time.Instant nextBoundary,java.time.Instant observedAt) { }
    public record User(String id, String username, boolean active) { }
    public enum Kind { ID, USERNAME, ID_OR_USERNAME }
    public record Reference(@NotBlank @Size(max = 255) String value, @NotNull Kind kind) { }
    public record Request(@NotEmpty @Size(max = 1000) List<@NotNull @Valid Reference> references) { }
}
