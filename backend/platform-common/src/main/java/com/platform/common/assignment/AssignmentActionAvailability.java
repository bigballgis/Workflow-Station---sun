package com.platform.common.assignment;

import java.util.List;

/** An informational action hint; the mutation always repeats current qualification and case checks. */
public record AssignmentActionAvailability(int contractVersion,String resourceId,String actor,boolean allowed,List<String> targets) {
    public AssignmentActionAvailability { targets=List.copyOf(targets); }
}
