package com.platform.common.assignment;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

/** Informational claim veto and projection state. Original visibility/workspace permissions and final write checks still apply. */
public record AssignmentTaskReadSnapshot(int contractVersion,String actor,long authorityRevision,Instant observedAt,List<Entry> tasks) {
    public record Request(@NotEmpty @Size(max=1000) List<@NotBlank @Size(max=64) String> taskIds,
            @NotBlank @Size(max=64) String actor) { }
    public enum State { LEGACY, CURRENT, RECONCILING, SYNC_PENDING, BLOCKED_NO_ELIGIBLE_USER, NOT_ACTIVE,
        CONTEXT_UNAVAILABLE, DEPENDENCY_UNAVAILABLE, ACTOR_INACTIVE }
    public record Entry(String taskId,Boolean tracked,State state,Claim claim,Stamp stamp) { }
    /** Additional restriction only: true does not grant an operation's existing role/BU permission. */
    public record Claim(boolean exclusionAllowsClaim,String poolSource,String holderSource,Holding holding) {
        public Claim(boolean exclusionAllowsClaim,String poolSource,String holderSource) {
            this(exclusionAllowsClaim,poolSource,holderSource,null);
        }
    }
    /** A non-null observation with a null user ID means currently unheld; absent means not verified. */
    public record Holding(String userId) { }
    public record Stamp(Long caseRevision,long authorityRevision,Instant validUntil) { }
}
