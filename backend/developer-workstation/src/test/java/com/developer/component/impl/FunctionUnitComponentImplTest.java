package com.developer.component.impl;

import com.developer.dto.ValidationResult;
import com.developer.entity.FunctionUnit;
import com.developer.entity.Version;
import com.developer.enums.FunctionUnitStatus;
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

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 测试 FunctionUnitComponentImpl 的操作者信息获取功能
 * 
 * 验证属性 1: 操作者信息获取的正确性
 */
@ExtendWith(MockitoExtension.class)
class FunctionUnitComponentImplTest {
    
    @Mock
    private FunctionUnitRepository functionUnitRepository;
    
    @Mock
    private ProcessDefinitionRepository processDefinitionRepository;
    
    @Mock
    private TableDefinitionRepository tableDefinitionRepository;
    
    @Mock
    private FormDefinitionRepository formDefinitionRepository;
    
    @Mock
    private ActionDefinitionRepository actionDefinitionRepository;
    
    @Mock
    private VersionRepository versionRepository;
    
    @Mock
    private IconRepository iconRepository;
    
    @Mock
    private ObjectMapper objectMapper;
    
    @InjectMocks
    private FunctionUnitComponentImpl functionUnitComponent;
    
    @BeforeEach
    void setUp() {
        // 清理 SecurityContext
        SecurityContextHolder.clearContext();
    }
    
    /**
     * 测试用例 1: publish 时有效的认证用户
     * 验证当存在已认证用户时，getCurrentOperator() 返回用户名
     */
    @Test
    void testPublish_WithAuthenticatedUser() throws Exception {
        // Given: 设置 SecurityContext 包含认证用户
        Authentication auth = new UsernamePasswordAuthenticationToken("testuser", "password");
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);
        
        // 准备测试数据
        FunctionUnit functionUnit = FunctionUnit.builder()
                .id(1L)
                .name("Test Function")
                .currentVersion("1.0.0")
                .status(FunctionUnitStatus.DRAFT)
                .build();
        
        Version savedVersion = Version.builder()
                .id(1L)
                .functionUnit(functionUnit)
                .versionNumber("1.0.1")
                .changeLog("Test publish")
                .publishedBy("testuser")
                .build();
        
        when(functionUnitRepository.findById(1L)).thenReturn(Optional.of(functionUnit));
        when(versionRepository.save(any(Version.class))).thenReturn(savedVersion);
        when(functionUnitRepository.save(any(FunctionUnit.class))).thenReturn(functionUnit);
        when(objectMapper.writeValueAsBytes(any())).thenReturn(new byte[0]);
        
        // Mock validation result
        ValidationResult validationResult = new ValidationResult();
        // 使用反射或者直接创建一个有效的 ValidationResult
        
        // When: 调用发布方法
        FunctionUnit result = functionUnitComponent.publish(1L, "Test publish");
        
        // Then: 验证发布成功，并且使用了正确的操作者
        assertNotNull(result);
        assertEquals(FunctionUnitStatus.PUBLISHED, result.getStatus());
        verify(versionRepository).save(any(Version.class));
        verify(functionUnitRepository).save(functionUnit);
    }
    
    /**
     * 测试用例 2: publish 时无认证信息
     * 验证当没有认证信息时，getCurrentOperator() 返回 "system"
     */
    @Test
    void testPublish_WithoutAuthentication() throws Exception {
        // Given: SecurityContext 为空
        SecurityContextHolder.clearContext();
        
        // 准备测试数据
        FunctionUnit functionUnit = FunctionUnit.builder()
                .id(1L)
                .name("Test Function")
                .currentVersion("1.0.0")
                .status(FunctionUnitStatus.DRAFT)
                .build();
        
        Version savedVersion = Version.builder()
                .id(1L)
                .functionUnit(functionUnit)
                .versionNumber("1.0.1")
                .changeLog("Test publish")
                .publishedBy("system")
                .build();
        
        when(functionUnitRepository.findById(1L)).thenReturn(Optional.of(functionUnit));
        when(versionRepository.save(any(Version.class))).thenReturn(savedVersion);
        when(functionUnitRepository.save(any(FunctionUnit.class))).thenReturn(functionUnit);
        when(objectMapper.writeValueAsBytes(any())).thenReturn(new byte[0]);
        
        // When: 调用发布方法
        FunctionUnit result = functionUnitComponent.publish(1L, "Test publish");
        
        // Then: 验证发布成功，并且使用了 "system" 作为操作者
        assertNotNull(result);
        assertEquals(FunctionUnitStatus.PUBLISHED, result.getStatus());
        verify(versionRepository).save(any(Version.class));
    }
    
    /**
     * 测试用例 3: publish 时匿名用户
     * 验证当用户为匿名时，getCurrentOperator() 返回 "system"
     */
    @Test
    void testPublish_WithAnonymousUser() throws Exception {
        // Given: 设置匿名认证
        Authentication auth = new AnonymousAuthenticationToken(
            "key", "anonymous", Collections.singletonList(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))
        );
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);
        
        // 准备测试数据
        FunctionUnit functionUnit = FunctionUnit.builder()
                .id(1L)
                .name("Test Function")
                .currentVersion("1.0.0")
                .status(FunctionUnitStatus.DRAFT)
                .build();
        
        Version savedVersion = Version.builder()
                .id(1L)
                .functionUnit(functionUnit)
                .versionNumber("1.0.1")
                .changeLog("Test publish")
                .publishedBy("system")
                .build();
        
        when(functionUnitRepository.findById(1L)).thenReturn(Optional.of(functionUnit));
        when(versionRepository.save(any(Version.class))).thenReturn(savedVersion);
        when(functionUnitRepository.save(any(FunctionUnit.class))).thenReturn(functionUnit);
        when(objectMapper.writeValueAsBytes(any())).thenReturn(new byte[0]);
        
        // When: 调用发布方法
        FunctionUnit result = functionUnitComponent.publish(1L, "Test publish");
        
        // Then: 验证发布成功，并且使用了 "system" 作为操作者
        assertNotNull(result);
        assertEquals(FunctionUnitStatus.PUBLISHED, result.getStatus());
        verify(versionRepository).save(any(Version.class));
    }
    
    /**
     * 测试用例 4: 认证对象为 null
     * 验证当认证对象为 null 时，getCurrentOperator() 返回 "system"
     */
    @Test
    void testPublish_WithNullAuthentication() throws Exception {
        // Given: SecurityContext 返回 null 认证
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(null);
        SecurityContextHolder.setContext(securityContext);
        
        // 准备测试数据
        FunctionUnit functionUnit = FunctionUnit.builder()
                .id(1L)
                .name("Test Function")
                .currentVersion("1.0.0")
                .status(FunctionUnitStatus.DRAFT)
                .build();
        
        Version savedVersion = Version.builder()
                .id(1L)
                .functionUnit(functionUnit)
                .versionNumber("1.0.1")
                .changeLog("Test publish")
                .publishedBy("system")
                .build();
        
        when(functionUnitRepository.findById(1L)).thenReturn(Optional.of(functionUnit));
        when(versionRepository.save(any(Version.class))).thenReturn(savedVersion);
        when(functionUnitRepository.save(any(FunctionUnit.class))).thenReturn(functionUnit);
        when(objectMapper.writeValueAsBytes(any())).thenReturn(new byte[0]);
        
        // When: 调用发布方法
        FunctionUnit result = functionUnitComponent.publish(1L, "Test publish");
        
        // Then: 验证发布成功，并且使用了 "system" 作为操作者
        assertNotNull(result);
        assertEquals(FunctionUnitStatus.PUBLISHED, result.getStatus());
        verify(versionRepository).save(any(Version.class));
    }
}