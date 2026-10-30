package com.workflow.assignment.impl;

import com.platform.common.assignment.AssignmentFailure;
import com.workflow.assignment.*;
import com.workflow.dto.request.StartProcessRequest;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Server allocates root/round context before Flowable can synchronously create the first task. */
@Component
public class AssignmentRootStarterImpl implements AssignmentRootStarter {
    private final RuntimeService runtime;
    private final AssignmentCaseRegistry registry;
    private final AssignmentRootQualification qualification;
    private final AssignmentDefinitionPolicy definitions;
    private final TransactionTemplate transaction;
    @org.springframework.beans.factory.annotation.Autowired @org.springframework.context.annotation.Lazy
    private org.flowable.engine.HistoryService history;
    @org.springframework.beans.factory.annotation.Autowired private AssignmentRuntimeSupport runtimeSupport;

    public AssignmentRootStarterImpl(RuntimeService runtime, AssignmentCaseRegistry registry, AssignmentRootQualification qualification,
            AssignmentDefinitionPolicy definitions, PlatformTransactionManager manager) {
        this.runtime = runtime; this.registry = registry; this.qualification = qualification; this.definitions = definitions;
        transaction = new TransactionTemplate(manager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Override public boolean supports(String definition) { return definitions.tracked(definition); }

    @Override public ProcessInstance start(StartProcessRequest request, String definition, java.util.function.Consumer<ProcessInstance> created) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE);
        runtimeSupport.require(true);
        var prepared = qualification.prepare(request);
        var variables = request.getVariables() == null ? new java.util.LinkedHashMap<String,Object>()
                : new java.util.LinkedHashMap<>(request.getVariables());
        variables.put("initiator", prepared.actor());
        if (prepared.activeBu() == null) variables.remove("activeBusinessUnitId");
        else variables.put("activeBusinessUnitId", prepared.activeBu());
        request.setStartUserId(prepared.actor());
        request.setVariables(variables);
        return transaction.execute(status -> {
            var registration = registry.newRoot(definition, prepared.actor(), prepared.startBuCode(), prepared.activeBu());
            qualification.check(prepared);
            String previous = org.flowable.common.engine.impl.identity.Authentication.getAuthenticatedUserId();
            try {
                org.flowable.common.engine.impl.identity.Authentication.setAuthenticatedUserId(prepared.actor());
                var instance = runtime.createProcessInstanceBuilder().processDefinitionId(registration.definition())
                        .predefineProcessInstanceId(registration.instance()).businessKey(request.getBusinessKey())
                        .name(request.getProcessInstanceName()).tenantId(request.getTenantId()).variables(variables).start();
                request.setVariables(AssignmentProcessVariables.read(history,instance.getId()));
                created.accept(instance);
                return instance;
            } finally { org.flowable.common.engine.impl.identity.Authentication.setAuthenticatedUserId(previous); }
        });
    }
}
