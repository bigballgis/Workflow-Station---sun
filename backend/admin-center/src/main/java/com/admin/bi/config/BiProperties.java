package com.admin.bi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.regex.Pattern;

/**
 * BI module configuration properties.
 * Contains Superset connection config and sync config.
 */
@ConfigurationProperties(prefix = "bi")
@Data
public class BiProperties {

    private Superset superset = new Superset();
    private Sync sync = new Sync();

    /**
     * Superset connection configuration.
     */
    @Data
    public static class Superset {
        private static final Pattern PG_IDENTIFIER = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

        /** Superset service address */
        private String host = "http://localhost:8088";
        /** Public address for embedding Superset in the frontend (optional) */
        private String publicHost = "http://localhost:8088";
        /**
         * PostgreSQL schema where Superset metadata tables reside (must match Flask-SQLAlchemy / Superset setup).
         * Defaults to {@code public}; override via env var {@code SUPERSET_DB_SCHEMA} if Superset uses a dedicated schema (e.g. {@code superset}).
         */
        private String dbSchema = "superset";
        /** Superset admin username (env var: BI_SUPERSET_ADMIN_USERNAME) */
        private String adminUsername;
        /** Superset admin password (env var: BI_SUPERSET_ADMIN_PASSWORD) */
        private String adminPassword;
        /** Guest token timeout in seconds */
        private int guestTokenTimeoutSeconds = 30;

        /**
         * Schema name used for building native SQL; invalid identifiers fall back to {@code public} to prevent config injection.
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
     * Sync configuration.
     */
    @Data
    public static class Sync {
        /** Sync cron expression; defaults to every 6 hours */
        private String cron = "0 0 */6 * * ?";
        /** Whether scheduled sync is enabled */
        private boolean enabled = true;
    }
}
