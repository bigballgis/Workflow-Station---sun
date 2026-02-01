package com.developer.component.impl;

import com.developer.entity.FunctionUnit;
import com.developer.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 测试 ExportImportComponentImpl 的操作者信息获取功能
 * 
 * 验证属性 1: 操作者信息获取的正确性
 */
@ExtendWith(MockitoExtension.class)
class ExportImportComponentImplTest {
    
    @Mock
    private FunctionUnitRepository functionUnitRepository;
    
    @Mock
    private TableDefinitionRepository tableDefinitionRepository;
    
    @Mock
    private FormDefinitionRepository formDefinitionRepository;
    
    @Mock
    private ActionDefinitionRepository actionDefinitionRepository;
    
    @Mock
    private ProcessDefinitionRepository processDefinitionRepository;
    
    @Mock
    private ObjectMapper objectMapper;
    
    @InjectMocks
    private ExportImportComponentImpl exportImportComponent;
    
    @BeforeEach
    void setUp() {
        // 清理 SecurityContext
        SecurityContextHolder.clearContext();
    }
    
    /**
     * 测试用例 1: 有效的认证用户
     * 验证当存在已认证用户时，getCurrentOperator() 返回用户名
     */
    @Test
    void testGetCurrentOperator_WithAuthenticatedUser() throws Exception {
        // Given: 设置 SecurityContext 包含认证用户
        Authentication auth = new UsernamePasswordAuthenticationToken("testuser", "password", Collections.emptyList());
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);
        
        // When: 通过反射调用 getCurrentOperator 方法
        String operator = (String) ReflectionTestUtils.invokeMethod(exportImportComponent, "getCurrentOperator");
        
        // Then: 验证返回认证用户名
        assertEquals("testuser", operator);
    }
    
    /**
     * 测试用例 2: 无认证信息
     * 验证当没有认证信息时，getCurrentOperator() 返回 "system"
     */
    @Test
    void testGetCurrentOperator_WithoutAuthentication() throws Exception {
        // Given: SecurityContext 为空
        SecurityContextHolder.clearContext();
        
        // When: 通过反射调用 getCurrentOperator 方法
        String operator = (String) ReflectionTestUtils.invokeMethod(exportImportComponent, "getCurrentOperator");
        
        // Then: 验证返回 "system"
        assertEquals("system", operator);
    }
    
    /**
     * 测试用例 3: 匿名用户
     * 验证当用户为匿名时，getCurrentOperator() 返回 "system"
     */
    @Test
    void testGetCurrentOperator_WithAnonymousUser() throws Exception {
        // Given: 设置匿名认证
        Authentication auth = new AnonymousAuthenticationToken(
            "key", "anonymous", Collections.singletonList(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))
        );
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);
        
        // When: 通过反射调用 getCurrentOperator 方法
        String operator = (String) ReflectionTestUtils.invokeMethod(exportImportComponent, "getCurrentOperator");
        
        // Then: 验证返回 "system"
        assertEquals("system", operator);
    }
    
    /**
     * 测试用例 4: 认证对象为 null
     * 验证当认证对象为 null 时，getCurrentOperator() 返回 "system"
     */
    @Test
    void testGetCurrentOperator_WithNullAuthentication() throws Exception {
        // Given: SecurityContext 返回 null 认证
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(null);
        SecurityContextHolder.setContext(securityContext);
        
        // When: 通过反射调用 getCurrentOperator 方法
        String operator = (String) ReflectionTestUtils.invokeMethod(exportImportComponent, "getCurrentOperator");
        
        // Then: 验证返回 "system"
        assertEquals("system", operator);
    }
}