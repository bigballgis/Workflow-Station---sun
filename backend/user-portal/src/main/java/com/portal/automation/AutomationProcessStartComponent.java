package com.portal.automation;

import com.platform.common.enums.ErrorCode;
import com.platform.common.exception.BusinessException;
import com.portal.component.ProcessStartComponent;
import com.portal.dto.ProcessInstanceInfo;
import com.portal.dto.ProcessStartRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Opens one Function Unit process as the flow's runtime identity. The caller cannot name the initiator. */
@Component
public class AutomationProcessStartComponent {

    private static final String SUB_TABLES = "__subTables__";

    private final AutomationAdminClient admin;
    private final ProcessStartComponent processes;
    private final AutomationIdempotency idempotency;
    private final JdbcTemplate jdbc;

    public AutomationProcessStartComponent(AutomationAdminClient admin, ProcessStartComponent processes,
                                           AutomationIdempotency idempotency, JdbcTemplate jdbc) {
        this.admin = admin;
        this.processes = processes;
        this.idempotency = idempotency;
        this.jdbc = jdbc;
    }

    public Map<String, Object> start(Map<String, Object> body, String idempotencyKey) {
        return idempotency.once(idempotencyKey, body, () -> openProcess(body));
    }

    private Map<String, Object> openProcess(Map<String, Object> body) {
        String projectId = text(body, "externalProjectId");
        String flowId = text(body, "flowId");
        AutomationAccessRules.requireRunContext(projectId, flowId);
        AutomationAdminClient.Scope scope = admin.scope(projectId, flowId);
        AutomationAccessRules.requireCapability(scope.capabilitySet(), "start");
        String code = text(body, "functionUnitCode");
        if (!code.equals(codeOf(scope.functionUnitId()))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "This flow cannot start that Function Unit");
        }
        String activeCatalogId = processes.activeCatalogIdForInternalStart(code);
        if (!scope.catalogId().equals(activeCatalogId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "The enabled catalog does not match the published scope");
        }
        Map<String, Object> fields = fields(body.get("fields"));
        if (fields.containsKey(SUB_TABLES)) {
            throw new BusinessException(ErrorCode.VALIDATION_FIELD_INVALID, "Start does not accept sub-table rows");
        }
        ProcessStartRequest request = new ProcessStartRequest();
        request.setProcessDefinitionKey(code);
        request.setFormData(fields);
        request.setRetainStartedInstance(true);
        String userId = admin.identity(projectId, flowId).userId();
        ProcessInstanceInfo info = processes.startProcessFromInternal(userId, code, request);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("processInstanceId", info.getId());
        result.put("requestId", info.getRequestId());
        result.put("status", info.getStatus());
        result.put("startUserId", info.getStartUserId());
        result.put("firstStepError", info.getFirstStepError());
        return result;
    }

    private String codeOf(long functionUnitId) {
        RowMapper<String> codes = (rs, row) -> rs.getString(1);
        List<String> found = jdbc.query("SELECT code FROM dw_function_units WHERE id = ?", codes, functionUnitId);
        if (found.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Function Unit was not found");
        }
        return found.get(0);
    }

    private static String text(Map<String, Object> body, String key) {
        Object value = body == null ? null : body.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> fields(Object value) {
        if (value == null) {
            return new LinkedHashMap<>();
        }
        if (!(value instanceof Map<?, ?> map)) {
            throw new BusinessException(ErrorCode.VALIDATION_FIELD_REQUIRED, "fields must be an object");
        }
        return new LinkedHashMap<>((Map<String, Object>) map);
    }
}
