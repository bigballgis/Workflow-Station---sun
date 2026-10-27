package com.admin.controller;

import com.admin.component.DeploymentManagerComponent;
import com.admin.component.FunctionUnitManagerComponent;
import com.admin.component.ProcessDeploymentComponent;
import com.platform.common.i18n.I18nService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Disabling a release must persist its deprecation, as {@code /function-units/{id}/deprecate} does. */
@ExtendWith(MockitoExtension.class)
class FunctionUnitImportControllerDisableTest {

    @Mock private FunctionUnitManagerComponent functionUnitManager;
    @Mock private DeploymentManagerComponent deploymentManager;
    @Mock private ProcessDeploymentComponent processDeploymentComponent;
    @Mock private I18nService i18nService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        lenient().when(i18nService.getMessage(anyString())).thenAnswer(inv -> inv.getArgument(0));
        mockMvc = MockMvcBuilders.standaloneSetup(new FunctionUnitImportController(
                functionUnitManager, deploymentManager, processDeploymentComponent, i18nService)).build();
    }

    @Test
    void disablePersistsTheDeprecation() throws Exception {
        mockMvc.perform(post("/function-units-import/fu-1/disable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        verify(functionUnitManager).deprecateFunctionUnit("fu-1");
    }
}
