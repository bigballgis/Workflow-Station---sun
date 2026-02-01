package com.platform.common.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Database Configuration
 * 
 * Externalized database connection and pool settings
 * 
 * @author Platform Team
 * @version 1.0
 */
public class DatabaseConfig {
    
    @NotBlank(message = "Database URL is required")
    private String url = "jdbc:postgresql://localhost:5432/workflow_platform";
    
    @NotBlank(message = "Database username is required")
    private String username = "platform";
    
    @NotBlank(message = "Database password is required")
    private String password = "platform123";
    
    @NotBlank(message = "Database driver class is required")
    private String driverClassName = "org.postgresql.Driver";
    
    @Min(value = 1, message = "Maximum connections must be at least 1")
    @Max(value = 100, message = "Maximum connections cannot exceed 100")
    private int maxConnections = 20;
    
    @Min(value = 0, message = "Minimum idle connections cannot be negative")
    private int minIdleConnections = 5;
    
    @Min(value = 1000, message = "Connection timeout must be at least 1000ms")
    private long connectionTimeoutMs = 20000;
    
    @Min(value = 30000, message = "Idle timeout must be at least 30 seconds")
    private long idleTimeoutMs = 300000;
    
    @Min(value = 600000, message = "Max lifetime must be at least 10 minutes")
    private long maxLifetimeMs = 1200000;
    
    @NotBlank(message = "Pool name is required")
    private String poolName = "PlatformHikariPool";
    
    private boolean showSql = false;
    private boolean formatSql = true;
    
    @NotBlank(message = "Database dialect is required")
    private String dialect = "org.hibernate.dialect.PostgreSQLDialect";
    
    @NotBlank(message = "Timezone is required")
    private String timezone = "UTC";
    
    // Getters and Setters
    public String getUrl() {
        return url;
    }
    
    public void setUrl(String url) {
        this.url = url;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
    
    public String getDriverClassName() {
        return driverClassName;
    }
    
    public void setDriverClassName(String driverClassName) {
        this.driverClassName = driverClassName;
    }
    
    public int getMaxConnections() {
        return maxConnections;
    }
    
    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }
    
    public int getMinIdleConnections() {
        return minIdleConnections;
    }
    
    public void setMinIdleConnections(int minIdleConnections) {
        this.minIdleConnections = minIdleConnections;
    }
    
    public long getConnectionTimeoutMs() {
        return connectionTimeoutMs;
    }
    
    public void setConnectionTimeoutMs(long connectionTimeoutMs) {
        this.connectionTimeoutMs = connectionTimeoutMs;
    }
    
    public long getIdleTimeoutMs() {
        return idleTimeoutMs;
    }
    
    public void setIdleTimeoutMs(long idleTimeoutMs) {
        this.idleTimeoutMs = idleTimeoutMs;
    }
    
    public long getMaxLifetimeMs() {
        return maxLifetimeMs;
    }
    
    public void setMaxLifetimeMs(long maxLifetimeMs) {
        this.maxLifetimeMs = maxLifetimeMs;
    }
    
    public String getPoolName() {
        return poolName;
    }
    
    public void setPoolName(String poolName) {
        this.poolName = poolName;
    }
    
    public boolean isShowSql() {
        return showSql;
    }
    
    public void setShowSql(boolean showSql) {
        this.showSql = showSql;
    }
    
    public boolean isFormatSql() {
        return formatSql;
    }
    
    public void setFormatSql(boolean formatSql) {
        this.formatSql = formatSql;
    }
    
    public String getDialect() {
        return dialect;
    }
    
    public void setDialect(String dialect) {
        this.dialect = dialect;
    }
    
    public String getTimezone() {
        return timezone;
    }
    
    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }
}