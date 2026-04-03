package com.developer.component;

import com.developer.dto.FormDefinitionRequest;
import com.developer.dto.FormTableBindingRequest;
import com.developer.dto.ValidationResult;
import com.developer.entity.FormDefinition;
import com.developer.entity.FormTableBinding;

import java.util.List;
import java.util.Map;
import java.util.Set;

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
    
    // ========== Process/Task Form 扩展方法 ==========
    
    /**
     * 校验 PROCESS form 唯一性
     * 查询 FunctionUnit 下 PROCESS form 数量，>0 时抛出 409 DeveloperBusinessException
     */
    void validateProcessFormUniqueness(Long functionUnitId);
    
    /**
     * 校验字段名是否存在于 Data_Table 列中
     * 对比字段名与 Data_Table 列名，不匹配时抛出 400 DeveloperBusinessException
     */
    void validateFieldNames(Long functionUnitId, List<String> fieldNames);
    
    /**
     * 复制 Task Form
     * 深拷贝 configJson，清空 stageBindings，生成新 ID
     */
    FormDefinition copyTaskForm(Long sourceFormId);
    
    /**
     * 获取 FunctionUnit 所有 Data_Table 列名
     * 查询所有 TableDefinition → FieldDefinition 列名
     */
    List<String> getDataTableColumns(Long functionUnitId);

    /**
     * 解析 RELATED 类型绑定对应的 Relation Table 名称
     * @param binding 表单表绑定
     * @return 关联表名称，非 RELATED 类型或不存在时返回 null
     */
    String resolveRelationTableName(FormTableBinding binding);
}
