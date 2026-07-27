package com.admin.service.impl;

import com.admin.dto.response.AutomationFlowSummary;
import com.admin.exception.ServiceTaskApiException;
import com.admin.service.AutomationFlowService;
import com.admin.servicetask.client.ServiceTaskApiClient;
import com.admin.servicetask.config.ServiceTaskProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Array;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * flow 迁移实现。读走共库 SQL（与 {@link AutomationPieceServiceImpl} 同模式:
 * 列名为 TypeORM 生成的 camelCase 引号标识符,SQL 全静态、参数绑定）；
 * 写一律经 AP API（共享账号会话）,不直写 AP 表。
 *
 * <p>迁移键 {@code hermesFlowKey} 放 flow.metadata（jsonb）:AP 的 REST 创建
 * 接口不接受 externalId,而 metadata 是 CreateFlowRequest 的一等字段——
 * 不用给 vendored AP 打补丁（Frozen Baseline,DECISIONS Q8/D1）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutomationFlowServiceImpl implements AutomationFlowService {

    /** 每 flow 取最新版本做展示；发布态看 publishedVersionId */
    private static final String LIST_SQL = """
            SELECT f.id, f.status, f."projectId",
                   f."publishedVersionId" IS NOT NULL AS published,
                   f.metadata->>'hermesFlowKey' AS "flowKey",
                   fv."displayName", fv.valid, fv.updated,
                   p."displayName" AS "projectName",
                   ui."firstName" AS "ownerFirstName", ui."lastName" AS "ownerLastName"
            FROM flow f
            JOIN LATERAL (SELECT "displayName", valid, updated FROM flow_version v
                          WHERE v."flowId" = f.id ORDER BY v.created DESC LIMIT 1) fv ON true
            JOIN project p ON p.id = f."projectId"
            LEFT JOIN "user" u ON u.id = f."ownerId"
            LEFT JOIN user_identity ui ON ui.id = u."identityId"
            ORDER BY fv.updated DESC
            """;

    /** 导出取已发布版本，未发布过则最新草稿 */
    private static final String EXPORT_SQL = """
            SELECT f.id, f.metadata->>'hermesFlowKey' AS "flowKey",
                   fv."displayName", fv.trigger::text AS trigger,
                   fv."schemaVersion", fv.notes::text AS notes,
                   fv."connectionIds",
                   (fv.id = f."publishedVersionId") IS TRUE AS "fromPublished"
            FROM flow f
            JOIN LATERAL (SELECT * FROM flow_version v WHERE v."flowId" = f.id
                          ORDER BY (v.id = f."publishedVersionId") IS TRUE DESC, v.created DESC
                          LIMIT 1) fv ON true
            WHERE f.id = ?
            """;

    /** upsert 匹配：同 id（同环境回导）或同迁移键（跨环境） */
    private static final String FIND_BY_KEY_SQL = """
            SELECT id, metadata->>'hermesFlowKey' AS "flowKey" FROM flow
            WHERE "projectId" = ? AND (id = ? OR metadata->>'hermesFlowKey' = ?)
            ORDER BY updated DESC
            """;

    private static final String RESOLVE_BY_ID_SQL = "SELECT id FROM flow WHERE id = ?";

    private static final String RESOLVE_BY_KEY_SQL = """
            SELECT id FROM flow WHERE metadata->>'hermesFlowKey' = ?
            ORDER BY updated DESC LIMIT 1
            """;

    private static final String PROJECT_BY_EXTERNAL_ID_SQL =
            "SELECT id FROM project WHERE \"externalId\" = ? LIMIT 1";

    private static final String FLOW_METADATA_SQL = "SELECT metadata::text FROM flow WHERE id = ?";

    /** 删除前取 flow 自身 id 与迁移键——BPMN 里可能引用其中任意一个（见 findReferencingUnits） */
    private static final String FLOW_REF_KEYS_SQL =
            "SELECT id, metadata->>'hermesFlowKey' AS \"flowKey\" FROM flow WHERE id = ?";

    /**
     * 引用检查候选集。BPMN 在两侧都是明文 XML 与 base64 混存，故 SQL 只做粗筛
     * （明文直接 LIKE 命中 + 所有非 '<' 开头的 base64 行），最终判定在 Java 里
     * smartDecode 后做——只用 LIKE 会漏掉全部 base64 行，得出"无引用"的假结论。
     *
     * <p><b>两个来源都必须扫</b>：prod 只有 admin-center 的 FU（DW 不部署），但
     * dev/uat 里 DW 侧的 {@code dw_process_definitions} 同样引用 flow，且它整表都是
     * base64——只扫 admin-center 会让"仅被 DW FU 引用"的 flow 被判成无引用而误删。
     * {@code to_regclass} 让本查询在 DW 表缺失的环境下也能安全跑。</p>
     */
    private static final String PROCESS_CONTENT_CANDIDATES_SQL = """
            SELECT fu.name AS "unitName", c.content_data AS "bpmn"
            FROM sys_function_unit_contents c
            JOIN sys_function_units fu ON fu.id = c.function_unit_id
            WHERE c.content_type = 'PROCESS'
              AND c.content_data IS NOT NULL
              AND (c.content_data LIKE ? OR c.content_data LIKE ?
                   OR left(ltrim(c.content_data), 1) <> '<')
            UNION ALL
            SELECT dfu.name AS "unitName", d.bpmn_xml AS "bpmn"
            FROM dw_process_definitions d
            JOIN dw_function_units dfu ON dfu.id = d.function_unit_id
            WHERE to_regclass('public.dw_process_definitions') IS NOT NULL
              AND d.bpmn_xml IS NOT NULL
              AND (d.bpmn_xml LIKE ? OR d.bpmn_xml LIKE ?
                   OR left(ltrim(d.bpmn_xml), 1) <> '<')
            """;

    private static final int EXPORT_FORMAT_VERSION = 1;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ServiceTaskApiClient serviceTaskApiClient;
    private final ServiceTaskProperties serviceTaskProperties;
    private final RestTemplate restTemplate;

    @Override
    public List<AutomationFlowSummary> listFlows() {
        return jdbcTemplate.query(LIST_SQL, (rs, rowNum) -> {
            String first = rs.getString("ownerFirstName");
            String last = rs.getString("ownerLastName");
            String owner = ((first != null ? first : "") + " " + (last != null ? last : "")).trim();
            return AutomationFlowSummary.builder()
                    .id(rs.getString("id"))
                    .flowKey(rs.getString("flowKey"))
                    .displayName(rs.getString("displayName"))
                    .projectId(rs.getString("projectId"))
                    .projectName(rs.getString("projectName"))
                    .status(rs.getString("status"))
                    .published(rs.getBoolean("published"))
                    .valid(rs.getBoolean("valid"))
                    .ownerName(owner.isEmpty() ? null : owner)
                    .updated(rs.getObject("updated", OffsetDateTime.class))
                    .build();
        });
    }

    @Override
    public FlowExportFile exportFlow(String flowId) {
        Map<String, Object> row;
        try {
            row = jdbcTemplate.queryForMap(EXPORT_SQL, flowId);
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("flow not found: " + flowId);
        }

        // 链式保留:被导入的 flow 再导出时沿用原始 key,同一逻辑 flow 在任意环境同键
        String existingKey = (String) row.get("flowKey");
        String flowKey = existingKey != null && !existingKey.isBlank() ? existingKey : flowId;

        ObjectNode export = objectMapper.createObjectNode();
        export.put("hermesFlowExport", EXPORT_FORMAT_VERSION);
        export.put("flowKey", flowKey);
        export.put("sourceFlowId", flowId);
        export.put("displayName", (String) row.get("displayName"));
        export.put("schemaVersion", (String) row.get("schemaVersion"));
        export.put("fromPublished", Boolean.TRUE.equals(row.get("fromPublished")));
        export.put("exportedAt", OffsetDateTime.now().toString());
        export.set("trigger", parseJson((String) row.get("trigger")));
        export.set("notes", parseJson((String) row.get("notes")));
        // connection 清单只作预检信息:凭据不随包走,目标环境导入前据此比对缺口
        export.set("connections", connectionManifest(sqlArrayToList(row.get("connectionIds"))));

        String slug = String.valueOf(row.get("displayName"))
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}_-]+", "-")
                .replaceAll("(^-|-$)", "");
        byte[] bytes;
        try {
            bytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(export);
        } catch (IOException e) {
            throw new IllegalStateException("flow export serialization failed", e);
        }
        return new FlowExportFile("flow-" + slug + "-" + flowKey + ".json", bytes);
    }

    @Override
    public FlowImportResult importFlow(byte[] json, boolean publish) {
        JsonNode export = readExport(json);
        String flowKey = export.path("flowKey").asText();
        String displayName = export.path("displayName").asText();

        ServiceTaskApiClient.ApSession session = serviceTaskApiClient.signInShared();
        String projectId = resolveTargetProjectId(session);

        List<Map<String, Object>> matches =
                jdbcTemplate.queryForList(FIND_BY_KEY_SQL, projectId, flowKey, flowKey);
        if (matches.size() > 1) {
            log.warn("Multiple flows match key '{}' in project {}; updating the most recent one",
                    flowKey, projectId);
        }

        boolean created = matches.isEmpty();
        String flowId;
        if (created) {
            flowId = createFlow(session, projectId, displayName, flowKey);
        } else {
            flowId = (String) matches.get(0).get("id");
            if (matches.get(0).get("flowKey") == null) {
                // 同环境回导命中原 flow(id==key):补上迁移键,后续再导出/解析同键可用
                stampFlowKey(session, flowId, flowKey);
            }
        }

        ObjectNode importRequest = objectMapper.createObjectNode();
        importRequest.put("displayName", displayName);
        importRequest.set("trigger", export.get("trigger"));
        importRequest.set("schemaVersion", export.get("schemaVersion"));
        importRequest.set("notes", export.hasNonNull("notes") ? export.get("notes") : null);
        applyFlowOperation(session, flowId, "IMPORT_FLOW", importRequest);

        if (publish) {
            ObjectNode publishRequest = objectMapper.createObjectNode();
            publishRequest.put("status", "ENABLED");
            applyFlowOperation(session, flowId, "LOCK_AND_PUBLISH", publishRequest);
        }
        return new FlowImportResult(flowId, flowKey, displayName, created, publish);
    }

    @Override
    public void setFlowEnabled(String flowId, boolean enabled) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("status", enabled ? "ENABLED" : "DISABLED");
        applyFlowOperation(serviceTaskApiClient.signInShared(), flowId, "CHANGE_STATUS", request);
    }

    @Override
    public void deleteFlow(String flowId, boolean force) {
        if (!force) {
            List<String> units = findReferencingUnits(flowId);
            if (!units.isEmpty()) {
                throw new FlowInUseException(flowId, units);
            }
        }
        ServiceTaskApiClient.ApSession session = serviceTaskApiClient.signInShared();
        // 不带 Content-Type：AP(Fastify) 对无 body 却声明 application/json 的请求直接 400
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(session.token());
        ResponseEntity<String> response = restTemplate.exchange(
                apUrl("/api/v1/flows/" + flowId), HttpMethod.DELETE,
                new HttpEntity<>(headers), String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new ServiceTaskApiException("AP delete flow failed: HTTP " + response.getStatusCode());
        }
    }

    /**
     * 找出 BPMN 里引用了该 flow 的 Function Unit。
     *
     * <p>同时匹配 flow 自身 id 与其迁移键：跨环境导入后，FU 的 BPMN 携带的仍是<b>源环境</b>
     * flowId（=迁移键），本环境实值只在部署产物里（Q7 部署期改写）。只查本地 id 会让
     * 恰恰正在使用的迁移 flow 被判成"无引用"。</p>
     */
    private List<String> findReferencingUnits(String flowId) {
        Map<String, Object> keys;
        try {
            keys = jdbcTemplate.queryForMap(FLOW_REF_KEYS_SQL, flowId);
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("flow not found: " + flowId);
        }
        List<String> refs = new ArrayList<>();
        refs.add(flowId);
        String flowKey = (String) keys.get("flowKey");
        if (flowKey != null && !flowKey.isBlank() && !flowKey.equals(flowId)) {
            refs.add(flowKey);
        }

        String firstLike = "%" + refs.get(0) + "%";
        String lastLike = "%" + refs.get(refs.size() - 1) + "%";
        List<Map<String, Object>> candidates = jdbcTemplate.queryForList(
                PROCESS_CONTENT_CANDIDATES_SQL, firstLike, lastLike, firstLike, lastLike);

        List<String> hits = new ArrayList<>();
        for (Map<String, Object> row : candidates) {
            String decoded = smartDecodeXml((String) row.get("bpmn"));
            if (decoded == null) {
                continue;
            }
            boolean referenced = refs.stream().anyMatch(decoded::contains);
            if (referenced) {
                String unitName = (String) row.get("unitName");
                if (unitName != null && !hits.contains(unitName)) {
                    hits.add(unitName);
                }
            }
        }
        return hits;
    }

    /** 与 DW 的 XmlEncodingUtil.smartDecode 同语义：'<' 开头即明文，否则按 base64 解 */
    private String smartDecodeXml(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String trimmed = content.trim();
        if (trimmed.startsWith("<")) {
            return content;
        }
        try {
            return new String(Base64.getDecoder().decode(trimmed), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            // 既非明文也非合法 base64：无法判定引用，按"可能引用"保守处理
            log.warn("Function unit process content is neither plain XML nor base64; treating as a reference");
            return content;
        }
    }

    @Override
    public List<ConnectionCheckItem> checkConnections(List<String> externalIds) {
        if (externalIds == null || externalIds.isEmpty()) {
            return List.of();
        }
        if (externalIds.size() > 200) {
            throw new IllegalArgumentException("connection 清单过大(>200)");
        }
        String projectId = resolveTargetProjectIdLazily();
        String placeholders = String.join(",", Collections.nCopies(externalIds.size(), "?"));
        // 占位符数量随入参生成,值全部绑定——无标识符拼接
        String sql = "SELECT \"externalId\", \"displayName\", \"pieceName\", status "
                + "FROM app_connection WHERE ? = ANY(\"projectIds\") AND \"externalId\" IN ("
                + placeholders + ")";
        Object[] args = new Object[externalIds.size() + 1];
        args[0] = projectId;
        for (int i = 0; i < externalIds.size(); i++) {
            args[i + 1] = externalIds.get(i);
        }
        Map<String, Map<String, Object>> found = new HashMap<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList(sql, args)) {
            found.put((String) row.get("externalId"), row);
        }
        return externalIds.stream().map(id -> {
            Map<String, Object> row = found.get(id);
            return row == null
                    ? new ConnectionCheckItem(id, false, null, null, null)
                    : new ConnectionCheckItem(id, true,
                            (String) row.get("displayName"),
                            (String) row.get("pieceName"),
                            (String) row.get("status"));
        }).toList();
    }

    @Override
    public Optional<String> resolveFlowRef(String ref) {
        List<String> direct = jdbcTemplate.queryForList(RESOLVE_BY_ID_SQL, String.class, ref);
        if (!direct.isEmpty()) {
            return Optional.of(direct.get(0));
        }
        List<String> mapped = jdbcTemplate.queryForList(RESOLVE_BY_KEY_SQL, String.class, ref);
        return mapped.stream().findFirst();
    }

    // ==================== AP API 写路径 ====================

    private String createFlow(ServiceTaskApiClient.ApSession session, String projectId,
                              String displayName, String flowKey) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("displayName", displayName);
        body.put("projectId", projectId);
        body.set("metadata", objectMapper.createObjectNode().put("hermesFlowKey", flowKey));

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                apUrl("/api/v1/flows"), HttpMethod.POST,
                new HttpEntity<>(body.toString(), jsonHeaders(session.token())),
                new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});
        Object id = response.getBody() != null ? response.getBody().get("id") : null;
        if (!response.getStatusCode().is2xxSuccessful() || id == null) {
            throw new ServiceTaskApiException("AP create flow failed: HTTP " + response.getStatusCode());
        }
        return id.toString();
    }

    /** 合并写回 metadata.hermesFlowKey（经 UPDATE_METADATA 操作,不直写表） */
    private void stampFlowKey(ServiceTaskApiClient.ApSession session, String flowId, String flowKey) {
        ObjectNode metadata;
        try {
            String current = jdbcTemplate.queryForObject(FLOW_METADATA_SQL, String.class, flowId);
            metadata = current == null || current.isBlank()
                    ? objectMapper.createObjectNode()
                    : (ObjectNode) objectMapper.readTree(current);
        } catch (IOException | ClassCastException e) {
            metadata = objectMapper.createObjectNode();
        }
        metadata.put("hermesFlowKey", flowKey);
        ObjectNode request = objectMapper.createObjectNode();
        request.set("metadata", metadata);
        applyFlowOperation(session, flowId, "UPDATE_METADATA", request);
    }

    private void applyFlowOperation(ServiceTaskApiClient.ApSession session, String flowId,
                                    String type, JsonNode request) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("type", type);
        body.set("request", request);
        ResponseEntity<String> response = restTemplate.exchange(
                apUrl("/api/v1/flows/" + flowId), HttpMethod.POST,
                new HttpEntity<>(body.toString(), jsonHeaders(session.token())), String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new ServiceTaskApiException(
                    "AP flow operation " + type + " failed: HTTP " + response.getStatusCode());
        }
    }

    // ==================== helpers ====================

    /** 源环境侧的 connection 清单（导出信息用；查不到的 id 仍列出,只带 externalId） */
    private JsonNode connectionManifest(List<String> connectionIds) {
        var manifest = objectMapper.createArrayNode();
        if (connectionIds.isEmpty()) {
            return manifest;
        }
        List<ConnectionCheckItem> items = checkConnectionsAgainstAnyProject(connectionIds);
        for (ConnectionCheckItem item : items) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("externalId", item.externalId());
            if (item.pieceName() != null) {
                node.put("pieceName", item.pieceName());
            }
            if (item.displayName() != null) {
                node.put("displayName", item.displayName());
            }
            manifest.add(node);
        }
        return manifest;
    }

    /** 导出侧无"目标 project"概念,按 externalId 全平台查（platform 单例部署） */
    private List<ConnectionCheckItem> checkConnectionsAgainstAnyProject(List<String> externalIds) {
        String placeholders = String.join(",", Collections.nCopies(externalIds.size(), "?"));
        String sql = "SELECT \"externalId\", \"displayName\", \"pieceName\", status "
                + "FROM app_connection WHERE \"externalId\" IN (" + placeholders + ")";
        Map<String, Map<String, Object>> found = new HashMap<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList(sql, externalIds.toArray())) {
            found.put((String) row.get("externalId"), row);
        }
        return externalIds.stream().map(id -> {
            Map<String, Object> row = found.get(id);
            return row == null
                    ? new ConnectionCheckItem(id, false, null, null, null)
                    : new ConnectionCheckItem(id, true,
                            (String) row.get("displayName"),
                            (String) row.get("pieceName"),
                            (String) row.get("status"));
        }).toList();
    }

    private List<String> sqlArrayToList(Object sqlArray) {
        if (!(sqlArray instanceof Array array)) {
            return List.of();
        }
        try {
            List<String> result = new ArrayList<>();
            for (Object item : (Object[]) array.getArray()) {
                if (item != null) {
                    result.add(String.valueOf(item));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read connectionIds array", e);
        }
    }

    /** 同 {@link #resolveTargetProjectId} 但按需才做共享账号 sign-in（比对预检不必然需要会话） */
    private String resolveTargetProjectIdLazily() {
        String externalId = serviceTaskProperties.getManaged().getProjectExternalId();
        if (externalId != null && !externalId.isBlank()) {
            List<String> ids = jdbcTemplate.queryForList(
                    PROJECT_BY_EXTERNAL_ID_SQL, String.class, externalId);
            if (!ids.isEmpty()) {
                return ids.get(0);
            }
        }
        return resolveTargetProjectId(serviceTaskApiClient.signInShared());
    }

    /**
     * 导入目标 project：managed（审计到人）配置的共享 project 优先，
     * 未配置或未建时回退共享账号会话自带的 project。
     */
    private String resolveTargetProjectId(ServiceTaskApiClient.ApSession session) {
        String externalId = serviceTaskProperties.getManaged().getProjectExternalId();
        if (externalId != null && !externalId.isBlank()) {
            List<String> ids = jdbcTemplate.queryForList(
                    PROJECT_BY_EXTERNAL_ID_SQL, String.class, externalId);
            if (!ids.isEmpty()) {
                return ids.get(0);
            }
        }
        if (session.projectId() == null) {
            throw new ServiceTaskApiException("Cannot determine target AP project for flow import");
        }
        return session.projectId();
    }

    private JsonNode readExport(byte[] json) {
        JsonNode export;
        try {
            export = objectMapper.readTree(new String(json, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalArgumentException("导入文件不是合法 JSON", e);
        }
        if (export.path("hermesFlowExport").asInt() != EXPORT_FORMAT_VERSION) {
            throw new IllegalArgumentException(
                    "导入文件不是 flow 导出包(缺 hermesFlowExport=" + EXPORT_FORMAT_VERSION + ")");
        }
        if (export.path("flowKey").asText().isBlank()
                || export.path("displayName").asText().isBlank()
                || !export.hasNonNull("trigger")) {
            throw new IllegalArgumentException("flow 导出包缺少 flowKey / displayName / trigger");
        }
        return export;
    }

    private JsonNode parseJson(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.nullNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid json column content", e);
        }
    }

    private String apUrl(String path) {
        String base = serviceTaskProperties.getInternalUrl();
        return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + path;
    }

    private HttpHeaders jsonHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
