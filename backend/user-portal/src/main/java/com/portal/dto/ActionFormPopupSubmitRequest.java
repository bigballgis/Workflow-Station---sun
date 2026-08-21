package com.portal.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * FORM_POPUP action submit request DTO.
 * POST /api/portal/tasks/{taskId}/actions/{actionId}/form-popup-submit
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActionFormPopupSubmitRequest {
    @NotNull
    private Map<String, Object> formData;
}
