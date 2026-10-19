package com.portal.dto;

/** Display-only directory information, with no account or membership details. */
public record UserSummaryResponse(String id, String username, String name) { }
