package com.admin.service;

import com.admin.client.EngineInternalClient;
import com.admin.dto.request.ClaimPoolResyncRequest.Pool;
import com.admin.exception.BusinessUnitNotFoundException;
import com.admin.exception.RoleNotFoundException;
import com.admin.repository.BusinessUnitRepository;
import com.admin.repository.RoleRepository;
import com.platform.security.entity.BusinessUnit;
import com.platform.security.entity.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The engine hears about a BU + Role pool only after the UBR change is committed, never on rollback, and a failed
 * push does not turn a committed change into an error.
 */
class ClaimPoolResyncServiceTest {

    private final BusinessUnitRepository businessUnitRepository = mock(BusinessUnitRepository.class);
    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private final EngineInternalClient client = mock(EngineInternalClient.class);
    private final ClaimPoolResyncService service =
            new ClaimPoolResyncService(businessUnitRepository, roleRepository, client);

    @BeforeEach
    void setUp() {
        when(businessUnitRepository.findById("bu-1")).thenReturn(Optional.of(
                BusinessUnit.builder().id("bu-1").code("E2E_FINANCE").build()));
        when(roleRepository.findAllById(anyCollection())).thenReturn(List.of(
                Role.builder().id("role-1").code("MANAGER").build(),
                Role.builder().id("role-2").code("CLERK").build()));
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void pushesOnceAfterCommitWithIdsAndCodes() {
        TransactionSynchronizationManager.initSynchronization();

        service.ubrChanged("bu-1", List.of("role-1", "role-2", "role-1"));

        verifyNoInteractions(client);
        commit();
        verify(client).resyncClaimPools(List.of(
                new Pool("bu-1", "E2E_FINANCE", "role-1", "MANAGER"),
                new Pool("bu-1", "E2E_FINANCE", "role-2", "CLERK")));
    }

    @Test
    void rollbackPushesNothing() {
        TransactionSynchronizationManager.initSynchronization();

        service.ubrChanged("bu-1", List.of("role-1"));
        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }

        verifyNoInteractions(client);
    }

    @Test
    void pushesImmediatelyWithoutTransaction() {
        service.ubrChanged("bu-1", List.of("role-1"));

        verify(client).resyncClaimPools(List.of(new Pool("bu-1", "E2E_FINANCE", "role-1", "MANAGER")));
    }

    @Test
    void failedPushAfterCommitDoesNotFailTheCommittedChange() {
        doThrow(new ResourceAccessException("engine down")).when(client).resyncClaimPools(any());
        TransactionSynchronizationManager.initSynchronization();
        service.ubrChanged("bu-1", List.of("role-1"));

        assertThatCode(this::commit).doesNotThrowAnyException();
        verify(client).resyncClaimPools(any());
    }

    @Test
    void nothingChangedMeansNoCall() {
        service.ubrChanged("bu-1", List.of());

        verifyNoInteractions(client, businessUnitRepository, roleRepository);
    }

    @Test
    void unknownBusinessUnitFailsInsideTheWritersTransaction() {
        TransactionSynchronizationManager.initSynchronization();

        assertThatThrownBy(() -> service.ubrChanged("bu-missing", List.of("role-1")))
                .isInstanceOf(BusinessUnitNotFoundException.class);
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
    }

    @Test
    void unknownRoleFailsInsideTheWritersTransaction() {
        TransactionSynchronizationManager.initSynchronization();

        assertThatThrownBy(() -> service.ubrChanged("bu-1", List.of("role-missing")))
                .isInstanceOf(RoleNotFoundException.class);
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
    }

    private void commit() {
        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCommit();
        }
    }
}
