package com.admin.service.impl;

import com.admin.dto.response.AutomationPieceSummary;
import com.admin.service.AutomationPieceService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

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
import java.util.List;
import java.util.Map;
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
@RequiredArgsConstructor
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

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public List<AutomationPieceSummary> listPieces() {
        return jdbcTemplate.query(LIST_SQL, (rs, rowNum) -> {
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
        });
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
