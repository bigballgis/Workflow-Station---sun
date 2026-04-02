package com.developer.component;

import com.developer.component.impl.DeploymentComponentImpl;
import com.developer.dto.DeployRequest;
import com.developer.dto.DeployResponse;
import com.developer.dto.ValidationResult;
import com.developer.entity.FunctionUnit;
import com.developer.entity.ProcessDefinition;
import com.developer.exception.BusinessException;
import com.developer.repository.FunctionUnitRepository;
import com.platform.common.i18n.I18nService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 测试 DeploymentComponentImpl 中的多实例配置验证
 * 
 * **Validates: Requirements 8.1, 8.2, 8.3**
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DeploymentComponent - Multi-Instance Validation")
class DeploymentComponentMultiInstanceValidationTest {

    @Mock
    private FunctionUnitRepository functionUnitRepository;
    
    @Mock
    private ExportImportComponent exportImportComponent;
    
    @Mock
    private RestTemplate restTemplate;
    
    @Mock
    private FunctionUnitComponent functionUnitComponent;
    
    @Mock
    private ProcessDesignComponent processDesignComponent;
    
    @Mock
    private I18nService i18nService;
    
    private DeploymentComponentImpl deploymentComponent;
    
    private static final Long FUNCTION_UNIT_ID = 1L;
    
    @BeforeEach
    void setUp() {
        // Use SyncTaskExecutor for synchronous testing
        deploymentComponent = new DeploymentComponentImpl(
            functionUnitRepository,
            exportImportComponent,
            restTemplate,
            functionUnitComponent,
            processDesignComponent,
            i18nService,
            new SyncTaskExecutor()
        );
        
        // Setup default i18n messages
        when(i18nService.getMessage(anyString())).thenReturn("Test Message");
        when(i18nService.getMessage(anyString(), (Object[]) any())).thenReturn("Test Message");
    }
    
    @Test
    @DisplayName("应在部署前调用多实例配置验证")
    void shouldCallMultiInstanceValidationBeforeDeployment() {
        // Given: 功能单元存在且有流程定义
        FunctionUnit functionUnit = new FunctionUnit();
        functionUnit.setId(FUNCTION_UNIT_ID);
        functionUnit.setName("Test Function Unit");
        
        ProcessDefinition processDefinition = new ProcessDefinition();
        processDefinition.setBpmnXml("<bpmn:definitions>...</bpmn:definitions>");
        
        ValidationResult validResult = new ValidationResult();
        
        when(functionUnitRepository.findById(FUNCTION_UNIT_ID))
            .thenReturn(Optional.of(functionUnit));
        when(functionUnitComponent.publish(eq(FUNCTION_UNIT_ID), anyString()))
            .thenReturn(functionUnit);
        when(processDesignComponent.getByFunctionUnitId(FUNCTION_UNIT_ID))
            .thenReturn(processDefinition);
        when(processDesignComponent.validateMultiInstance(anyString(), eq(FUNCTION_UNIT_ID)))
            .thenReturn(validResult);
        
        DeployRequest request = new DeployRequest();
        request.setChangeLog("Test deployment");
        
        // When: 执行部署
        DeployResponse response = deploymentComponent.deployToAdminCenter(FUNCTION_UNIT_ID, request);
        
        // Then: 应该调用 validateMultiInstance
        verify(processDesignComponent, timeout(2000).times(1))
            .validateMultiInstance(processDefinition.getBpmnXml(), FUNCTION_UNIT_ID);
    }
    
    @Test
    @DisplayName("当多实例配置验证失败时应抛出 BusinessException")
    void shouldThrowBusinessExceptionWhenValidationFails() {
        // Given: 功能单元存在但多实例配置无效
        FunctionUnit functionUnit = new FunctionUnit();
        functionUnit.setId(FUNCTION_UNIT_ID);
        functionUnit.setName("Test Function Unit");
        
        ProcessDefinition processDefinition = new ProcessDefinition();
        processDefinition.setBpmnXml("<bpmn:definitions>...</bpmn:definitions>");
        
        ValidationResult invalidResult = new ValidationResult();
        invalidResult.addError("INVALID_COLLECTION_VARIABLE", 
            "Collection variable name is invalid", "subprocess-1");
        
        when(functionUnitRepository.findById(FUNCTION_UNIT_ID))
            .thenReturn(Optional.of(functionUnit));
        when(functionUnitComponent.publish(eq(FUNCTION_UNIT_ID), anyString()))
            .thenReturn(functionUnit);
        when(processDesignComponent.getByFunctionUnitId(FUNCTION_UNIT_ID))
            .thenReturn(processDefinition);
        when(processDesignComponent.validateMultiInstance(anyString(), eq(FUNCTION_UNIT_ID)))
            .thenReturn(invalidResult);
        
        DeployRequest request = new DeployRequest();
        request.setChangeLog("Test deployment");
        
        // When: 执行部署
        DeployResponse response = deploymentComponent.deployToAdminCenter(FUNCTION_UNIT_ID, request);
        
        // Then: 部署应该失败
        // Wait for async execution to complete
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        assertEquals(DeployResponse.DeployStatus.FAILED, response.getStatus());
        assertTrue(response.getMessage().contains("MULTI_INSTANCE_VALIDATION_FAILED") || 
                   response.getMessage().contains("多实例配置验证失败"));
    }
    
    @Test
    @DisplayName("当流程定义不存在时应跳过多实例验证")
    void shouldSkipValidationWhenNoProcessDefinition() {
        // Given: 功能单元存在但没有流程定义
        FunctionUnit functionUnit = new FunctionUnit();
        functionUnit.setId(FUNCTION_UNIT_ID);
        functionUnit.setName("Test Function Unit");
        
        when(functionUnitRepository.findById(FUNCTION_UNIT_ID))
            .thenReturn(Optional.of(functionUnit));
        when(functionUnitComponent.publish(eq(FUNCTION_UNIT_ID), anyString()))
            .thenReturn(functionUnit);
        when(processDesignComponent.getByFunctionUnitId(FUNCTION_UNIT_ID))
            .thenReturn(null);
        
        DeployRequest request = new DeployRequest();
        request.setChangeLog("Test deployment");
        
        // When: 执行部署
        DeployResponse response = deploymentComponent.deployToAdminCenter(FUNCTION_UNIT_ID, request);
        
        // Then: 不应该调用 validateMultiInstance
        verify(processDesignComponent, timeout(2000).times(0))
            .validateMultiInstance(anyString(), anyLong());
    }
    
    @Test
    @DisplayName("当 BPMN XML 为空时应跳过多实例验证")
    void shouldSkipValidationWhenBpmnXmlIsEmpty() {
        // Given: 功能单元存在但 BPMN XML 为空
        FunctionUnit functionUnit = new FunctionUnit();
        functionUnit.setId(FUNCTION_UNIT_ID);
        functionUnit.setName("Test Function Unit");
        
        ProcessDefinition processDefinition = new ProcessDefinition();
        processDefinition.setBpmnXml("");
        
        when(functionUnitRepository.findById(FUNCTION_UNIT_ID))
            .thenReturn(Optional.of(functionUnit));
        when(functionUnitComponent.publish(eq(FUNCTION_UNIT_ID), anyString()))
            .thenReturn(functionUnit);
        when(processDesignComponent.getByFunctionUnitId(FUNCTION_UNIT_ID))
            .thenReturn(processDefinition);
        
        DeployRequest request = new DeployRequest();
        request.setChangeLog("Test deployment");
        
        // When: 执行部署
        DeployResponse response = deploymentComponent.deployToAdminCenter(FUNCTION_UNIT_ID, request);
        
        // Then: 不应该调用 validateMultiInstance
        verify(processDesignComponent, timeout(2000).times(0))
            .validateMultiInstance(anyString(), anyLong());
    }
}
