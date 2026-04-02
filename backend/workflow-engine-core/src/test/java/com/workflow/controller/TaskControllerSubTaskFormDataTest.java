package com.workflow.controller;

import com.workflow.component.MultiInstanceDataResolver;
import com.workflow.dto.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * TaskController 子任务表单数据加载接口单元测试
 * 
 * **Validates: Requirements 6.1**
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TaskController - 子任务表单数据加载接口测试")
class TaskControllerSubTaskFormDataTest {

    @Mock
    private MultiInstanceDataResolver multiInstanceDataResolver;

    private TaskController controller;

    private String taskId;
    private MultiInstanceDataResolver.SubTaskFormData mockFormData;

    @BeforeEach
    void setUp() {
        // 手动创建 controller，只注入需要的依赖
        controller = new TaskController(
            null, // taskManagerComponent
            null, // userPermissionService
            null, // historyService
            null, // configurationManager
            null, // securityIntegrationService
            null, // adminCenterClient
            null, // subTableAssignmentHandler
            multiInstanceDataResolver
        );

        taskId = "task-001";
        
        // 准备模拟数据
        Map<String, Object> mainFormData = new HashMap<>();
        mainFormData.put("meetingTitle", "2026 Q2 产品规划会议");
        mainFormData.put("meetingTime", "2026-04-15T14:00:00");
        mainFormData.put("meetingLocation", "3 楼会议室");
        mainFormData.put("organizer", "张经理");

        List<MultiInstanceDataResolver.FormField> mainFormFields = Arrays.asList(
            MultiInstanceDataResolver.FormField.builder()
                .name("meetingTitle").label("会议主题").type("text").build(),
            MultiInstanceDataResolver.FormField.builder()
                .name("meetingTime").label("会议时间").type("datetime").build(),
            MultiInstanceDataResolver.FormField.builder()
                .name("meetingLocation").label("会议地点").type("text").build(),
            MultiInstanceDataResolver.FormField.builder()
                .name("organizer").label("组织者").type("text").build()
        );

        Map<String, Object> subTableRowData = new HashMap<>();
        subTableRowData.put("id", 101);
        subTableRowData.put("name", "张三");
        subTableRowData.put("department", "技术部");
        subTableRowData.put("email", "zhang@example.com");
        subTableRowData.put("willAttend", null);
        subTableRowData.put("dietaryPreference", null);
        subTableRowData.put("remarks", null);
        subTableRowData.put("row_version", 1);

        List<MultiInstanceDataResolver.FormField> subFormFields = Arrays.asList(
            MultiInstanceDataResolver.FormField.builder()
                .name("name").label("姓名").type("text").readonly(true).build(),
            MultiInstanceDataResolver.FormField.builder()
                .name("department").label("部门").type("text").readonly(true).build(),
            MultiInstanceDataResolver.FormField.builder()
                .name("email").label("邮箱").type("email").readonly(true).build(),
            MultiInstanceDataResolver.FormField.builder()
                .name("willAttend").label("是否参会").type("select").required(true).build(),
            MultiInstanceDataResolver.FormField.builder()
                .name("dietaryPreference").label("饮食偏好").type("select").build(),
            MultiInstanceDataResolver.FormField.builder()
                .name("remarks").label("备注").type("textarea").build()
        );

        mockFormData = MultiInstanceDataResolver.SubTaskFormData.builder()
            .taskId(taskId)
            .mainFormData(mainFormData)
            .mainFormFields(mainFormFields)
            .subTableRowData(subTableRowData)
            .subFormFields(subFormFields)
            .rowVersion(1L)
            .build();
    }

    @Test
    @DisplayName("应该成功加载子任务表单数据 - 包含主任务信息和子表数据行")
    void shouldLoadSubTaskFormData() {
        // Given: 准备模拟数据
        when(multiInstanceDataResolver.loadSubTaskFormData(taskId)).thenReturn(mockFormData);

        // When: 调用接口
        ResponseEntity<ApiResponse<MultiInstanceDataResolver.SubTaskFormData>> response = 
            controller.getSubTaskFormData(taskId);

        // Then: 验证响应
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();

        MultiInstanceDataResolver.SubTaskFormData data = response.getBody().getData();
        assertThat(data).isNotNull();
        assertThat(data.getTaskId()).isEqualTo(taskId);
        
        // 验证主任务表单数据
        assertThat(data.getMainFormData()).isNotNull();
        assertThat(data.getMainFormData().get("meetingTitle")).isEqualTo("2026 Q2 产品规划会议");
        assertThat(data.getMainFormData().get("meetingTime")).isEqualTo("2026-04-15T14:00:00");
        assertThat(data.getMainFormData().get("meetingLocation")).isEqualTo("3 楼会议室");
        assertThat(data.getMainFormData().get("organizer")).isEqualTo("张经理");
        
        // 验证主任务表单字段定义
        assertThat(data.getMainFormFields()).hasSize(4);
        assertThat(data.getMainFormFields().get(0).getName()).isEqualTo("meetingTitle");
        assertThat(data.getMainFormFields().get(0).getLabel()).isEqualTo("会议主题");
        
        // 验证子表数据行
        assertThat(data.getSubTableRowData()).isNotNull();
        assertThat(data.getSubTableRowData().get("id")).isEqualTo(101);
        assertThat(data.getSubTableRowData().get("name")).isEqualTo("张三");
        assertThat(data.getSubTableRowData().get("department")).isEqualTo("技术部");
        assertThat(data.getSubTableRowData().get("email")).isEqualTo("zhang@example.com");
        
        // 验证子表单字段定义
        assertThat(data.getSubFormFields()).hasSize(6);
        assertThat(data.getSubFormFields().get(0).getName()).isEqualTo("name");
        assertThat(data.getSubFormFields().get(0).getReadonly()).isTrue();
        assertThat(data.getSubFormFields().get(3).getName()).isEqualTo("willAttend");
        assertThat(data.getSubFormFields().get(3).getRequired()).isTrue();
        
        // 验证乐观锁版本号
        assertThat(data.getRowVersion()).isEqualTo(1L);
        
        // 验证调用了 resolver
        verify(multiInstanceDataResolver, times(1)).loadSubTaskFormData(taskId);
    }

    @Test
    @DisplayName("应该返回错误 - 当任务不存在时")
    void shouldReturnError_WhenTaskNotFound() {
        // Given: 任务不存在
        when(multiInstanceDataResolver.loadSubTaskFormData(taskId))
            .thenThrow(new RuntimeException("任务不存在"));

        // When: 调用接口
        ResponseEntity<ApiResponse<MultiInstanceDataResolver.SubTaskFormData>> response = 
            controller.getSubTaskFormData(taskId);

        // Then: 验证返回错误
        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getCode()).isEqualTo("LOAD_SUBTASK_FORM_DATA_FAILED");
        assertThat(response.getBody().getMessage()).contains("任务不存在");
    }

    @Test
    @DisplayName("应该返回错误 - 当数据行不存在时")
    void shouldReturnError_WhenDataRowNotFound() {
        // Given: 数据行不存在
        when(multiInstanceDataResolver.loadSubTaskFormData(taskId))
            .thenThrow(new RuntimeException("关联的数据行已不存在"));

        // When: 调用接口
        ResponseEntity<ApiResponse<MultiInstanceDataResolver.SubTaskFormData>> response = 
            controller.getSubTaskFormData(taskId);

        // Then: 验证返回错误
        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getMessage()).contains("关联的数据行已不存在");
    }

    @Test
    @DisplayName("应该正确处理空的主表单数据")
    void shouldHandleEmptyMainFormData() {
        // Given: 主表单数据为空
        MultiInstanceDataResolver.SubTaskFormData emptyMainFormData = 
            MultiInstanceDataResolver.SubTaskFormData.builder()
                .taskId(taskId)
                .mainFormData(new HashMap<>())
                .mainFormFields(new ArrayList<>())
                .subTableRowData(mockFormData.getSubTableRowData())
                .subFormFields(mockFormData.getSubFormFields())
                .rowVersion(1L)
                .build();
        
        when(multiInstanceDataResolver.loadSubTaskFormData(taskId)).thenReturn(emptyMainFormData);

        // When: 调用接口
        ResponseEntity<ApiResponse<MultiInstanceDataResolver.SubTaskFormData>> response = 
            controller.getSubTaskFormData(taskId);

        // Then: 验证响应
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        MultiInstanceDataResolver.SubTaskFormData data = response.getBody().getData();
        assertThat(data.getMainFormData()).isEmpty();
        assertThat(data.getMainFormFields()).isEmpty();
        assertThat(data.getSubTableRowData()).isNotEmpty();
    }

    @Test
    @DisplayName("应该正确返回 rowVersion 用于乐观锁")
    void shouldReturnRowVersionForOptimisticLock() {
        // Given: 准备不同版本的数据
        Map<String, Object> subTableRowData = new HashMap<>(mockFormData.getSubTableRowData());
        subTableRowData.put("row_version", 5);
        
        MultiInstanceDataResolver.SubTaskFormData dataWithVersion5 = 
            MultiInstanceDataResolver.SubTaskFormData.builder()
                .taskId(taskId)
                .mainFormData(mockFormData.getMainFormData())
                .mainFormFields(mockFormData.getMainFormFields())
                .subTableRowData(subTableRowData)
                .subFormFields(mockFormData.getSubFormFields())
                .rowVersion(5L)
                .build();
        
        when(multiInstanceDataResolver.loadSubTaskFormData(taskId)).thenReturn(dataWithVersion5);

        // When: 调用接口
        ResponseEntity<ApiResponse<MultiInstanceDataResolver.SubTaskFormData>> response = 
            controller.getSubTaskFormData(taskId);

        // Then: 验证 rowVersion
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        MultiInstanceDataResolver.SubTaskFormData data = response.getBody().getData();
        assertThat(data.getRowVersion()).isEqualTo(5L);
    }
}
