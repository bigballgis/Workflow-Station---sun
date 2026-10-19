package com.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Batch ACTIVE/INACTIVE status change for Relation Table rows; all rows succeed or none do. */
public record RelationTableBatchStatusRequest(
        @NotEmpty @Size(max = 500) List<String> rowIds,
        @NotBlank String status) {
}
