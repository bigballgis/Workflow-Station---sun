package com.portal.component;

import com.portal.dto.ProcessInstanceInfo;

/**
 * New Request FORM_POPUP result: the (started or reused) instance returned to the client, plus the
 * ACTION row the popup wrote, which post-email send conditions on ACTION fields evaluate against.
 */
public record ProcessStartFormPopupOutcome(ProcessInstanceInfo instance, ActionTableRowRef actionRow) {}
