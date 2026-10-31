package com.workflow.assignment;

import com.platform.common.assignment.AssignmentExclusionBpmnValidator;
import com.platform.common.assignment.AssignmentFailure;
import org.springframework.stereotype.Component;

/** Runtime capability is checked before any Flowable deployment is created. */
@Component
public class AssignmentDeploymentPreparation {
    private final AssignmentDeploymentCompiler compiler;
    private final AssignmentRuntimeSupport support;
    public AssignmentDeploymentPreparation(AssignmentDeploymentCompiler compiler,AssignmentRuntimeSupport support) { this.compiler=compiler; this.support=support; }
    public String prepare(String xml) {
        try {
            support.require(AssignmentExclusionBpmnValidator.policies(xml).values().stream().anyMatch(policy->policy.enabled()));
            var compiled=compiler.compileDeployment(xml);
            support.require(compiled.tracked());
            return compiled.xml();
        } catch (IllegalArgumentException invalid) { throw new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_POLICY_INVALID); }
    }
}
