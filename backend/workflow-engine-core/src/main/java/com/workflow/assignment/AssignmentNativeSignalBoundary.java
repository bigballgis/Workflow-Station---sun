package com.workflow.assignment;

import com.platform.common.assignment.AssignmentFailure;
import org.flowable.common.engine.impl.ServiceConfigurator;
import org.flowable.eventsubscription.service.EventSubscriptionServiceConfiguration;
import org.flowable.eventsubscription.service.impl.EventSubscriptionServiceImpl;
import org.flowable.eventsubscription.service.impl.persistence.entity.SignalEventSubscriptionEntity;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import java.util.List;

/** Validate the exact subscription list consumed by SignalEventReceivedCmd, before its first synchronous effect or async job. */
@Component
public class AssignmentNativeSignalBoundary implements ServiceConfigurator<EventSubscriptionServiceConfiguration> {
    private final AssignmentLegacyTaskCheck legacy;
    private final AssignmentDefinitionPolicy definitions;
    public AssignmentNativeSignalBoundary(@Lazy AssignmentLegacyTaskCheck legacy,@Lazy AssignmentDefinitionPolicy definitions) {
        this.legacy=legacy; this.definitions=definitions;
    }
    @Override public void beforeInit(EventSubscriptionServiceConfiguration configuration) { }
    @Override public int getPriority() { return Integer.MAX_VALUE; }
    @Override public void afterInit(EventSubscriptionServiceConfiguration configuration) {
        if (configuration.getEventSubscriptionService().getClass()!=EventSubscriptionServiceImpl.class) {
            throw new IllegalStateException("Assignment native signal boundary requires the supported Flowable subscription service");
        }
        configuration.setEventSubscriptionService(new NativeSignals(configuration));
    }
    private void check(List<SignalEventSubscriptionEntity> subscriptions) {
        for (var subscription:subscriptions) {
            if (!subscription.isGlobalScoped()) continue; // Same selection as the SDK command's dispatch loop.
            if (subscription.getProcessInstanceId()!=null) legacy.requireLegacyInstance(subscription.getProcessInstanceId());
            else if (subscription.getExecutionId()!=null) legacy.requireLegacyExecution(subscription.getExecutionId());
            else if (subscription.getProcessDefinitionId()==null || definitions.tracked(subscription.getProcessDefinitionId())) {
                throw new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE);
            }
        }
    }
    private final class NativeSignals extends EventSubscriptionServiceImpl {
        NativeSignals(EventSubscriptionServiceConfiguration configuration) { super(configuration); }
        @Override public List<SignalEventSubscriptionEntity> findSignalEventSubscriptionsByEventName(String name,String tenant) {
            var captured=super.findSignalEventSubscriptionsByEventName(name,tenant);
            if (!AssignmentNativeRestScope.active()) return captured;
            var selected=List.copyOf(captured);
            check(selected);
            return selected; // No second query: concurrent new subscriptions are outside this broadcast's captured set.
        }
    }
}
