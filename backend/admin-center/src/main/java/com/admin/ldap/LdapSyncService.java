package com.admin.ldap;
import com.admin.component.SecurityAuditComponent;
import com.admin.enums.AuditAction;
import com.admin.repository.UserRepository;
import com.admin.repository.VirtualGroupMemberRepository;
import com.admin.repository.VirtualGroupRepository;
import com.platform.security.entity.VirtualGroup;
import com.platform.security.entity.VirtualGroupMember;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import static com.admin.ldap.LdapConstants.HERMES_SYNC_MEMBER_ADDED_BY;
import static com.admin.ldap.LdapConstants.LDAP_SYNC_ACTOR;
import static com.admin.ldap.LdapConstants.SKIPPED_WITHOUT_EMPLOYEE_ID;
import static com.admin.ldap.LdapConstants.SYNC_TYPE_HERMES_AD_GROUP;
import static com.admin.ldap.LdapConstants.SYNC_TYPE_HERMES_AD_INCR;
/**
 * AD 组 → 虚拟组成员同步（平台访问模型 §5.6）。
 *
 * <p>哪个 AD 组同步到哪里，只看虚拟组上的配置：
 * <ul>
 *   <li>SYSTEM、CUSTOM 组：{@code ad_group} → 成员；</li>
 *   <li>DEVELOPER 团队：{@code ad_group} → Developer 名单，{@code tech_lead_ad_group} → Technical Lead 名单。</li>
 * </ul>
 * 每次同步把每份名单的 AD 来源行对齐到 AD 组当前成员：缺的补、多的删，人工来源从不改动；
 * 没有 AD 映射的名单删除它的 AD 来源行（D-29）。同步不创建任何组、角色或角色绑定。
 * 每个 AD 组只拉一次；拉到的用户写画像，并补导入一层 Entity / Function Manager。
 * 每一条成员增删都和人工维护一样写进 Admin 操作审计，操作人为 {@code LDAP_SYNC_JOB}（D-32）。</p>
 *
 * <p>安全：LDAP-only 用户写入不可用占位密码（见 {@link LdapUserSyncService}）。</p>
 *
 * <p>仅 {@code ldap.enabled=true} 且 {@code ldap.sync-enabled=true} 时创建。</p>
 *
 * <p>FALLBACK(external) — class-wide policy: this is a periodic sync job against an external
 * directory. An AD group that cannot be read, or whose members could not all be stored, leaves the
 * AD rows of the lists it feeds without removals (nothing is removed on partial data); one bad user
 * is skipped with a warning; the run-level catch records the failure into {@link LdapSyncAudit} —
 * failures are persisted and visible, never silent. Do not "fix" these catches into rethrows:
 * aborting the run on the first bad entry is worse for directory convergence than a partial sync
 * plus an audit trail.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ldap", name = {"enabled", "sync-enabled"}, havingValue = "true")
public class LdapSyncService {
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    /** Some mapped AD groups were not read, or read only in part; the rest were reconciled. */
    private static final String STATUS_PARTIAL = "PARTIAL";
    private static final int AUDIT_GROUPS_MAX = 2000;
    private static final int AUDIT_MESSAGE_MAX = 1000;
    private final LdapClient ldapClient;
    private final LdapUserMapper ldapUserMapper;
    private final LdapUserSyncService ldapUserSyncService;
    private final LdapProperties ldapProperties;
    private final LdapSyncAuditRepository auditRepository;
    private final VirtualGroupRepository virtualGroupRepository;
    private final VirtualGroupMemberRepository virtualGroupMemberRepository;
    private final UserRepository userRepository;
    private final TransactionTemplate transactionTemplate;
    private final SecurityAuditComponent securityAuditComponent;
    private final ObjectMapper objectMapper;

    /** 手动触发的全量同步。与定时同步是同一次对账，只是审计类型不同。 */
    public LdapSyncAudit runHermesAdGroupSync() {
        return reconcile(SYNC_TYPE_HERMES_AD_GROUP);
    }

    /** 启动与定时同步。 */
    public LdapSyncAudit runHermesAdGroupIncrementalSync() {
        return reconcile(SYNC_TYPE_HERMES_AD_INCR);
    }

    private LdapSyncAudit reconcile(String syncType) {
        long start = System.currentTimeMillis();
        LdapSyncAudit audit = startAudit(syncType);
        try {
            Map<VirtualGroup, Map<String, String>> listsByGroup = new LinkedHashMap<>();
            Set<String> adGroups = new LinkedHashSet<>();
            for (VirtualGroup group : virtualGroupRepository.findAll()) {
                Map<String, String> lists = adGroupByList(group);
                listsByGroup.put(group, lists);
                lists.values().stream().filter(StringUtils::hasText).forEach(adGroups::add);
            }
            audit.setGroups(truncate(String.join(",", adGroups), AUDIT_GROUPS_MAX));
            GroupFetchResult fetched = fetchAdGroups(adGroups);
            int totalFetched = fetched.totalFetched() + supplementDirectManagers(fetched.accumulator());
            UpsertBatchResult counts = upsertAccumulatedUsers(new ArrayList<>(fetched.accumulator().entrySet()));
            AdMembership membership = AdMembership.of(fetched, counts);
            List<String> heldBackGroups = new ArrayList<>();
            for (Map.Entry<VirtualGroup, Map<String, String>> entry : listsByGroup.entrySet()) {
                if (reconcileGroup(entry.getKey(), entry.getValue(), membership)) {
                    heldBackGroups.add(entry.getKey().getCode());
                }
            }
            audit.setSuccessCount(counts.success());
            audit.setInsertCount(counts.insert());
            audit.setUpdateCount(counts.update());
            int skipped = fetched.withoutEmployeeId() + counts.skipped();
            audit.setSkippedMissingKey(skipped);
            audit.setUpserted(counts.success());
            audit.setFailed(counts.failedEmployeeIds().size());
            return finish(audit, outcomeStatus(adGroups, membership), totalFetched, start,
                    outcomeMessage(adGroups, membership, heldBackGroups, skipped));
        } catch (Exception e) {
            return finishFailed(audit, e, start);
        }
    }

    /**
     * The lists of a group and the AD group each one is synced from ({@code null} = not mapped).
     * SYSTEM and CUSTOM groups have their members only; a team has its Developer and Technical Lead lists;
     * Public has none.
     */
    static Map<String, String> adGroupByList(VirtualGroup group) {
        Map<String, String> lists = new LinkedHashMap<>();
        if (group.isPublicTeam()) {
            return lists; // Public is a reserved team without lists (6.4)
        }
        if ("DEVELOPER".equals(group.getType())) {
            lists.put(VirtualGroupMember.LIST_DEVELOPER, trimToNull(group.getAdGroup()));
            lists.put(VirtualGroupMember.LIST_TECH_LEAD, trimToNull(group.getTechLeadAdGroup()));
        } else {
            lists.put(VirtualGroupMember.LIST_MEMBER, trimToNull(group.getAdGroup()));
        }
        return lists;
    }

    /**
     * Aligns the AD rows of each list of one group with its AD group. AD rows of a list the group does
     * not have, or of a list without a mapping, are removed (D-29); manual rows are never touched.
     * System Administrators never loses its last active member this way (D-31): the removals are then
     * held back and the audit says so.
     *
     * @return whether removals were held back to keep System Administrators staffed
     */
    private boolean reconcileGroup(VirtualGroup group, Map<String, String> adGroupByList, AdMembership membership) {
        Boolean heldBack = transactionTemplate.execute(status -> {
            Map<String, List<VirtualGroupMember>> adRowsByList = virtualGroupMemberRepository
                    .findByGroupIdAndSource(group.getId(), VirtualGroupMember.SOURCE_AD).stream()
                    .collect(Collectors.groupingBy(VirtualGroupMember::getListType, LinkedHashMap::new,
                            Collectors.toList()));
            List<VirtualGroupMember> additions = new ArrayList<>();
            List<VirtualGroupMember> removals = new ArrayList<>();
            for (Map.Entry<String, List<VirtualGroupMember>> rows : adRowsByList.entrySet()) {
                if (adGroupByList.get(rows.getKey()) == null) {
                    removals.addAll(rows.getValue());
                }
            }
            for (Map.Entry<String, String> list : adGroupByList.entrySet()) {
                String adGroup = list.getValue();
                if (adGroup == null) {
                    continue;
                }
                Optional<Set<String>> desired = membership.userIds(adGroup);
                if (desired.isEmpty()) {
                    log.warn("AD group sync left {}/{} unchanged: AD group CN={} could not be read",
                            group.getCode(), list.getKey(), adGroup);
                    continue;
                }
                List<VirtualGroupMember> rows = adRowsByList.getOrDefault(list.getKey(), List.of());
                Set<String> present = rows.stream().map(VirtualGroupMember::getUserId).collect(Collectors.toSet());
                for (String userId : desired.get()) {
                    if (!present.contains(userId)) {
                        additions.add(VirtualGroupMember.builder()
                                .id(UUID.randomUUID().toString())
                                .groupId(group.getId())
                                .userId(userId)
                                .addedBy(HERMES_SYNC_MEMBER_ADDED_BY)
                                .listType(list.getKey())
                                .source(VirtualGroupMember.SOURCE_AD)
                                .build());
                    }
                }
                if (!membership.complete(adGroup)) {
                    log.warn("AD group sync kept possible leavers in {}/{}: some members of CN={} were not stored",
                            group.getCode(), list.getKey(), adGroup);
                    continue;
                }
                rows.stream().filter(row -> !desired.get().contains(row.getUserId())).forEach(removals::add);
            }
            boolean keepAdministrators = !removals.isEmpty() && group.isSystemAdministrators()
                    && !anyActiveMemberAfter(group, additions, removals);
            if (keepAdministrators) {
                log.error("AD group sync kept the AD members of {}: removing them would leave it without an "
                        + "active member", group.getCode());
                removals.clear();
            }
            virtualGroupMemberRepository.saveAll(additions);
            virtualGroupMemberRepository.deleteAll(removals);
            additions.forEach(row -> auditMembershipChange(group, row, AuditAction.CREATE));
            removals.forEach(row -> auditMembershipChange(group, row, AuditAction.DELETE));
            if (!additions.isEmpty() || !removals.isEmpty()) {
                log.info("AD group sync: {} +{} -{} AD members", group.getCode(), additions.size(), removals.size());
            }
            return keepAdministrators;
        });
        return Boolean.TRUE.equals(heldBack);
    }

    /**
     * One Admin operation audit row per AD row added or removed, carrying the same details as a manual
     * change (D-32). It is written in the group's transaction, so a change is never kept without its audit.
     */
    private void auditMembershipChange(VirtualGroup group, VirtualGroupMember row, AuditAction action) {
        Map<String, String> detail = new LinkedHashMap<>();
        detail.put("userId", row.getUserId());
        detail.put("listType", row.getListType());
        detail.put("source", VirtualGroupMember.SOURCE_AD);
        String detailJson;
        try {
            detailJson = objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot write the audit of AD member " + row.getUserId()
                    + " in " + group.getCode(), e);
        }
        securityAuditComponent.recordAudit(SecurityAuditComponent.AuditLogRequest.builder()
                .action(action)
                .resourceType("VIRTUAL_GROUP")
                .resourceId(group.getId())
                .resourceName(group.getName())
                .userId(LDAP_SYNC_ACTOR)
                .userName(LDAP_SYNC_ACTOR)
                .newValue(detailJson)
                .success(true)
                .build());
    }

    /** Whether the group still has an active, not deleted member once the planned rows are applied. */
    private boolean anyActiveMemberAfter(VirtualGroup group, List<VirtualGroupMember> additions,
                                         List<VirtualGroupMember> removals) {
        Set<String> removedIds = removals.stream().map(VirtualGroupMember::getId).collect(Collectors.toSet());
        Set<String> userIds = new LinkedHashSet<>();
        virtualGroupMemberRepository.findByGroupId(group.getId()).stream()
                .filter(row -> !removedIds.contains(row.getId()))
                .forEach(row -> userIds.add(row.getUserId()));
        additions.forEach(row -> userIds.add(row.getUserId()));
        return userRepository.findAllById(userIds).stream()
                .anyMatch(user -> user.isActive() && !Boolean.TRUE.equals(user.getDeleted()));
    }

    /**
     * Reads the members of each mapped AD group. Members without an employee ID are counted and left out: such
     * accounts are not stored by design, so they cannot be on a list and do not make the AD group incomplete.
     */
    private GroupFetchResult fetchAdGroups(Set<String> adGroups) {
        Map<String, LdapGroupUserAccumulator> accumulator = new LinkedHashMap<>();
        Set<String> readGroups = new LinkedHashSet<>();
        Set<String> partlyReadGroups = new LinkedHashSet<>();
        int totalFetched = 0;
        int withoutEmployeeId = 0;
        String employeeIdAttr = ldapProperties.getAttributes().getEmployeeId();
        for (String groupCn : adGroups) {
            try {
                LdapClient.AdGroupMembers members = ldapClient.fetchUsersInGroup(groupCn);
                List<Map<String, String>> users = members.users();
                if (!members.complete()) {
                    partlyReadGroups.add(groupCn);
                }
                totalFetched += users.size();
                for (Map<String, String> userAttrs : users) {
                    String employeeId = trimToNull(userAttrs.get(employeeIdAttr));
                    if (employeeId != null) {
                        accumulator.computeIfAbsent(employeeId, k -> new LdapGroupUserAccumulator(userAttrs))
                                .hitGroupNames.add(groupCn);
                    } else {
                        withoutEmployeeId++;
                    }
                }
                readGroups.add(groupCn);
            } catch (Exception e) {
                log.warn("Failed to fetch users from AD group CN={}: {}", groupCn, e.getMessage());
            }
        }
        return new GroupFetchResult(accumulator, totalFetched, withoutEmployeeId, readGroups, partlyReadGroups);
    }

    private UpsertBatchResult upsertAccumulatedUsers(
            List<Map.Entry<String, LdapGroupUserAccumulator>> entries) {
        int batchSize = ldapProperties.getGroupSync().getBatchSize();
        if (batchSize <= 0) {
            batchSize = 20;
        }
        int success = 0;
        int insert = 0;
        int update = 0;
        int skipped = 0;
        Map<String, String> syncedUserIdsByEmployeeId = new LinkedHashMap<>();
        Set<String> failedEmployeeIds = new LinkedHashSet<>();
        for (int i = 0; i < entries.size(); i += batchSize) {
            UpsertBatchResult result = upsertBatch(
                    entries.subList(i, Math.min(i + batchSize, entries.size())));
            success += result.success();
            insert += result.insert();
            update += result.update();
            skipped += result.skipped();
            syncedUserIdsByEmployeeId.putAll(result.syncedUserIdsByEmployeeId());
            failedEmployeeIds.addAll(result.failedEmployeeIds());
        }
        return new UpsertBatchResult(success, insert, update, skipped, syncedUserIdsByEmployeeId,
                failedEmployeeIds);
    }

    /**
     * 补齐本次 AD group 用户的一层 EM/FM 用户画像。
     * <p>只根据同步前已有的组内用户收集 manager ID，补入的 manager 不再继续追踪其 EM/FM，避免递归；
     * 补入的 manager 不进任何名单。</p>
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
                accumulator.computeIfAbsent(employeeId, k -> new LdapGroupUserAccumulator(managerAttrs.get()));
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

    // ==================== 批量 Upsert ====================
    private UpsertBatchResult upsertBatch(List<Map.Entry<String, LdapGroupUserAccumulator>> batch) {
        int success = 0, insert = 0, update = 0, skipped = 0;
        Map<String, String> syncedUserIdsByEmployeeId = new LinkedHashMap<>();
        Set<String> failedEmployeeIds = new LinkedHashSet<>();
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
                failedEmployeeIds.add(entry.getKey());
            }
        }
        return new UpsertBatchResult(success, insert, update, skipped, syncedUserIdsByEmployeeId,
                failedEmployeeIds);
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
    private LdapSyncAudit finish(LdapSyncAudit audit, String status, int totalFetched,
                                 long startMs, String overrideMessage) {
        audit.setStatus(status);
        audit.setTotalFetched(totalFetched);
        audit.setFinishedAt(Instant.now());
        audit.setDurationMs(System.currentTimeMillis() - startMs);
        if (overrideMessage != null) {
            audit.setMessage(overrideMessage);
        }
        log.info("Hermes AD group sync ({}) {}: fetched={}, success={}, insert={}, update={}, skipped={}, {}ms",
                audit.getSyncType(), status, totalFetched, audit.getSuccessCount(),
                audit.getInsertCount(), audit.getUpdateCount(),
                audit.getSkippedMissingKey(), audit.getDurationMs());
        return auditRepository.save(audit);
    }
    private LdapSyncAudit finishFailed(LdapSyncAudit audit, Exception e, long startMs) {
        audit.setStatus(STATUS_FAILED);
        audit.setFinishedAt(Instant.now());
        audit.setDurationMs(System.currentTimeMillis() - startMs);
        audit.setMessage(truncate(e.getMessage(), AUDIT_MESSAGE_MAX));
        log.error("Hermes AD group sync ({}) failed: {}", audit.getSyncType(), e.getMessage());
        return auditRepository.save(audit);
    }
    private static String truncate(String v, int max) {
        if (v == null) return null;
        return v.length() <= max ? v : v.substring(0, max);
    }
    private static String trimToNull(String v) {
        return StringUtils.hasText(v) ? v.trim() : null;
    }
    // ==================== 内部类型 ====================
    /** {@code skipped}: entries without an employee ID; {@code failedEmployeeIds}: entries that could not be stored. */
    record UpsertBatchResult(
            int success,
            int insert,
            int update,
            int skipped,
            Map<String, String> syncedUserIdsByEmployeeId,
            Set<String> failedEmployeeIds) {
    }

    private record GroupFetchResult(
            Map<String, LdapGroupUserAccumulator> accumulator,
            int totalFetched,
            int withoutEmployeeId,
            Set<String> readGroups,
            Set<String> partlyReadGroups) {
    }

    /**
     * Who is in each AD group that was read, as stored user ids. An AD group is complete when all its
     * members were read and every one found was stored; only then may its lists lose rows.
     */
    private record AdMembership(Map<String, Set<String>> userIdsByGroup, Set<String> incompleteGroups) {
        static AdMembership of(GroupFetchResult fetched, UpsertBatchResult counts) {
            Map<String, Set<String>> userIdsByGroup = new LinkedHashMap<>();
            Set<String> incompleteGroups = new LinkedHashSet<>(fetched.partlyReadGroups());
            fetched.readGroups().forEach(groupCn -> userIdsByGroup.put(groupCn, new LinkedHashSet<>()));
            for (Map.Entry<String, LdapGroupUserAccumulator> entry : fetched.accumulator().entrySet()) {
                String userId = counts.syncedUserIdsByEmployeeId().get(entry.getKey());
                for (String groupCn : entry.getValue().hitGroupNames) {
                    if (userId != null) {
                        userIdsByGroup.get(groupCn).add(userId);
                    } else if (counts.failedEmployeeIds().contains(entry.getKey())) {
                        incompleteGroups.add(groupCn);
                    }
                }
            }
            return new AdMembership(userIdsByGroup, incompleteGroups);
        }

        Optional<Set<String>> userIds(String groupCn) {
            return Optional.ofNullable(userIdsByGroup.get(groupCn));
        }

        boolean complete(String groupCn) {
            return !incompleteGroups.contains(groupCn);
        }
    }

    /**
     * How the run ended, as the record shows it: FAILED when no mapped AD group could be read (the directory was
     * unreachable, nothing changed), PARTIAL when some were not read or read only in part (their lists only gained
     * members or stayed as they were), SUCCESS otherwise. The message says which AD groups.
     */
    private static String outcomeStatus(Set<String> adGroups, AdMembership membership) {
        if (adGroups.isEmpty()) {
            return STATUS_SUCCESS;
        }
        long unread = adGroups.stream().filter(cn -> membership.userIds(cn).isEmpty()).count();
        if (unread == adGroups.size()) {
            return STATUS_FAILED;
        }
        boolean partlyRead = adGroups.stream().anyMatch(cn -> !membership.complete(cn));
        return unread > 0 || partlyRead ? STATUS_PARTIAL : STATUS_SUCCESS;
    }

    /**
     * What the audit record says beyond the counts: why nothing was read, which AD groups held back, and how many
     * members were skipped for having no employee ID.
     */
    private static String outcomeMessage(Set<String> adGroups, AdMembership membership, List<String> heldBackGroups,
                                         int skipped) {
        StringBuilder message = new StringBuilder();
        if (!heldBackGroups.isEmpty()) {
            message.append("Kept the AD members of ").append(String.join(", ", heldBackGroups))
                    .append(": removing them would leave no active member");
        }
        if (adGroups.isEmpty()) {
            return message.length() > 0 ? message.toString() : "No virtual group maps an AD group";
        }
        List<String> unread = adGroups.stream().filter(cn -> membership.userIds(cn).isEmpty()).toList();
        List<String> partial = adGroups.stream()
                .filter(cn -> membership.userIds(cn).isPresent() && !membership.complete(cn))
                .toList();
        List<String> empty = adGroups.stream()
                .filter(cn -> membership.userIds(cn).map(Set::isEmpty).orElse(false) && membership.complete(cn))
                .toList();
        if (!unread.isEmpty()) {
            if (message.length() > 0) {
                message.append("; ");
            }
            message.append("Not read, lists unchanged: ").append(String.join(", ", unread));
        }
        if (!partial.isEmpty()) {
            if (message.length() > 0) {
                message.append("; ");
            }
            message.append("Read incompletely, nobody removed: ").append(String.join(", ", partial));
        }
        if (!empty.isEmpty()) {
            if (message.length() > 0) {
                message.append("; ");
            }
            message.append("No members found (missing or empty AD group): ").append(String.join(", ", empty));
        }
        if (skipped > 0) {
            if (message.length() > 0) {
                message.append("; ");
            }
            message.append(SKIPPED_WITHOUT_EMPLOYEE_ID).append(skipped);
        }
        return message.length() == 0 ? null : truncate(message.toString(), AUDIT_MESSAGE_MAX);
    }

    /**
     * 按 employeeID 聚合的 LDAP 用户数据。
     * 同一用户可能出现在多个 AD 组中，本对象累积其命中；补导入的 manager 不命中任何组。
     */
    static class LdapGroupUserAccumulator {
        final Map<String, String> userAttrs;
        final Set<String> hitGroupNames = new LinkedHashSet<>();
        LdapGroupUserAccumulator(Map<String, String> attrs) {
            this.userAttrs = attrs;
        }
    }
}
