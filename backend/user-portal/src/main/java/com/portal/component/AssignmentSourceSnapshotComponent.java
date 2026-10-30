package com.portal.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.assignment.AssignmentFailure;
import com.platform.common.assignment.AssignmentSourceSnapshot;
import com.platform.common.subtable.SubTableStoreKeys;
import com.portal.repository.AssignmentSourceSnapshotRepository;
import org.springframework.stereotype.Component;
import java.util.HashSet;

/** Source snapshots are service-only and expose only explicitly requested assignment fields. */
@Component
public class AssignmentSourceSnapshotComponent {
    private final AssignmentSourceSnapshotRepository repository;
    private final ObjectMapper json;
    public AssignmentSourceSnapshotComponent(AssignmentSourceSnapshotRepository repository, ObjectMapper json) {
        this.repository = repository; this.json = json;
    }
    public AssignmentSourceSnapshot read(AssignmentSourceSnapshot.Request request) {
        if (request.fields().contains("__subTables__") || new HashSet<>(request.fields()).size()!=request.fields().size()) throw unavailable();
        var stored = repository.read(request);
        if (!stored.present()) {
            if (stored.previouslyPresent()) throw unavailable();
            return result(request,stored,AssignmentSourceSnapshot.State.INITIAL,json.createObjectNode());
        }
        JsonNode source = decode(stored.variables());
        if (request.row()!=null) source = row(source,request.row());
        var values = json.createObjectNode();
        for (String field : request.fields()) if (source.has(field)) values.set(field,source.get(field));
        return result(request,stored,AssignmentSourceSnapshot.State.READY,values);
    }
    private JsonNode row(JsonNode variables, AssignmentSourceSnapshot.Row selector) {
        if (!SubTableStoreKeys.isCanonical(selector.storeKey()) || !selector.key().isObject()
                || selector.key().isEmpty() || selector.key().size()>32) throw unavailable();
        for (JsonNode value : selector.key()) if (!value.isValueNode() || value.isNull() || value.asText().isBlank()) throw unavailable();
        var rows = variables.path("__subTables__").path(selector.storeKey());
        if (!rows.isArray()) throw unavailable();
        JsonNode found = null;
        for (JsonNode candidate : rows) {
            if (!candidate.isObject()) throw unavailable();
            boolean matches = true;
            var keys = selector.key().fields();
            while (keys.hasNext()) {
                var key = keys.next();
                if (!key.getValue().equals(candidate.get(key.getKey()))) { matches=false; break; }
            }
            if (matches) { if (found!=null) throw unavailable(); found=candidate; }
        }
        if (found==null) throw unavailable();
        return found;
    }
    private JsonNode decode(String value) {
        try {
            JsonNode decoded = value==null ? null : json.readTree(value);
            if (decoded==null || !decoded.isObject()) throw unavailable();
            return decoded;
        } catch (com.fasterxml.jackson.core.JsonProcessingException failure) { throw unavailable(); }
    }
    private AssignmentSourceSnapshot result(AssignmentSourceSnapshot.Request request, AssignmentSourceSnapshotRepository.Stored stored,
            AssignmentSourceSnapshot.State state, JsonNode values) {
        return new AssignmentSourceSnapshot(1,request.caseId(),request.instanceId(),stored.caseRevision(),stored.portalRevision(),state,values);
    }
    private AssignmentFailure unavailable() { return new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE); }
}
