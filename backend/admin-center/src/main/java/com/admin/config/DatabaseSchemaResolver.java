package com.admin.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 从 spring.datasource.url (JDBC URL) 中解析 PostgreSQL schema。
 *
 * <p>优先顺序：
 * <ol>
 *   <li>URL 查询串中的 {@code currentSchema=...} 参数（取第一个 schema，忽略逗号后备项）；</li>
 *   <li>回退为 {@code public}。</li>
 * </ol>
 *
 * <p>避免在代码或 application.yml 中再显式维护一个 schema 配置项——各环境的
 * {@code SPRING_DATASOURCE_URL} 已经是唯一真相。
 */
@Component
@Slf4j
public class DatabaseSchemaResolver {

    private static final String DEFAULT_SCHEMA = "public";
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    private final String schema;

    public DatabaseSchemaResolver(@Value("${spring.datasource.url:}") String jdbcUrl) {
        this.schema = resolve(jdbcUrl);
        log.info("DatabaseSchemaResolver: resolved schema='{}' from JDBC URL", this.schema);
    }

    /**
     * 返回已解析并校验过的 schema 标识符，可直接用于 SQL 拼接。
     */
    public String getSchema() {
        return schema;
    }

    static String resolve(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return DEFAULT_SCHEMA;
        }
        int q = jdbcUrl.indexOf('?');
        if (q < 0 || q == jdbcUrl.length() - 1) {
            return DEFAULT_SCHEMA;
        }
        // 某些 .env 文件可能残留结尾的引号，直接剥掉以免干扰解析
        String query = jdbcUrl.substring(q + 1).replace("\"", "").replace("'", "");
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0 || eq == pair.length() - 1) {
                continue;
            }
            String key = pair.substring(0, eq).trim();
            String value = pair.substring(eq + 1).trim();
            if (!"currentSchema".equalsIgnoreCase(key) || value.isEmpty()) {
                continue;
            }
            // PostgreSQL 驱动支持逗号分隔的 schema 搜索路径，列表中第一个即为默认写入 schema
            String first = value.split(",")[0].trim();
            if (SAFE_IDENTIFIER.matcher(first).matches()) {
                return first;
            }
            log.warn("Ignoring unsafe currentSchema='{}', falling back to '{}'", first, DEFAULT_SCHEMA);
            return DEFAULT_SCHEMA;
        }
        return DEFAULT_SCHEMA;
    }
}
