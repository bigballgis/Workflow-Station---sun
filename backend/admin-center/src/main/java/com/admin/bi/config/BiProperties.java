package com.admin.bi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.regex.Pattern;

/**
 * BI 模块配置属性
 * 包含 Superset 连接配置和同步配置
 */
@ConfigurationProperties(prefix = "bi")
@Data
public class BiProperties {

    private Superset superset = new Superset();
    private Sync sync = new Sync();

    /**
     * Superset 连接配置
     */
    @Data
    public static class Superset {
        private static final Pattern PG_IDENTIFIER = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

        /** Superset 服务地址 */
        private String host = "http://localhost:8088";
        /** 前端嵌入 Superset 使用的公开地址（可选） */
        private String publicHost = "http://localhost:8088";
        /**
         * Superset 元数据表所在 PostgreSQL schema（与 Flask-SQLAlchemy / Superset 安装一致）。
         * 默认 {@code public}；若 Superset 使用独立 schema（如 {@code superset}），通过环境变量 {@code SUPERSET_DB_SCHEMA} 覆盖。
         */
        private String dbSchema = "public";
        /** Superset 管理员用户名 */
        private String adminUsername = "admin";
        /** Superset 管理员密码 */
        private String adminPassword = "admin";
        /** Guest Token 超时时间（秒） */
        private int guestTokenTimeoutSeconds = 30;

        /**
         * 用于拼接原生 SQL 的 schema 名；非法标识符回退为 {@code public}，避免配置注入。
         */
        public String resolveDbSchemaForSql() {
            String s = dbSchema == null || dbSchema.isBlank() ? "public" : dbSchema.trim();
            if (!PG_IDENTIFIER.matcher(s).matches()) {
                return "public";
            }
            return s;
        }
    }

    /**
     * 同步配置
     */
    @Data
    public static class Sync {
        /** 同步 cron 表达式，默认每6小时执行一次 */
        private String cron = "0 0 */6 * * ?";
        /** 是否启用定时同步 */
        private boolean enabled = true;
    }
}
