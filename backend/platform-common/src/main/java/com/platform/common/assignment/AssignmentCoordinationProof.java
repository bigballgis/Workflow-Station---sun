package com.platform.common.assignment;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Neutral coordination metadata only; no workflow, identity or business data is committed by this protocol. */
public final class AssignmentCoordinationProof {
    private AssignmentCoordinationProof() { }
    public enum Participant { ADMIN_CENTER, USER_PORTAL }
    public record Request(@NotNull Participant participant) { }
    public record Challenge(UUID probeId,UUID nonce,Participant participant,Map<String,Long> relations,Instant expiresAt) {
        public Challenge { relations=Map.copyOf(relations); }
    }
    public record Confirmation(@NotNull UUID probeId,@NotNull UUID nonce,@NotNull Participant participant,@NotNull UUID acknowledgement) { }
}
