package com.admin.service;

import com.admin.client.EngineInternalClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** The engine hears that a user left only once that is committed, and a failed call keeps the change (D-42). */
class DepartedUserTasksServiceTest {

    private final EngineInternalClient client = mock(EngineInternalClient.class);
    private final DepartedUserTasksService service = new DepartedUserTasksService(client);

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void tellsTheEngineAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();

        service.userDeparted("u-1");

        verifyNoInteractions(client);
        complete(true);
        verify(client).reassignTasksOfDepartedUsers(List.of("u-1"));
    }

    @Test
    void rollbackTellsNothing() {
        TransactionSynchronizationManager.initSynchronization();

        service.userDeparted("u-1");
        complete(false);

        verifyNoInteractions(client);
    }

    @Test
    void tellsImmediatelyWithoutTransaction() {
        service.userDeparted("u-1");

        verify(client).reassignTasksOfDepartedUsers(List.of("u-1"));
    }

    @Test
    void aFailedCallDoesNotFailTheCommittedChange() {
        doThrow(new ResourceAccessException("engine down")).when(client).reassignTasksOfDepartedUsers(any());
        TransactionSynchronizationManager.initSynchronization();
        service.userDeparted("u-1");

        assertThatCode(() -> complete(true)).doesNotThrowAnyException();
        verify(client).reassignTasksOfDepartedUsers(List.of("u-1"));
    }

    private static void complete(boolean committed) {
        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            if (committed) {
                sync.afterCommit();
            }
            sync.afterCompletion(committed ? TransactionSynchronization.STATUS_COMMITTED
                    : TransactionSynchronization.STATUS_ROLLED_BACK);
        }
    }
}
