package com.admin.ldap;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.Control;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.PagedResultsControl;
import javax.naming.ldap.PagedResultsResponseControl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * 基于 JDK 原生 JNDI 的 LDAP/AD 访问封装（仅 {@code ldap.enabled=true} 时创建 Bean）。
 *
 * <p>职责：服务账号连接、用户 DN 搜索、账号口令 bind 校验、分页全量/增量拉取、按 DN 取属性。
 * 不含落库与业务编排（由 {@code LdapUserSyncService} / {@code LdapAuthenticator} 负责）。</p>
 *
 * <p>安全：日志不打印明文密码；DN/username 等敏感信息仅在 DEBUG 级输出（生产 INFO 不泄露）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ldap", name = "enabled", havingValue = "true")
public class LdapClient {

    private static final String CTX_FACTORY = "com.sun.jndi.ldap.LdapCtxFactory";
    private static final String CONNECT_TIMEOUT_KEY = "com.sun.jndi.ldap.connect.timeout";
    private static final String READ_TIMEOUT_KEY = "com.sun.jndi.ldap.read.timeout";

    private final LdapProperties props;

    /**
     * 启动时配置自定义 LDAPS truststore（含 HSBC CA 根证书）。
     * 设置 JVM 级 trustStore 系统属性；未配置则沿用 JVM 默认信任库。
     *
     * <p>传输安全 fail-fast：simple bind 会把服务账号与用户口令原文发给 LDAP 服务器，
     * 因此除非显式 {@code ldap.allow-insecure=true}（仅本地 mock OpenLDAP 联调），
     * 连接必须为 ldaps:// 或 {@code ldap.tls=true}，否则启动即失败，防止生产误配明文过网。</p>
     */
    @PostConstruct
    void configureTrustStore() {
        String url = props.getProviderUrl() == null ? "" : props.getProviderUrl().trim().toLowerCase(java.util.Locale.ROOT);
        boolean cleartext = url.startsWith("ldap://") && !props.isTls();
        if (cleartext && !props.isAllowInsecure()) {
            throw new IllegalStateException(
                    "LDAP is configured with cleartext transport (ldap:// and ldap.tls=false). "
                            + "Credentials would cross the network unencrypted. Use ldaps:// / LDAP_TLS=true, "
                            + "or set ldap.allow-insecure=true explicitly for local mock testing only.");
        }
        if (cleartext) {
            log.warn("LDAP cleartext transport explicitly allowed (ldap.allow-insecure=true) — local/mock use only");
        }
        if (StringUtils.hasText(props.getKeystorePath())) {
            System.setProperty("javax.net.ssl.trustStore", props.getKeystorePath());
            if (StringUtils.hasText(props.getKeystorePassword())) {
                System.setProperty("javax.net.ssl.trustStorePassword", props.getKeystorePassword());
            }
            log.info("LDAP custom truststore configured");
        }
    }

    /**
     * 按 uid → samAccountName → cn → employeeID 顺序搜索用户 DN。
     *
     * @return 用户完整 DN；未命中返回 empty
     * @throws NamingException 连接/搜索异常（调用方据此决定是否回退本地）
     */
    public Optional<String> findUserDn(String username) throws NamingException {
        String escaped = escapeFilterValue(username);
        LdapProperties.Attributes a = props.getAttributes();
        List<String> attrCandidates = List.of(
                a.getUid(), a.getSamAccountName(), a.getCn(), a.getEmployeeId());

        DirContext ctx = openServiceContext();
        try {
            for (String attr : attrCandidates) {
                String dn = searchSingleDn(ctx, "(" + attr + "=" + escaped + ")");
                if (dn != null) {
                    log.debug("Resolved user DN via attribute {}", attr);
                    return Optional.of(dn);
                }
            }
            return Optional.empty();
        } finally {
            closeQuietly(ctx);
        }
    }

    /**
     * 用用户 DN + 明文密码做 LDAP simple bind 校验。
     *
     * @return {@code true}=口令正确；{@code false}=口令错误（确定性失败，不应回退本地）
     * @throws NamingException 连接异常（非认证失败），调用方可回退本地
     */
    public boolean bindAuthenticate(String userDn, String password) throws NamingException {
        if (!StringUtils.hasText(userDn) || !StringUtils.hasText(password)) {
            return false;
        }
        Hashtable<String, Object> env = baseEnv(userDn, password);
        DirContext userCtx = null;
        try {
            userCtx = new InitialLdapContext(env, null);
            return true;
        } catch (javax.naming.AuthenticationException e) {
            // 口令错误：确定性失败
            return false;
        } finally {
            closeQuietly(userCtx);
        }
    }

    /** 全量拉取用户（分页）。 */
    public List<Map<String, String>> fetchAllUsers() throws NamingException {
        return pagedSearch(props.getUserSearchFilter());
    }

    /**
     * 增量拉取：在基础用户过滤上叠加额外条件（如 whenChanged 水位）。
     *
     * @param extraFilter 形如 {@code (whenChanged>=20260101000000.0Z)}；为空时等价全量
     */
    public List<Map<String, String>> fetchUsersWithFilter(String extraFilter) throws NamingException {
        String base = props.getUserSearchFilter();
        String combined = StringUtils.hasText(extraFilter) ? "(&" + base + extraFilter + ")" : base;
        return pagedSearch(combined);
    }

    /** 按 DN 取单用户属性（用于登录 JIT）。 */
    public Optional<Map<String, String>> getUserAttributes(String userDn) throws NamingException {
        DirContext ctx = openServiceContext();
        try {
            Attributes attrs = ctx.getAttributes(userDn, retrieveAttributeNames());
            return Optional.of(flatten(attrs));
        } finally {
            closeQuietly(ctx);
        }
    }

    /** 按 employeeID 取单用户属性（用于同步时补齐一层 EM/FM 画像）。 */
    public Optional<Map<String, String>> getUserAttributesByEmployeeId(String employeeId) throws NamingException {
        if (!StringUtils.hasText(employeeId)) {
            return Optional.empty();
        }
        DirContext ctx = openServiceContext();
        try {
            String attr = props.getAttributes().getEmployeeId();
            String dn = searchSingleDn(ctx, "(" + attr + "=" + escapeFilterValue(employeeId) + ")");
            if (dn == null) {
                return Optional.empty();
            }
            Attributes attrs = ctx.getAttributes(dn, retrieveAttributeNames());
            return Optional.of(flatten(attrs));
        } finally {
            closeQuietly(ctx);
        }
    }

    /**
     * 在 group-search-base-dn 下按 CN 搜索组，返回组 DN。
     *
     * @param groupCn 组的 CN（如 {@code Infodir-Hermes-Default-UAT-User}）
     * @return 组 DN；未命中返回 empty
     */
    public Optional<String> findGroupDnByCn(String groupCn) throws NamingException {
        String escaped = escapeFilterValue(groupCn);
        String groupSearchBase = getGroupSearchBaseDn();
        DirContext ctx = openServiceContext();
        try {
            SearchControls controls = new SearchControls();
            controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            controls.setReturningAttributes(new String[]{"cn", "member", "whenChanged"});
            controls.setCountLimit(1);
            NamingEnumeration<SearchResult> results =
                    ctx.search(groupSearchBase, "(cn=" + escaped + ")", controls);
            try {
                if (results.hasMore()) {
                    SearchResult sr = results.next();
                    String dn = sr.getNameInNamespace();
                    log.debug("Resolved group DN for CN {}: {}", groupCn, dn);
                    return Optional.of(dn);
                }
                return Optional.empty();
            } finally {
                closeQuietly(results);
            }
        } finally {
            closeQuietly(ctx);
        }
    }

    /**
     * 读取组的 member 属性（支持 AD ranged 分段读取，步长 500），返回所有 member DN 列表。
     * <p>AD 大组场景：member 属性值过多时，AD 返回 {@code member;range=0-499} 而非完整 member。
     * 本方法自动检测 range 分段并迭代拉取直到返回 {@code member} 或 {@code member;range=xxx-*}。</p>
     */
    public List<String> fetchGroupMemberDns(String groupDn) throws NamingException {
        List<String> members = new ArrayList<>();
        DirContext ctx = openServiceContext();
        try {
            int step = 500;
            int start = 0;
            while (true) {
                String rangedAttr = "member;range=" + start + "-" + (start + step - 1);
                // 首次尝试 ranged；若不存在则回退普通 member
                String[] attrIds = start == 0
                        ? new String[]{rangedAttr, "member"}
                        : new String[]{rangedAttr};
                boolean viaRanged = true;
                Attributes attrs;
                try {
                    attrs = ctx.getAttributes(groupDn, attrIds);
                } catch (NamingException e) {
                    // 当前没有更多 ranged 段或属性不存在，回退尝试普通 member（仅首次）
                    if (start == 0) {
                        attrs = ctx.getAttributes(groupDn, new String[]{"member"});
                        viaRanged = false;
                    } else {
                        break;
                    }
                }

                Attribute memberAttr = attrs.get(rangedAttr);
                if (memberAttr == null && start == 0) {
                    memberAttr = attrs.get("member");
                    viaRanged = false;
                }
                if (memberAttr == null) {
                    break;
                }

                int count = 0;
                NamingEnumeration<?> values = memberAttr.getAll();
                try {
                    while (values.hasMore()) {
                        Object v = values.next();
                        if (v != null) {
                            members.add(String.valueOf(v));
                            count++;
                        }
                    }
                } finally {
                    closeQuietly(values);
                }

                // 判断是否读取结束
                String attrId = memberAttr.getID();
                if (!viaRanged || attrId.equalsIgnoreCase("member") || attrId.endsWith("*")) {
                    break;
                }
                start += count;
                if (count < step) {
                    break;
                }
            }
        } finally {
            closeQuietly(ctx);
        }
        log.debug("Fetched {} member DNs from group DN {}", members.size(), groupDn);
        return members;
    }

    /**
     * 拉取一个 AD 组内的所有用户（完整 pipeline：按 CN 找组 DN → 读取 member DNs → 逐个取用户属性）。
     *
     * @param groupCn 组的 CN
     * @return 扁平化的用户属性列表（每行包含 memberOf 等完整属性）
     */
    public List<Map<String, String>> fetchUsersInGroup(String groupCn) throws NamingException {
        Optional<String> groupDnOpt = findGroupDnByCn(groupCn);
        if (groupDnOpt.isEmpty()) {
            log.warn("AD group not found: CN={}", groupCn);
            return List.of();
        }
        String groupDn = groupDnOpt.get();
        List<String> memberDns = fetchGroupMemberDns(groupDn);
        log.info("AD group CN={} has {} member DNs", groupCn, memberDns.size());

        if (memberDns.isEmpty()) {
            return fetchUsersByMemberOf(groupCn, groupDn);
        }

        List<Map<String, String>> users = new ArrayList<>();
        for (String memberDn : memberDns) {
            try {
                Attributes attrs = getRawAttributes(memberDn);
                Map<String, String> flat = flatten(attrs);
                // 注入命中组名，用于后续虚拟组映射
                flat.put("_hitGroupCn", groupCn);
                users.add(flat);
            } catch (NamingException e) {
                log.warn("Failed to read attributes for member DN {}: {}", memberDn, e.getMessage());
            }
            if (reachedLimit(users)) {
                break;
            }
        }
        return users;
    }

    /** 组 member 属性为空或不可读时，反向搜索用户侧 memberOf。 */
    private List<Map<String, String>> fetchUsersByMemberOf(String groupCn, String groupDn) throws NamingException {
        String filter = "(memberOf=" + escapeFilterValue(groupDn) + ")";
        List<Map<String, String>> users = fetchUsersWithFilter(filter);
        users.forEach(user -> user.put("_hitGroupCn", groupCn));
        log.info("AD group CN={} memberOf fallback matched {} users", groupCn, users.size());
        return users;
    }

    /**
     * 检查 AD 组对象自 {@code watermark} 以来是否发生变更。
     * <p>在 group-search-base-dn 中按 CN 查询组，读取 whenChanged 属性与 watermark 比较。</p>
     *
     * @return {@code true}=已变更（需要重新同步）；{@code false}=未变更或组不存在
     */
    public boolean hasGroupChangedSince(String groupCn, Instant watermark) {
        try {
            String escaped = escapeFilterValue(groupCn);
            String groupSearchBase = getGroupSearchBaseDn();
            DirContext ctx = openServiceContext();
            try {
                SearchControls controls = new SearchControls();
                controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
                controls.setReturningAttributes(new String[]{"whenChanged", "modifyTimestamp"});
                controls.setCountLimit(1);
                NamingEnumeration<SearchResult> results =
                        ctx.search(groupSearchBase, "(cn=" + escaped + ")", controls);
                try {
                    if (results.hasMore()) {
                        Attributes attrs = results.next().getAttributes();
                        Attribute whenChangedAttr = attrs.get("whenChanged");
                        if (whenChangedAttr != null && whenChangedAttr.size() > 0) {
                            String whenChangedStr = String.valueOf(whenChangedAttr.get(0));
                            Instant groupChanged = parseGeneralizedTime(whenChangedStr);
                            if (groupChanged != null) {
                                return !groupChanged.isBefore(watermark);
                            }
                        }
                    }
                } finally {
                    closeQuietly(results);
                }
            } finally {
                closeQuietly(ctx);
            }
        } catch (NamingException e) {
            log.warn("Failed to check group change for CN={}: {}", groupCn, e.getMessage());
        }
        // 查询失败时保守处理：认为有变更，触发同步
        return true;
    }

    /** 按 DN 获取原始 Attributes（用于内部 pipeline）。 */
    Attributes getRawAttributes(String dn) throws NamingException {
        DirContext ctx = openServiceContext();
        try {
            return ctx.getAttributes(dn, retrieveAttributeNames());
        } finally {
            closeQuietly(ctx);
        }
    }

    /** 解析 AD generalized time 格式（yyyyMMddHHmmss[.0Z]）。 */
    static Instant parseGeneralizedTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String cleaned = value.replaceAll("\\..*", "").trim();
        if (cleaned.length() >= 14) {
            cleaned = cleaned.substring(0, 14);
        }
        try {
            return java.time.LocalDateTime.parse(cleaned,
                            java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                    .atZone(java.time.ZoneOffset.UTC)
                    .toInstant();
        } catch (Exception e) {
            log.debug("Failed to parse AD generalized time: {}", value);
            return null;
        }
    }

    // ==================== 内部实现 ====================

    /** 在 baseDn 下做 SUBTREE 搜索，返回首个命中 DN。 */
    private String searchSingleDn(DirContext ctx, String filter) throws NamingException {
        SearchControls controls = new SearchControls();
        controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        controls.setReturningAttributes(new String[0]);
        controls.setCountLimit(1);
        NamingEnumeration<SearchResult> results = ctx.search(props.getBaseDn(), filter, controls);
        try {
            if (results.hasMore()) {
                return results.next().getNameInNamespace();
            }
            return null;
        } finally {
            closeQuietly(results);
        }
    }

    /** RFC 2696 分页搜索，逐页累积；受 {@code maxEntries} 截断保护。 */
    private List<Map<String, String>> pagedSearch(String filter) throws NamingException {
        List<Map<String, String>> out = new ArrayList<>();
        LdapContext ctx = (LdapContext) openServiceContext();
        try {
            ctx.setRequestControls(new Control[]{
                    new PagedResultsControl(props.getPageSize(), Control.NONCRITICAL)});
            byte[] cookie = null;
            do {
                cookie = collectOnePage(ctx, filter, out);
                ctx.setRequestControls(new Control[]{
                        new PagedResultsControl(props.getPageSize(), cookie, Control.NONCRITICAL)});
            } while (cookie != null && cookie.length != 0 && !reachedLimit(out));
            return out;
        } catch (java.io.IOException e) {
            throw new NamingException("LDAP paged control error: " + e.getMessage());
        } finally {
            closeQuietly(ctx);
        }
    }

    /** 收集一页结果到 out，返回下一页 cookie。 */
    private byte[] collectOnePage(LdapContext ctx, String filter, List<Map<String, String>> out)
            throws NamingException {
        SearchControls controls = new SearchControls();
        controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        controls.setReturningAttributes(retrieveAttributeNames());
        NamingEnumeration<SearchResult> page = ctx.search(props.getBaseDn(), filter, controls);
        try {
            while (page.hasMore() && !reachedLimit(out)) {
                out.add(flatten(page.next().getAttributes()));
            }
        } finally {
            closeQuietly(page);
        }
        return extractCookie(ctx.getResponseControls());
    }

    private boolean reachedLimit(List<Map<String, String>> out) {
        return props.getMaxEntries() > 0 && out.size() >= props.getMaxEntries();
    }

    private byte[] extractCookie(Control[] controls) {
        if (controls == null) {
            return null;
        }
        for (Control c : controls) {
            if (c instanceof PagedResultsResponseControl prrc) {
                return prrc.getCookie();
            }
        }
        return null;
    }

    /** Attributes → 大小写不敏感的 {@code 属性名 -> 首值字符串}。 */
    private Map<String, String> flatten(Attributes attrs) throws NamingException {
        Map<String, String> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (attrs == null) {
            return map;
        }
        NamingEnumeration<? extends Attribute> all = attrs.getAll();
        try {
            while (all.hasMore()) {
                Attribute attr = all.next();
                Object value = attr.size() > 0 ? attr.get(0) : null;
                if (value != null) {
                    map.put(attr.getID(), String.valueOf(value));
                }
            }
        } finally {
            closeQuietly(all);
        }
        return map;
    }

    /** 获取组搜索基准 DN（优先 group-sync.search-base-dn，其次 ldap.base-dn）。 */
    private String getGroupSearchBaseDn() {
        String specific = props.getGroupSync().getSearchBaseDn();
        return (specific != null && !specific.isBlank()) ? specific : props.getBaseDn();
    }

    private String[] retrieveAttributeNames() {
        List<String> attrs = props.getRetrieveAttributes();
        if (attrs == null || attrs.isEmpty()) {
            return null; // null = 返回全部属性
        }
        return attrs.toArray(new String[0]);
    }

    private DirContext openServiceContext() throws NamingException {
        return new InitialLdapContext(baseEnv(props.getBindDn(), props.getBindPassword()), null);
    }

    private Hashtable<String, Object> baseEnv(String principal, String credentials) {
        Hashtable<String, Object> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, CTX_FACTORY);
        env.put(Context.PROVIDER_URL, props.getProviderUrl());
        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        env.put(Context.SECURITY_PRINCIPAL, principal == null ? "" : principal);
        env.put(Context.SECURITY_CREDENTIALS, credentials == null ? "" : credentials);
        env.put(CONNECT_TIMEOUT_KEY, String.valueOf(props.getConnectTimeoutMs()));
        env.put(READ_TIMEOUT_KEY, String.valueOf(props.getReadTimeoutMs()));
        if (props.isTls()) {
            env.put(Context.SECURITY_PROTOCOL, "ssl");
        }
        return env;
    }

    /** 转义 LDAP 过滤值，防注入：\ * ( ) NUL / 。 */
    static String escapeFilterValue(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\5c");
                case '*' -> sb.append("\\2a");
                case '(' -> sb.append("\\28");
                case ')' -> sb.append("\\29");
                case '\u0000' -> sb.append("\\00");
                case '/' -> sb.append("\\2f");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private void closeQuietly(NamingEnumeration<?> e) {
        if (e != null) {
            try {
                e.close();
            } catch (NamingException ignored) {
                // 关闭枚举失败不影响结果
            }
        }
    }

    private void closeQuietly(Context ctx) {
        if (ctx != null) {
            try {
                ctx.close();
            } catch (NamingException ignored) {
                // 关闭上下文失败不影响结果
            }
        }
    }
}
