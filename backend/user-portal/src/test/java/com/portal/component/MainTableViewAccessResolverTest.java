package com.portal.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.platform.security.entity.UserBusinessUnit;
import com.portal.repository.UserBusinessUnitRepository;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MainTableViewAccessResolverTest {

    private FunctionUnitAccessComponent functionUnitAccessComponent;
    private UserBusinessUnitRepository userBusinessUnitRepository;
    private MainTableViewAccessResolver resolver;

    @BeforeEach
    void setUp() {
        functionUnitAccessComponent = mock(FunctionUnitAccessComponent.class);
        userBusinessUnitRepository = mock(UserBusinessUnitRepository.class);
        resolver = new MainTableViewAccessResolver(functionUnitAccessComponent, userBusinessUnitRepository);
    }

    @Test
    void sysAdminBypassesViewRules() {
        when(functionUnitAccessComponent.isSystemAdministrator("admin")).thenReturn(true);

        boolean visible = resolver.canUserSeeView("admin", List.of(
                new MainTableViewAccessResolver.AccessRule("ROLE", "role-other"),
                new MainTableViewAccessResolver.AccessRule("BUSINESS_UNIT", "bu-other")));

        assertThat(visible).isTrue();
    }

    @Test
    void emptyRulesHiddenFromNonAdmin() {
        assertThat(resolver.canUserSeeView("user-1", List.of())).isFalse();
        assertThat(resolver.canUserSeeView("user-1", null)).isFalse();
    }

    @Test
    void emptyRulesVisibleToSysAdmin() {
        when(functionUnitAccessComponent.isSystemAdministrator("admin")).thenReturn(true);
        assertThat(resolver.canUserSeeView("admin", List.of())).isTrue();
    }

    @Test
    void requiresBothBuAndRoleWhenBothConfigured() {
        UserBusinessUnit membership = UserBusinessUnit.builder()
                .userId("user-1")
                .businessUnitId("bu-1")
                .build();
        when(userBusinessUnitRepository.findByUserId("user-1")).thenReturn(List.of(membership));
        when(functionUnitAccessComponent.getUserBusinessRoleIds("user-1")).thenReturn(Set.of("role-1"));

        List<MainTableViewAccessResolver.AccessRule> rules = List.of(
                new MainTableViewAccessResolver.AccessRule("BUSINESS_UNIT", "bu-1"),
                new MainTableViewAccessResolver.AccessRule("ROLE", "role-1"));

        assertThat(resolver.canUserSeeView("user-1", rules)).isTrue();
        assertThat(resolver.canUserSeeView("user-1", List.of(
                new MainTableViewAccessResolver.AccessRule("BUSINESS_UNIT", "bu-1"),
                new MainTableViewAccessResolver.AccessRule("ROLE", "role-missing")))).isFalse();
        assertThat(resolver.canUserSeeView("user-1", List.of(
                new MainTableViewAccessResolver.AccessRule("BUSINESS_UNIT", "bu-missing"),
                new MainTableViewAccessResolver.AccessRule("ROLE", "role-1")))).isFalse();
    }

    @Test
    void partialConfigHiddenFromNonAdmin() {
        UserBusinessUnit membership = UserBusinessUnit.builder()
                .userId("user-3")
                .businessUnitId("bu-a")
                .build();
        when(userBusinessUnitRepository.findByUserId("user-3")).thenReturn(List.of(membership));
        when(functionUnitAccessComponent.getUserBusinessRoleIds("user-4"))
                .thenReturn(Set.of("role-a"));

        assertThat(resolver.canUserSeeView("user-3", List.of(
                new MainTableViewAccessResolver.AccessRule("BUSINESS_UNIT", "bu-a")))).isFalse();
        assertThat(resolver.canUserSeeView("user-4", List.of(
                new MainTableViewAccessResolver.AccessRule("ROLE", "role-a")))).isFalse();
    }
}
