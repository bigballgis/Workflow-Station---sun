package com.admin.service.impl;

import com.admin.config.RestTemplateConfig;
import com.admin.dto.response.AutomationFlowRunSummary;
import com.admin.exception.ServiceTaskApiException;
import com.admin.service.AutomationFlowRunService;
import com.admin.servicetask.client.ServiceTaskApiClient;
import com.admin.servicetask.config.ServiceTaskProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 执行记录实现。列表读走共库 SQL（列名为 TypeORM 生成的 camelCase 引号标识符，
 * SQL 全静态、参数绑定，与 {@link AutomationFlowServiceImpl} 同模式）；
 * 详情经 AP API（按当前操作人换取的会话）。
 */
@Slf4j
@Service
public class AutomationFlowRunServiceImpl implements AutomationFlowRunService {

    /**
     * flow 名取<b>执行时那个版本</b>的 displayName——flow 改名后历史记录仍应显示当时的名字。
     *
     * <p>不取 {@code triggeredBy}：Service Task 是引擎打 AP 的 webhook，AP 不给这类 run 记
     * 触发人，整列对本页可见的运行恒为空——列宽白占，还让人以为"查不到是谁触发的"。</p>
     */
    private static final String ROW_SQL = """
            SELECT r.id, r."flowId", r.status, r."startTime", r."finishTime",
                   r."projectId", p."displayName" AS "projectName",
                   f.metadata->>'hermesFlowKey' AS "flowKey",
                   fv."displayName" AS "flowDisplayName",
                   r."failedStep"->>'displayName' AS "failedStepName",
                   r."failedStep"->>'message' AS "failedStepMessage"
            FROM flow_run r
            JOIN flow f ON f.id = r."flowId"
            JOIN flow_version fv ON fv.id = r."flowVersionId"
            JOIN project p ON p.id = r."projectId"
            WHERE r.id IN (%s)
            """;

    /** AP id 是 21 位 nanoid；拼进 AP URL 前先卡形状，不让路径段带上任意字符 */
    private static final Pattern AP_ID = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ServiceTaskApiClient serviceTaskApiClient;
    private final ServiceTaskProperties serviceTaskProperties;
    /** AP control-plane calls only — long read timeout, own breaker (see RestTemplateConfig). */
    private final RestTemplate restTemplate;

    public AutomationFlowRunServiceImpl(JdbcTemplate jdbcTemplate,
                                        ObjectMapper objectMapper,
                                        ServiceTaskApiClient serviceTaskApiClient,
                                        ServiceTaskProperties serviceTaskProperties,
                                        @Qualifier(RestTemplateConfig.AP_REST_TEMPLATE) RestTemplate restTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.serviceTaskApiClient = serviceTaskApiClient;
        this.serviceTaskProperties = serviceTaskProperties;
        this.restTemplate = restTemplate;
    }

    @Override
    public List<AutomationFlowRunSummary> findRunsByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", ids.stream().map(id -> "?").toList());
        return jdbcTemplate.query(ROW_SQL.formatted(placeholders),
                (rs, rowNum) -> mapRow(rs), ids.toArray());
    }

    @Override
    public Optional<JsonNode> getRunDetail(String runId) {
        if (runId == null || !AP_ID.matcher(runId).matches()) {
            return Optional.empty();
        }
        ServiceTaskApiClient.ApSession session = serviceTaskApiClient.signInAsCurrentActor();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(session.token());
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    apUrl("/api/v1/flow-runs/" + runId), HttpMethod.GET,
                    new HttpEntity<>(headers), String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new ServiceTaskApiException(
                        "AP flow-run detail failed: HTTP " + response.getStatusCode());
            }
            return Optional.of(objectMapper.readTree(response.getBody()));
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND || e.getStatusCode() == HttpStatus.FORBIDDEN) {
                // 该运行不在当前操作人会话的 project 里（或已被清理）——按"看不到"处理
                log.warn("AP flow-run {} not visible to the current actor: {}", runId, e.getStatusCode());
                return Optional.empty();
            }
            throw new ServiceTaskApiException(
                    "AP flow-run detail failed: HTTP " + e.getStatusCode(), e);
        } catch (IOException e) {
            throw new ServiceTaskApiException("AP flow-run detail returned unparsable JSON", e);
        } catch (RestClientException e) {
            throw new ServiceTaskApiException("AP flow-run detail request failed", e);
        }
    }

    private AutomationFlowRunSummary mapRow(ResultSet rs) throws SQLException {
        OffsetDateTime start = rs.getObject("startTime", OffsetDateTime.class);
        OffsetDateTime finish = rs.getObject("finishTime", OffsetDateTime.class);
        return AutomationFlowRunSummary.builder()
                .id(rs.getString("id"))
                .flowId(rs.getString("flowId"))
                .flowKey(rs.getString("flowKey"))
                .flowDisplayName(rs.getString("flowDisplayName"))
                .projectId(rs.getString("projectId"))
                .projectName(rs.getString("projectName"))
                .status(rs.getString("status"))
                .startTime(start)
                .finishTime(finish)
                .durationMs(start != null && finish != null
                        ? finish.toInstant().toEpochMilli() - start.toInstant().toEpochMilli()
                        : null)
                .failedStepName(rs.getString("failedStepName"))
                .failedStepMessage(rs.getString("failedStepMessage"))
                .build();
    }

    private String apUrl(String path) {
        String base = serviceTaskProperties.getInternalUrl();
        return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + path;
    }
}
