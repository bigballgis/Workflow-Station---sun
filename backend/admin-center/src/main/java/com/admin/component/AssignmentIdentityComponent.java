package com.admin.component;

import com.platform.common.assignment.AssignmentIdentitySnapshot;

/** Exact identity authority used only by authenticated first-party services. */
public interface AssignmentIdentityComponent {
    AssignmentIdentitySnapshot resolve(AssignmentIdentitySnapshot.Request request);
    com.platform.common.assignment.AssignmentManagementSnapshot management(String userId);
}
