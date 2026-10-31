package com.developer.util;

import com.platform.common.assignment.AssignmentExclusionBpmnValidator;
import com.platform.common.assignment.AssignmentFailure;
import com.developer.dto.ValidationResult;

/** Same portable policy validation for save, package import, clone and rollback. */
public final class AssignmentDesignValidation {
    private AssignmentDesignValidation() { }

    public static void requireValid(String storedOrPlainXml) {
        if (storedOrPlainXml == null || storedOrPlainXml.isBlank()) return; // Explicit empty design has no embedded node policy.
        try {
            AssignmentExclusionBpmnValidator.policies(XmlEncodingUtil.smartDecode(storedOrPlainXml));
        } catch (IllegalArgumentException e) {
            throw new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_POLICY_INVALID);
        }
    }

    public static void collect(String bpmn, ValidationResult result) {
        try {
            requireValid(bpmn);
        } catch (AssignmentFailure e) {
            result.addError("ASSIGNMENT_POLICY_INVALID", "Invalid assignment exclusion policy or task reference", null);
        }
    }
}
