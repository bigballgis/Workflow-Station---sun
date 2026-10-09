package com.developer.dto;

import java.time.Instant;
import java.util.List;

/** Read-only, sanitized comparison of two saved Function Unit version snapshots. */
public record VersionCompareResponse(
        VersionRef baseVersion,
        VersionRef targetVersion,
        Counts totals,
        List<ModuleDiff> modules,
        Counts displayTotals) {

    public record VersionRef(Long id, String versionNumber, Instant publishedAt) {}

    public record Counts(int added, int modified, int removed) {
        public Counts plus(Counts other) {
            return new Counts(added + other.added, modified + other.modified, removed + other.removed);
        }
    }

    public record ModuleDiff(String key, String status, Counts counts, boolean truncated,
                             List<Change> changes, SemanticDiff semantic) {}

    public record Change(String path, String type, String oldValue, String newValue) {}

    /** Additive presentation contract; the Phase 1 field-level result remains unchanged. */
    public record SemanticDiff(String status, String scope, Counts counts, boolean truncated,
                               List<SemanticChange> items) {}

    public record SemanticChange(String objectType, String objectKey, String label, String type,
                                 String scope, List<FieldChange> fields) {}

    public record FieldChange(String field, String oldValue, String newValue, TextContext textContext) {
        public FieldChange(String field, String oldValue, String newValue) { this(field, oldValue, newValue, null); }
    }

    /** UTF-16 offsets into saved text, not an assertion that the preview contains all changes. */
    public record TextContext(int oldStart, int newStart, int oldLength, int newLength, boolean omittedChanges) {}
}
