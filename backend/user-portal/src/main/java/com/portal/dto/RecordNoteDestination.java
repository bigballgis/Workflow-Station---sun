package com.portal.dto;

/** Authorized anchor for opening exactly one committed attachment submission. */
public record RecordNoteDestination(String processInstanceId,String requestTitle,String targetType,String targetId,
        String tableId,String tableKind,String submissionId) {}
