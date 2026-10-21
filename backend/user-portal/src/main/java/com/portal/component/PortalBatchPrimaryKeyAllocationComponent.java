package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.dto.PkGenerationConfig;
import com.platform.common.fk.PrimaryKeyAllocationService;
import com.portal.dto.AllocatePrimaryKeyRequest;
import com.portal.dto.AllocatePrimaryKeyResponse;
import com.portal.dto.TaskInfo;
import com.portal.exception.PortalException;
import com.platform.security.util.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** Allocates sequence values from the frozen Batch Add contract, including its configured parents. */
@Component
@RequiredArgsConstructor
public class PortalBatchPrimaryKeyAllocationComponent {
    private final TaskQueryComponent taskQuery;
    private final TaskPermissionEvaluator permissions;
    private final TaskFormComponent taskForms;
    private final PrimaryKeyAllocationService allocator;
    private final ObjectMapper mapper;

    public AllocatePrimaryKeyResponse allocate(String taskId, String userId, AllocatePrimaryKeyRequest request) {
        if (taskId == null || taskId.isBlank()) throw forbidden("Batch allocation requires a current task");
        TaskInfo task = taskQuery.getTaskById(taskId).orElseThrow(() -> forbidden("Batch allocation task is unavailable"));
        String username = SecurityContextUtils.getCurrentUsername().orElse(null);
        if (!permissions.canProcessTask(task, userId, username) && !permissions.canClaimTask(task, userId, username)) {
            throw forbidden("Batch allocation is not allowed for this task");
        }
        Map<?, ?> controls = map(taskForms.getTaskFormData(taskId).getSubTableBatchCapabilities());
        Map<?, ?> control = map(controls.get(request.getBatchControlId()));
        if (!Boolean.TRUE.equals(map(control.get("capabilities")).get("allowBatchAdd"))
                || Boolean.FALSE.equals(map(control.get("originalCrud")).get("allowAdd"))) throw forbidden("Batch add is disabled");
        int count = request.getCount() == null ? 1 : request.getCount();
        if (count < 1 || count > 200) throw forbidden("Batch allocation count is invalid");
        Map<?, ?> field = resolveField(map(control.get("rowDesign")), request);
        Object rawConfig = field.containsKey("pkGenerationJson") ? field.get("pkGenerationJson") : field.get("pkGeneration");
        PkGenerationConfig config = rawConfig instanceof Map<?, ?> json && !json.isEmpty()
                ? mapper.convertValue(json, PkGenerationConfig.class) : PkGenerationConfig.builder().strategy("uuid").build();
        if ("manual".equals(config.getStrategy())) throw forbidden("Manual keys cannot be allocated");
        List<String> values = allocator.allocate(request.getTableId(), request.getFieldName(), config, count, "", "dw_pk_sequences");
        return AllocatePrimaryKeyResponse.builder().values(values).build();
    }

    private static Map<?, ?> resolveField(Map<?, ?> design, AllocatePrimaryKeyRequest request) {
        Map<?, ?> binding = map(design.get("binding"));
        Object rawFields = design.get("fields");
        if (!String.valueOf(request.getTableId()).equals(String.valueOf(binding.get("tableId")))) {
            Object runtimeFields = binding.get("fieldDefinitions");
            boolean parent = runtimeFields instanceof List<?> fields && fields.stream().map(PortalBatchPrimaryKeyAllocationComponent::map)
                    .anyMatch(field -> Boolean.TRUE.equals(field.get("isForeignKey"))
                            && String.valueOf(request.getTableId()).equals(String.valueOf(field.get("refTableId"))));
            if (!parent || (request.getCount() != null && request.getCount() != 1)) throw forbidden("Batch parent allocation is not configured");
            rawFields = map(map(design.get("parentTablesById")).get(String.valueOf(request.getTableId()))).get("fieldDefinitions");
        }
        if (!(rawFields instanceof List<?> fields)) throw forbidden("Frozen primary-key metadata is missing");
        return fields.stream().map(PortalBatchPrimaryKeyAllocationComponent::map)
                .filter(field -> request.getFieldName().equals(field.get("fieldName")) && Boolean.TRUE.equals(field.get("isPrimaryKey")))
                .findFirst().orElseThrow(() -> forbidden("Primary key does not belong to the frozen batch contract"));
    }

    private static Map<?, ?> map(Object value) { return value instanceof Map<?, ?> map ? map : Map.of(); }
    private static PortalException forbidden(String message) { return new PortalException("403", message); }
}
