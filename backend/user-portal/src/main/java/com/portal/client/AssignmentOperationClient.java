package com.portal.client;

import com.platform.common.assignment.*;
import com.platform.common.dto.ApiResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Typed service-only effects. A missing endpoint never implies legacy authorization. */
@Component
public class AssignmentOperationClient {
    private static final ParameterizedTypeReference<ApiResponse<AssignmentOperationReceipt>> RECEIPT = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<ApiResponse<List<AssignmentOperationReceipt>>> RECEIPTS = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<ApiResponse<Boolean>> BOOLEAN = new ParameterizedTypeReference<>() { };
    private final AssignmentEngineTransport http;
    public AssignmentOperationClient(AssignmentEngineTransport http) { this.http = http; }
    public AssignmentOperationReceipt complete(AssignmentCompletionRequest request) {
        var receipt = http.post(List.of("operations"), request, RECEIPT, true);
        if (receipt == null) throw unavailable();
        return receipt;
    }
    public Optional<AssignmentOperationReceipt> find(UUID id) { return Optional.ofNullable(http.get(List.of("operations", id.toString()), Map.of(), RECEIPT)); }
    public Optional<AssignmentOperationReceipt> byTask(String task) { return Optional.ofNullable(http.get(List.of("operations", "by-task", task), Map.of(), RECEIPT)); }
    public List<AssignmentOperationReceipt> pending(int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("Invalid assignment recovery batch");
        var receipts = http.get(List.of("operations"), Map.of("limit", limit), RECEIPTS);
        if (receipts == null) throw unavailable();
        return List.copyOf(receipts);
    }
    public void confirm(AssignmentOperationReceipt receipt) {
        if (!Boolean.TRUE.equals(http.post(List.of("operations", receipt.operationId().toString(), "confirm"),
                Map.of("digest", receipt.requestDigest()), BOOLEAN, false))) throw unavailable();
    }
    public void retry(AssignmentOperationReceipt receipt, boolean conflict) {
        if (!Boolean.TRUE.equals(http.post(List.of("operations", receipt.operationId().toString(), "retry"),
                Map.of("conflict", conflict), BOOLEAN, false))) throw unavailable();
    }
    private AssignmentFailure unavailable() { return new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_DEPENDENCY_UNAVAILABLE); }
}
