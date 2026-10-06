package com.admin.component;

import com.admin.dto.SlaPolicyListQueryRequest;
import com.admin.dto.SlaPolicyResponse;
import com.admin.dto.SlaPolicyRow;
import com.admin.dto.SlaPolicyUpdateRequest;
import com.admin.dto.SlaRecalcJobItemResponse;
import com.admin.dto.SlaRecalcJobResponse;
import com.admin.dto.list.AdminListPage;
import com.admin.entity.SlaPolicy;
import com.admin.entity.SlaPolicyHistory;
import com.admin.list.ListQuerySupport;
import com.admin.list.SlaPolicyColumnSpec;
import com.admin.repository.SlaPolicyHistoryRepository;
import com.admin.repository.SlaPolicyRepository;
import com.platform.common.enums.ErrorCode;
import com.platform.common.exception.BusinessException;
import com.platform.common.exception.ResourceNotFoundException;
import com.platform.common.i18n.I18nService;
import com.platform.common.list.ListFilterSql;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * SLA lead time per Function Unit. A change is saved (policy + history) in its own transaction and
 * only then handed to the portal as a recalculation job, so a portal outage never rolls back an
 * authorised change: the history row records {@code DISPATCH_FAILED} and the page offers a retrigger.
 */
@Slf4j
@Component
public class SlaPolicyComponent {

    private static final String LIST_KEY = "admin-sla-policy";

    /**
     * Stored on the history row and returned to the UI instead of the exception text, which names
     * internal service hosts and ports; the full exception goes to the admin-center log.
     */
    static final String DISPATCH_ERROR_CODE = "PORTAL_DISPATCH_FAILED";

    /**
     * The Function Unit's SLA mapping lives on the table bound PRIMARY to its PROCESS form — the same
     * resolution user-portal's SlaDueDateEnricher (and RequestIdEnricher) use at runtime.
     */
    static final String MAPPING_FROM = " " + """
             FROM dw_function_units fu
             CROSS JOIN LATERAL (
                 SELECT td.sla_config
                 FROM dw_form_definitions fd
                 INNER JOIN dw_form_table_bindings ftb
                     ON ftb.form_id = fd.id AND ftb.binding_type = 'PRIMARY'
                 INNER JOIN dw_table_definitions td ON td.id = ftb.table_id
                 WHERE fd.function_unit_id = fu.id AND fd.form_type = 'PROCESS'
                 ORDER BY ftb.sort_order NULLS LAST, ftb.id
                 LIMIT 1) m
             LEFT JOIN ac_sla_policies p ON p.function_unit_code = fu.code
             WHERE m.sla_config IS NOT NULL""";

    private final SlaPolicyRepository policyRepository;
    private final SlaPolicyHistoryRepository historyRepository;
    private final PortalSlaRecalcClient portalClient;
    private final JdbcTemplate jdbcTemplate;
    private final I18nService i18nService;
    private final TransactionTemplate txTemplate;

    public SlaPolicyComponent(SlaPolicyRepository policyRepository,
                              SlaPolicyHistoryRepository historyRepository,
                              PortalSlaRecalcClient portalClient,
                              JdbcTemplate jdbcTemplate,
                              I18nService i18nService,
                              PlatformTransactionManager transactionManager) {
        this.policyRepository = policyRepository;
        this.historyRepository = historyRepository;
        this.portalClient = portalClient;
        this.jdbcTemplate = jdbcTemplate;
        this.i18nService = i18nService;
        this.txTemplate = new TransactionTemplate(transactionManager);
    }

    public AdminListPage<SlaPolicyRow> query(SlaPolicyListQueryRequest request) {
        long started = System.nanoTime();
        ListFilterSql filterSql = SlaPolicyColumnSpec.sql();
        List<Object> params = new ArrayList<>();
        String where = MAPPING_FROM + filterSql.whereClause(request.filters(), params);

        ResultSetExtractor<Long> countExtractor = rs -> rs.next() ? rs.getLong(1) : 0L;
        long total = ListQuerySupport.requireCount(
                ListQuerySupport.query(jdbcTemplate, "SELECT COUNT(*)" + where, params, countExtractor),
                LIST_KEY);

        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(request.size());
        pageParams.add(request.page() * request.size());
        String sql = "SELECT fu.code, fu.name, p.lead_time_days, p.version, p.updated_by, "
                + SlaPolicyColumnSpec.isoUtc("p.updated_at") + " AS updated_at, "
                + "(SELECT j.status || '|' || j.id FROM up_sla_recalc_jobs j WHERE j.function_unit_code = fu.code "
                + "ORDER BY j.submitted_at DESC LIMIT 1) AS latest_job"
                + where + filterSql.orderBy(request.sortField(), request.sortDirection()) + " LIMIT ? OFFSET ?";
        ResultSetExtractor<List<SlaPolicyRow>> extractor = rs -> {
            List<SlaPolicyRow> rows = new ArrayList<>();
            while (rs.next()) {
                String latest = rs.getString("latest_job");
                int sep = latest == null ? -1 : latest.indexOf('|');
                rows.add(new SlaPolicyRow(
                        rs.getString("code"),
                        rs.getString("name"),
                        (Integer) rs.getObject("lead_time_days"),
                        (Integer) rs.getObject("version"),
                        rs.getString("updated_by"),
                        rs.getString("updated_at"),
                        sep < 0 ? null : latest.substring(0, sep),
                        sep < 0 ? null : latest.substring(sep + 1)));
            }
            return rows;
        };
        List<SlaPolicyRow> rows = ListQuerySupport.query(jdbcTemplate, sql, pageParams, extractor);
        ListQuerySupport.logIfSlow(log, LIST_KEY, request.page(), request.size(), total, started);
        return new AdminListPage<>(SlaPolicyColumnSpec.columns(), rows, request.page(), request.size(), total);
    }

    /**
     * Saves a new lead time (version + 1, history row) and then dispatches the recalculation job.
     * A dispatch failure is recorded on the history row and returned, not thrown.
     */
    public SlaPolicyResponse update(String functionUnitCode, SlaPolicyUpdateRequest request, String userId) {
        requireMapped(functionUnitCode);
        SlaPolicyHistory history = txTemplate.execute(status -> {
            // A row lock cannot serialise the first change of a Function Unit (there is no row yet), so
            // two concurrent first saves would both INSERT and one would hit the primary key. Make sure
            // the row exists first; the lock below then orders them and the second sees the first.
            boolean created = jdbcTemplate.update("""
                    INSERT INTO ac_sla_policies (function_unit_code, lead_time_days, version, updated_by)
                    VALUES (?, ?, 0, ?) ON CONFLICT (function_unit_code) DO NOTHING
                    """, functionUnitCode, request.leadTimeDaysValue(), userId) == 1;
            SlaPolicy policy = policyRepository.findForUpdate(functionUnitCode).orElseThrow();
            Integer oldDays = created ? null : policy.getLeadTimeDays();
            Integer oldVersion = created ? null : policy.getVersion();
            policy.setLeadTimeDays(request.leadTimeDaysValue());
            policy.setVersion(oldVersion == null ? 1 : oldVersion + 1);
            policy.setUpdatedBy(userId);
            policyRepository.save(policy);
            return historyRepository.save(SlaPolicyHistory.builder()
                    .id(UUID.randomUUID().toString())
                    .functionUnitCode(functionUnitCode)
                    .oldLeadTimeDays(oldDays)
                    .newLeadTimeDays(request.leadTimeDaysValue())
                    .oldVersion(oldVersion)
                    .newVersion(policy.getVersion())
                    .changeReason(request.getChangeReason())
                    .changedBy(userId)
                    .dispatchStatus(SlaPolicyHistory.DispatchStatus.PENDING)
                    .build());
        });
        dispatch(history, userId);
        SlaPolicy saved = policyRepository.findById(functionUnitCode).orElseThrow();
        return SlaPolicyResponse.builder()
                .functionUnitCode(functionUnitCode)
                .leadTimeDays(saved.getLeadTimeDays())
                .version(saved.getVersion())
                .updatedBy(saved.getUpdatedBy())
                .updatedAt(saved.getUpdatedAt())
                .dispatchStatus(history.getDispatchStatus())
                .recalcJobId(history.getRecalcJobId())
                .dispatchError(history.getDispatchError())
                .build();
    }

    /** Re-runs the recalculation for the current lead time (e.g. after a failed dispatch or job). */
    public String recalculate(String functionUnitCode, String userId) {
        requireMapped(functionUnitCode);
        SlaPolicy policy = policyRepository.findById(functionUnitCode).orElseThrow(() -> new BusinessException(
                ErrorCode.VALIDATION_FIELD_INVALID,
                i18nService.getMessage("admin.sla.lead_time_not_set", functionUnitCode)));
        try {
            return portalClient.submit(functionUnitCode, policy.getVersion(), userId);
        } catch (Exception e) {
            log.warn("SLA recalculation dispatch failed for {}", functionUnitCode, e);
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR,
                    i18nService.getMessage("admin.sla.dispatch_failed"), e);
        }
    }

    public List<SlaPolicyHistory> history(String functionUnitCode) {
        return historyRepository.findTop50ByFunctionUnitCodeOrderByChangedAtDesc(functionUnitCode);
    }

    public List<SlaRecalcJobResponse> jobs(String functionUnitCode) {
        return jdbcTemplate.query("""
                SELECT id, policy_version, lead_time_days, triggered_by, status, total_count, updated_count,
                       unchanged_count, skipped_count, failed_count, error_message,
                       %s AS submitted_at, %s AS started_at, %s AS finished_at
                FROM up_sla_recalc_jobs WHERE function_unit_code = ?
                ORDER BY submitted_at DESC LIMIT 20
                """.formatted(SlaPolicyColumnSpec.isoUtc("submitted_at"), SlaPolicyColumnSpec.isoUtc("started_at"),
                SlaPolicyColumnSpec.isoUtc("finished_at")), (rs, i) -> new SlaRecalcJobResponse(
                rs.getString("id"),
                (Integer) rs.getObject("policy_version"),
                (Integer) rs.getObject("lead_time_days"),
                rs.getString("triggered_by"),
                rs.getString("status"),
                rs.getInt("total_count"),
                rs.getInt("updated_count"),
                rs.getInt("unchanged_count"),
                rs.getInt("skipped_count"),
                rs.getInt("failed_count"),
                rs.getString("error_message"),
                rs.getString("submitted_at"),
                rs.getString("started_at"),
                rs.getString("finished_at")), functionUnitCode);
    }

    /** Items of one job; the function unit code scopes the lookup so a job id cannot leak across FUs. */
    public List<SlaRecalcJobItemResponse> jobItems(String functionUnitCode, String jobId) {
        return jdbcTemplate.query("""
                SELECT i.process_instance_id, i.outcome, i.old_due_date, i.new_due_date, i.reason,
                       %s AS created_at
                FROM up_sla_recalc_job_items i
                INNER JOIN up_sla_recalc_jobs j ON j.id = i.job_id
                WHERE j.function_unit_code = ? AND i.job_id = ?
                ORDER BY i.id LIMIT 500
                """.formatted(SlaPolicyColumnSpec.isoUtc("i.created_at")), (rs, i) -> new SlaRecalcJobItemResponse(
                rs.getString("process_instance_id"),
                rs.getString("outcome"),
                rs.getString("old_due_date"),
                rs.getString("new_due_date"),
                rs.getString("reason"),
                rs.getString("created_at")), functionUnitCode, jobId);
    }

    private void dispatch(SlaPolicyHistory history, String userId) {
        try {
            history.setRecalcJobId(portalClient.submit(history.getFunctionUnitCode(), history.getNewVersion(), userId));
            history.setDispatchStatus(SlaPolicyHistory.DispatchStatus.DISPATCHED);
        } catch (Exception e) {
            log.warn("SLA recalculation dispatch failed for {} v{} (history {})",
                    history.getFunctionUnitCode(), history.getNewVersion(), history.getId(), e);
            history.setDispatchStatus(SlaPolicyHistory.DispatchStatus.DISPATCH_FAILED);
            history.setDispatchError(DISPATCH_ERROR_CODE);
        }
        historyRepository.save(history);
    }

    private void requireMapped(String functionUnitCode) {
        Long mapped = jdbcTemplate.queryForObject(
                "SELECT COUNT(*)" + MAPPING_FROM + " AND fu.code = ?", Long.class, functionUnitCode);
        if (mapped == null || mapped == 0) {
            throw new ResourceNotFoundException("SLA mapping", functionUnitCode);
        }
    }
}
