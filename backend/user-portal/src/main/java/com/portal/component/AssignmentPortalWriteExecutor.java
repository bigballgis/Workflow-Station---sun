package com.portal.component;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.function.Supplier;

/** Prepare outside the local transaction; take the case fence before any business row can be locked. */
@Component
public class AssignmentPortalWriteExecutor {
    private final AssignmentPortalWriteGuard guard;
    private final TransactionTemplate transaction;
    public AssignmentPortalWriteExecutor(AssignmentPortalWriteGuard guard,PlatformTransactionManager manager) {
        this.guard=guard; transaction=new TransactionTemplate(manager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }
    public <T> T execute(AssignmentPortalWriteGuard.Request request,boolean changesCandidates,Supplier<T> write) {
        var decision=guard.prepare(request);
        return transaction.execute(status->{
            guard.enter(decision,request);
            T result=write.get();
            guard.written(decision,changesCandidates);
            return result;
        });
    }
}
