package com.portal.testsupport;

import com.platform.common.assignment.AssignmentTaskReadSnapshot.*;

/** Old batch behavior fixtures explicitly represent positively legacy tasks, not missing authorization context. */
public final class AssignmentReadTestSupport {
    private AssignmentReadTestSupport() { }
    public static Entry legacy(String task) { return new Entry(task,false,State.LEGACY,new Claim(true,null,null),null); }
}
