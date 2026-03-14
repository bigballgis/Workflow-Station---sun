package com.admin.bi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

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
        /** Superset 服务地址 */
        private String host = "http://localhost:8088";
        /** Superset 管理员用户名 */
        private String adminUsername = "admin";
        /** Superset 管理员密码 */
        private String adminPassword = "admin";
        /** Guest Token 超时时间（秒） */
        private int guestTokenTimeoutSeconds = 30;
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
