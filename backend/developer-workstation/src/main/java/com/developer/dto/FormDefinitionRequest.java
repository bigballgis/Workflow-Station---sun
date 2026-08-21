package com.developer.dto;

import com.developer.enums.FormScene;
import com.developer.enums.FormType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 表单定义请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormDefinitionRequest {
    
    @NotBlank(message = "{validation.form_name_required}")
    @Size(max = 100, message = "{validation.form_name_max_length}")
    private String formName;
    
    @NotNull(message = "{validation.form_type_required}")
    private FormType formType;

    /** Null is read as {@link FormScene#TASK} so existing callers keep working. */
    private FormScene scene;

    @NotNull(message = "{validation.form_config_required}")
    private Map<String, Object> configJson;

    private String description;

    private Long boundTableId;

    /**
     * TASK-scene field permission overrides (EDITABLE/READONLY). Main-table fields use a bare
     * field-name key; sub-table fields use a composite {@code "${bindingId}:${fieldName}"} key.
     * Null means "not sent by this caller" — {@code update()} leaves the persisted value as-is
     * rather than clearing it, since ACTION/PROCESS-scene saves never send this key at all.
     */
    private Map<String, String> fieldPermissions;

    /**
     * Create the To Do and My Requests designs of one step in a single call.
     *
     * <p>Only meaningful for {@link FormType#PROCESS} and {@link FormType#TASK}: those render a
     * workflow step, which each scene presents differently. The two rows are bound to the same
     * BPMN node later (the node carries {@code formId} and {@code requestFormId} side by side),
     * so their names are free to differ — the REQUEST row takes a suffix because form names are
     * unique per function unit.
     *
     * <p>Null is read as false so existing callers create a single form as before.
     */
    private Boolean createBothScenes;
}
