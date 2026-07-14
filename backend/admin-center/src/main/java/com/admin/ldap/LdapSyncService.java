package com.admin.ldap;
import com.admin.repository.RoleRepository;
import com.admin.repository.UserRepository;
import com.admin.repository.VirtualGroupMemberRepository;
import com.admin.repository.VirtualGroupRepository;
import com.admin.repository.VirtualGroupRoleRepository;
import com.platform.security.entity.Role;
import com.platform.security.entity.RoleAssignment;
import com.platform.security.entity.VirtualGroup;
import com.platform.security.entity.VirtualGroupMember;
import com.platform.security.entity.VirtualGroupRole;
import com.platform.security.enums.AssignmentTargetType;
import com.platform.security.repository.RoleAssignmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.LinkedHashSet;
import static com.admin.ldap.LdapConstants.AD_ATTR_MEMBER_OF;
import static com.admin.ldap.LdapConstants.GROUP_ROLE_KEY_ADMIN;
import static com.admin.ldap.LdapConstants.GROUP_ROLE_KEY_DEVELOPER;
import static com.admin.ldap.LdapConstants.GROUP_ROLE_KEY_USER;
import static com.admin.ldap.LdapConstants.HERMES_SYNC_MEMBER_ADDED_BY;
import static com.admin.ldap.LdapConstants.LDAP_SYNC_ACTOR;
import static com.admin.ldap.LdapConstants.ROLE_AUDITOR;
import static com.admin.ldap.LdapConstants.ROLE_DEVELOPER;
import static com.admin.ldap.LdapConstants.ROLE_SYSTEM_ADMIN;
import static com.admin.ldap.LdapConstants.ROLE_TEAM_LEAD;
import static com.admin.ldap.LdapConstants.ROLE_TECH_LEAD;
import static com.admin.ldap.LdapConstants.SYNC_TYPE_HERMES_AD_GROUP;
import static com.admin.ldap.LdapConstants.SYNC_TYPE_HERMES_AD_INCR;
import static com.admin.ldap.LdapConstants.VG_AUDITORS;
import static com.admin.ldap.LdapConstants.VG_DEVELOPERS;
import static com.admin.ldap.LdapConstants.VG_HERMES_USERS;
import static com.admin.ldap.LdapConstants.VG_SYSTEM_ADMINISTRATORS;
import static com.admin.ldap.LdapConstants.VG_TEAM_LEADS;
import static com.admin.ldap.LdapConstants.VG_TECH_LEADS;
/**
 * Hermes AD Group → Admin Center 用户/虚拟组/角色 同步服务。
 *
 * <p>核心职责：
 * <ol>
 *   <li>解析目标 AD 组清单（显式映射 / roles+pattern 拼接）</li>
 *   <li>确保 Hermes 角色、虚拟组、角色绑定、权限分配基础数据存在</li>
 *   <li>全量同步：拉取每组所有成员 → 去重 → 分批 upsert → 写虚拟组关系</li>
 *   <li>增量同步：基于审计水位检测变更组 → 只同步变化部分 → 补充用户侧变更</li>
 *   <li>统一写审计（{@link LdapSyncAudit}）</li>
 * </ol>
 *
 * <p>安全：LDAP-only 用户写入不可用占位密码（见 {@link LdapUserSyncService}）；
 * {@code memberOf} 敏感信息仅 DEBUG 级输出。</p>
 *
 * <p>仅 {@code ldap.enabled=true} 且 {@code ldap.sync-enabled=true} 时创建。</p>
 *
 * <p>FALLBACK(external) — class-wide policy: this is a periodic sync job against an external
 * directory. Per-group / per-batch catches deliberately tolerate individual failures (one bad
 * AD group must not abort the whole sync; batch upsert degrades to per-user), and the run-level
 * catch records the failure into {@link LdapSyncAudit} — failures are persisted and visible,
 * never silent. Do not "fix" these catches into rethrows: aborting the run on the first bad
 * entry is worse for directory convergence than a partial sync plus an audit trail.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ldap", name = {"enabled", "sync-enabled"}, havingValue = "true")
public class LdapSyncService {
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    /** Hermes AD 组键 → 虚拟组 → 角色编码 预定义绑定。 */
    private static final List<HermesGroupBinding> HERMES_BINDINGS = List.of(
            new HermesGroupBinding(GROUP_ROLE_KEY_ADMIN, VG_SYSTEM_ADMINISTRATORS, "System Administrators", ROLE_SYSTEM_ADMIN, "System Administrator", "ADMIN"),
            new HermesGroupBinding(GROUP_ROLE_KEY_ADMIN, VG_AUDITORS, "Auditors", ROLE_AUDITOR, "Auditor", "ADMIN"),
            new HermesGroupBinding(GROUP_ROLE_KEY_DEVELOPER, VG_TECH_LEADS, "Technical Leads", ROLE_TECH_LEAD, "Technical Lead", "DEVELOPER"),
            new HermesGroupBinding(GROUP_ROLE_KEY_DEVELOPER, VG_TEAM_LEADS, "Team Leads", ROLE_TEAM_LEAD, "Team Lead", "DEVELOPER"),
            new HermesGroupBinding(GROUP_ROLE_KEY_DEVELOPER, VG_DEVELOPERS, "Developers", ROLE_DEVELOPER, "Developer", "DEVELOPER"),
            new HermesGroupBinding(GROUP_ROLE_KEY_USER, VG_HERMES_USERS, "Hermes Default Users", null, null, null));private final LdapClient ldapClient;
    private final LdapUserMapper ldapUserMapper;
    private final LdapUserSyncService ldapUserSyncService;
    private final LdapProperties ldapProperties;
    private final LdapSyncAuditRepository auditRepository;
    private final VirtualGroupRepository virtualGroupRepository;
    private final VirtualGroupMemberRepository virtualGroupMemberRepository;
    private final VirtualGroupRoleRepository virtualGroupRoleRepository;
    private final RoleRepository roleRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final UserRepository userRepository;
    private final Environment environment;
    private final TransactionTemplate transactionTemplate;
    // ==================== 全量 AD 组同步 ====================
    /**
     * 全量 Hermes AD 组同步：解析目标组 → 确保绑定存在 → 清理旧成员 → 拉取 → upsert → 写组成员关系。
     */
    public LdapSyncAudit runHermesAdGroupSync() {
        long start = System.currentTimeMillis();
        LdapSyncAudit audit = startAudit(SYNC_TYPE_HERMES_AD_GROUP);
        try {
            Map<String, String> groupDefs = resolveGroupDefinitions();
            if (groupDefs.isEmpty()) {
                return finishSuccess(audit, 0, start, "No Hermes AD groups configured");
            }
            audit.setGroups(String.join(",", groupDefs.values()));
            // 1. 确保 Admin Center 侧角色/虚拟组/绑定存在
            ensureHermesBindings(groupDefs);
            // 2. 清理本次涉及虚拟组的旧 LDAP 管理成员
            cleanOldLdapMembershipsForBindings();
            // 3. 按组拉取成员并聚合（一个用户可命中多个组）
            Map<String, LdapGroupUserAccumulator> accumulator = new LinkedHashMap<>();
            int totalFetched = 0;
            for (Map.Entry<String, String> entry : groupDefs.entrySet()) {
                String roleKey = entry.getKey();
                String groupCn = entry.getValue();
                try {
                    List<Map<String, String>> users = ldapClient.fetchUsersInGroup(groupCn);
                    totalFetched += users.size();
                    for (Map<String, String> userAttrs : users) {
                        String employeeId = userAttrs.get(ldapProperties.getAttributes().getEmployeeId());
                        if (!StringUtils.hasText(employeeId)) {
                            continue;
                        }
                        accumulator.computeIfAbsent(employeeId, k -> new LdapGroupUserAccumulator(userAttrs))
                                .addHitGroup(groupCn, roleKey);
                    }
                } catch (Exception e) {
                    log.warn("Failed to fetch users from AD group CN={}: {}", groupCn, e.getMessage());
                }
            }
            log.info("Hermes AD group sync: {} unique users from {} groups",
                    accumulator.size(), groupDefs.size());
            int supplementalManagerFetched = supplementDirectManagers(accumulator);
            totalFetched += supplementalManagerFetched;
            // 4. 分批 upsert 用户（含逐条失败降级）
            int successCount = 0;
            int insertCount = 0;
            int updateCount = 0;
            int skippedMissingKey = 0;
            Map<String, String> syncedUserIdsByEmployeeId = new LinkedHashMap<>();
            int batchSize = ldapProperties.getGroupSync().getBatchSize();
            if (batchSize <= 0) {
                batchSize = 20;
            }
            List<Map.Entry<String, LdapGroupUserAccumulator>> entries =
                    new ArrayList<>(accumulator.entrySet());
            for (int i = 0; i < entries.size(); i += batchSize) {
                int end = Math.min(i + batchSize, entries.size());
                List<Map.Entry<String, LdapGroupUserAccumulator>> batch =
                        entries.subList(i, end);
                try {
                    UpsertBatchResult result = upsertBatchWithFallback(batch);
                    successCount += result.success();
                    insertCount += result.insert();
                    updateCount += result.update();
                    skippedMissingKey += result.skipped();
                    syncedUserIdsByEmployeeId.putAll(result.syncedUserIdsByEmployeeId());
                } catch (Exception e) {
                    // 整批失败时降级为逐条
                    log.warn("Batch upsert failed, falling back to individual: {}", e.getMessage());
                    UpsertBatchResult result = upsertBatchWithFallback(batch);
                    successCount += result.success();
                    insertCount += result.insert();
                    updateCount += result.update();
                    skippedMissingKey += result.skipped();
                    syncedUserIdsByEmployeeId.putAll(result.syncedUserIdsByEmployeeId());
                }
            }
            // 5. 同步用户与虚拟组关系
            syncVirtualGroupMemberships(entries, groupDefs, syncedUserIdsByEmployeeId);
            // 6. 数据驱动的团队组同步（CUSTOM + ad_group）——新团队零代码接入
            syncCustomAdGroupBackedVirtualGroups();
            audit.setSuccessCount(successCount);
            audit.setInsertCount(insertCount);
            audit.setUpdateCount(updateCount);
            audit.setSkippedMissingKey(skippedMissingKey);
            audit.setUpserted(successCount);
            audit.setFailed(skippedMissingKey);
            return finishSuccess(audit, totalFetched, start, null);
        } catch (Exception e) {
            return finishFailed(audit, e, start);
        }
    }
    // ==================== 增量 AD 组同步 ====================
    /**
     * 增量 Hermes AD 组同步：找基线 → 检测变更组 → 只同步变化部分。
     * 无历史基线时自动降级为全量同步。
     */
    public LdapSyncAudit runHermesAdGroupIncrementalSync() {
        // 找基线：最近一次成功的 HERMES_AD_GROUP 或 HERMES_AD_INCR
        Optional<LdapSyncAudit> baseline = auditRepository
                .findTopBySyncTypeInAndStatusOrderByStartedAtDesc(
                        List.of(SYNC_TYPE_HERMES_AD_GROUP, SYNC_TYPE_HERMES_AD_INCR), STATUS_SUCCESS);
        if (baseline.isEmpty()) {
            log.info("No Hermes AD group sync baseline; incremental falls back to full");
            return runHermesAdGroupSync();
        }
        LdapSyncAudit prev = baseline.get();
        Instant watermark = prev.getSnapshotAt() != null ? prev.getSnapshotAt() : prev.getStartedAt();
        auditRepository.findById(prev.getId()).ifPresent(a -> {
            // Refresh watermark from the most recent record
        });
        long start = System.currentTimeMillis();
        LdapSyncAudit audit = startAudit(SYNC_TYPE_HERMES_AD_INCR);
        try {
            Map<String, String> groupDefs = resolveGroupDefinitions();
            if (groupDefs.isEmpty()) {
                return finishSuccess(audit, 0, start, "No Hermes AD groups configured");
            }
            audit.setGroups(String.join(",", groupDefs.values()));
            audit.setHighWaterMark(watermark.toString());
            ensureHermesBindings(groupDefs);
            boolean supportsUserDelta = supportsUserChangeIncrementalSync();
            // 检测哪些组发生了变化
            List<String> changedGroupKeys = new ArrayList<>();
            for (Map.Entry<String, String> entry : groupDefs.entrySet()) {
                if (ldapClient.hasGroupChangedSince(entry.getValue(), watermark)) {
                    changedGroupKeys.add(entry.getKey());
                }
            }
            Map<String, LdapGroupUserAccumulator> accumulator = new LinkedHashMap<>();
            int totalFetched = 0;
            if (!changedGroupKeys.isEmpty()) {
                log.info("Changed Hermes AD groups: {}", changedGroupKeys);
                // 只清理变化组对应的虚拟组成员关系
                cleanOldLdapMembershipsForChangedBindings(changedGroupKeys);
                // 拉取变化组的成员
                for (String roleKey : changedGroupKeys) {
                    String groupCn = groupDefs.get(roleKey);
                    try {
                        List<Map<String, String>> users = ldapClient.fetchUsersInGroup(groupCn);
                        totalFetched += users.size();
                        for (Map<String, String> userAttrs : users) {
                            String employeeId = userAttrs.get(ldapProperties.getAttributes().getEmployeeId());
                            if (!StringUtils.hasText(employeeId)) {
                                continue;
                            }
                            accumulator.computeIfAbsent(employeeId, k -> new LdapGroupUserAccumulator(userAttrs))
                                    .addHitGroup(groupCn, roleKey);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to fetch users from changed AD group CN={}: {}", groupCn, e.getMessage());
                    }
                }
            } else if (supportsUserDelta) {
                log.info("No Hermes AD groups changed since {}; checking user whenChanged deltas in target groups", watermark);
            } else {
                log.info("No Hermes AD groups changed since {} and no LDAP user change watermark attribute is configured; skipping sync", watermark);
                return finishSuccess(audit, 0, start, "No groups changed");
            }
            // 补充用户侧变更：通过 whenChanged 拉取变化用户，过滤 memberOf
            if (supportsUserDelta) {
                try {
                String whenChangedAttr = ldapProperties.getAttributes().getWhenChanged();
                String whenStr = java.time.format.DateTimeFormatter
                        .ofPattern("yyyyMMddHHmmss").withZone(java.time.ZoneOffset.UTC)
                        .format(watermark) + ".0Z";
                String userFilter = "(" + whenChangedAttr + ">=" + whenStr + ")";
                List<Map<String, String>> changedUsers = ldapClient.fetchUsersWithFilter(userFilter);
                for (Map<String, String> userAttrs : changedUsers) {
                    String employeeId = userAttrs.get(ldapProperties.getAttributes().getEmployeeId());
                    if (!StringUtils.hasText(employeeId)) {
                        continue;
                    }
                    // 仅当该用户属于目标组时才纳入
                    Set<String> memberOfCns = parseMemberOfCns(userAttrs);
                    for (Map.Entry<String, String> entry : groupDefs.entrySet()) {
                        if (memberOfCns.contains(entry.getValue())) {
                            accumulator.computeIfAbsent(employeeId,
                                            k -> new LdapGroupUserAccumulator(userAttrs))
                                    .addHitGroup(entry.getValue(), entry.getKey());
                        }
                    }
                }
                totalFetched += changedUsers.size();
                } catch (Exception e) {
                    log.warn("Failed to fetch changed users for incremental sync: {}", e.getMessage());
                }
            }
            if (changedGroupKeys.isEmpty() && accumulator.isEmpty()) {
                log.info("No Hermes AD groups or in-scope users changed since {}; skipping sync", watermark);
                return finishSuccess(audit, 0, start, "No groups or users changed");
            }
            int supplementalManagerFetched = supplementDirectManagers(accumulator);
            totalFetched += supplementalManagerFetched;
            // 分批 upsert
            int batchSize = ldapProperties.getGroupSync().getBatchSize();
            if (batchSize <= 0) {
                batchSize = 20;
            }
            int successCount = 0, insertCount = 0, updateCount = 0, skippedMissingKey = 0;
            Map<String, String> syncedUserIdsByEmployeeId = new LinkedHashMap<>();
            List<Map.Entry<String, LdapGroupUserAccumulator>> entries =
                    new ArrayList<>(accumulator.entrySet());
            for (int i = 0; i < entries.size(); i += batchSize) {
                int end = Math.min(i + batchSize, entries.size());
                List<Map.Entry<String, LdapGroupUserAccumulator>> batch =
                        entries.subList(i, end);
                UpsertBatchResult result = upsertBatchWithFallback(batch);
                successCount += result.success();
                insertCount += result.insert();
                updateCount += result.update();
                skippedMissingKey += result.skipped();
                syncedUserIdsByEmployeeId.putAll(result.syncedUserIdsByEmployeeId());
            }
            // 同步虚拟组关系
            syncVirtualGroupMemberships(entries, groupDefs, syncedUserIdsByEmployeeId);
            // 数据驱动的团队组同步（CUSTOM + ad_group）——新团队零代码接入
            syncCustomAdGroupBackedVirtualGroups();
            audit.setSuccessCount(successCount);
            audit.setInsertCount(insertCount);
            audit.setUpdateCount(updateCount);
            audit.setSkippedMissingKey(skippedMissingKey);
            audit.setUpserted(successCount);
            audit.setFailed(skippedMissingKey);
            return finishSuccess(audit, totalFetched, start, null);
        } catch (Exception e) {
            return finishFailed(audit, e, start);
        }
    }
    /** LDAP 若提供用户变更水位字段（如 AD 的 whenChanged），即可安全执行增量组同步。 */
    boolean supportsUserChangeIncrementalSync() {
        return StringUtils.hasText(ldapProperties.getAttributes().getWhenChanged());
    }

    /**
     * 补齐本次 AD group 用户的一层 EM/FM 用户画像。
     * <p>只根据同步前已有的组内用户收集 manager ID，补入的 manager 不再继续追踪其 EM/FM，避免递归。</p>
     */
    int supplementDirectManagers(Map<String, LdapGroupUserAccumulator> accumulator) {
        Set<String> managerEmployeeIds = new LinkedHashSet<>();
        List<LdapGroupUserAccumulator> primaryUsers = new ArrayList<>(accumulator.values());
        for (LdapGroupUserAccumulator acc : primaryUsers) {
            ldapUserMapper.mapToUser(acc.userAttrs).ifPresent(mapped -> {
                addMissingManagerId(managerEmployeeIds, accumulator, mapped.getEntityManagerId());
                addMissingManagerId(managerEmployeeIds, accumulator, mapped.getFunctionManagerId());
            });
        }
        int fetched = 0;
        for (String managerEmployeeId : managerEmployeeIds) {
            if (userRepository.existsById(managerEmployeeId)) {
                continue;
            }
            try {
                Optional<Map<String, String>> managerAttrs = ldapClient.getUserAttributesByEmployeeId(managerEmployeeId);
                if (managerAttrs.isEmpty()) {
                    log.warn("LDAP manager supplemental import skipped: employeeID {} not found", managerEmployeeId);
                    continue;
                }
                Optional<LdapUserData> mappedManager = ldapUserMapper.mapToUser(managerAttrs.get());
                if (mappedManager.isEmpty()) {
                    log.warn("LDAP manager supplemental import skipped: employeeID {} has no mappable employeeID", managerEmployeeId);
                    continue;
                }
                String employeeId = mappedManager.get().getEmployeeId();
                accumulator.computeIfAbsent(employeeId, k -> LdapGroupUserAccumulator.supplemental(managerAttrs.get()));
                fetched++;
            } catch (Exception e) {
                log.warn("LDAP manager supplemental import failed for employeeID {}: {}", managerEmployeeId, e.getMessage());
            }
        }
        if (fetched > 0) {
            log.info("Hermes AD group sync supplemented {} direct EM/FM users", fetched);
        }
        return fetched;
    }
    private void addMissingManagerId(Set<String> managerEmployeeIds,
                                     Map<String, LdapGroupUserAccumulator> accumulator,
                                     String managerEmployeeId) {
        if (!StringUtils.hasText(managerEmployeeId)) {
            return;
        }
        String trimmed = managerEmployeeId.trim();
        if (!accumulator.containsKey(trimmed)) {
            managerEmployeeIds.add(trimmed);
        }
    }

    // ==================== 环境解析 ====================
    /**
     * 解析 Hermes 环境名。优先级：
     * <ol>
     *   <li>{@code ldap.hermes-env} 配置</li>
     *   <li>环境变量 {@code LDAP_HERMES_ENV}</li>
     *   <li>Spring profile 推断（dev→DEV, uat→UAT, ppd→PPD, prd→PRD）</li>
     *   <li>默认 {@code DEV}</li>
     * </ol>
     */
    String resolveHermesEnvironment() {
        String configured = ldapProperties.getHermesEnv();
        if (StringUtils.hasText(configured)) {
            return configured.toUpperCase();
        }
        // Spring profile 推断
        for (String profile : environment.getActiveProfiles()) {
            String upper = profile.toUpperCase();
            if (Set.of("DEV", "UAT", "PPD", "PRD").contains(upper)) {
                return upper;
            }
            if (upper.contains("DEV")) return "DEV";
            if (upper.contains("UAT")) return "UAT";
            if (upper.contains("PPD")) return "PPD";
            if (upper.contains("PRD")) return "PRD";
        }
        return "DEV";
    }
    // ==================== 组定义解析 ====================
    /**
     * 解析「角色标识 → AD 组名」映射。优先使用显式映射
     * ({@code ldap.group-sync.groups})；未配置时使用 roles + pattern 拼接。
     */
    Map<String, String> resolveGroupDefinitions() {
        String env = resolveHermesEnvironment();
        LdapProperties.GroupSync gs = ldapProperties.getGroupSync();
        // 显式映射优先
        String explicitGroups = gs.getGroups();
        if (StringUtils.hasText(explicitGroups)) {
            Map<String, String> map = new LinkedHashMap<>();
            for (String pair : explicitGroups.split(",")) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2) {
                    String roleKey = kv[0].trim();
                    if (isSupportedGroupRoleKey(roleKey)) {
                        map.put(roleKey, kv[1].trim());
                    } else {
                        log.warn("Ignoring unsupported Hermes AD group mapping key: {}", roleKey);
                    }
                }
            }
            if (!map.isEmpty()) {
                log.debug("Using explicit Hermes group mapping: {}", map);
                return map;
            }
        }
        // roles + pattern 拼接
        String roles = gs.getRoles();
        String pattern = gs.getPattern();
        if (StringUtils.hasText(roles) && StringUtils.hasText(pattern)) {
            Map<String, String> map = new LinkedHashMap<>();
            for (String role : roles.split(",")) {
                String trimmed = role.trim();
                if (!trimmed.isEmpty()) {
                    String groupCn = pattern.replace("{env}", env).replace("{role}", trimmed);
                    if (isSupportedGroupRoleKey(trimmed)) {
                        map.put(trimmed, groupCn);
                    } else {
                        log.warn("Ignoring unsupported Hermes AD group role: {}", trimmed);
                    }
                }
            }
            log.debug("Resolved Hermes group mapping via pattern: {}", map);
            return map;
        }

        // 兜底：使用默认 Hermes 模式
        log.warn("No Hermes group mapping configured; using default pattern");
        Map<String, String> map = new LinkedHashMap<>();
        for (String role : List.of(GROUP_ROLE_KEY_ADMIN, GROUP_ROLE_KEY_USER, GROUP_ROLE_KEY_DEVELOPER)) {
            map.put(role, "Infodir-Hermes-Default-" + env + "-" + role);
        }
        return map;
    }

    private static boolean isSupportedGroupRoleKey(String roleKey) {
            return HERMES_BINDINGS.stream().anyMatch(binding -> binding.roleKey.equals(roleKey));
    }
    // ==================== 绑定确保 ====================
    /**
     * 确保 Hermes 角色、虚拟组、角色绑定、权限分配基础数据存在。
     * 幂等——已存在的跳过创建。
     */
    @Transactional
    void ensureHermesBindings(Map<String, String> groupDefs) {
        for (HermesGroupBinding binding : HERMES_BINDINGS) {
            // 仅处理本次配置中涉及的绑定
            if (!groupDefs.containsKey(binding.roleKey)) {
                continue;
            }
            VirtualGroup vg = ensureVirtualGroup(binding.vgCode, binding.vgName,
                    binding.adGroupCn(groupDefs), LDAP_SYNC_ACTOR);
            // 仅对配置了角色编码的系统组确保角色绑定；Default Users 只维护成员关系。
            if (StringUtils.hasText(binding.roleCode)) {
                Role role = ensureRole(binding.roleCode, binding.displayName, binding.roleType, LDAP_SYNC_ACTOR);
                // VirtualGroupRole binding
                ensureVirtualGroupRole(vg.getId(), role.getId(), LDAP_SYNC_ACTOR);
                // RoleAssignment (VIRTUAL_GROUP → Role)
                ensureRoleAssignment(role.getId(), AssignmentTargetType.VIRTUAL_GROUP, vg.getId(), LDAP_SYNC_ACTOR);
            }
        }
    }
    private Role ensureRole(String code, String displayName, String type, String actor) {
        return roleRepository.findByCode(code)
                .orElseGet(() -> {
                    Role r = Role.builder()
                            .id(UUID.randomUUID().toString())
                            .code(code)
                            .name(displayName)
                            .type(type)
                            .displayName(displayName)
                            .status("ACTIVE")
                            .isSystem(true)
                            .createdBy(actor)
                            .updatedBy(actor)
                            .build();
                    return roleRepository.save(r);
                });
    }
    private VirtualGroup ensureVirtualGroup(String code, String name, String adGroup, String actor) {
        return virtualGroupRepository.findByCode(code)
                .map(existing -> {
                    boolean changed = false;
                    if (!name.equals(existing.getName())) {
                        existing.setName(name);
                        changed = true;
                    }
                    if (!"SYSTEM".equals(existing.getType())) {
                        existing.setType("SYSTEM");
                        changed = true;
                    }
                    if (StringUtils.hasText(adGroup) && !adGroup.equals(existing.getAdGroup())) {
                        existing.setAdGroup(adGroup);
                        changed = true;
                    }
                    if (!"ACTIVE".equals(existing.getStatus())) {
                        existing.setStatus("ACTIVE");
                        changed = true;
                    }
                    if (changed) {
                        existing.setUpdatedBy(actor);
                        return virtualGroupRepository.save(existing);
                    }
                    return existing;
                })
                .orElseGet(() -> {
                    VirtualGroup vg = VirtualGroup.builder()
                            .id(UUID.randomUUID().toString())
                            .code(code)
                            .name(name)
                            .type("SYSTEM")
                            .displayName(name)
                            .adGroup(adGroup)
                            .status("ACTIVE")
                            .createdBy(actor)
                            .updatedBy(actor)
                            .build();
                    return virtualGroupRepository.save(vg);
                });
    }

    private void ensureVirtualGroupRole(String vgId, String roleId, String actor) {
        if (!virtualGroupRoleRepository.existsByVirtualGroupIdAndRoleId(vgId, roleId)) {
            VirtualGroupRole vgr = VirtualGroupRole.builder()
                    .id(UUID.randomUUID().toString())
                    .virtualGroupId(vgId)
                    .roleId(roleId)
                    .createdBy(actor)
                    .build();
            virtualGroupRoleRepository.save(vgr);
        }
    }
    private void ensureRoleAssignment(String roleId, AssignmentTargetType targetType,
                                      String targetId, String actor) {
        if (!roleAssignmentRepository.existsByRoleIdAndTargetTypeAndTargetId(
                roleId, targetType, targetId)) {
            RoleAssignment ra = RoleAssignment.builder()
                    .id(UUID.randomUUID().toString())
                    .roleId(roleId)
                    .targetType(targetType)
                    .targetId(targetId)
                    .assignedBy(actor)
                    .build();
            roleAssignmentRepository.save(ra);
        }
    }
    // ==================== 成员清理 ====================
    /** 清理所有 Hermes 虚拟组中由 LDAP 同步添加的旧成员关系。 */
    private void cleanOldLdapMembershipsForBindings() {
        transactionTemplate.executeWithoutResult(status -> {
            for (HermesGroupBinding binding : HERMES_BINDINGS) {
                virtualGroupRepository.findByCode(binding.vgCode).ifPresent(vg -> {
                    List<VirtualGroupMember> members = virtualGroupMemberRepository.findByGroupId(vg.getId());
                    members.stream()
                            .filter(m -> HERMES_SYNC_MEMBER_ADDED_BY.equals(m.getAddedBy()))
                            .forEach(m -> virtualGroupMemberRepository.deleteByGroupIdAndUserId(
                                    m.getGroupId(), m.getUserId()));
                });
            }
        });
    }
   /** 只清理变更组对应的虚拟组的旧成员关系。 */
    private void cleanOldLdapMembershipsForChangedBindings(List<String> changedRoleKeys) {
        transactionTemplate.executeWithoutResult(status -> {
            for (HermesGroupBinding binding : HERMES_BINDINGS) {
                if (!changedRoleKeys.contains(binding.roleKey)) {
                    continue;
                }
                virtualGroupRepository.findByCode(binding.vgCode).ifPresent(vg -> {
                    List<VirtualGroupMember> members = virtualGroupMemberRepository.findByGroupId(vg.getId());
                    members.stream()
                            .filter(m -> HERMES_SYNC_MEMBER_ADDED_BY.equals(m.getAddedBy()))
                            .forEach(m -> virtualGroupMemberRepository.deleteByGroupIdAndUserId(
                                    m.getGroupId(), m.getUserId()));
                });
            }
        });
    }
    // ==================== 批量 Upsert ====================
    /**
     * 批量 upsert 用户，返回 [success, insert, update, skipped]。
     * 单条失败不影响同批其他记录。
     */
    private UpsertBatchResult upsertBatchWithFallback(
            List<Map.Entry<String, LdapGroupUserAccumulator>> batch) {
        int success = 0, insert = 0, update = 0, skipped = 0;
        Map<String, String> syncedUserIdsByEmployeeId = new LinkedHashMap<>();
        for (Map.Entry<String, LdapGroupUserAccumulator> entry : batch) {
            Map<String, String> attrs = entry.getValue().userAttrs;
            Optional<LdapUserData> mapped = ldapUserMapper.mapToUser(attrs);
            if (mapped.isEmpty()) {
                skipped++;
                continue;
            }
            try {
                LdapUserSyncService.UpsertResult upsertResult = ldapUserSyncService.upsertUser(mapped.get());
                String employeeId = mapped.get().getId();
                if (StringUtils.hasText(employeeId) && StringUtils.hasText(upsertResult.userId())) {
                    syncedUserIdsByEmployeeId.put(employeeId, upsertResult.userId());
                }
                success++;
                if (upsertResult.isNew()) {
                    insert++;
                } else {
                    update++;
                }
            } catch (Exception e) {
                log.warn("Upsert failed for employeeId {}: {}", entry.getKey(), e.getMessage());
                skipped++;
            }
        }
        return new UpsertBatchResult(success, insert, update, skipped, syncedUserIdsByEmployeeId);
    }

    // ==================== 虚拟组成员关系同步 ====================
    /**
     * 为用户同步 Hermes 虚拟组成员关系。
     * <p>规则：
     * <ol>
     *   <li>解析用户 {@code memberOf} → 提取 CN</li>
     *   <li>匹配 Hermes 目标组 → 加入对应虚拟组</li>
     *   <li>无命中时使用 {@code fallbackGroupNames}（用户在当前同步中被发现的组）</li>
     *   <li>仍未命中时落入默认 Users 组</li>
     * </ol>
     */
    @Transactional
    void syncVirtualGroupMemberships(
            List<Map.Entry<String, LdapGroupUserAccumulator>> entries,
            Map<String, String> groupDefs,
            Map<String, String> syncedUserIdsByEmployeeId) {
        // 构建组名 → 虚拟组的快速查找
        Map<String, Set<String>> groupToVgCodes = new LinkedHashMap<>();
        for (HermesGroupBinding binding : HERMES_BINDINGS) {
            if (groupDefs.containsKey(binding.roleKey)) {
                String adGroupCn = groupDefs.get(binding.roleKey);
                virtualGroupRepository.findByCode(binding.vgCode)
                        .ifPresent(vg -> groupToVgCodes.computeIfAbsent(adGroupCn, k -> new java.util.LinkedHashSet<>())
                                .add(vg.getCode()));
            }
        }
        // 默认 Users 虚拟组
        VirtualGroup defaultVg = virtualGroupRepository.findByCode(VG_HERMES_USERS).orElse(null);
        for (Map.Entry<String, LdapGroupUserAccumulator> entry : entries) {
            if (entry.getValue().supplementalOnly) {
                continue;
            }
            String employeeId = entry.getKey();
            String actualUserId = syncedUserIdsByEmployeeId.getOrDefault(employeeId, employeeId);
            LdapGroupUserAccumulator acc = entry.getValue();
            // 通过 memberOf 确定目标虚拟组
            Set<String> targetVgCodes = resolveTargetVirtualGroups(
                    acc.userAttrs, acc.hitGroupNames, acc.hitRoleKeys, groupDefs, groupToVgCodes);
            if (targetVgCodes.isEmpty() && defaultVg != null) {
                targetVgCodes = Set.of(defaultVg.getCode());
            }
            // 写入组成员关系
            for (String vgCode : targetVgCodes) {
                virtualGroupRepository.findByCode(vgCode).ifPresent(vg -> {
                    if (!virtualGroupMemberRepository.existsByGroupIdAndUserId(vg.getId(), actualUserId)) {
                        VirtualGroupMember member = VirtualGroupMember.builder()
                                .id(UUID.randomUUID().toString())
                                .groupId(vg.getId())
                                .userId(actualUserId)
                                .addedBy(HERMES_SYNC_MEMBER_ADDED_BY)
                                .build();
                        virtualGroupMemberRepository.save(member);
                    }
                });
            }
            // 清理该用户不再属于的 Hermes LDAP 管理虚拟组
            cleanStaleMembershipsForUser(actualUserId, targetVgCodes);
        }
    }

    // ==================== 数据驱动的团队组同步 ====================
    /**
     * 为所有「配置了 {@code ad_group} 的 CUSTOM 虚拟组」（团队组）从 AD 拉取成员。
     *
     * <p>与硬编码的 {@link #HERMES_BINDINGS}（SYSTEM 能力组）解耦：新团队接入只需在 Admin Center
     * 创建 CUSTOM 虚拟组并填写 {@code adGroup}，下一次同步即自动灌入成员，<b>无需改代码或发版</b>。</p>
     *
     * <p>SYSTEM 组由 Hermes 绑定链处理，此处按 {@code type != SYSTEM} 排除，避免重复处理与冲突清理。
     * 手工添加的成员（{@code addedBy != HERMES_AD_SYNC}）予以保留，仅回收本同步任务管理的过期成员。</p>
     */
    void syncCustomAdGroupBackedVirtualGroups() {
        List<VirtualGroup> teamGroups = virtualGroupRepository.findAll().stream()
                .filter(vg -> !"SYSTEM".equals(vg.getType()))
                .filter(vg -> StringUtils.hasText(vg.getAdGroup()))
                .toList();
        if (teamGroups.isEmpty()) {
            return;
        }
        for (VirtualGroup vg : teamGroups) {
            try {
                syncOneCustomAdGroup(vg);
            } catch (Exception e) {
                log.warn("Custom AD-backed team group sync failed: vg={} adGroup={}: {}",
                        vg.getCode(), vg.getAdGroup(), e.getMessage());
            }
        }
    }

    private void syncOneCustomAdGroup(VirtualGroup vg) throws javax.naming.NamingException {
        String adGroupCn = vg.getAdGroup().trim();
        List<Map<String, String>> users = ldapClient.fetchUsersInGroup(adGroupCn);
        Set<String> targetUserIds = new LinkedHashSet<>();
        for (Map<String, String> attrs : users) {
            Optional<LdapUserData> mapped = ldapUserMapper.mapToUser(attrs);
            if (mapped.isEmpty()) {
                continue;
            }
            try {
                LdapUserSyncService.UpsertResult res = ldapUserSyncService.upsertUser(mapped.get());
                if (StringUtils.hasText(res.userId())) {
                    targetUserIds.add(res.userId());
                }
            } catch (Exception e) {
                log.warn("Upsert failed for team group {} member {}: {}",
                        vg.getCode(), mapped.get().getId(), e.getMessage());
            }
        }
        transactionTemplate.executeWithoutResult(status -> {
            for (String userId : targetUserIds) {
                if (!virtualGroupMemberRepository.existsByGroupIdAndUserId(vg.getId(), userId)) {
                    virtualGroupMemberRepository.save(VirtualGroupMember.builder()
                            .id(UUID.randomUUID().toString())
                            .groupId(vg.getId())
                            .userId(userId)
                            .addedBy(HERMES_SYNC_MEMBER_ADDED_BY)
                            .build());
                }
            }
            // 仅回收由本同步任务添加、现已不在 AD 组中的成员；手工添加的成员保留
            for (VirtualGroupMember m : virtualGroupMemberRepository.findByGroupId(vg.getId())) {
                if (HERMES_SYNC_MEMBER_ADDED_BY.equals(m.getAddedBy())
                        && !targetUserIds.contains(m.getUserId())) {
                    virtualGroupMemberRepository.deleteByGroupIdAndUserId(m.getGroupId(), m.getUserId());
                }
            }
        });
        log.info("Custom AD-backed team group sync: vg={} adGroup={} members={}",
                vg.getCode(), adGroupCn, targetUserIds.size());
    }

   /**
     * 根据 {@code memberOf} 属性解析目标虚拟组集合。
     */
    private Set<String> resolveTargetVirtualGroups(
            Map<String, String> userAttrs,
            Set<String> fallbackGroupCns,
            Set<String> hitRoleKeys,
            Map<String, String> groupDefs,
            Map<String, Set<String>> groupToVgCodes) {
        Set<String> result = new java.util.LinkedHashSet<>();
        // 1. 通过 memberOf 匹配
        Set<String> memberOfCns = parseMemberOfCns(userAttrs);
        for (Map.Entry<String, String> defEntry : groupDefs.entrySet()) {
            String roleKey = defEntry.getKey();
            String adGroupCn = defEntry.getValue();
            if (memberOfCns.contains(adGroupCn)) {
                findBindings(roleKey).forEach(binding -> result.add(binding.vgCode));
            }
        }
        // 2. memberOf 无命中时，使用 fallback（本次同步命中组）
        if (result.isEmpty()) {
            for (String groupCn : fallbackGroupCns) {
                result.addAll(groupToVgCodes.getOrDefault(groupCn, Collections.emptySet()));
            }
        }
        // 3. 仍未命中：从 hitRoleKeys 推断
        if (result.isEmpty()) {
            for (String roleKey : hitRoleKeys) {
                findBindings(roleKey).forEach(binding -> result.add(binding.vgCode));
            }
        }
        return result;
    }

    /** 从用户属性中解析 {@code memberOf}，提取每个 DN 的 CN 部分。 */
    Set<String> parseMemberOfCns(Map<String, String> userAttrs) {
        String memberOf = userAttrs.get(AD_ATTR_MEMBER_OF);
        if (!StringUtils.hasText(memberOf)) {
            return Collections.emptySet();
        }
        Set<String> cns = new java.util.LinkedHashSet<>();
        // memberOf 可能是多值属性（扁平化为逗号分隔或分号分隔）
        String[] parts = memberOf.split("[,;]");
        for (String part : parts) {
            String trimmed = part.trim();
            // 提取 CN=xxx 部分
            if (trimmed.toUpperCase().startsWith("CN=")) {
                String cn = trimmed.substring(3);
                // 去掉可能尾随的其他 LDAP 属性
                int nextAttr = cn.indexOf(',');
                if (nextAttr > 0) {
                    cn = cn.substring(0, nextAttr);
                }
                cns.add(cn.trim());
            }
        }
        return cns;
    }

    /** 清理用户在 Hermes 虚拟组中不再需要的旧 LDAP 管理成员关系。 */
    private void cleanStaleMembershipsForUser(String userId, Set<String> targetVgCodes) {
        transactionTemplate.executeWithoutResult(status -> {
            List<VirtualGroupMember> allMemberships =
                    virtualGroupMemberRepository.findByUserId(userId);
            for (VirtualGroupMember m : allMemberships) {
                if (!HERMES_SYNC_MEMBER_ADDED_BY.equals(m.getAddedBy())) {
                    continue; // 跳过非 LDAP 管理的成员关系
                }
                VirtualGroup vg = virtualGroupRepository.findById(m.getGroupId()).orElse(null);
                if (vg == null) {
                    continue;
                }
                // 检查是否为 Hermes 虚拟组
                boolean isHermesVg = HERMES_BINDINGS.stream()
                        .anyMatch(b -> b.vgCode.equals(vg.getCode()));
                if (isHermesVg && !targetVgCodes.contains(vg.getCode())) {
                    virtualGroupMemberRepository.deleteByGroupIdAndUserId(m.getGroupId(), userId);
                    log.debug("Removed stale Hermes membership: userId={} vg={}", userId, vg.getCode());
                }
            }
        });
    }
    private List<HermesGroupBinding> findBindings(String roleKey) {
        return HERMES_BINDINGS.stream()
                .filter(b -> b.roleKey.equals(roleKey))
                .toList();
    }
    // ==================== 审计 ====================
    private LdapSyncAudit startAudit(String type) {
        return auditRepository.save(LdapSyncAudit.builder()
                .id(UUID.randomUUID().toString())
                .syncType(type)
                .status(STATUS_RUNNING)
                .snapshotAt(Instant.now())
                .startedAt(Instant.now())
                .build());
    }
    private LdapSyncAudit finishSuccess(LdapSyncAudit audit, int totalFetched,
                                        long startMs, String overrideMessage) {
        audit.setStatus(STATUS_SUCCESS);
        audit.setTotalFetched(totalFetched);
        audit.setFinishedAt(Instant.now());
        audit.setDurationMs(System.currentTimeMillis() - startMs);
        if (overrideMessage != null) {
            audit.setMessage(overrideMessage);
        }
        log.info("Hermes AD group sync ({}) done: fetched={}, success={}, insert={}, update={}, skipped={}, {}ms",
                audit.getSyncType(), totalFetched, audit.getSuccessCount(),
                audit.getInsertCount(), audit.getUpdateCount(),
                audit.getSkippedMissingKey(), audit.getDurationMs());
        return auditRepository.save(audit);
    }
    private LdapSyncAudit finishFailed(LdapSyncAudit audit, Exception e, long startMs) {
        audit.setStatus(STATUS_FAILED);
        audit.setFinishedAt(Instant.now());
        audit.setDurationMs(System.currentTimeMillis() - startMs);
        audit.setMessage(truncate(e.getMessage(), 1000));
        log.error("Hermes AD group sync ({}) failed: {}", audit.getSyncType(), e.getMessage());
        return auditRepository.save(audit);
    }
    private String truncate(String v, int max) {
        if (v == null) return null;
        return v.length() <= max ? v : v.substring(0, max);
    }
    // ==================== 内部类型 ====================
    /** Hermes 角色 → 虚拟组 → 角色编码 绑定定义。 */
    record HermesGroupBinding(
            String roleKey,
            String vgCode,
            String vgName,
            String roleCode,
            String displayName,
            String roleType) {
        String adGroupCn(Map<String, String> groupDefs) {
            return groupDefs.getOrDefault(roleKey, "");
        }
    }


    record UpsertBatchResult(
            int success,
            int insert,
            int update,
            int skipped,
            Map<String, String> syncedUserIdsByEmployeeId) {
    }
    /**
     * 按 employeeID 聚合的 LDAP 用户数据。
     * 同一用户可能出现在多个 AD 组中，本对象累积其命中。
     */
    static class LdapGroupUserAccumulator {
        final Map<String, String> userAttrs;
        final Set<String> hitGroupNames = new java.util.LinkedHashSet<>();
        final Set<String> hitRoleKeys = new java.util.LinkedHashSet<>();
        final boolean supplementalOnly;
        LdapGroupUserAccumulator(Map<String, String> attrs) {
            this(attrs, false);
        }
        private LdapGroupUserAccumulator(Map<String, String> attrs, boolean supplementalOnly) {
            this.userAttrs = attrs;
            this.supplementalOnly = supplementalOnly;
        }
        static LdapGroupUserAccumulator supplemental(Map<String, String> attrs) {
            return new LdapGroupUserAccumulator(attrs, true);
        }
        void addHitGroup(String groupCn, String roleKey) {
            hitGroupNames.add(groupCn);
            hitRoleKeys.add(roleKey);
        }
    }
}