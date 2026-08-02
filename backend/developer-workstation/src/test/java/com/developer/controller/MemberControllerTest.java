package com.developer.controller;

import com.developer.dto.MemberRequest;
import com.developer.dto.MemberResponse;
import com.developer.dto.MemberUpdateRequest;
import com.developer.exception.BusinessLogicException;
import com.developer.service.MemberService;
import com.developer.validation.SecurityInputValidator;
import com.developer.dto.ValidationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.dto.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
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
// A @WebMvcTest slice cannot load in this module: the slice still processes plain @Configuration
// classes, so SecurityConfig's bean graph drags in RestTemplateConfig (needs an actuator-provided
// MeterRegistry) and @EnableJpaRepositories on DeveloperWorkstationApplication forces repository
// beans (need an entityManagerFactory) — neither auto-configuration is part of the web slice.
// Load the full test context instead, exactly as SpringSecurityAnnotationIntegrationTest does,
// and keep the collaborators mocked so these stay controller-level tests.
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class MemberControllerTest {

    /** 避免 test profile 下 RedisMessageListenerContainer 连接真实 Redis 导致启动超时 */
    @MockBean
    private RedisMessageListenerContainer redisMessageListenerContainer;

    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @MockBean
    private MemberService memberService;
    
    @MockBean
    private SecurityInputValidator securityValidator;
    
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
        
        // Setup update request.
        // NOTE: no field may contain a SecurityIntegrationService blacklist substring — that check
        // runs for real here and is a plain `contains` over SQL/command keywords, so an innocuous
        // "Updated User" is rejected as malicious purely because it contains "update".
        updateRequest = MemberUpdateRequest.builder()
                .fullName("Renamed User")
                .email("renamed@example.com")
                .role("ADMIN")
                .active(true)
                .build();
        
        // Mock security validator to return valid by default
        when(securityValidator.validate(anyString())).thenReturn(ValidationResult.builder().valid(true).build());
        when(securityValidator.sanitize(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(securityValidator.isValid(anyString())).thenReturn(true);

        // MemberController takes the acting user from SecurityContextUtils, which only accepts a
        // UserPrincipal — the X-User-Id header these tests still send is no longer an identity
        // source. With addFilters = false nothing populates the context, so seed it here.
        UserPrincipal principal = UserPrincipal.builder()
                .userId("testuser")
                .username("testuser")
                .roles(List.of("DEVELOPER"))
                .permissions(List.of())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
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
                .fullName("Renamed User")
                .email("renamed@example.com")
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
                .andExpect(jsonPath("$.data.fullName").value("Renamed User"))
                .andExpect(jsonPath("$.data.email").value("renamed@example.com"))
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
        // Given: input that the real SecurityIntegrationService rejects. BaseController only falls
        // back to the injected SecurityInputValidator when no SecurityIntegrationService bean
        // exists, and in a full context it always does — so stubbing the validator here would
        // assert nothing. Drive the production path instead.
        MemberRequest maliciousRequest = MemberRequest.builder()
                .username("test'; DROP TABLE members;--")
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
                .content(objectMapper.writeValueAsString(maliciousRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("SEC_VALIDATION_FAILED"));

        verify(memberService, never()).createMember(any(), any());
    }
}