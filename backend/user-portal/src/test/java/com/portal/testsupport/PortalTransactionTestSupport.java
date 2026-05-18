package com.portal.testsupport;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit-test helpers for components that depend on {@link PlatformTransactionManager}.
 */
public final class PortalTransactionTestSupport {

    private PortalTransactionTestSupport() {
    }

    /**
     * A Mockito-backed manager whose {@code getTransaction}/{@code commit}/{@code rollback}
     * no-op so {@link org.springframework.transaction.support.TransactionTemplate}
     * runs callbacks synchronously without a real datasource.
     */
    public static PlatformTransactionManager noopPlatformTransactionManager() {
        PlatformTransactionManager tm = mock(PlatformTransactionManager.class);
        when(tm.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());
        doNothing().when(tm).commit(any(TransactionStatus.class));
        doNothing().when(tm).rollback(any(TransactionStatus.class));
        return tm;
    }
}
