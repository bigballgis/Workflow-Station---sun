package com.workflow.util;

import org.flowable.engine.TaskService;
import org.flowable.identitylink.api.IdentityLink;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Flowable candidate <em>user</em> identity links for a runtime task.
 * Group-only links (virtual group) are ignored — those stay on {@code VIRTUAL_GROUP}.
 */
public final class FlowableCandidateUsers {

    private FlowableCandidateUsers() {
    }

    public static List<String> userIds(TaskService taskService, String taskId) {
        if (taskService == null || !StringUtils.hasText(taskId)) {
            return List.of();
        }
        List<IdentityLink> links = taskService.getIdentityLinksForTask(taskId);
        if (links == null || links.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (IdentityLink link : links) {
            if (link == null || !"candidate".equals(link.getType())) {
                continue;
            }
            if (StringUtils.hasText(link.getUserId())) {
                ids.add(link.getUserId().trim());
            }
        }
        return new ArrayList<>(ids);
    }
}
