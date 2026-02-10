package com.admin.controller;

import com.admin.component.DeploymentManagerComponent;
import com.admin.component.FunctionUnitManagerComponent;
import com.admin.component.ProcessDeploymentComponent;
import com.admin.dto.response.VersionHistoryEntry;
import com.admin.entity.FunctionUnit;
import com.admin.enums.FunctionUnitStatus;
import com.admin.exception.AdminBusinessException;
import com.admin.exception.FunctionUnitNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 功能单元版本管理控制器单元测试
 * 
 * 使用 standalone MockMvc 设置，避免加载完整 Spring ApplicationContext。
 * 测试版本激活和版本历史端点。
 * 
 * Feature: function-unit-version-management
 * Validates: Requirements 2.3, 2.4, 4.1, 4.5, 6.1, 6.2
 */
@ExtendWith(MockitoExtension.class)
class FunctionUnitVersionManagementControllerTest {

    private MockMvc mockMvc;

    @Mock
    private FunctionUnitManagerComponent functionUnitManager;

    @Mock
    private DeploymentManagerComponent deploymentManager;

    @Mock
    private ProcessDeploymentComponent processDeploymentComponent;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private FunctionUnitImportController controller;

    private FunctionUnit testUnit;
    private String testCode;
    private String testVersion;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        testCode = "TEST_FUNCTION_UNIT";
        testVersion = "1.0.0";
        
        testUnit = FunctionUnit.builder()
                .id("test-id-123")
                .code(testCode)
                .name("Test Function Unit")
                .version(testVersion)
                .status(FunctionUnitStatus.DEPLOYED)
                .enabled(true)
                .createdAt(Instant.now())
                .deployedAt(Instant.now())
                .build();
    }

    // ==================== 版本激活端点测试 ====================

    @Test
    @DisplayName("成功激活版本应该返回 200 和功能单元信息")
    void activateVersion_Success() throws Exception {
        when(functionUnitManager.activateVersion(eq(testCode), eq(testVersion), anyString()))
                .thenReturn(testUnit);

        mockMvc.perform(post("/function-units-import/{code}/activate/{version}", testCode, testVersion)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.functionUnitId").value("test-id-123"))
                .andExpect(jsonPath("$.code").value(testCode))
                .andExpect(jsonPath("$.version").value(testVersion))
                .andExpect(jsonPath("$.name").value("Test Function Unit"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.message").value("版本激活成功"));
    }

    @Test
    @DisplayName("激活不存在的版本应该返回 404")
    void activateVersion_NotFound() throws Exception {
        when(functionUnitManager.activateVersion(eq(testCode), eq(testVersion), anyString()))
                .thenThrow(new FunctionUnitNotFoundException("功能单元版本不存在: " + testCode + ":" + testVersion));

        mockMvc.perform(post("/function-units-import/{code}/activate/{version}", testCode, testVersion)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.message").value(containsString("功能单元版本不存在")));
    }

    @Test
    @DisplayName("激活 DRAFT 状态版本应该返回 400")
    void activateVersion_DraftStatus() throws Exception {
        when(functionUnitManager.activateVersion(eq(testCode), eq(testVersion), anyString()))
                .thenThrow(new AdminBusinessException("INVALID_STATUS", 
                        "无法激活状态为 DRAFT 的版本。只能激活 VALIDATED 或 DEPLOYED 状态的版本。"));

        mockMvc.perform(post("/function-units-import/{code}/activate/{version}", testCode, testVersion)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.message").value(containsString("DRAFT")))
                .andExpect(jsonPath("$.message").value(containsString("VALIDATED 或 DEPLOYED")));
    }

    @Test
    @DisplayName("激活 DEPRECATED 状态版本应该返回 400")
    void activateVersion_DeprecatedStatus() throws Exception {
        when(functionUnitManager.activateVersion(eq(testCode), eq(testVersion), anyString()))
                .thenThrow(new AdminBusinessException("INVALID_STATUS", 
                        "无法激活状态为 DEPRECATED 的版本。只能激活 VALIDATED 或 DEPLOYED 状态的版本。"));

        mockMvc.perform(post("/function-units-import/{code}/activate/{version}", testCode, testVersion)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.message").value(containsString("DEPRECATED")))
                .andExpect(jsonPath("$.message").value(containsString("VALIDATED 或 DEPLOYED")));
    }

    // ==================== 版本历史端点测试 ====================

    @Test
    @DisplayName("获取版本历史（多个版本）应该返回 200 和完整列表")
    void getVersionHistory_MultipleVersions() throws Exception {
        List<VersionHistoryEntry> history = Arrays.asList(
                VersionHistoryEntry.builder()
                        .version("1.0.2")
                        .status(FunctionUnitStatus.DEPLOYED)
                        .enabled(true)
                        .isLatest(true)
                        .isCurrentlyEnabled(true)
                        .changeType("PATCH")
                        .createdAt(Instant.now())
                        .createdBy("user1")
                        .build(),
                VersionHistoryEntry.builder()
                        .version("1.0.1")
                        .status(FunctionUnitStatus.DEPLOYED)
                        .enabled(false)
                        .isLatest(false)
                        .isCurrentlyEnabled(false)
                        .changeType("PATCH")
                        .createdAt(Instant.now().minusSeconds(86400))
                        .createdBy("user1")
                        .build(),
                VersionHistoryEntry.builder()
                        .version("1.0.0")
                        .status(FunctionUnitStatus.DEPLOYED)
                        .enabled(false)
                        .isLatest(false)
                        .isCurrentlyEnabled(false)
                        .changeType("INITIAL")
                        .createdAt(Instant.now().minusSeconds(172800))
                        .createdBy("user1")
                        .build()
        );

        when(functionUnitManager.getVersionHistoryWithStatus(testCode))
                .thenReturn(history);

        mockMvc.perform(get("/function-units-import/{code}/versions", testCode)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(testCode))
                .andExpect(jsonPath("$.totalVersions").value(3))
                .andExpect(jsonPath("$.versions").isArray())
                .andExpect(jsonPath("$.versions", hasSize(3)))
                .andExpect(jsonPath("$.versions[0].version").value("1.0.2"))
                .andExpect(jsonPath("$.versions[0].enabled").value(true))
                .andExpect(jsonPath("$.versions[0].isLatest").value(true))
                .andExpect(jsonPath("$.versions[0].isCurrentlyEnabled").value(true))
                .andExpect(jsonPath("$.versions[1].version").value("1.0.1"))
                .andExpect(jsonPath("$.versions[1].enabled").value(false))
                .andExpect(jsonPath("$.versions[1].isLatest").value(false))
                .andExpect(jsonPath("$.versions[2].version").value("1.0.0"))
                .andExpect(jsonPath("$.versions[2].changeType").value("INITIAL"));
    }

    @Test
    @DisplayName("获取版本历史（单个版本）应该返回 200 和单个条目")
    void getVersionHistory_SingleVersion() throws Exception {
        List<VersionHistoryEntry> history = Arrays.asList(
                VersionHistoryEntry.builder()
                        .version("1.0.0")
                        .status(FunctionUnitStatus.DEPLOYED)
                        .enabled(true)
                        .isLatest(true)
                        .isCurrentlyEnabled(true)
                        .changeType("INITIAL")
                        .createdAt(Instant.now())
                        .createdBy("user1")
                        .build()
        );

        when(functionUnitManager.getVersionHistoryWithStatus(testCode))
                .thenReturn(history);

        mockMvc.perform(get("/function-units-import/{code}/versions", testCode)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(testCode))
                .andExpect(jsonPath("$.totalVersions").value(1))
                .andExpect(jsonPath("$.versions").isArray())
                .andExpect(jsonPath("$.versions", hasSize(1)))
                .andExpect(jsonPath("$.versions[0].version").value("1.0.0"))
                .andExpect(jsonPath("$.versions[0].isLatest").value(true))
                .andExpect(jsonPath("$.versions[0].changeType").value("INITIAL"));
    }

    @Test
    @DisplayName("获取不存在代码的版本历史应该返回 200 和空列表")
    void getVersionHistory_NonExistentCode() throws Exception {
        String nonExistentCode = "NON_EXISTENT_CODE";
        when(functionUnitManager.getVersionHistoryWithStatus(nonExistentCode))
                .thenReturn(Arrays.asList());

        mockMvc.perform(get("/function-units-import/{code}/versions", nonExistentCode)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(nonExistentCode))
                .andExpect(jsonPath("$.totalVersions").value(0))
                .andExpect(jsonPath("$.versions").isArray())
                .andExpect(jsonPath("$.versions", hasSize(0)));
    }

    @Test
    @DisplayName("版本历史应该验证排序顺序")
    void getVersionHistory_VerifyOrdering() throws Exception {
        List<VersionHistoryEntry> history = Arrays.asList(
                VersionHistoryEntry.builder()
                        .version("2.0.0")
                        .status(FunctionUnitStatus.DEPLOYED)
                        .enabled(true)
                        .isLatest(true)
                        .changeType("MAJOR")
                        .build(),
                VersionHistoryEntry.builder()
                        .version("1.5.0")
                        .status(FunctionUnitStatus.DEPLOYED)
                        .enabled(false)
                        .isLatest(false)
                        .changeType("MINOR")
                        .build(),
                VersionHistoryEntry.builder()
                        .version("1.0.0")
                        .status(FunctionUnitStatus.DEPLOYED)
                        .enabled(false)
                        .isLatest(false)
                        .changeType("INITIAL")
                        .build()
        );

        when(functionUnitManager.getVersionHistoryWithStatus(testCode))
                .thenReturn(history);

        mockMvc.perform(get("/function-units-import/{code}/versions", testCode)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versions[0].version").value("2.0.0"))
                .andExpect(jsonPath("$.versions[1].version").value("1.5.0"))
                .andExpect(jsonPath("$.versions[2].version").value("1.0.0"))
                .andExpect(jsonPath("$.versions[0].isLatest").value(true))
                .andExpect(jsonPath("$.versions[1].isLatest").value(false))
                .andExpect(jsonPath("$.versions[2].isLatest").value(false));
    }

    @Test
    @DisplayName("版本历史应该包含状态指示器")
    void getVersionHistory_IncludesStatusIndicators() throws Exception {
        List<VersionHistoryEntry> history = Arrays.asList(
                VersionHistoryEntry.builder()
                        .version("1.0.1")
                        .status(FunctionUnitStatus.DEPLOYED)
                        .enabled(true)
                        .isLatest(true)
                        .isCurrentlyEnabled(true)
                        .changeType("PATCH")
                        .build(),
                VersionHistoryEntry.builder()
                        .version("1.0.0")
                        .status(FunctionUnitStatus.DEPRECATED)
                        .enabled(false)
                        .isLatest(false)
                        .isCurrentlyEnabled(false)
                        .changeType("INITIAL")
                        .build()
        );

        when(functionUnitManager.getVersionHistoryWithStatus(testCode))
                .thenReturn(history);

        mockMvc.perform(get("/function-units-import/{code}/versions", testCode)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versions[0].status").value("DEPLOYED"))
                .andExpect(jsonPath("$.versions[0].enabled").value(true))
                .andExpect(jsonPath("$.versions[0].isCurrentlyEnabled").value(true))
                .andExpect(jsonPath("$.versions[1].status").value("DEPRECATED"))
                .andExpect(jsonPath("$.versions[1].enabled").value(false))
                .andExpect(jsonPath("$.versions[1].isCurrentlyEnabled").value(false));
    }
}
