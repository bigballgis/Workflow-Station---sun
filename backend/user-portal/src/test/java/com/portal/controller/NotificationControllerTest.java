package com.portal.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.portal.dto.NotificationDto;
import com.portal.dto.PageResponse;
import com.portal.exception.PortalException;
import com.portal.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * NotificationController 单元测试
 * 验证: 需求 3.1-3.7
 */
@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationController notificationController;

    private static final String USER_ID = "user-123";
    private static final String USER_ID_HEADER = "X-User-Id";

    /**
     * Test-local exception handler to simulate how PortalException is handled.
     */
    @RestControllerAdvice
    static class TestExceptionHandler {
        @ExceptionHandler(PortalException.class)
        public ResponseEntity<Map<String, Object>> handlePortalException(PortalException ex) {
            HttpStatus status = switch (ex.getCode()) {
                case "403" -> HttpStatus.FORBIDDEN;
                case "404" -> HttpStatus.NOT_FOUND;
                case "400" -> HttpStatus.BAD_REQUEST;
                default -> HttpStatus.UNPROCESSABLE_ENTITY;
            };
            return ResponseEntity.status(status)
                    .body(Map.of("code", ex.getCode(), "message", ex.getMessage()));
        }

        @ExceptionHandler(MissingRequestHeaderException.class)
        public ResponseEntity<Map<String, Object>> handleMissingHeader(MissingRequestHeaderException ex) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", "400", "message", ex.getMessage()));
        }
    }

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);

        mockMvc = MockMvcBuilders.standaloneSetup(notificationController)
                .setControllerAdvice(new TestExceptionHandler())
                .setMessageConverters(converter)
                .build();
    }

    // --- GET /notifications (Requirement 3.1, 3.2) ---

    @Test
    @DisplayName("GET /notifications - 正常分页查询返回通知列表")
    void getNotifications_normalPaginated_returnsSuccess() throws Exception {
        NotificationDto dto = NotificationDto.builder()
                .id(1L).type("TASK").title("新任务").content("内容")
                .link("/tasks/1").isRead(false).createdAt(LocalDateTime.now())
                .build();
        PageResponse<NotificationDto> page = PageResponse.of(List.of(dto), 0, 20, 1);

        when(notificationService.getNotifications(eq(USER_ID), eq(0), eq(20), isNull(), isNull()))
                .thenReturn(page);

        mockMvc.perform(get("/notifications")
                        .header(USER_ID_HEADER, USER_ID)
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].id").value(1))
                .andExpect(jsonPath("$.data.content[0].type").value("TASK"))
                .andExpect(jsonPath("$.data.content[0].title").value("新任务"))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        verify(notificationService).getNotifications(USER_ID, 0, 20, null, null);
    }

    @Test
    @DisplayName("GET /notifications?type=TASK - 按类型筛选")
    void getNotifications_filteredByType_returnsFilteredResults() throws Exception {
        NotificationDto dto = NotificationDto.builder()
                .id(2L).type("TASK").title("任务通知").content("内容")
                .isRead(false).createdAt(LocalDateTime.now())
                .build();
        PageResponse<NotificationDto> page = PageResponse.of(List.of(dto), 0, 20, 1);

        when(notificationService.getNotifications(eq(USER_ID), eq(0), eq(20), eq("TASK"), isNull()))
                .thenReturn(page);

        mockMvc.perform(get("/notifications")
                        .header(USER_ID_HEADER, USER_ID)
                        .param("type", "TASK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].type").value("TASK"));

        verify(notificationService).getNotifications(USER_ID, 0, 20, "TASK", null);
    }

    @Test
    @DisplayName("GET /notifications?type=INVALID - 无效类型返回错误")
    void getNotifications_invalidType_returnsError() throws Exception {
        mockMvc.perform(get("/notifications")
                        .header(USER_ID_HEADER, USER_ID)
                        .param("type", "INVALID"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("400"));

        verify(notificationService, never()).getNotifications(any(), anyInt(), anyInt(), any(), any());
    }

    // --- GET /notifications/unread-count (Requirement 3.3) ---

    @Test
    @DisplayName("GET /notifications/unread-count - 返回未读数量")
    void getUnreadCount_returnsCount() throws Exception {
        when(notificationService.getUnreadCount(USER_ID)).thenReturn(5L);

        mockMvc.perform(get("/notifications/unread-count")
                        .header(USER_ID_HEADER, USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(5));

        verify(notificationService).getUnreadCount(USER_ID);
    }

    // --- PUT /notifications/{id}/read (Requirement 3.4) ---

    @Test
    @DisplayName("PUT /notifications/{id}/read - 正常标记已读")
    void markAsRead_success() throws Exception {
        doNothing().when(notificationService).markAsRead(USER_ID, 1L);

        mockMvc.perform(put("/notifications/1/read")
                        .header(USER_ID_HEADER, USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(notificationService).markAsRead(USER_ID, 1L);
    }

    @Test
    @DisplayName("PUT /notifications/{id}/read - 通知不存在返回404")
    void markAsRead_notFound_returns404() throws Exception {
        doThrow(new PortalException("404", "通知不存在"))
                .when(notificationService).markAsRead(USER_ID, 999L);

        mockMvc.perform(put("/notifications/999/read")
                        .header(USER_ID_HEADER, USER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("404"))
                .andExpect(jsonPath("$.message").value("通知不存在"));

        verify(notificationService).markAsRead(USER_ID, 999L);
    }

    @Test
    @DisplayName("PUT /notifications/{id}/read - 无权操作返回403")
    void markAsRead_forbidden_returns403() throws Exception {
        doThrow(new PortalException("403", "无权操作此通知"))
                .when(notificationService).markAsRead(USER_ID, 1L);

        mockMvc.perform(put("/notifications/1/read")
                        .header(USER_ID_HEADER, USER_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403"))
                .andExpect(jsonPath("$.message").value("无权操作此通知"));

        verify(notificationService).markAsRead(USER_ID, 1L);
    }

    // --- PUT /notifications/read-all (Requirement 3.5) ---

    @Test
    @DisplayName("PUT /notifications/read-all - 全部标记已读")
    void markAllAsRead_success() throws Exception {
        doNothing().when(notificationService).markAllAsRead(USER_ID);

        mockMvc.perform(put("/notifications/read-all")
                        .header(USER_ID_HEADER, USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(notificationService).markAllAsRead(USER_ID);
    }

    // --- DELETE /notifications/{id} (Requirement 3.6) ---

    @Test
    @DisplayName("DELETE /notifications/{id} - 正常删除通知")
    void deleteNotification_success() throws Exception {
        doNothing().when(notificationService).deleteNotification(USER_ID, 1L);

        mockMvc.perform(delete("/notifications/1")
                        .header(USER_ID_HEADER, USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(notificationService).deleteNotification(USER_ID, 1L);
    }

    @Test
    @DisplayName("DELETE /notifications/{id} - 通知不存在返回404")
    void deleteNotification_notFound_returns404() throws Exception {
        doThrow(new PortalException("404", "通知不存在"))
                .when(notificationService).deleteNotification(USER_ID, 999L);

        mockMvc.perform(delete("/notifications/999")
                        .header(USER_ID_HEADER, USER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("404"))
                .andExpect(jsonPath("$.message").value("通知不存在"));

        verify(notificationService).deleteNotification(USER_ID, 999L);
    }

    @Test
    @DisplayName("DELETE /notifications/{id} - 无权操作返回403")
    void deleteNotification_forbidden_returns403() throws Exception {
        doThrow(new PortalException("403", "无权操作此通知"))
                .when(notificationService).deleteNotification(USER_ID, 1L);

        mockMvc.perform(delete("/notifications/1")
                        .header(USER_ID_HEADER, USER_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403"))
                .andExpect(jsonPath("$.message").value("无权操作此通知"));

        verify(notificationService).deleteNotification(USER_ID, 1L);
    }

    // --- Missing X-User-Id header ---

    @Test
    @DisplayName("缺少 X-User-Id 请求头 - GET /notifications 返回400")
    void getNotifications_missingUserIdHeader_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/notifications"))
                .andExpect(status().isBadRequest());

        verify(notificationService, never()).getNotifications(any(), anyInt(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("缺少 X-User-Id 请求头 - PUT /notifications/{id}/read 返回400")
    void markAsRead_missingUserIdHeader_returnsBadRequest() throws Exception {
        mockMvc.perform(put("/notifications/1/read"))
                .andExpect(status().isBadRequest());

        verify(notificationService, never()).markAsRead(any(), any());
    }

    @Test
    @DisplayName("缺少 X-User-Id 请求头 - DELETE /notifications/{id} 返回400")
    void deleteNotification_missingUserIdHeader_returnsBadRequest() throws Exception {
        mockMvc.perform(delete("/notifications/1"))
                .andExpect(status().isBadRequest());

        verify(notificationService, never()).deleteNotification(any(), any());
    }
}
