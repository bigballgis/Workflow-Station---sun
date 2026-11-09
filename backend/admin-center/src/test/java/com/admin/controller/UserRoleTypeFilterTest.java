package com.admin.controller;

import com.admin.component.RolePermissionManagerComponent;
import com.admin.component.UserListQueryComponent;
import com.admin.component.UserManagerComponent;
import com.admin.exception.AdminApiExceptionHandler;
import com.admin.helper.RoleHelper;
import com.admin.repository.RoleRepository;
import com.admin.repository.VirtualGroupMemberRepository;
import com.admin.repository.VirtualGroupRepository;
import com.admin.service.UserBusinessUnitService;
import com.admin.service.UserImportService;
import com.admin.service.UserPermissionService;
import com.admin.service.UserPortalMembershipService;
import com.platform.common.i18n.MessageSourceConfig;
import com.platform.common.i18n.impl.I18nServiceImpl;
import com.platform.security.entity.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /users/{id}/roles?type=}: an unknown type is a business error ({@code ROLE_TYPE_INVALID}) worded in the
 * caller's language and listing the accepted values, never the enum's class name. Messages come from the real
 * bundles, wired with the {@code spring.messages.basename} of application.yml.
 */
class UserRoleTypeFilterTest {

    private final UserPermissionService userPermissionService = mock(UserPermissionService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        UserController controller = new UserController(
                mock(UserManagerComponent.class), mock(UserImportService.class),
                mock(RolePermissionManagerComponent.class), mock(VirtualGroupMemberRepository.class),
                mock(VirtualGroupRepository.class), mock(UserBusinessUnitService.class), userPermissionService,
                new RoleHelper(mock(RoleRepository.class)), mock(UserPortalMembershipService.class),
                mock(UserListQueryComponent.class),
                new I18nServiceImpl(new MessageSourceConfig().messageSource(configuredBasenames(), "UTF-8")));
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new AdminApiExceptionHandler())
                .build();
        when(userPermissionService.getUserRolesForProfile("u-1", null)).thenReturn(List.of(
                role("role-admin", "SYS_ADMIN", "ADMIN"),
                role("role-dev", "DEVELOPER", "DEVELOPER"),
                role("role-manager", "MANAGER", "BU_BOUNDED")));
    }

    @Test
    void shouldReturnBusinessError_whenTypeIsUnknown() throws Exception {
        String body = mvc.perform(get("/users/u-1/roles").param("type", "NOPE").header("Accept-Language", "en"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ROLE_TYPE_INVALID"))
                .andExpect(jsonPath("$.error.message")
                        .value("Unknown role type: NOPE. Use one of: BUSINESS, BU_BOUNDED, ADMIN, AUDITOR, DEVELOPER"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("No enum constant").doesNotContain("com.admin");
    }

    @Test
    void shouldWordTheErrorInTheCallersLanguage_whenTypeIsUnknown() throws Exception {
        mvc.perform(get("/users/u-1/roles").param("type", "admin").header("Accept-Language", "zh-CN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message")
                        .value("无法识别的角色类型：admin。可用的值：BUSINESS, BU_BOUNDED, ADMIN, AUDITOR, DEVELOPER"));
        mvc.perform(get("/users/u-1/roles").param("type", "admin").header("Accept-Language", "zh-TW"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message")
                        .value("無法識別的角色類型：admin。可用的值：BUSINESS, BU_BOUNDED, ADMIN, AUDITOR, DEVELOPER"));
    }

    @Test
    void shouldFilterByType_whenTypeIsKnown() throws Exception {
        mvc.perform(get("/users/u-1/roles").param("type", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].code").value("SYS_ADMIN"));
        mvc.perform(get("/users/u-1/roles").param("type", "BUSINESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].code").value("MANAGER"));
        mvc.perform(get("/users/u-1/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    /** The bundle list the service is configured with, so the test reads messages exactly as the app does. */
    private static String configuredBasenames() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        return yaml.getObject().getProperty("spring.messages.basename");
    }

    private static Role role(String id, String code, String type) {
        return Role.builder().id(id).code(code).name(code).type(type).build();
    }
}
