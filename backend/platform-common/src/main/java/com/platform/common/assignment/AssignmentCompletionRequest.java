package com.platform.common.assignment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Internal Portal-to-Engine request only. The public client never supplies the accepted server effect. */
public record AssignmentCompletionRequest(@NotNull UUID operationId, @NotBlank @Size(max=64) String taskId,
        @NotBlank @Size(max=64) String actor, @Size(max=64) String representedUser, @NotBlank String action,
        @NotNull Map<String,Object> variables, @PositiveOrZero long portalBaseRevision, @NotNull @Valid Effect effect) {
    public record Effect(@NotBlank @Size(max=64) String instanceId, @NotNull List<AssignmentVariablePatch.Entry> variablePatch,
            @Size(max=255) String stageId, @jakarta.validation.constraints.Pattern(regexp="[0-9a-f]{64}") String clientRequestDigest,
            @NotNull @Size(max=10000) List<@Valid AuditChange> auditChanges, @NotNull Map<String,Object> observation,
            @NotNull @Size(max=32) List<@Valid InstanceBaseline> baselines,@NotNull Map<String,Object> postAction) {
        public Effect(String instanceId,List<AssignmentVariablePatch.Entry> variablePatch,String stageId,String clientRequestDigest,
                List<AuditChange> auditChanges,Map<String,Object> observation,List<InstanceBaseline> baselines) {
            this(instanceId,variablePatch,stageId,clientRequestDigest,auditChanges,observation,baselines,Map.of());
        }
        public Effect(String instanceId,List<AssignmentVariablePatch.Entry> variablePatch,String stageId,String clientRequestDigest,
                List<AuditChange> auditChanges,Map<String,Object> observation) {
            this(instanceId,variablePatch,stageId,clientRequestDigest,auditChanges,observation,List.of());
        }
        public Effect(String instanceId,List<AssignmentVariablePatch.Entry> variablePatch) {
            this(instanceId,variablePatch,null,null,List.of(),Map.of());
        }
        public Effect {
            if (instanceId == null || instanceId.isBlank() || variablePatch == null || auditChanges==null || observation==null || baselines==null || postAction==null
                    || !auditChanges.isEmpty() && (stageId==null || stageId.isBlank())) throw new IllegalArgumentException("Missing accepted Portal effect");
            variablePatch = List.copyOf(variablePatch);
            auditChanges = List.copyOf(auditChanges);
            baselines = List.copyOf(baselines);
            observation = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(observation));
            postAction = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(postAction));
        }
    }
    public record InstanceBaseline(@NotBlank String instanceId,@NotBlank String definitionId,@NotNull com.fasterxml.jackson.databind.JsonNode values) {
        public InstanceBaseline {
            if (instanceId==null || definitionId==null || values==null || !values.isObject()) throw new IllegalArgumentException("Missing original Portal baseline");
            values=values.deepCopy();
        }
    }
    public enum AuditKind { FIELD_UPDATE, SUB_TABLE_ROW_ADD, SUB_TABLE_ROW_UPDATE, SUB_TABLE_ROW_DELETE }
    public record AuditChange(@NotBlank @Size(max=255) String fieldName,String oldValue,String newValue,@NotNull AuditKind kind,
            @Size(max=255) String subTableName,@Size(max=255) String rowIdentifier,@NotNull java.time.Instant timestamp,
            @Size(max=36) String batchId,java.time.Instant batchTime) { }
}
