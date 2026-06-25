package com.developer.component.impl;

import com.developer.entity.FunctionUnit;
import com.developer.entity.ProcessDefinition;
import com.developer.entity.TableDefinition;
import com.developer.repository.*;
import com.developer.util.BpmnProcessIdRewriter;
import com.developer.util.XmlEncodingUtil;
import com.developer.security.FunctionUnitWorkspaceAccessService;
import com.developer.validation.DmnXmlParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.dto.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    private FormTableBindingRepository formTableBindingRepository;

    @Mock
    private FormStageBindingRepository formStageBindingRepository;

    @Mock
    private TableRelationRepository tableRelationRepository;

    @Mock
    private DmnXmlParser dmnXmlParser;

    @Mock
    private FunctionUnitWorkspaceAccessService functionUnitWorkspaceAccessService;

    @Mock
    private FunctionUnitDevGroupAssignmentRepository functionUnitDevGroupAssignmentRepository;

    @Mock
    private jakarta.persistence.EntityManager entityManager;

    @Mock
    private com.developer.util.DeveloperWorkstationSequenceSynchronizer sequenceSynchronizer;
    
    private FunctionUnitExporter functionUnitExporter;

    @BeforeEach
    void setUp() {
        // 清理 SecurityContext
        SecurityContextHolder.clearContext();
        // getCurrentOperator 已随导出职责迁移到 FunctionUnitExporter，这里单独装配以保持原测试覆盖
        functionUnitExporter = ExportImportTestComponents.exporter(
                functionUnitRepository,
                tableDefinitionRepository,
                formDefinitionRepository,
                actionDefinitionRepository,
                decisionDefinitionRepository,
                formStageBindingRepository,
                tableRelationRepository,
                functionUnitWorkspaceAccessService,
                new ObjectMapper());
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
        String operator = (String) ReflectionTestUtils.invokeMethod(functionUnitExporter, "getCurrentOperator");
        
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
        String operator = (String) ReflectionTestUtils.invokeMethod(functionUnitExporter, "getCurrentOperator");
        
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
        String operator = (String) ReflectionTestUtils.invokeMethod(functionUnitExporter, "getCurrentOperator");
        
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
        String operator = (String) ReflectionTestUtils.invokeMethod(functionUnitExporter, "getCurrentOperator");
        
        // Then: 验证返回 "system"
        assertEquals("system", operator);
    }

    @Test
    void validateImportPackage_acceptsManifestWithoutMetadata() throws Exception {
        ObjectMapper om = new ObjectMapper();
        ExportImportComponentImpl impl = ExportImportTestComponents.build(
                functionUnitRepository,
                processDefinitionRepository,
                tableDefinitionRepository,
                formDefinitionRepository,
                actionDefinitionRepository,
                decisionDefinitionRepository,
                mock(FormTableBindingRepository.class),
                mock(FormStageBindingRepository.class),
                mock(TableRelationRepository.class),
                dmnXmlParser,
                mock(FunctionUnitWorkspaceAccessService.class),
                mock(FunctionUnitDevGroupAssignmentRepository.class),
                mock(jakarta.persistence.EntityManager.class),
                om,
                mock(com.developer.util.DeveloperWorkstationSequenceSynchronizer.class));

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
        ExportImportComponentImpl impl = ExportImportTestComponents.build(
                functionUnitRepository,
                processDefinitionRepository,
                tableDefinitionRepository,
                formDefinitionRepository,
                actionDefinitionRepository,
                decisionDefinitionRepository,
                mock(FormTableBindingRepository.class),
                mock(FormStageBindingRepository.class),
                mock(TableRelationRepository.class),
                dmnXmlParser,
                mock(FunctionUnitWorkspaceAccessService.class),
                mock(FunctionUnitDevGroupAssignmentRepository.class),
                mock(jakarta.persistence.EntityManager.class),
                om,
                mock(com.developer.util.DeveloperWorkstationSequenceSynchronizer.class));

        byte[] zip = zipSingleEntry("metadata.json", "{\"name\":\"FU_LegacyMeta\"}");
        MockMultipartFile file = new MockMultipartFile("file", "fu.zip", "application/zip", zip);

        Map<String, Object> res = impl.validateImportPackage(file);
        assertTrue((Boolean) res.get("valid"), () -> String.valueOf(res.get("errors")));
    }

    @Test
    void importFunctionUnit_newImport_generatesNewCodeWhenManifestCodeAlreadyExists() throws Exception {
        ObjectMapper om = new ObjectMapper();
        ExportImportComponentImpl impl = ExportImportTestComponents.build(
                functionUnitRepository,
                processDefinitionRepository,
                tableDefinitionRepository,
                formDefinitionRepository,
                actionDefinitionRepository,
                decisionDefinitionRepository,
                formTableBindingRepository,
                formStageBindingRepository,
                tableRelationRepository,
                dmnXmlParser,
                functionUnitWorkspaceAccessService,
                functionUnitDevGroupAssignmentRepository,
                entityManager,
                om,
                mock(com.developer.util.DeveloperWorkstationSequenceSynchronizer.class));

        // Name is free → new import; but the manifest code is already taken → generate a fresh code.
        String existingCode = "fu-20260422-23tfag";
        when(functionUnitRepository.findByName("kk")).thenReturn(java.util.Optional.empty());
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

        Map<String, Object> result = impl.importFunctionUnit(file, null);

        assertEquals("SUCCESS", result.get("status"));
        assertEquals(false, result.get("versioned"));
        org.mockito.ArgumentCaptor<FunctionUnit> captor = org.mockito.ArgumentCaptor.forClass(FunctionUnit.class);
        verify(functionUnitRepository).save(captor.capture());
        FunctionUnit saved = captor.getValue();
        assertEquals("kk", saved.getName());
        assertNotEquals(existingCode, saved.getCode());
    }

    @Test
    void importFunctionUnit_rewritesBpmnIdsAfterImport() throws Exception {
        ObjectMapper om = new ObjectMapper();
        ExportImportComponentImpl impl = ExportImportTestComponents.build(
                functionUnitRepository,
                processDefinitionRepository,
                tableDefinitionRepository,
                formDefinitionRepository,
                actionDefinitionRepository,
                decisionDefinitionRepository,
                formTableBindingRepository,
                formStageBindingRepository,
                tableRelationRepository,
                dmnXmlParser,
                functionUnitWorkspaceAccessService,
                functionUnitDevGroupAssignmentRepository,
                entityManager,
                om,
                mock(com.developer.util.DeveloperWorkstationSequenceSynchronizer.class));

        when(functionUnitRepository.findByName("ImportedFU")).thenReturn(java.util.Optional.empty());
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
        when(formDefinitionRepository.findById(300L)).thenAnswer(invocation -> {
            com.developer.entity.FormDefinition form = com.developer.entity.FormDefinition.builder()
                    .id(300L)
                    .formName("MainForm")
                    .formType(com.developer.enums.FormType.PROCESS)
                    .configJson(new java.util.HashMap<>())
                    .build();
            return java.util.Optional.of(form);
        });

        String bpmn = """
                <?xml version="1.0"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI">
                  <bpmn:process id="Process_1_xx" isExecutable="true">
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
                  </bpmn:process>
                  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
                    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_1_xx" />
                  </bpmndi:BPMNDiagram>
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
        Map<String, Object> result = impl.importFunctionUnit(file, null);
        assertEquals("SUCCESS", result.get("status"));

        org.mockito.ArgumentCaptor<FunctionUnit> functionUnitCaptor =
                org.mockito.ArgumentCaptor.forClass(FunctionUnit.class);
        verify(functionUnitRepository).save(functionUnitCaptor.capture());
        String importedCode = functionUnitCaptor.getValue().getCode();
        assertNotNull(importedCode);
        assertFalse(importedCode.isBlank());

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
        assertEquals(importedCode, BpmnProcessIdRewriter.extractProcessId(savedBpmn));
        assertFalse(savedBpmn.contains("Process_1_xx"));
    }

    @Test
    void importFunctionUnit_remapsBindingIdsInConfigJson() throws Exception {
        ObjectMapper om = new ObjectMapper();
        ExportImportComponentImpl impl = ExportImportTestComponents.build(
                functionUnitRepository,
                processDefinitionRepository,
                tableDefinitionRepository,
                formDefinitionRepository,
                actionDefinitionRepository,
                decisionDefinitionRepository,
                formTableBindingRepository,
                formStageBindingRepository,
                tableRelationRepository,
                dmnXmlParser,
                functionUnitWorkspaceAccessService,
                functionUnitDevGroupAssignmentRepository,
                entityManager,
                om,
                mock(com.developer.util.DeveloperWorkstationSequenceSynchronizer.class));

        when(functionUnitRepository.findByName("BindingFU")).thenReturn(java.util.Optional.empty());
        when(functionUnitRepository.existsByCode(any())).thenReturn(false);
        when(functionUnitRepository.save(any(FunctionUnit.class))).thenAnswer(invocation -> {
            FunctionUnit saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });
        when(tableDefinitionRepository.save(any(TableDefinition.class))).thenAnswer(invocation -> {
            TableDefinition saved = invocation.getArgument(0);
            saved.setId(20L);
            return saved;
        });
        when(tableDefinitionRepository.getReferenceById(20L)).thenAnswer(invocation ->
                TableDefinition.builder().id(20L).tableName("Main").build());
        when(formDefinitionRepository.save(any())).thenAnswer(invocation -> {
            var saved = invocation.getArgument(0, com.developer.entity.FormDefinition.class);
            if (saved.getId() == null) {
                saved.setId(30L);
            }
            return saved;
        });
        when(formDefinitionRepository.findById(30L)).thenAnswer(invocation -> {
            com.developer.entity.FormDefinition form = com.developer.entity.FormDefinition.builder()
                    .id(30L)
                    .formName("MainForm")
                    .formType(com.developer.enums.FormType.PROCESS)
                    .configJson(new java.util.HashMap<>(Map.of("subForms", new java.util.HashMap<>(Map.of("101", Map.of("title", "Sub"))))))
                    .build();
            return java.util.Optional.of(form);
        });
        when(formTableBindingRepository.save(any())).thenAnswer(invocation -> {
            com.developer.entity.FormTableBinding binding = invocation.getArgument(0);
            binding.setId(501L);
            return binding;
        });

        String formJson = """
                {
                  "formId": 11,
                  "formName": "MainForm",
                  "formType": "PROCESS",
                  "boundTableName": "Main",
                  "configJson": {
                    "rule": [
                      { "type": "subTable", "_bindingId": 101, "title": "Sub Table", "props": {} }
                    ],
                    "subForms": { "101": { "title": "Sub" } },
                    "relationViews": { "101": { "columns": [] } }
                  },
                  "tableBindings": [
                    {
                      "bindingId": 101,
                      "bindingType": "SUB",
                      "bindingMode": "EDITABLE",
                      "tableName": "Main",
                      "foreignKeyField": "main_id",
                      "sortOrder": 1,
                      "subMode": "FULL"
                    }
                  ],
                  "stageBindings": [
                    { "stageId": "Task_1", "stageName": "Review", "readOnly": false }
                  ]
                }
                """;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("manifest.json"));
            zos.write("{\"name\":\"BindingFU\",\"code\":\"binding-fu\",\"version\":\"1.0.0\"}".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("tables/table_0.json"));
            zos.write("{\"tableId\":13,\"tableName\":\"Main\",\"tableType\":\"MAIN\",\"fields\":[{\"fieldName\":\"id\",\"dataType\":\"BIGINT\",\"sortOrder\":0}]}".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("forms/form_0.json"));
            zos.write(formJson.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        MockMultipartFile file = new MockMultipartFile("file", "fu.zip", "application/zip", baos.toByteArray());
        Map<String, Object> result = impl.importFunctionUnit(file, null);
        assertEquals("SUCCESS", result.get("status"));

        org.mockito.ArgumentCaptor<com.developer.entity.FormDefinition> formCaptor =
                org.mockito.ArgumentCaptor.forClass(com.developer.entity.FormDefinition.class);
        verify(formDefinitionRepository, org.mockito.Mockito.atLeastOnce()).save(formCaptor.capture());
        com.developer.entity.FormDefinition finalForm = formCaptor.getAllValues().get(formCaptor.getAllValues().size() - 1);
        @SuppressWarnings("unchecked")
        Map<String, Object> subForms = (Map<String, Object>) finalForm.getConfigJson().get("subForms");
        assertTrue(subForms.containsKey("501"), () -> "Expected remapped binding key 501, got: " + subForms.keySet());
        assertFalse(subForms.containsKey("101"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rule = (List<Map<String, Object>>) finalForm.getConfigJson().get("rule");
        assertEquals(501L, ((Number) rule.get(0).get("_bindingId")).longValue());
        verify(formTableBindingRepository).save(any());
        verify(tableRelationRepository, never()).save(any());
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