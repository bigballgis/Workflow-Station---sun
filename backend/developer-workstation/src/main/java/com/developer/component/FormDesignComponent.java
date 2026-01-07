package com.developer.component;

import com.developer.dto.FormDefinitionRequest;
import com.developer.dto.FormTableBindingRequest;
import com.developer.dto.ValidationResult;
import com.developer.entity.FormDefinition;
import com.developer.entity.FormTableBinding;

import java.util.List;
import java.util.Map;

/**
 * 表单设计组件接口
 */
public interface FormDesignComponent {
    
    /**
     * 创建表单定义
     */
    FormDefinition create(Long functionUnitId, FormDefinitionRequest request);
    
    /**
     * 更新表单定义
     */
    FormDefinition update(Long id, FormDefinitionRequest request);
    
    /**
     * 删除表单定义
     */
    void delete(Long id);
    
    /**
     * 根据ID获取表单定义
     */
    FormDefinition getById(Long id);
    
    /**
     * 获取功能单元的所有表单定义
     */
    List<FormDefinition> getByFunctionUnitId(Long functionUnitId);
    
    /**
     * 生成Form-Create兼容的JSON配置
     */
    String generateFormConfig(Long id);
    
    /**
     * 解析Form-Create JSON配置
     */
    Map<String, Object> parseFormConfig(String configJson);
    
    /**
     * 验证表单定义
     */
    ValidationResult validate(Long id);
    
    // ========== 表绑定管理方法 ==========
    
    /**
     * 创建表单表绑定
     */
    FormTableBinding createBinding(Long formId, FormTableBindingRequest request);
    
    /**
     * 更新表单表绑定
     */
    FormTableBinding updateBinding(Long bindingId, FormTableBindingRequest request);
    
    /**
     * 删除表单表绑定
     */
    void deleteBinding(Long bindingId);
    
    /**
     * 获取表单的所有表绑定
     */
    List<FormTableBinding> getBindings(Long formId);
}
