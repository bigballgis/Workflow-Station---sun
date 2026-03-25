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
import java.util.HashMap;
import java.util.Map;

/**
 * Runs once at startup to log which database the app is actually connected to.
 */
@Component
@Order(Integer.MAX_VALUE)
@RequiredArgsConstructor
@Slf4j
public class DataSourceDiagnosticRunner implements ApplicationRunner {

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

        log.info("DataSource diagnostic: jdbcUrl={}, currentDatabase={}, serverAddr={}, serverPort={}, biDashboardRegistryExists={}",
                jdbcUrl, data.get("currentDatabase"), data.get("serverAddr"), data.get("serverPort"), data.get("biDashboardRegistryExists"));
    }
}
