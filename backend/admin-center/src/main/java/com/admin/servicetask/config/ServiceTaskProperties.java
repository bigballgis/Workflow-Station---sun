package com.admin.servicetask.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Activepieces 集成配置。
 *
 * <p>方案：社区版 + 网关登录桥。生产仅作 runtime（不开 UI 登录），非生产经边缘网关 :8085
 * 进入 AP。<b>身份一律 per-user</b>：服务端按当前操作人签发 managed 外部 token 换 AP 会话
 * （共享账号已移除），私钥仅服务端持有。
 */
@ConfigurationProperties(prefix = "service-task")
@Data
public class ServiceTaskProperties {

    /**
     * AP 服务的服务端可达地址（容器内网，例如 {@code http://activepieces:80}）。
     * 仅用于服务端调用，浏览器永远经 :8085 网关，不直连此地址。
     */
    private String internalUrl = "http://localhost:8086";

    private Bridge bridge = new Bridge();
    private Managed managed = new Managed();

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
     * Per-user provisioning（审计到人）配置——<b>AP 身份的唯一路径</b>（没有开关，没有回退）。
     * 按当前操作人签发 AP 外部 token（RS256），换取<b>该用户专属</b>的 AP token——
     * AP 侧 {@code user.externalId = 平台 userId}，每一步 AP 操作天然映射回发起人。
     *
     * <p>依赖 AP 的 {@code /v1/managed-authn/external-token} 端点（vendored CE 重写）。
     * 签名私钥来自一次性 {@code POST /v1/signing-keys}（平台 admin），其 {@code id} 作为 JWT
     * header 的 {@code kid}，AP 据此查 publicKey 验签。私钥仅 HERMES 持有（secret），浏览器永不接触。
     *
     * <p><b>未配置</b>（signing-key-id / private-key 为空）时，任何 AP 操作在调用点
     * fail-loud 抛 {@code ACTIVEPIECES_API_ERROR}，而不是悄悄换一个别的身份继续。
     */
    @Data
    public static class Managed {
        /** AP 签名密钥 id（{@code POST /v1/signing-keys} 返回的 id），作为外部 token 的 {@code kid}。 */
        private String signingKeyId;

        /** 签名私钥（PKCS8 PEM，{@code BEGIN PRIVATE KEY}）。secret，仅服务端持有。 */
        private String privateKey;

        /**
         * 共享 project 的外部 id（{@code externalProjectId}）。所有 per-user 会话绑定到这一个
         * 共享 project（Q4a 共享-project 模型），AP 首次见到时按此 id getOrCreate。
         *
         * <p><b>默认值必须与运维侧 stamp 的值一致</b>：`ap-provision-db.js`、
         * `ap-verify-provisioning.js`、`build-and-deploy.ps1` 与 PROD_WIRING_RUNBOOK 全部用
         * {@code hermes-main}。此处曾是 {@code hermes-shared}——因为 managed 以前默认关闭，
         * 这个默认值从不生效；去掉开关后它就成了活的，取错值会让 AP 对<b>每个用户各建一个
         * project</b>（getOrCreate 找不到就新建），侧栏出现重名 project。
         */
        private String projectExternalId = "hermes-main";

        /** 外部 token 有效期（秒），短时——仅用于换取 AP token 的一次握手。 */
        private int tokenTtlSeconds = 120;

        /**
         * <b>SYS_ADMIN</b> 影子用户的 AP 平台角色（{@code ADMIN} / {@code MEMBER}）。默认
         * {@code ADMIN}：平台级页面（AI provider、piece 管理、签名密钥）需要它，而这些入口在
         * admin-center 侧本就只对 SYS_ADMIN 开放。
         */
        private String platformRole = "ADMIN";

        /**
         * 非 SYS_ADMIN 影子用户的 AP 平台角色。<b>必须是 {@code MEMBER}</b>：AP 的
         * {@code projectMemberService.getRole} 对平台 {@code ADMIN} 直接返回全平台 project 的
         * {@code Admin} 角色（{@code OPERATOR} 同理返回 {@code Editor}），会<b>整条旁路</b>
         * workspace 隔离与 Public 只读——把每个人都签成平台 ADMIN 时，project 成员表形同虚设。
         * {@code MEMBER} 才会落到 {@code project_member} 的实际角色上。
         */
        private String memberPlatformRole = "MEMBER";

        /**
         * 影子用户在其 workspace project 里的角色，逐字对应 AP {@code project_role.name}
         * （{@code Admin} / {@code Editor} / {@code Viewer}）。默认 {@code Admin}。
         */
        private String projectRole = "Admin";

        /**
         * 只读 workspace 使用的 AP project 角色（{@code project_role.name}）。默认 {@code Viewer}
         * ——AP CE 的 rbac 在路由层按角色权限判定（{@code Viewer} 只有 READ_*），故这是
         * <b>真实的写入边界</b>，不是前端遮罩：Public workspace 的非 SYS_ADMIN 用它签会话。
         */
        private String readOnlyProjectRole = "Viewer";

        /**
         * DW 内置 Public 开发组的虚拟组 id（与 DW {@code DevGroupConstants.PUBLIC_GROUP_ID}
         * 同值，由种子脚本固定写入）。它映射到 {@link #projectExternalId}（历史共享 project），
         * 因此存量 flow 无需迁移即成为「Public workspace」的内容。
         */
        private String publicGroupId = "vg-dev-public";

        /**
         * 团队 workspace 的 AP {@code externalProjectId} 前缀：团队虚拟组 {@code <id>} 映射到
         * {@code <prefix><id>}。AP 的 managed-authn 首次见到该 externalId 时自建 TEAM project
         * 并写入成员，故无需运维预建。
         */
        private String teamProjectExternalIdPrefix = "hermes-dg-";
    }
}
