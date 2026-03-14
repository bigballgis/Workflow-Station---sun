package com.admin.bi.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;

/**
 * Runs once at startup to log which database the app is actually connected to.
 * Writes NDJSON to debug log file for session bfd681.
 */
@Component
@Order(Integer.MAX_VALUE)
@RequiredArgsConstructor
@Slf4j
public class DataSourceDiagnosticRunner implements ApplicationRunner {

    private static final String DEBUG_LOG_PATH = "/Users/qiweige/Desktop/PROJECTXXXSUN/Workflow-Station---sun/.cursor/debug-bfd681.log";

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void run(ApplicationArguments args) {
        Map<String, Object> data = new HashMap<>();
        String jdbcUrl = "unknown";
        try {
            jdbcUrl = dataSource.getConnection().getMetaData().getURL();
            if (jdbcUrl != null && jdbcUrl.contains("password=")) {
                jdbcUrl = jdbcUrl.replaceAll("password=[^&]+", "password=***");
            }
        } catch (Exception e) {
            data.put("urlError", e.getMessage());
        }
        data.put("jdbcUrlMasked", jdbcUrl);

        try {
            String db = jdbcTemplate.queryForObject("SELECT current_database()", String.class);
            data.put("currentDatabase", db);
        } catch (Exception e) {
            data.put("currentDatabaseError", e.getMessage());
        }
        try {
            String addr = jdbcTemplate.queryForObject("SELECT inet_server_addr()::text", String.class);
            data.put("serverAddr", addr);
        } catch (Exception e) {
            data.put("serverAddrError", e.getMessage());
        }
        try {
            Integer port = jdbcTemplate.queryForObject("SELECT inet_server_port()", Integer.class);
            data.put("serverPort", port);
        } catch (Exception e) {
            data.put("serverPortError", e.getMessage());
        }
        try {
            String schema = jdbcTemplate.queryForObject("SELECT current_schema()", String.class);
            data.put("currentSchema", schema);
        } catch (Exception e) {
            data.put("currentSchemaError", e.getMessage());
        }
        try {
            Boolean exists = jdbcTemplate.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'bi_dashboard_registry')",
                    Boolean.class);
            data.put("biDashboardRegistryExists", exists);
        } catch (Exception e) {
            data.put("biDashboardRegistryExistsError", e.getMessage());
        }

        // #region agent log
        try {
            String dataJson = objectMapper.writeValueAsString(data);
            String ndjson = String.format("{\"sessionId\":\"bfd681\",\"runId\":\"startup\",\"hypothesisId\":\"A\",\"location\":\"DataSourceDiagnosticRunner.run\",\"message\":\"DB connection diagnostic\",\"data\":%s,\"timestamp\":%d}%n",
                    dataJson, System.currentTimeMillis());
            Path path = Paths.get(DEBUG_LOG_PATH);
            Files.createDirectories(path.getParent());
            Files.write(path, ndjson.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            log.warn("Could not write debug log file (e.g. when running in Docker): {}", e.getMessage());
        }
        // #endregion

        log.info("DataSource diagnostic: jdbcUrl={}, currentDatabase={}, serverAddr={}, serverPort={}, biDashboardRegistryExists={}",
                jdbcUrl, data.get("currentDatabase"), data.get("serverAddr"), data.get("serverPort"), data.get("biDashboardRegistryExists"));
        try {
            log.info("DEBUG_DB_CONNECTION: {}", objectMapper.writeValueAsString(data));
        } catch (Exception ignored) { }
    }
}
