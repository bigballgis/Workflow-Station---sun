package com.portal.component;

import com.portal.dto.PermissionRequestListItem;
import com.portal.entity.PermissionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 权限申请展示字段填充与字符串工具。
 * 从 {@link PermissionComponent} 拆出，供门面与 {@link PermissionApprovalComponent} 共用，
 * 行为与原内联实现逐字一致。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionRequestEnrichmentComponent {

    private final RoleAccessComponent roleAccessComponent;

    /**
     * 为审批列表填充申请人登录名等信息（供前端展示，避免只显示 applicantId UUID）
     */
    public void enrichDisplayFields(List<PermissionRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return;
        }
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (PermissionRequest r : requests) {
            if (r.getApplicantId() != null && !r.getApplicantId().isBlank()) {
                ids.add(r.getApplicantId().trim());
            }
            if (r.getSubmittedByUserId() != null && !r.getSubmittedByUserId().isBlank()) {
                ids.add(r.getSubmittedByUserId().trim());
            }
        }
        Map<String, Map<String, Object>> userById = new HashMap<>();
        for (String id : ids) {
            Map<String, Object> info = roleAccessComponent.getUserById(id);
            if (info != null) {
                userById.put(id, info);
            }
        }
        for (PermissionRequest r : requests) {
            Map<String, Object> ainfo = userById.get(r.getApplicantId());
            if (ainfo != null) {
                String shown = firstNonBlank(
                        nonBlankString(ainfo.get("username")),
                        nonBlankString(ainfo.get("fullName")),
                        nonBlankString(ainfo.get("displayName")));
                if (shown != null) {
                    r.setApplicantUsername(shown);
                }
            }
            if (r.getSubmittedByUserId() != null && !r.getSubmittedByUserId().isBlank()) {
                Map<String, Object> sinfo = userById.get(r.getSubmittedByUserId().trim());
                if (sinfo != null) {
                    String subShown = firstNonBlank(
                            nonBlankString(sinfo.get("username")),
                            nonBlankString(sinfo.get("fullName")),
                            nonBlankString(sinfo.get("displayName")));
                    if (subShown != null) {
                        r.setSubmittedByUsername(subShown);
                    }
                }
            }
        }
    }

    public void enrichListItemUsernames(List<PermissionRequestListItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (PermissionRequestListItem r : items) {
            if (r.getApplicantId() != null && !r.getApplicantId().isBlank()) {
                ids.add(r.getApplicantId().trim());
            }
            if (r.getSubmittedByUserId() != null && !r.getSubmittedByUserId().isBlank()) {
                ids.add(r.getSubmittedByUserId().trim());
            }
        }
        Map<String, Map<String, Object>> userById = new HashMap<>();
        for (String id : ids) {
            Map<String, Object> info = roleAccessComponent.getUserById(id);
            if (info != null) {
                userById.put(id, info);
            }
        }
        for (PermissionRequestListItem r : items) {
            Map<String, Object> ainfo = userById.get(r.getApplicantId());
            if (ainfo != null) {
                String shown = firstNonBlank(
                        nonBlankString(ainfo.get("username")),
                        nonBlankString(ainfo.get("fullName")),
                        nonBlankString(ainfo.get("displayName")));
                if (shown != null) {
                    r.setApplicantUsername(shown);
                }
            }
            if (r.getSubmittedByUserId() != null && !r.getSubmittedByUserId().isBlank()) {
                Map<String, Object> sinfo = userById.get(r.getSubmittedByUserId().trim());
                if (sinfo != null) {
                    String subShown = firstNonBlank(
                            nonBlankString(sinfo.get("username")),
                            nonBlankString(sinfo.get("fullName")),
                            nonBlankString(sinfo.get("displayName")));
                    if (subShown != null) {
                        r.setSubmittedByUsername(subShown);
                    }
                }
            }
        }
    }

    public String nonBlankString(Object v) {
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    public String firstNonBlank(String... parts) {
        if (parts == null) {
            return null;
        }
        for (String p : parts) {
            if (p != null && !p.isBlank()) {
                return p;
            }
        }
        return null;
    }
}
