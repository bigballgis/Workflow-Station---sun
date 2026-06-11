package com.developer.enums;

/**
 * Binding-level link semantics (PRD §10), separate from structural FK metadata.
 */
public enum BindingLinkMode {
    /** Standard structural FK auto-fill from parent PK. */
    structuralFk,
    /** MI participant row association (e.g. MCY row_id); not a structural FK. */
    miParticipantRow
}
