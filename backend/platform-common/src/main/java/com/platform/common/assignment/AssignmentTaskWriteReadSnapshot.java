package com.platform.common.assignment;

/** Informational write veto only. Existing operation/workspace permissions and final write authorization still apply. */
public record AssignmentTaskWriteReadSnapshot(int contractVersion,Boolean tracked,boolean exclusionAllowsWrite,
        String reason,AssignmentTaskReadSnapshot.Stamp stamp) { }
