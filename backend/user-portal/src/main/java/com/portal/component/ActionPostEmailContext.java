package com.portal.component;

import lombok.Builder;
import lombok.Value;

/**
 * Identifies which Action just ran so a configured post-action email can be published.
 */
@Value
@Builder
public class ActionPostEmailContext {

    public static final String OP_SAVE = "SAVE";
    public static final String OP_APPROVE = "APPROVE";
    public static final String OP_REJECT = "REJECT";
    public static final String OP_RETURN = "RETURN";
    public static final String OP_DRAFT = "DRAFT";
    public static final String OP_DELEGATE = "DELEGATE";
    public static final String OP_TRANSFER = "TRANSFER";
    public static final String OP_URGE = "URGE";
    public static final String OP_WITHDRAW = "WITHDRAW";
    public static final String OP_START = "START";
    public static final String OP_FORM_POPUP = "FORM_POPUP";

    /** DW / catalog Action id. Blank means this request is not from an Action button (e.g. autosave). */
    String actionId;
    /**
     * Function unit code (or id) of the process being started. Required when {@link #taskId} is blank
     * and the process instance is not yet stored. Ignored when {@link #taskId} is set.
     */
    String functionUnitCode;
    /** Portal operation family used to match {@code actionType}. */
    String operation;
    String taskId;
    String processInstanceId;
    String operatorId;
    String comment;
}
