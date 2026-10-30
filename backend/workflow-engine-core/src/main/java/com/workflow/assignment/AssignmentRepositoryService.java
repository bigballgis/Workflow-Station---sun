package com.workflow.assignment;

import org.flowable.common.engine.impl.interceptor.CommandContext;
import org.flowable.engine.impl.RepositoryServiceImpl;
import org.flowable.engine.impl.cmd.DeleteDeploymentCmd;

/** Every SDK/platform/native deployment delete executes the veto inside the same Flowable transaction. */
public final class AssignmentRepositoryService extends RepositoryServiceImpl {
    private final AssignmentDefinitionRetention retention;
    public AssignmentRepositoryService(AssignmentDefinitionRetention retention) { this.retention=retention; }
    @Override public void suspendProcessDefinitionById(String id,boolean instances,java.util.Date date) {
        if (!instances) { super.suspendProcessDefinitionById(id,false,date); return; }
        cascade(id,()->super.suspendProcessDefinitionById(id,true,date));
    }
    @Override public void activateProcessDefinitionById(String id,boolean instances,java.util.Date date) {
        if (!instances) { super.activateProcessDefinitionById(id,false,date); return; }
        cascade(id,()->super.activateProcessDefinitionById(id,true,date));
    }
    private void cascade(String id,Runnable change) {
        commandExecutor.execute(context->{
            retention.cascade(id);
            var definition=createProcessDefinitionQuery().processDefinitionId(id).singleResult();
            if (definition!=null && new AssignmentDefinitionPolicy(this).tracked(id)) {
                throw new com.platform.common.assignment.AssignmentFailure(com.platform.common.assignment.AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE);
            }
            change.run();
            return null;
        });
    }
    @Override public void deleteDeployment(String id) { deleteDeployment(id,false); }
    @Override public void deleteDeploymentCascade(String id) { deleteDeployment(id,true); }
    @Override public void deleteDeployment(String id,boolean cascade) {
        commandExecutor.execute(new DeleteDeploymentCmd(id,cascade) {
            @Override public Void execute(CommandContext context) {
                retention.deletion(id);
                var result=super.execute(context);
                retention.deleted(id);
                return result;
            }
        });
    }
}
