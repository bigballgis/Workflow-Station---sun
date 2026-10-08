package com.admin.controller;

import com.admin.component.SlaPolicyComponent;
import com.admin.dto.SlaPolicyListQueryRequest;
import com.admin.dto.SlaPolicyResponse;
import com.admin.dto.SlaPolicyRow;
import com.admin.dto.SlaPolicyUpdateRequest;
import com.admin.dto.SlaRecalcJobItemResponse;
import com.admin.dto.SlaRecalcJobResponse;
import com.admin.dto.list.AdminListPage;
import com.admin.entity.SlaPolicyHistory;
import com.platform.common.dto.ApiResponse;
import com.platform.common.resource.AbstractBaseController;
import com.platform.security.util.SecurityContextUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** SLA lead time per Function Unit. Access is enforced by {@code SlaPolicyAccessInterceptor}. */
@RestController
@RequestMapping("/sla-policies")
@Tag(name = "SLA policies", description = "SLA lead time per Function Unit and due date recalculation")
public class SlaPolicyController extends AbstractBaseController {

    private final SlaPolicyComponent slaPolicyComponent;

    public SlaPolicyController(SlaPolicyComponent slaPolicyComponent) {
        this.slaPolicyComponent = slaPolicyComponent;
    }

    @PostMapping("/query")
    @Operation(summary = "Query SLA policies (true paging; column filters and sort)")
    public ResponseEntity<ApiResponse<AdminListPage<SlaPolicyRow>>> queryPolicies(
            @RequestBody SlaPolicyListQueryRequest request) {
        return handleRequest(() -> slaPolicyComponent.query(request));
    }

    @PutMapping("/{functionUnitCode}")
    @Operation(summary = "Set the SLA lead time and start recalculating open cases")
    public ResponseEntity<ApiResponse<SlaPolicyResponse>> updatePolicy(
            @PathVariable String functionUnitCode,
            @Valid @RequestBody SlaPolicyUpdateRequest request) {
        String userId = SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new IllegalStateException("Unauthenticated"));
        return handleRequest(() -> slaPolicyComponent.update(functionUnitCode, request, userId));
    }

    @PostMapping("/{functionUnitCode}/recalculate")
    @Operation(summary = "Re-run the due date recalculation for the current lead time")
    public ResponseEntity<ApiResponse<Map<String, String>>> recalculate(@PathVariable String functionUnitCode) {
        String userId = SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new IllegalStateException("Unauthenticated"));
        return handleRequest(() -> Map.of("jobId", slaPolicyComponent.recalculate(functionUnitCode, userId)));
    }

    @GetMapping("/{functionUnitCode}/history")
    @Operation(summary = "Lead time change history (latest 50)")
    public ResponseEntity<ApiResponse<List<SlaPolicyHistory>>> getHistory(@PathVariable String functionUnitCode) {
        return handleRequest(() -> slaPolicyComponent.history(functionUnitCode));
    }

    @GetMapping("/{functionUnitCode}/jobs")
    @Operation(summary = "Recalculation jobs (latest 20)")
    public ResponseEntity<ApiResponse<List<SlaRecalcJobResponse>>> getJobs(@PathVariable String functionUnitCode) {
        return handleRequest(() -> slaPolicyComponent.jobs(functionUnitCode));
    }

    @GetMapping("/{functionUnitCode}/jobs/{jobId}/items")
    @Operation(summary = "Cases a job updated, skipped or failed on (first 500)")
    public ResponseEntity<ApiResponse<List<SlaRecalcJobItemResponse>>> getJobItems(
            @PathVariable String functionUnitCode, @PathVariable String jobId) {
        return handleRequest(() -> slaPolicyComponent.jobItems(functionUnitCode, jobId));
    }
}
