package com.workflow.component;

import com.workflow.client.AdminCenterClient;
import com.workflow.component.ClaimPoolTaskLocator.Pool;
import com.workflow.dto.request.ClaimPoolResyncRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Names a claim pool in codes (platform access model WP8b-2, D-37). The current designer writes BU and role codes,
 * older deployments ids, and Admin Center's task-assignment endpoints take codes. One place for every resync path:
 * Admin push, periodic resync and single-task resync.
 *
 * <p>A value that is not an id of an existing BU or BU-bounded role stays as written (it is a code). Lookups are kept
 * for {@link #TTL_MS}; an Admin push teaches the pairs it names at once.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PoolCodes {

    static final long TTL_MS = 10 * 60_000L;

    private final AdminCenterClient adminCenterClient;
    private final Map<String, String> businessUnits = new ConcurrentHashMap<>();
    private final Map<String, String> roles = new ConcurrentHashMap<>();
    private volatile boolean rolesLoaded;
    private volatile long since = System.currentTimeMillis();

    /** The pool with its BU and roles as codes. */
    public Pool of(Pool pool) {
        expireIfStale();
        return new Pool(businessUnit(pool.businessUnit()), pool.roles().stream().map(this::role).toList());
    }

    /** The id / code pairs an Admin push names. */
    public void learn(List<ClaimPoolResyncRequest.Pool> pools) {
        expireIfStale();
        for (ClaimPoolResyncRequest.Pool p : pools) {
            businessUnits.put(p.businessUnitCode(), p.businessUnitCode());
            roles.put(p.roleCode(), p.roleCode());
            if (StringUtils.hasText(p.businessUnitId())) {
                businessUnits.put(p.businessUnitId(), p.businessUnitCode());
            }
            if (StringUtils.hasText(p.roleId())) {
                roles.put(p.roleId(), p.roleCode());
            }
        }
    }

    private String businessUnit(String value) {
        return businessUnits.computeIfAbsent(value, v -> {
            String code = adminCenterClient.getBusinessUnitCodeById(v);
            return StringUtils.hasText(code) ? code.trim() : v;
        });
    }

    private String role(String value) {
        String code = roles.get(value);
        if (code != null) {
            return code;
        }
        if (!rolesLoaded) {
            loadRoles();
        }
        return roles.getOrDefault(value, value);
    }

    private synchronized void loadRoles() {
        if (rolesLoaded) {
            return;
        }
        List<Map<String, Object>> buBounded = adminCenterClient.getBuBoundedRoles();
        for (Map<String, Object> role : buBounded) {
            Object id = role.get("id");
            Object code = role.get("code");
            if (id != null && code != null && StringUtils.hasText(code.toString())) {
                roles.putIfAbsent(id.toString(), code.toString().trim());
            }
        }
        if (buBounded.isEmpty()) {
            // getBuBoundedRoles answers an empty list when Admin Center cannot be read; ids stay unconverted until the
            // next reload, and those pools are skipped as "not admitted" (logged by the resync).
            log.warn("No BU-bounded roles from Admin Center; role ids in BPMN pools are not turned into codes for now");
        }
        rolesLoaded = true;
    }

    private synchronized void expireIfStale() {
        long now = System.currentTimeMillis();
        if (now - since > TTL_MS) {
            businessUnits.clear();
            roles.clear();
            rolesLoaded = false;
            since = now;
        }
    }
}
