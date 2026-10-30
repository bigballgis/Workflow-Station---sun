package com.workflow.assignment;

import com.platform.common.assignment.AssignmentExclusionPolicyCodec;
import com.workflow.util.BpmnExtensionUtils;
import org.flowable.bpmn.model.BaseElement;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.impl.bpmn.parser.BpmnParse;
import org.flowable.engine.parse.BpmnParseHandler;
import java.util.Collection;
import java.util.List;

/** All deployment APIs reach parsing. Existing definitions may be re-parsed after cache eviction or restart. */
public final class AssignmentDeploymentParseGuard implements BpmnParseHandler {
    private final AssignmentDefinitionRetention retention;
    public AssignmentDeploymentParseGuard(AssignmentDefinitionRetention retention) { this.retention=retention; }
    @Override public Collection<Class<? extends BaseElement>> getHandledTypes() { return List.of(org.flowable.bpmn.model.Process.class); }
    @Override public void parse(BpmnParse parse,BaseElement element) {
        if (!parse.getDeployment().isNew()) return; // Restore the existing frozen contract, without granting a new deployment scope.
        var process=(org.flowable.bpmn.model.Process)element;
        boolean compiled=List.of(AssignmentDeploymentCompiler.CONTRACT,AssignmentDeploymentCompiler.TRACKING,AssignmentDeploymentCompiler.USERS,AssignmentDeploymentCompiler.DEPENDENCIES)
                .stream().anyMatch(property->BpmnExtensionUtils.getExtensionProperty(process,property)!=null);
        boolean protectedPolicy=process.findFlowElementsOfType(UserTask.class,true).stream().anyMatch(task->{
            String value=BpmnExtensionUtils.getExtensionProperty(task,"assignmentExclusion");
            return value!=null && AssignmentExclusionPolicyCodec.decode(value).enabled();
        });
        if (compiled || protectedPolicy) {
            AssignmentCompiledDeployment.require(parse.getDeployment());
            retention.publication(parse.getDeployment().getId(),AssignmentFrozenDefinitions.read(process));
        }
    }
}
