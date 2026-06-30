package com.admin.ap.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Activepieces 集成配置。
 *
 * <p>方案：社区版 + 网关共享账号。生产仅作 runtime（不开 UI 登录），非生产经边缘网关 :8085
 * 的「共享账号登录桥」进入 AP。本配置仅服务端使用（用共享账号调 AP sign-in 换取 AP token）。
 */
@ConfigurationProperties(prefix = "activepieces")
@Data
public class ActivepiecesProperties {

    /**
     * AP 服务的服务端可达地址（容器内网，例如 {@code http://activepieces:80}）。
     * 仅用于服务端 sign-in 调用，浏览器永远经 :8085 网关，不直连此地址。
     */
    private String internalUrl = "http://localhost:8086";

    private Bridge bridge = new Bridge();
    private SharedAccount sharedAccount = new SharedAccount();

    /**
     * 登录桥开关。仅非生产开启；生产 runtime 关闭（无 UI 登录入口），
     * 关闭时 {@code /internal/ap/*} 端点返回 404。
     */
    @Data
    public static class Bridge {
        private boolean enabled = false;

        /**
         * AP 网关的桥页公网地址（浏览器侧），例如
         * {@code http://hermes-workflow-activepieces.<域>/__ap/bridge}（dev：{@code http://localhost:8085/__ap/bridge}）。
         *
         * <p>跨域 SSO 握手（方案 B）：admin 域的 {@code /launch} 在此地址后附 {@code #nonce=<一次性票>}，
         * 浏览器整页跳到 AP 域的桥页；桥页凭 nonce 换 AP token，故 AP 域无需平台 JWT cookie。
         * 留空表示未配置 → {@code /launch} 返回 502。
         */
        private String publicUrl = "";

        /** 跨域握手一次性 nonce 的有效期（秒）。短时、单次消费。 */
        private int nonceTtlSeconds = 60;
    }

    /**
     * AP 共享服务账号（platformRole=ADMIN，例如 {@code hermes-svc@platform.local}）。
     * 服务端持有，浏览器永不接触其口令——只拿到换来的 AP token。
     */
    @Data
    public static class SharedAccount {
        private String email;
        private String password;
    }
}
