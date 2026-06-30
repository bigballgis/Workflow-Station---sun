package com.developer.enums;

/**
 * What an inbound email monitor rule does when a matching email arrives.
 */
public enum EmailMonitorActionType {
    /** Start a new process instance, filling main-table (and optional sub-table) fields. */
    START_PROCESS,
    /** Append a row to a sub-table of an existing running process (Phase 2). */
    APPEND_SUB_TABLE
}
