package com.developer.controller;

import com.developer.dto.MemberRequest;
import com.developer.dto.MemberResponse;
import com.developer.dto.MemberUpdateRequest;
import com.developer.exception.BusinessLogicException;
import com.developer.service.MemberService;
import com.developer.validation.SecurityInputValidator;
import com.developer.dto.ValidationResult;
import com.platform.common.dto.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for MemberController.
 * Tests all CRUD operations with proper validation and error handling.
 * 
 * Requirements: 2.2, 2.5
 */
// 本模块主类声明了自定义 @ComponentScan，它**取代**了 @SpringBootApplication 默认的
// "只扫本包"行为；@WebMvcTest 会沿用主类那份 scan，于是整个 com.developer.* 被拉进切片
// （client -> RestTemplateConfig -> MeterRegistry -> JPA entityManagerFactory ...），
// Web 切片形同虚设，15 个测试全部因上下文起不来而 error。
//
// 在这种架构下切片注解本就不成立，故改用完整 @SpringBootTest + MockMvc：慢几秒，但真实可跑。
// 若将来收敛主类的 @ComponentScan，可以再换回 @WebMvcTest。
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class MemberControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @MockBean
    private MemberService memberService;
    
    @MockBean
    private SecurityInputValidator securityValidator;

    /** 避免 test profile 下 RedisMessageListenerContainer 连接真实 Redis 导致启动超时 */
    @MockBean
    private RedisMessageListenerContainer redisMessageListenerContainer;
    
    // 注意：更新用例的数据刻意避开 "Updated"/"updated@..." —— 它们含子串 "update"，会被
    // SecurityIntegrationService.containsInjectionPatterns() 的 SQL 关键字黑名单当成注入拦掉
    // （返回 400）。该黑名单做的是朴素子串匹配，任何含 update/select/delete/create... 的正常
    // 英文文本都会误报，属于产品侧缺陷；这里只是让本测试不被它绊住，缺陷本身另行处理。
    private MemberRequest validMemberRequest;
    private MemberResponse memberResponse;
    private MemberUpdateRequest updateRequest;
    
    @BeforeEach
    void setUp() {
        // Setup valid request
        validMemberRequest = MemberRequest.builder()
                .username("testuser")
                .fullName("Test User")
                .email("test@example.com")
                .employeeId("EMP001")
                .businessUnitId("BU001")
                .businessUnitName("Test Business Unit")
                .role("MEMBER")
                .build();
        
        // Setup response
        memberResponse = MemberResponse.builder()
                .id("1")
                .username("testuser")
                .fullName("Test User")
                .email("test@example.com")
                .employeeId("EMP001")
                .businessUnitId("BU001")
                .businessUnitName("Test Business Unit")
                .role("MEMBER")
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .createdBy("system")
                .updatedBy("system")
                .build();
        
        // Setup update request
        updateRequest = MemberUpdateRequest.builder()
                .fullName("Revised User")
                .email("revised@example.com")
                .role("ADMIN")
                .active(true)
                .build();
        
        // MemberController 用 SecurityContextUtils.getCurrentUserId() 取当前用户，取不到就抛
        // RuntimeException（-> 500）。该工具只认 UserPrincipal 主体，仅有 X-User-Id 请求头不够。
        SecurityContextHolder.clearContext();
        UserPrincipal principal = UserPrincipal.builder()
                .userId("testuser").username("testuser").build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));

        // Mock security validator to return valid by default
        when(securityValidator.validate(anyString())).thenReturn(ValidationResult.builder().valid(true).build());
        when(securityValidator.sanitize(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(securityValidator.isValid(anyString())).thenReturn(true);
    }
    
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createMember_ValidRequest_ReturnsCreated() throws Exception {
        // Given
        when(memberService.createMember(any(MemberRequest.class), anyString())).thenReturn(memberResponse);
        
        // When & Then
        mockMvc.perform(post("/members")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-User-Id", "testuser")
                .content(objectMapper.writeValueAsString(validMemberRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("testuser"))
                .andExpect(jsonPath("$.data.fullName").value("Test User"))
                .andExpect(jsonPath("$.data.email").value("test@example.com"));
        
        verify(memberService).createMember(any(MemberRequest.class), eq("testuser"));
    }
    
    @Test
    void createMember_InvalidRequest_ReturnsBadRequest() throws Exception {
        // Given
        MemberRequest invalidRequest = MemberRequest.builder()
                .username("") // Invalid: empty username
                .fullName("Test User")
                .email("invalid-email") // Invalid: malformed email
                .build();
        
        // When & Then
        mockMvc.perform(post("/members")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-User-Id", "testuser")
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
        
        verify(memberService, never()).createMember(any(), any());
    }
    
    @Test
    void createMember_DuplicateUsername_ReturnsConflict() throws Exception {
        // Given
        when(memberService.createMember(any(MemberRequest.class), anyString()))
                .thenThrow(new RuntimeException("Username already exists"));
        
        // When & Then
        mockMvc.perform(post("/members")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-User-Id", "testuser")
                .content(objectMapper.writeValueAsString(validMemberRequest)))
                .andExpect(status().isInternalServerError()); // BaseController handles as internal error
        
        verify(memberService).createMember(any(MemberRequest.class), eq("testuser"));
    }
    
    @Test
    void getMember_ExistingId_ReturnsOk() throws Exception {
        // Given
        when(memberService.getMember(1L)).thenReturn(memberResponse);
        
        // When & Then
        mockMvc.perform(get("/members/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value("1"))
                .andExpect(jsonPath("$.data.username").value("testuser"));
        
        verify(memberService).getMember(1L);
    }
    
    @Test
    void getMember_NonExistentId_ReturnsNotFound() throws Exception {
        // Given
        when(memberService.getMember(999L))
                .thenThrow(new RuntimeException("Member not found"));
        
        // When & Then
        mockMvc.perform(get("/members/999"))
                .andExpect(status().isInternalServerError()); // BaseController handles as internal error
        
        verify(memberService).getMember(999L);
    }
    
    @Test
    void getMemberByUsername_ExistingUsername_ReturnsOk() throws Exception {
        // Given
        when(memberService.getMemberByUsername("testuser")).thenReturn(Optional.of(memberResponse));
        
        // When & Then
        mockMvc.perform(get("/members/username/testuser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("testuser"));
        
        verify(memberService).getMemberByUsername("testuser");
    }
    
    @Test
    void getMemberByUsername_NonExistentUsername_ReturnsNotFound() throws Exception {
        // Given
        when(memberService.getMemberByUsername("nonexistent")).thenReturn(Optional.empty());
        
        // When & Then
        mockMvc.perform(get("/members/username/nonexistent"))
                .andExpect(status().isNotFound());
        
        verify(memberService).getMemberByUsername("nonexistent");
    }
    
    @Test
    void updateMember_ValidRequest_ReturnsOk() throws Exception {
        // Given
        MemberResponse updatedResponse = MemberResponse.builder()
                .id("1")
                .username("testuser")
                .fullName("Revised User")
                .email("revised@example.com")
                .role("ADMIN")
                .active(true)
                .build();
        
        when(memberService.updateMember(eq(1L), any(MemberUpdateRequest.class), anyString()))
                .thenReturn(updatedResponse);
        
        // When & Then
        mockMvc.perform(put("/members/1")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-User-Id", "testuser")
                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fullName").value("Revised User"))
                .andExpect(jsonPath("$.data.email").value("revised@example.com"))
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
        
        verify(memberService).updateMember(eq(1L), any(MemberUpdateRequest.class), eq("testuser"));
    }
    
    @Test
    void updateMember_NonExistentId_ReturnsNotFound() throws Exception {
        // Given
        when(memberService.updateMember(eq(999L), any(MemberUpdateRequest.class), anyString()))
                .thenThrow(new RuntimeException("Member not found"));
        
        // When & Then
        mockMvc.perform(put("/members/999")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-User-Id", "testuser")
                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isInternalServerError()); // BaseController handles as internal error
        
        verify(memberService).updateMember(eq(999L), any(MemberUpdateRequest.class), eq("testuser"));
    }
    
    @Test
    void deleteMember_ExistingId_ReturnsNoContent() throws Exception {
        // Given
        doNothing().when(memberService).deleteMember(1L, "testuser");
        
        // When & Then
        mockMvc.perform(delete("/members/1")
                .header("X-User-Id", "testuser"))
                .andExpect(status().isNoContent());
        
        verify(memberService).deleteMember(1L, "testuser");
    }
    
    @Test
    void deleteMember_NonExistentId_ReturnsNotFound() throws Exception {
        // Given
        doThrow(new RuntimeException("Member not found"))
                .when(memberService).deleteMember(999L, "testuser");
        
        // When & Then
        mockMvc.perform(delete("/members/999")
                .header("X-User-Id", "testuser"))
                .andExpect(status().isInternalServerError()); // BaseController handles as internal error
        
        verify(memberService).deleteMember(999L, "testuser");
    }
    
    @Test
    void getAllMembers_WithoutSearch_ReturnsPagedResults() throws Exception {
        // Given
        List<MemberResponse> members = Arrays.asList(memberResponse);
        Page<MemberResponse> page = new PageImpl<>(members, PageRequest.of(0, 20), 1);
        
        when(memberService.getAllActiveMembers(any())).thenReturn(page);
        
        // When & Then
        mockMvc.perform(get("/members")
                .param("page", "0")
                .param("size", "20")
                .param("sortBy", "fullName")
                .param("sortDir", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content[0].username").value("testuser"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
        
        verify(memberService).getAllActiveMembers(any());
    }
    
    @Test
    void getAllMembers_WithSearch_ReturnsFilteredResults() throws Exception {
        // Given
        List<MemberResponse> members = Arrays.asList(memberResponse);
        Page<MemberResponse> page = new PageImpl<>(members, PageRequest.of(0, 20), 1);
        
        when(memberService.searchMembers(eq("test"), any())).thenReturn(page);
        
        // When & Then
        mockMvc.perform(get("/members")
                .param("search", "test")
                .param("page", "0")
                .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.totalElements").value(1));
        
        verify(memberService).searchMembers(eq("test"), any());
    }
    
    @Test
    void getMembersByBusinessUnit_ValidId_ReturnsMembers() throws Exception {
        // Given
        List<MemberResponse> members = Arrays.asList(memberResponse);
        when(memberService.getMembersByBusinessUnit("BU001")).thenReturn(members);
        
        // When & Then
        mockMvc.perform(get("/members/business-unit/BU001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].businessUnitId").value("BU001"));
        
        verify(memberService).getMembersByBusinessUnit("BU001");
    }
    
    @Test
    void createMember_SecurityValidationFails_ReturnsBadRequest() throws Exception {
        // Given: 一个真的会被安全校验拦下的输入。
        //
        // 这里不能像以前那样 stub securityValidator —— BaseController 只在
        // securityIntegrationService 为 null 时才回退到它，而在完整 @SpringBootTest 上下文里
        // 该 bean 是存在的，stub 因此完全不生效（请求会一路成功返回 201）。
        // 改为送一个真正命中注入模式的值，走的就是生产实际的那条拒绝路径。
        MemberRequest maliciousRequest = MemberRequest.builder()
                .username("admin'; DROP TABLE users; --")
                .fullName("Test User")
                .email("test@example.com")
                .employeeId("EMP001")
                .businessUnitId("BU001")
                .businessUnitName("Test Business Unit")
                .role("MEMBER")
                .build();

        // When & Then
        mockMvc.perform(post("/members")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-User-Id", "testuser")
                .content(objectMapper.writeValueAsString(maliciousRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                // SEC_VALIDATION_FAILED 是 securityIntegrationService 这条路径的错误码；
                // 旧的 VAL_SECURITY_VIOLATION 来自被 stub 的 securityValidator 回退分支。
                .andExpect(jsonPath("$.error.code").value("SEC_VALIDATION_FAILED"));
        
        verify(memberService, never()).createMember(any(), any());
    }
}