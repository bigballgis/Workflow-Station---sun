package com.portal.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * New Request FORM_POPUP submit: optional start payload + popup form data.
 * POST /api/portal/processes/{processKey}/actions/{actionId}/form-popup-submit
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessStartFormPopupSubmitRequest {

    /** When set, append popup data to this running instance (same initiator only). */
    private String processInstanceId;

    @NotNull
    private Map<String, Object> popupFormData;

    /** Main form + sub-tables when starting a new instance (ignored when processInstanceId is set). */
    private Map<String, Object> formData;

    private BatchStartContext batchStartContext;

    private List<String> emptiedSubTableKeys;

    private List<SubTableBindingScope> subTableBindingScopes;
}
