package com.admin.ldap;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.naming.CommunicationException;
import javax.naming.Context;
import javax.naming.InvalidNameException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.Control;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.PagedResultsControl;
import javax.naming.ldap.PagedResultsResponseControl;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
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

    private static final String RANGED_MEMBER_PREFIX = "member;range=";

    private static final String CTX_FACTORY = "com.sun.jndi.ldap.LdapCtxFactory";
    private static final String CONNECT_TIMEOUT_KEY = "com.sun.jndi.ldap.connect.timeout";
    private static final String READ_TIMEOUT_KEY = "com.sun.jndi.ldap.read.timeout";
    private static final String SOCKET_FACTORY_KEY = "java.naming.ldap.factory.socket";

    private final LdapProperties props;

    /**
     * Why the configured truststore could not be loaded; every LDAP connection then fails with this text instead of
     * falling back to the JVM's default trust. {@code null} when it loaded or none is configured.
     */
    private String trustStoreFailure;

    /**
     * 启动时加载自定义 LDAPS truststore（含 HSBC CA 根证书），只给 LDAP 连接用（{@link LdapTrustStoreSocketFactory}），
     * 不改 JVM 级 {@code javax.net.ssl.trustStore}，Admin Center 其它出站 TLS 仍用 JVM 默认信任库；未配置则 LDAP 也用默认。
     * 读不了时不阻止启动，但所有 LDAP 连接都报明确的错误。
     *
     * <p>传输安全 fail-fast：simple bind 会把服务账号与用户口令原文发给 LDAP 服务器，
     * 因此除非显式 {@code ldap.allow-insecure=true}（仅本地 mock OpenLDAP 联调），
     * 连接必须为 ldaps:// 或 {@code ldap.tls=true}，否则启动即失败，防止生产误配明文过网。</p>
     */
    @PostConstruct
    void configureTransport() {
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
        if (StringUtils.hasText(props.getKeystorePath()) && !usesTls()) {
            log.warn("ldap.keystore-path is set but LDAP connections do not use TLS; the truststore is not used");
        } else if (StringUtils.hasText(props.getKeystorePath())) {
            try {
                LdapTrustStoreSocketFactory.load(props.getKeystorePath(), props.getKeystorePassword());
                log.info("LDAP custom truststore loaded; it applies to LDAP connections only");
            } catch (IOException | GeneralSecurityException e) {
                trustStoreFailure = "LDAP truststore " + props.getKeystorePath() + " could not be loaded: "
                        + e.getMessage();
                log.error("{}. Every LDAP connection fails until it is fixed.", trustStoreFailure);
            }
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
            userCtx = connect(env);
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
            Attributes attrs = ctx.getAttributes(ldapName(userDn), retrieveAttributeNames());
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
            Attributes attrs = ctx.getAttributes(ldapName(dn), retrieveAttributeNames());
            return Optional.of(flatten(attrs));
        } finally {
            closeQuietly(ctx);
        }
    }

    /**
     * 在 group-search-base-dn 下按 CN 搜索组，返回组 DN。
     *
     * @param groupCn 组的 CN（如 {@code Finance-Approvers}）
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
                    ctx.search(ldapName(groupSearchBase), "(cn=" + escaped + ")", controls);
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
     * 读取组的全部 member DN；大组的分段读取规则见 {@link #readMemberRanges}。
     */
    public List<String> fetchGroupMemberDns(String groupDn) throws NamingException {
        LdapName group = ldapName(groupDn);
        DirContext ctx = openServiceContext();
        try {
            List<String> members = readMemberRanges(attrIds -> ctx.getAttributes(group, attrIds));
            log.debug("Fetched {} member DNs from group DN {}", members.size(), groupDn);
            return members;
        } finally {
            closeQuietly(ctx);
        }
    }

    /** Reads one request of attributes of the group entry. */
    @FunctionalInterface
    interface MemberAttributeReader {
        Attributes read(String[] attrIds) throws NamingException;
    }

    /**
     * Reads every value of {@code member}, following AD ranged retrieval: each request asks for
     * {@code member;range=<start>-*}; AD answers with {@code member;range=<start>-<end>} while more values
     * follow and with {@code member;range=<start>-*} for the range holding the last one. A server without
     * ranged retrieval answers with plain {@code member}. A failure after the first range is thrown, so the
     * caller does not mistake a cut-off list for the whole group.
     */
    static List<String> readMemberRanges(MemberAttributeReader reader) throws NamingException {
        List<String> members = new ArrayList<>();
        int start = 0;
        while (true) {
            Attributes attrs;
            if (start == 0) {
                try {
                    attrs = reader.read(new String[]{RANGED_MEMBER_PREFIX + "0-*", "member"});
                } catch (NamingException e) {
                    // A server that rejects the range option: read the plain attribute.
                    attrs = reader.read(new String[]{"member"});
                }
            } else {
                attrs = reader.read(new String[]{RANGED_MEMBER_PREFIX + start + "-*"});
            }
            Attribute memberAttr = memberAttribute(attrs);
            if (memberAttr == null) {
                return members;
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
            String attrId = memberAttr.getID().toLowerCase(Locale.ROOT);
            if (!attrId.startsWith(RANGED_MEMBER_PREFIX) || attrId.endsWith("-*") || count == 0) {
                return members;
            }
            start += count;
        }
    }

    /** The member attribute in an answer, preferring a ranged one over plain {@code member}. */
    private static Attribute memberAttribute(Attributes attrs) throws NamingException {
        if (attrs == null) {
            return null;
        }
        Attribute plain = null;
        NamingEnumeration<? extends Attribute> all = attrs.getAll();
        try {
            while (all.hasMore()) {
                Attribute attr = all.next();
                String id = attr.getID().toLowerCase(Locale.ROOT);
                if (id.startsWith(RANGED_MEMBER_PREFIX)) {
                    return attr;
                }
                if (id.equals("member")) {
                    plain = attr;
                }
            }
        } finally {
            closeQuietly(all);
        }
        return plain;
    }

    /**
     * 拉取一个 AD 组内的所有用户（完整 pipeline：按 CN 找组 DN → 读取 member DNs → 逐个取用户属性）。
     *
     * @param groupCn 组的 CN
     * @return 扁平化的用户属性列表（每行包含 memberOf 等完整属性），以及是否读全：有成员读不出属性、
     *         或因 {@code max-entries} 截断时为不完整，调用方不得据此判断谁已离开该组
     */
    public AdGroupMembers fetchUsersInGroup(String groupCn) throws NamingException {
        Optional<String> groupDnOpt = findGroupDnByCn(groupCn);
        if (groupDnOpt.isEmpty()) {
            log.warn("AD group not found: CN={}", groupCn);
            return new AdGroupMembers(List.of(), true);
        }
        String groupDn = groupDnOpt.get();
        List<String> memberDns = fetchGroupMemberDns(groupDn);
        log.info("AD group CN={} has {} member DNs", groupCn, memberDns.size());

        if (memberDns.isEmpty()) {
            List<Map<String, String>> users = fetchUsersByMemberOf(groupCn, groupDn);
            return new AdGroupMembers(users, !reachedLimit(users));
        }

        List<Map<String, String>> users = new ArrayList<>();
        boolean complete = true;
        for (String memberDn : memberDns) {
            try {
                Attributes attrs = getRawAttributes(memberDn);
                Map<String, String> flat = flatten(attrs);
                // 注入命中组名，用于后续虚拟组映射
                flat.put("_hitGroupCn", groupCn);
                users.add(flat);
            } catch (NamingException e) {
                log.warn("Failed to read attributes for member DN {}: {}", memberDn, e.getMessage());
                complete = false;
            }
            if (reachedLimit(users)) {
                complete = complete && users.size() == memberDns.size();
                break;
            }
        }
        return new AdGroupMembers(users, complete);
    }

    /** Members read from one AD group; {@code complete} is false when some could not be read. */
    public record AdGroupMembers(List<Map<String, String>> users, boolean complete) {
    }

    /** 组 member 属性为空或不可读时，反向搜索用户侧 memberOf。 */
    private List<Map<String, String>> fetchUsersByMemberOf(String groupCn, String groupDn) throws NamingException {
        String filter = "(memberOf=" + escapeFilterValue(groupDn) + ")";
        List<Map<String, String>> users = fetchUsersWithFilter(filter);
        users.forEach(user -> user.put("_hitGroupCn", groupCn));
        log.info("AD group CN={} memberOf fallback matched {} users", groupCn, users.size());
        return users;
    }

    /** 按 DN 获取原始 Attributes（用于内部 pipeline）。 */
    Attributes getRawAttributes(String dn) throws NamingException {
        DirContext ctx = openServiceContext();
        try {
            return ctx.getAttributes(ldapName(dn), retrieveAttributeNames());
        } finally {
            closeQuietly(ctx);
        }
    }

    // ==================== 内部实现 ====================

    /** 在 baseDn 下做 SUBTREE 搜索，返回首个命中 DN。 */
    private String searchSingleDn(DirContext ctx, String filter) throws NamingException {
        SearchControls controls = new SearchControls();
        controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        controls.setReturningAttributes(new String[0]);
        controls.setCountLimit(1);
        NamingEnumeration<SearchResult> results = ctx.search(ldapName(props.getBaseDn()), filter, controls);
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
        NamingEnumeration<SearchResult> page = ctx.search(ldapName(props.getBaseDn()), filter, controls);
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
                    if (value instanceof byte[] bytes) {
                        map.put(attr.getID(), java.util.Base64.getEncoder().encodeToString(bytes));
                    } else {
                        map.put(attr.getID(), String.valueOf(value));
                    }
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

    /**
     * A DN in the form JNDI must receive it. The {@code String} overloads of {@code DirContext} read the name as a
     * composite name, in which '/' separates components, so {@code CN=Ops/Dana,OU=People,...} would be cut at the
     * '/' and the server asked for another entry (LDAP 32). An {@link LdapName} reaches the server as one DN, escaped
     * commas and multi-valued RDNs included. Every read or search by DN goes through here; a malformed DN is a
     * {@link NamingException} like any other read failure.
     */
    static LdapName ldapName(String dn) throws InvalidNameException {
        return new LdapName(dn);
    }

    private DirContext openServiceContext() throws NamingException {
        return connect(baseEnv(props.getBindDn(), props.getBindPassword()));
    }

    /**
     * Opens the connection. JNDI loads the socket factory by name through the thread's context class loader, which on
     * a pool thread (the startup sync runs on one) is the system class loader and does not see Admin Center's own
     * classes in the packaged jar; this class's loader does.
     */
    private static LdapContext connect(Hashtable<String, Object> env) throws NamingException {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        thread.setContextClassLoader(LdapClient.class.getClassLoader());
        try {
            return new InitialLdapContext(env, null);
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    private Hashtable<String, Object> baseEnv(String principal, String credentials) throws NamingException {
        if (trustStoreFailure != null) {
            throw new CommunicationException(trustStoreFailure);
        }
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
        if (StringUtils.hasText(props.getKeystorePath()) && usesTls()) {
            env.put(SOCKET_FACTORY_KEY, LdapTrustStoreSocketFactory.class.getName());
        }
        return env;
    }

    /** Whether connections are TLS from the start: {@code ldaps://}, or {@code ldap.tls=true}. */
    private boolean usesTls() {
        return props.isTls() || (props.getProviderUrl() != null
                && props.getProviderUrl().trim().toLowerCase(Locale.ROOT).startsWith("ldaps://"));
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

    private static void closeQuietly(NamingEnumeration<?> e) {
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
