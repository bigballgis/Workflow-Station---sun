package com.developer.component.impl;

import com.developer.entity.FunctionUnit;
import com.developer.entity.ProcessDefinition;
import com.developer.entity.TableDefinition;
import com.developer.repository.*;
import com.developer.util.XmlEncodingUtil;
import com.developer.security.FunctionUnitWorkspaceAccessService;
import com.developer.validation.DmnXmlParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.dto.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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

    @Mock
    private DecisionDefinitionRepository decisionDefinitionRepository;

    @Mock
    private DmnXmlParser dmnXmlParser;

    @Mock
    private FunctionUnitWorkspaceAccessService functionUnitWorkspaceAccessService;

    @Mock
    private FunctionUnitDevGroupAssignmentRepository functionUnitDevGroupAssignmentRepository;

    @Mock
    private jakarta.persistence.EntityManager entityManager;
    
    @InjectMocks
    private ExportImportComponentImpl exportImportComponent;
    
    @BeforeEach
    void setUp() {
        // 清理 SecurityContext
        SecurityContextHolder.clearContext();
    }
    
    /**
     * 测试用例 1: 有效的认证用户（principal 须为 UserPrincipal，与 SecurityContextUtils 一致）
     */
    @Test
    void testGetCurrentOperator_WithAuthenticatedUser() throws Exception {
        // Given
        UserPrincipal principal = UserPrincipal.builder()
                .userId("u-1")
                .username("testuser")
                .build();
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, "n/a", Collections.emptyList());
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

    @Test
    void validateImportPackage_acceptsManifestWithoutMetadata() throws Exception {
        ObjectMapper om = new ObjectMapper();
        ExportImportComponentImpl impl = new ExportImportComponentImpl(
                functionUnitRepository,
                processDefinitionRepository,
                tableDefinitionRepository,
                formDefinitionRepository,
                actionDefinitionRepository,
                decisionDefinitionRepository,
                dmnXmlParser,
                mock(FunctionUnitWorkspaceAccessService.class),
                mock(FunctionUnitDevGroupAssignmentRepository.class),
                mock(jakarta.persistence.EntityManager.class),
                om);

        byte[] zip = zipSingleEntry("manifest.json", "{\"name\":\"FU_ManifestOnly\",\"code\":\"c1\"}");
        MockMultipartFile file = new MockMultipartFile("file", "fu.zip", "application/zip", zip);

        Map<String, Object> res = impl.validateImportPackage(file);
        assertTrue((Boolean) res.get("valid"), () -> String.valueOf(res.get("errors")));
        @SuppressWarnings("unchecked")
        List<String> errors = (List<String>) res.get("errors");
        assertTrue(errors.isEmpty());
    }

    @Test
    void validateImportPackage_stillAcceptsLegacyMetadataJson() throws Exception {
        ObjectMapper om = new ObjectMapper();
        ExportImportComponentImpl impl = new ExportImportComponentImpl(
                functionUnitRepository,
                processDefinitionRepository,
                tableDefinitionRepository,
                formDefinitionRepository,
                actionDefinitionRepository,
                decisionDefinitionRepository,
                dmnXmlParser,
                mock(FunctionUnitWorkspaceAccessService.class),
                mock(FunctionUnitDevGroupAssignmentRepository.class),
                mock(jakarta.persistence.EntityManager.class),
                om);

        byte[] zip = zipSingleEntry("metadata.json", "{\"name\":\"FU_LegacyMeta\"}");
        MockMultipartFile file = new MockMultipartFile("file", "fu.zip", "application/zip", zip);

        Map<String, Object> res = impl.validateImportPackage(file);
        assertTrue((Boolean) res.get("valid"), () -> String.valueOf(res.get("errors")));
    }

    @Test
    void importFunctionUnit_renameStrategy_generatesNewCodeWhenCodeAlreadyExists() throws Exception {
        ObjectMapper om = new ObjectMapper();
        ExportImportComponentImpl impl = new ExportImportComponentImpl(
                functionUnitRepository,
                processDefinitionRepository,
                tableDefinitionRepository,
                formDefinitionRepository,
                actionDefinitionRepository,
                decisionDefinitionRepository,
                dmnXmlParser,
                functionUnitWorkspaceAccessService,
                functionUnitDevGroupAssignmentRepository,
                entityManager,
                om);

        String existingCode = "fu-20260422-23tfag";
        when(functionUnitRepository.existsByName("kk")).thenReturn(true);
        when(functionUnitRepository.existsByCode(existingCode)).thenReturn(true);
        when(functionUnitRepository.existsByCode(org.mockito.ArgumentMatchers.argThat(
                candidate -> candidate != null && !candidate.equals(existingCode)))).thenReturn(false);
        when(functionUnitRepository.save(any(FunctionUnit.class))).thenAnswer(invocation -> {
            FunctionUnit saved = invocation.getArgument(0);
            saved.setId(99L);
            return saved;
        });

        byte[] zip = zipSingleEntry("manifest.json",
                "{\"name\":\"kk\",\"code\":\"" + existingCode + "\",\"version\":\"1.0.0\"}");
        MockMultipartFile file = new MockMultipartFile("file", "fu.zip", "application/zip", zip);

        Map<String, Object> result = impl.importFunctionUnit(file, "RENAME");

        assertEquals("SUCCESS", result.get("status"));
        org.mockito.ArgumentCaptor<FunctionUnit> captor = org.mockito.ArgumentCaptor.forClass(FunctionUnit.class);
        verify(functionUnitRepository).save(captor.capture());
        FunctionUnit saved = captor.getValue();
        assertTrue(saved.getName().startsWith("kk_imported_"));
        assertNotEquals(existingCode, saved.getCode());
    }

    @Test
    void importFunctionUnit_rewritesBpmnIdsAfterImport() throws Exception {
        ObjectMapper om = new ObjectMapper();
        ExportImportComponentImpl impl = new ExportImportComponentImpl(
                functionUnitRepository,
                processDefinitionRepository,
                tableDefinitionRepository,
                formDefinitionRepository,
                actionDefinitionRepository,
                decisionDefinitionRepository,
                dmnXmlParser,
                functionUnitWorkspaceAccessService,
                functionUnitDevGroupAssignmentRepository,
                entityManager,
                om);

        when(functionUnitRepository.existsByName("ImportedFU")).thenReturn(false);
        when(functionUnitRepository.existsByCode(any())).thenReturn(false);
        when(functionUnitRepository.save(any(FunctionUnit.class))).thenAnswer(invocation -> {
            FunctionUnit saved = invocation.getArgument(0);
            saved.setId(100L);
            return saved;
        });
        when(tableDefinitionRepository.save(any(TableDefinition.class))).thenAnswer(invocation -> {
            TableDefinition saved = invocation.getArgument(0);
            saved.setId(200L);
            return saved;
        });
        when(formDefinitionRepository.save(any())).thenAnswer(invocation -> {
            var saved = invocation.getArgument(0, com.developer.entity.FormDefinition.class);
            saved.setId(300L);
            return saved;
        });

        String bpmn = """
                <?xml version="1.0"?>
                <bpmn:definitions>
                  <bpmn:userTask id="MI_Task">
                    <bpmn:extensionElements>
                      <custom:properties>
                        <custom:property name="subTableName" value="participants" />
                        <custom:property name="subTableId" value="13" />
                        <custom:property name="formName" value="MainForm" />
                        <custom:property name="formId" value="11" />
                      </custom:properties>
                    </bpmn:extensionElements>
                  </bpmn:userTask>
                </bpmn:definitions>
                """;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("manifest.json"));
            zos.write("{\"name\":\"ImportedFU\",\"code\":\"imported-fu\",\"version\":\"1.0.0\"}".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("tables/table_0.json"));
            zos.write("{\"tableId\":13,\"tableName\":\"participants\",\"tableType\":\"SUB\",\"fields\":[{\"fieldName\":\"assignee\",\"dataType\":\"VARCHAR\",\"sortOrder\":0}]}".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("forms/form_0.json"));
            zos.write("{\"formId\":11,\"formName\":\"MainForm\",\"formType\":\"PROCESS\",\"configJson\":{}}".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("process/process.bpmn"));
            zos.write(bpmn.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        MockMultipartFile file = new MockMultipartFile("file", "fu.zip", "application/zip", baos.toByteArray());
        Map<String, Object> result = impl.importFunctionUnit(file, "RENAME");
        assertEquals("SUCCESS", result.get("status"));

        org.mockito.ArgumentCaptor<ProcessDefinition> processCaptor =
                org.mockito.ArgumentCaptor.forClass(ProcessDefinition.class);
        verify(processDefinitionRepository).save(processCaptor.capture());
        String savedBpmn = XmlEncodingUtil.smartDecode(processCaptor.getValue().getBpmnXml());
        assertTrue(savedBpmn.contains("subTableId") && savedBpmn.contains("200"),
                () -> "Expected rewritten subTableId=200 in: " + savedBpmn);
        assertTrue(savedBpmn.contains("formId") && savedBpmn.contains("300"),
                () -> "Expected rewritten formId=300 in: " + savedBpmn);
        assertFalse(savedBpmn.contains("value=\"13\""));
        assertFalse(savedBpmn.contains("value=\"11\""));
    }

    private static byte[] zipSingleEntry(String entryName, String utf8Content) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry(entryName));
            zos.write(utf8Content.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
}