package com.developer.enums;

/**
 * Whether an email connection is used for sending (SMTP), receiving (OAuth inbound), or both.
 */
public enum EmailConnectionDirection {
    OUTBOUND,
    INBOUND,
    BOTH
}
