package com.portal.client;

import com.platform.common.assignment.AssignmentFailure;
import com.platform.common.assignment.AssignmentReopenReceipt;
import com.platform.common.dto.ApiResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;

@Component
public class AssignmentReopenClient {
    private static final ParameterizedTypeReference<ApiResponse<AssignmentReopenReceipt>> RECEIPT = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<ApiResponse<List<AssignmentReopenReceipt>>> RECEIPTS = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<ApiResponse<Boolean>> BOOLEAN = new ParameterizedTypeReference<>() { };
    private final AssignmentEngineTransport http;
    public AssignmentReopenClient(AssignmentEngineTransport http) { this.http = http; }
    public AssignmentReopenReceipt reopen(String source, AssignmentReopenReceipt.Request request) {
        var receipt = http.post(List.of("reopens", source), request, RECEIPT, true);
        if (receipt == null || receipt.caseId() == null || receipt.variables() == null || receipt.processState() == null) throw unavailable();
        return receipt;
    }
    public List<AssignmentReopenReceipt> pending(int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("Invalid reopen recovery batch");
        var receipts = http.get(List.of("reopens"), Map.of("limit", limit), RECEIPTS);
        if (receipts == null) throw unavailable();
        return List.copyOf(receipts);
    }
    public void mirrored(AssignmentReopenReceipt receipt) {
        if (!Boolean.TRUE.equals(http.post(List.of("reopens", receipt.operationId().toString(), "mirrored"), null, BOOLEAN, false))) throw unavailable();
    }
    public void retry(AssignmentReopenReceipt receipt, boolean conflict) {
        if (!Boolean.TRUE.equals(http.post(List.of("reopens", receipt.operationId().toString(), "retry"),
                Map.of("conflict", conflict), BOOLEAN, false))) throw unavailable();
    }
    private AssignmentFailure unavailable() { return new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_DEPENDENCY_UNAVAILABLE); }
}
