package com.portal.component;

import com.portal.entity.UserPreference;
import com.portal.repository.DashboardLayoutRepository;
import com.portal.repository.NotificationPreferenceRepository;
import com.portal.repository.UserPreferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPreferenceComponentAutoClaimTest {

    @Mock
    private UserPreferenceRepository userPreferenceRepository;
    @Mock
    private DashboardLayoutRepository dashboardLayoutRepository;
    @Mock
    private NotificationPreferenceRepository notificationPreferenceRepository;

    private UserPreferenceComponent component;

    @BeforeEach
    void setUp() {
        component = new UserPreferenceComponent(
                userPreferenceRepository, dashboardLayoutRepository, notificationPreferenceRepository);
    }

    @Test
    void updateCopiesAutoClaimOnOpen() {
        UserPreference existing = UserPreference.builder()
                .userId("u1")
                .autoClaimOnOpen(Boolean.FALSE)
                .build();
        when(userPreferenceRepository.findByUserId("u1")).thenReturn(Optional.of(existing));
        when(userPreferenceRepository.save(existing)).thenReturn(existing);

        component.updateUserPreference("u1", UserPreference.builder().autoClaimOnOpen(Boolean.TRUE).build());

        ArgumentCaptor<UserPreference> captor = ArgumentCaptor.forClass(UserPreference.class);
        verify(userPreferenceRepository).save(captor.capture());
        assertThat(captor.getValue().getAutoClaimOnOpen()).isTrue();
    }
}
