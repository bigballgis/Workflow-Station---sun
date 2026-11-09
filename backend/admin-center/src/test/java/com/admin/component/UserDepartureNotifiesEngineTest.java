package com.admin.component;

import com.admin.repository.BusinessUnitRepository;
import com.admin.repository.LoginAuditQueryRepository;
import com.admin.repository.PasswordHistoryRepository;
import com.admin.repository.UserBusinessUnitRepository;
import com.admin.repository.UserRepository;
import com.admin.repository.VirtualGroupMemberRepository;
import com.admin.repository.VirtualGroupRepository;
import com.admin.service.DepartedUserTasksService;
import com.platform.common.i18n.I18nService;
import com.platform.security.entity.User;
import com.platform.security.model.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Setting a user INACTIVE or deleting them hands their tasks to their manager; LOCKED does not (D-42). */
class UserDepartureNotifiesEngineTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final DepartedUserTasksService departedUserTasksService = mock(DepartedUserTasksService.class);
    private final UserManagerComponent component = new UserManagerComponent(userRepository,
            mock(BusinessUnitRepository.class), mock(PasswordHistoryRepository.class), mock(PasswordEncoder.class),
            mock(UserBusinessUnitRepository.class), mock(LoginAuditQueryRepository.class), mock(I18nService.class),
            mock(VirtualGroupRepository.class), mock(VirtualGroupMemberRepository.class), departedUserTasksService);

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().id("u-1").username("u1").status(UserStatus.ACTIVE).deleted(false).build();
        when(userRepository.findById("u-1")).thenReturn(Optional.of(user));
    }

    @Test
    void inactiveTellsTheEngine() {
        component.updateUserStatus("u-1", UserStatus.INACTIVE, "left");

        verify(departedUserTasksService).userDeparted("u-1");
    }

    @Test
    void lockedIsNotLeaving() {
        component.updateUserStatus("u-1", UserStatus.LOCKED, "too many attempts");

        verify(departedUserTasksService, never()).userDeparted(anyString());
    }

    @Test
    void deletionTellsTheEngine() {
        component.deleteUser("u-1");

        verify(departedUserTasksService).userDeparted("u-1");
    }
}
