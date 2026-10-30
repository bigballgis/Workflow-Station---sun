package com.portal.dto;

import com.platform.common.assignment.AssignmentTaskWriteReadSnapshot;

/** Portal operation permission AND Engine qualification; original viewing rules remain separate. */
public record AssignmentTaskWriteAccess(boolean canWrite,AssignmentTaskWriteReadSnapshot evaluation) { }
