package com.workflow.enums;

/**
 * Single-task delegate target. USER stores {@code delegated_to}; BU_ROLE stores
 * paired business-unit and role codes. Null on legacy rows is treated as USER.
 */
public enum DelegatedTargetType {
    USER,
    BU_ROLE
}
