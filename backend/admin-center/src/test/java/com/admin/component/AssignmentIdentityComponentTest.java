package com.admin.component;

import com.admin.repository.UserRepository;
import com.platform.common.assignment.AssignmentIdentitySnapshot;
import com.platform.security.entity.User;
import com.platform.security.model.UserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AssignmentIdentityComponentTest {
    final UserRepository users = mock(UserRepository.class);
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final DataSource source = mock(DataSource.class);
    final AssignmentManagementAuthority permissions = mock(AssignmentManagementAuthority.class);
    final AssignmentAuthorityTimeWindow time=mock(AssignmentAuthorityTimeWindow.class);
    final AssignmentIdentityComponent component = new com.admin.component.impl.AssignmentIdentityComponentImpl(users, jdbc, permissions,time);

    @BeforeEach void bindTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.bindResource(source, new Object());
        when(jdbc.getDataSource()).thenReturn(source);
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(7L);
        when(users.findAllById(any())).thenReturn(List.of());
        when(users.findByUsernameIn(any())).thenReturn(List.of());
        when(time.read()).thenReturn(new AssignmentIdentitySnapshot.TimeWindow(null,java.time.Instant.now().plusSeconds(5),java.time.Instant.now()));
    }

    @AfterEach void clearTransaction() {
        TransactionSynchronizationManager.unbindResource(source);
        TransactionSynchronizationManager.clear();
    }

    @Test void exactUsernameProducesCanonicalIdAndAuthorityVersion() {
        var user = user("canonical-id", "exact-user", UserStatus.ACTIVE);
        when(users.findByUsernameIn(List.of("exact-user"))).thenReturn(List.of(user));
        var snapshot = component.resolve(request("exact-user", AssignmentIdentitySnapshot.Kind.USERNAME));
        assertEquals(7, snapshot.authorityRevision());
        assertEquals("canonical-id", snapshot.users().get("exact-user").id());
        assertTrue(snapshot.users().get("exact-user").active());
    }

    @Test void displayNameOrSubstringCannotBindAnUnknownIdentity() {
        var snapshot = component.resolve(request("partial", AssignmentIdentitySnapshot.Kind.USERNAME));
        assertTrue(snapshot.users().isEmpty());
        assertEquals(java.util.Set.of("partial"), snapshot.missingReferences());
        verify(users).findByUsernameIn(List.of("partial"));
    }

    @Test void disabledAndDeletedAccountsRemainExplicitlyIneligible() {
        var user = user("id", "exact", UserStatus.INACTIVE);
        when(users.findAllById(List.of("id"))).thenReturn(List.of(user));
        assertFalse(component.resolve(request("id", AssignmentIdentitySnapshot.Kind.ID)).users().get("id").active());
        user.setStatus(UserStatus.ACTIVE);
        user.setDeleted(true);
        assertFalse(component.resolve(request("id", AssignmentIdentitySnapshot.Kind.ID)).users().get("id").active());
    }

    @Test void ambiguousLegacyIdentityMustNotChooseAnArbitraryAccount() {
        when(users.findAllById(List.of("same"))).thenReturn(List.of(user("same", "first", UserStatus.ACTIVE)));
        when(users.findByUsernameIn(List.of("same"))).thenReturn(List.of(user("other", "same", UserStatus.ACTIVE)));
        assertThrows(IllegalArgumentException.class, () -> component.resolve(request("same", AssignmentIdentitySnapshot.Kind.ID_OR_USERNAME)));
    }

    @Test void managementReadsCurrentAdministratorRoleAndDoesNotRetainARevokedGrant() {
        when(users.findById("id")).thenReturn(java.util.Optional.of(user("id","account",UserStatus.ACTIVE)));
        when(permissions.systemAdministrator("id")).thenReturn(true,false);
        assertTrue(component.management("id").systemAdministrator());
        var current=component.management("id");
        assertEquals(7,current.authorityRevision()); assertTrue(current.active()); assertFalse(current.systemAdministrator());
        verify(permissions,times(2)).systemAdministrator("id");
    }

    @Test void inactiveOrMissingAdministratorCannotManageReopen() {
        when(users.findById("disabled")).thenReturn(java.util.Optional.of(user("disabled","account",UserStatus.INACTIVE)));
        when(users.findById("missing")).thenReturn(java.util.Optional.empty());
        assertFalse(component.management("disabled").systemAdministrator()); assertFalse(component.management("missing").active());
        verifyNoInteractions(permissions);
    }

    private AssignmentIdentitySnapshot.Request request(String value, AssignmentIdentitySnapshot.Kind kind) {
        return new AssignmentIdentitySnapshot.Request(List.of(new AssignmentIdentitySnapshot.Reference(value, kind)));
    }

    private User user(String id, String username, UserStatus status) {
        User user = new User();
        user.setId(id); user.setUsername(username); user.setStatus(status); user.setDeleted(false);
        return user;
    }
}
