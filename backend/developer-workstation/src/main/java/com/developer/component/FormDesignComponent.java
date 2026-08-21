package com.developer.component;

import com.developer.dto.FormConfigPasteRepairRequest;
import com.developer.dto.FormConfigPasteRepairResponse;
import com.developer.dto.FormDefinitionRequest;
import com.developer.dto.FormTableBindingRequest;
import com.developer.dto.FormTableBindingResponse;
import com.developer.dto.ValidationResult;
import com.developer.entity.FormDefinition;
import com.developer.entity.FormTableBinding;
import com.developer.enums.FormScene;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Form design component interface
 */
public interface FormDesignComponent {
    
    /**
     * Create form definition
     */
    FormDefinition create(Long functionUnitId, FormDefinitionRequest request);
    
    /**
     * Update form definition
     */
    FormDefinition update(Long id, FormDefinitionRequest request);
    
    /**
     * Delete form definition
     */
    void delete(Long id);
    
    /**
     * Get form definition by ID
     */
    FormDefinition getById(Long id);
    
    /**
     * Get all form definitions of a function unit
     */
    List<FormDefinition> getByFunctionUnitId(Long functionUnitId);
    
    /**
     * Generate Form-Create compatible JSON config
     */
    String generateFormConfig(Long id);
    
    /**
     * Parse Form-Create JSON config
     */
    Map<String, Object> parseFormConfig(String configJson);
    
    /**
     * Validate form definition
     */
    ValidationResult validate(Long id);
    
    // ========== Table binding management methods ==========
    
    /**
     * Create form table binding and return a directly serializable DTO (avoids accessing LAZY form/table associations outside transaction).
     */
    FormTableBindingResponse createBinding(Long formId, FormTableBindingRequest request);
    
    /**
     * Update form table binding
     */
    FormTableBinding updateBinding(Long bindingId, FormTableBindingRequest request);
    
    /**
     * Delete form table binding
     */
    void deleteBinding(Long bindingId);
    
    /**
     * Get all table bindings of a form
     */
    List<FormTableBinding> getBindings(Long formId);
    
    // ========== Process/Task Form extension methods ==========
    
    /**
     * Validate PROCESS form uniqueness for the To Do scene.
     * Query the count of PROCESS forms under the FunctionUnit; throws 409 DeveloperBusinessException if > 0.
     */
    void validateProcessFormUniqueness(Long functionUnitId);

    /**
     * Validate PROCESS form uniqueness within one scene — a function unit may hold
     * one start form for To Do and a separate one for the My Requests view.
     */
    void validateProcessFormUniqueness(Long functionUnitId, FormScene scene);
    
    /**
     * Validate that field names exist in Data_Table columns.
     * Compare field names against Data_Table column names; throws 400 DeveloperBusinessException on mismatch.
     */
    void validateFieldNames(Long functionUnitId, List<String> fieldNames);
    
    /**
     * Copy Task Form.
     * Deep copy configJson, clear stageBindings, generate new ID.
     */
    FormDefinition copyTaskForm(Long sourceFormId);
    
    /**
     * Copy Process Form to Task Form.
     * Deep copy configJson and fieldPermissions, clear stageBindings,
     * change formType to TASK, generate new ID.
     */
    FormDefinition copyProcessToTaskForm(Long sourceFormId);
    
    /**
     * Get all Data_Table column names of a FunctionUnit.
     * Query all TableDefinition -> FieldDefinition column names.
     */
    List<String> getDataTableColumns(Long functionUnitId);

    /**
     * Resolve the Relation Table name for a RELATED type binding.
     * @param binding form table binding
     * @return relation table name, or null if not RELATED type or not found
     */
    String resolveRelationTableName(FormTableBinding binding);

    /**
     * Repair a pasted {@code configJson} against the target form's table bindings
     * (cross-FU paste). Remaps stale SubTable / Lookup bindingIds and lookup tableIds.
     *
     * @param functionUnitId owning FU (ownership check)
     * @param formId         target form that already has bindings
     * @param request        pasted config (+ optional persist flag)
     */
    FormConfigPasteRepairResponse repairPastedConfig(
            Long functionUnitId, Long formId, FormConfigPasteRepairRequest request);
}
