package com.admin.service.impl;

import com.admin.dto.response.AutomationPieceSummary;
import com.admin.exception.ServiceTaskApiException;
import com.admin.servicetask.CurrentActor;
import com.admin.service.AutomationPieceService;
import com.admin.servicetask.client.ServiceTaskApiClient;
import com.admin.servicetask.config.ServiceTaskProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import com.admin.config.RestTemplateConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * piece 目录只读实现。
 *
 * <p>与 AP 共库(见 DECISIONS Q5,schema 隔离未实施前同 public schema),此处
 * 仅做只读 SELECT;列名为 TypeORM 生成的 camelCase 引号标识符。SQL 全部静态、
 * 参数一律绑定,无标识符拼接。</p>
 */
@Slf4j
@Service
public class AutomationPieceServiceImpl implements AutomationPieceService {

    private static final String LIST_SQL = """
            SELECT id, name, "displayName", description, "logoUrl", version,
                   "pieceType", "packageType", "archiveId", "platformId",
                   actions, triggers, categories, authors,
                   "minimumSupportedRelease", "maximumSupportedRelease",
                   "projectUsage", created, updated
            FROM piece_metadata
            ORDER BY name ASC, version DESC
            """;

    private static final String EXPORT_SQL = """
            SELECT name, version, "displayName", "logoUrl", description,
                   "minimumSupportedRelease", "maximumSupportedRelease",
                   auth, actions, triggers, categories, authors, i18n, "archiveId"
            FROM piece_metadata
            WHERE name = ? AND version = ?
            LIMIT 1
            """;

    private static final String ARCHIVE_SQL = "SELECT data FROM file WHERE id = ?";

    /**
     * HERMES-PATCH-030: 自研 piece 启停的存储。
     *
     * <p>原先查的是 {@code platform.filteredPieceNames}。AP 0.88 的迁移
     * DropPlatformPieceFilters1809000000000（上游自己标了 breaking=true）删掉了那两列，
     * 替代机制 piece_set 属 EE、已随 EE 剥离 —— 列不存在导致本方法抛 SQLException，
     * Automation Pieces 整页 500（2026-08 UAT 事故）。
     *
     * <p>现在落在 HERMES 自有的 hermes_piece_block 表（automation 侧
     * app/pieces/hermes-piece-block.entity.ts + 迁移 1826000000000）。表名带 hermes_ 前缀，
     * 不与上游冲突；AP 的 piece list 读同一张表做过滤。
     */
    private static final String DISABLED_NAMES_SQL =
            "SELECT \"pieceName\" FROM hermes_piece_block";

    /** 停用：写入即生效（AP 的 list() 每次读库，无缓存窗口）。重复停用是幂等的。 */
    private static final String BLOCK_PIECE_SQL =
            "INSERT INTO hermes_piece_block (\"pieceName\", \"blockedAt\", \"blockedBy\") "
                    + "VALUES (?, now(), ?) ON CONFLICT (\"pieceName\") DO NOTHING";

    /** 启用：删掉屏蔽行。 */
    private static final String UNBLOCK_PIECE_SQL =
            "DELETE FROM hermes_piece_block WHERE \"pieceName\" = ?";

    /** flow_version 的 trigger JSON 内嵌整条 step 链,包名以带引号字符串出现 */
    /**
     * 占用该 piece 的 flow 名称。取每个 flow 最新版本的 displayName——报"被 1 个 flow 占用"
     * 而不给名字时,管理员无从判断能不能删。
     */
    private static final String FLOW_REF_SQL = """
            SELECT DISTINCT ON (v."flowId") coalesce(nullif(v."displayName", ''), v."flowId")
            FROM flow_version v
            WHERE v."flowId" IN (
                SELECT DISTINCT "flowId" FROM flow_version
                WHERE trigger::text LIKE '%"' || ? || '"%'
            )
            ORDER BY v."flowId", v.created DESC
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ServiceTaskApiClient serviceTaskApiClient;
    private final ServiceTaskProperties serviceTaskProperties;
    /** AP control-plane calls only — long read timeout, own breaker (see RestTemplateConfig). */
    private final RestTemplate restTemplate;

    public AutomationPieceServiceImpl(JdbcTemplate jdbcTemplate,
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
    public List<AutomationPieceSummary> listPieces() {
        Set<String> disabledNames = fetchDisabledNames();
        return jdbcTemplate.query(LIST_SQL, (rs, rowNum) -> mapRow(rs, disabledNames));
    }

    @Override
    public List<AutomationPieceSummary> findPiecesByNames(List<String> names) {
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        Set<String> disabledNames = fetchDisabledNames();
        String placeholders = String.join(",", names.stream().map(n -> "?").toList());
        String sql = """
                SELECT id, name, "displayName", description, "logoUrl", version,
                       "pieceType", "packageType", "archiveId", "platformId",
                       actions, triggers, categories, authors,
                       "minimumSupportedRelease", "maximumSupportedRelease",
                       "projectUsage", created, updated
                FROM piece_metadata
                WHERE name IN (""" + placeholders + ") ORDER BY name ASC, version DESC";
        return jdbcTemplate.query(sql, (rs, rowNum) -> mapRow(rs, disabledNames), names.toArray());
    }

    private AutomationPieceSummary mapRow(ResultSet rs, Set<String> disabledNames) throws SQLException {
        List<String> actionNames = jsonKeys(rs.getString("actions"));
        List<String> triggerNames = jsonKeys(rs.getString("triggers"));
        return AutomationPieceSummary.builder()
                .id(rs.getString("id"))
                .name(rs.getString("name"))
                .displayName(rs.getString("displayName"))
                .description(rs.getString("description"))
                .logoUrl(rs.getString("logoUrl"))
                .version(rs.getString("version"))
                .pieceType(rs.getString("pieceType"))
                .packageType(rs.getString("packageType"))
                .hasArchive(rs.getString("archiveId") != null)
                .disabled(disabledNames.contains(rs.getString("name")))
                .platformId(rs.getString("platformId"))
                .actionCount(actionNames.size())
                .triggerCount(triggerNames.size())
                .actionNames(actionNames)
                .triggerNames(triggerNames)
                .categories(sqlArrayToList(rs, "categories"))
                .authors(sqlArrayToList(rs, "authors"))
                .minimumSupportedRelease(rs.getString("minimumSupportedRelease"))
                .maximumSupportedRelease(rs.getString("maximumSupportedRelease"))
                .projectUsage(rs.getInt("projectUsage"))
                .created(rs.getObject("created", OffsetDateTime.class))
                .updated(rs.getObject("updated", OffsetDateTime.class))
                .build();
    }

    @Override
    public PieceExportFile exportPiece(String name, String version) {
        Map<String, Object> row;
        try {
            row = jdbcTemplate.queryForMap(EXPORT_SQL, name, version);
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("piece not found: " + name + "@" + version);
        }

        // 与 deploy/pieces/serialize-piece-metadata.js 输出同构(字段名与顺序一致),
        // 导出文件可直接落进 deploy/pieces/metadata/ 供 generate-metadata-seed.js 消费
        ObjectNode meta = objectMapper.createObjectNode();
        meta.put("name", (String) row.get("name"));
        meta.put("version", (String) row.get("version"));
        meta.put("displayName", (String) row.get("displayName"));
        meta.put("logoUrl", (String) row.get("logoUrl"));
        meta.put("description", (String) row.get("description"));
        meta.put("minimumSupportedRelease", (String) row.get("minimumSupportedRelease"));
        meta.put("maximumSupportedRelease", (String) row.get("maximumSupportedRelease"));
        meta.set("auth", parseJsonColumn(row.get("auth")));
        meta.set("actions", parseJsonColumn(row.get("actions")));
        meta.set("triggers", parseJsonColumn(row.get("triggers")));
        meta.set("categories", toArrayNode(row.get("categories")));
        meta.set("authors", toArrayNode(row.get("authors")));
        meta.set("i18n", parseJsonColumn(row.get("i18n")));

        // 包短名:@activepieces/piece-xxx → piece-xxx(与 metadata/ 文件命名约定一致)
        String shortName = name.contains("/") ? name.substring(name.indexOf('/') + 1) : name;
        byte[] metaBytes = toPrettyBytes(meta);

        String archiveId = (String) row.get("archiveId");
        if (archiveId == null) {
            return new PieceExportFile(shortName + ".json", "application/json", metaBytes);
        }

        byte[] archive = jdbcTemplate.queryForObject(ARCHIVE_SQL, byte[].class, archiveId);
        byte[] zip = buildZip(Map.of(
                shortName + ".json", metaBytes,
                shortName + "-" + version + ".tgz", archive));
        return new PieceExportFile(shortName + "-" + version + "-bundle.zip", "application/zip", zip);
    }

    // ==================== P2 写路径:一律经 AP API,不直写表 ====================

    @Override
    public PieceImportResult importPiece(byte[] tarball, String filename) {
        JsonNode pkg = readPackageJsonFromTarball(tarball);
        String name = pkg.path("name").asText(null);
        String version = pkg.path("version").asText(null);
        if (name == null || version == null || !version.matches("\\d+\\.\\d+\\.\\d+")) {
            throw new IllegalArgumentException(
                    "tarball 内 package/package.json 缺少合法 name/version(须为 build-piece 产物)");
        }

        ServiceTaskApiClient.ApSession session = serviceTaskApiClient.signInAsCurrentActor();

        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("packageType", "ARCHIVE");
        form.add("scope", "PLATFORM");
        form.add("pieceName", name);
        form.add("pieceVersion", version);
        form.add("pieceArchive", new ByteArrayResource(tarball) {
            @Override
            public String getFilename() {
                return filename != null ? filename : "piece.tgz";
            }
        });

        HttpHeaders headers = bearerHeaders(session.token());
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                apUrl("/api/v1/pieces"), HttpMethod.POST,
                new HttpEntity<>(form, headers),
                new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new ServiceTaskApiException("AP install piece failed: HTTP " + response.getStatusCode());
        }
        Object displayName = response.getBody().get("displayName");
        return new PieceImportResult(name, version, displayName != null ? displayName.toString() : name);
    }

    @Override
    public void deletePiece(String name, String version, boolean force) {
        if (!force) {
            List<String> refs = jdbcTemplate.queryForList(FLOW_REF_SQL, String.class, name);
            if (!refs.isEmpty()) {
                throw new PieceInUseException(name, refs);
            }
        }
        ServiceTaskApiClient.ApSession session = serviceTaskApiClient.signInAsCurrentActor();
        HttpHeaders headers = bearerHeaders(session.token());
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> body = Map.of("pieceName", name, "pieceVersion", version);
        restTemplate.exchange(apUrl("/api/v1/pieces"), HttpMethod.DELETE,
                new HttpEntity<>(body, headers), Void.class);
    }

    @Override
    public void setPieceDisabled(String name, boolean disabled) {
        // HERMES-PATCH-030: 自研实现，替代上游删掉的 platform 级 piece 过滤。
        //
        // 语义与 0.84 一致 —— 只影响设计器目录，不影响运行：AP 的 piece list() 读同一张
        // hermes_piece_block 过滤，而 get() 刻意不过滤，所以已经引用了该 piece 的存量 flow
        // 照常加载、照常执行。停用是「不让人再选它」，不是「把已有的打断」。
        //
        // 直接写库而非经 AP REST：这是 HERMES 自有的表（不是上游的），AP 与平台共库，
        // 且 AP 的 list() 每次读库无结果缓存，写入即生效，不需要缓存失效握手。
        String actor = CurrentActor.require().getUserId();
        if (disabled) {
            jdbcTemplate.update(BLOCK_PIECE_SQL, name, actor);
        }
        else {
            jdbcTemplate.update(UNBLOCK_PIECE_SQL, name);
        }
    }

    /**
     * 读取被停用的 piece 名单（HERMES-PATCH-030 起落在 hermes_piece_block）。
     *
     * <p>不吞异常：读不到就让调用方失败。吞掉会静默退化成「所有 piece 都可见」，
     * 管理员以为停用生效、设计器里那个 piece 照常出现 —— 比报错更难排查。
     */
    private Set<String> fetchDisabledNames() {
        return new HashSet<>(jdbcTemplate.queryForList(DISABLED_NAMES_SQL, String.class));
    }

    /**
     * 从 npm pack 产物(gzip tar)里取出 {@code package/package.json}。
     * tar 格式:512 字节头(name@0..100,size 八进制@124..136),数据按 512 对齐。
     * 手写极简解析,避免为此引入新依赖。
     */
    private JsonNode readPackageJsonFromTarball(byte[] tarball) {
        try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(tarball))) {
            byte[] tar = gis.readAllBytes();
            int offset = 0;
            while (offset + 512 <= tar.length) {
                String entryName = new String(tar, offset, 100, StandardCharsets.US_ASCII)
                        .split("\0", 2)[0];
                if (entryName.isEmpty()) {
                    break;
                }
                String sizeOctal = new String(tar, offset + 124, 12, StandardCharsets.US_ASCII)
                        .replace("\0", "").trim();
                long size = sizeOctal.isEmpty() ? 0 : Long.parseLong(sizeOctal, 8);
                if (entryName.equals("package/package.json")) {
                    return objectMapper.readTree(
                            new String(tar, offset + 512, (int) size, StandardCharsets.UTF_8));
                }
                offset += 512 + (int) ((size + 511) / 512 * 512);
            }
            throw new IllegalArgumentException("tarball 里没有 package/package.json(不是 npm pack 产物?)");
        } catch (IOException e) {
            throw new IllegalArgumentException("无法解压 tarball(须为 .tgz)", e);
        }
    }

    private String apUrl(String path) {
        String base = serviceTaskProperties.getInternalUrl();
        return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + path;
    }

    private HttpHeaders bearerHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private List<String> jsonKeys(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            List<String> keys = new ArrayList<>();
            node.fieldNames().forEachRemaining(keys::add);
            return keys;
        } catch (IOException e) {
            log.warn("Failed to parse piece json column: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private JsonNode parseJsonColumn(Object value) {
        if (value == null) {
            return objectMapper.nullNode();
        }
        try {
            return objectMapper.readTree(value.toString());
        } catch (IOException e) {
            throw new UncheckedIOException("Invalid json column content", e);
        }
    }

    private JsonNode toArrayNode(Object sqlArray) {
        var arrayNode = objectMapper.createArrayNode();
        if (sqlArray instanceof Array array) {
            try {
                for (Object item : (Object[]) array.getArray()) {
                    arrayNode.add(String.valueOf(item));
                }
            } catch (SQLException e) {
                log.warn("Failed to read sql array column: {}", e.getMessage());
            }
        }
        return arrayNode;
    }

    private List<String> sqlArrayToList(ResultSet rs, String column) throws SQLException {
        Array array = rs.getArray(column);
        if (array == null) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (Object item : (Object[]) array.getArray()) {
            result.add(String.valueOf(item));
        }
        return result;
    }

    private byte[] toPrettyBytes(ObjectNode node) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(node);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private byte[] buildZip(Map<String, byte[]> entries) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(entry.getKey()));
                zos.write(entry.getValue());
                zos.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to build export zip", e);
        }
        return bos.toByteArray();
    }
}
