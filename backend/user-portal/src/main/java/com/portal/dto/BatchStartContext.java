package com.portal.dto;

/** Published design consistency only; never an authorization token or a workflow variable. */
public record BatchStartContext(int schemaVersion, String catalogId, String definitionDigest) { }
