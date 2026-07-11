package com.portal.component;

import com.portal.client.WorkflowEngineClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Merges the live engine's {@code __subTables__} into a caller-supplied slice map, filling only
 * empty/missing slices (real user edits — including deletions — are left untouched).
 *
 * <p>This is the shared <em>compute</em> half of the sub-table hydration that several read/write
 * paths need (portal store row, process-detail DTO, task-completion guard). Each caller keeps its
 * own <em>persistence</em> policy — this helper never writes to the DB or mutates the caller's
 * inputs; it returns the merged result and lets the caller decide what to store.
 *
 * <p>Best-effort: when the round-trip fails, it returns {@link Optional#empty()} and the caller
 * keeps its current values unchanged. Whether to skip the round-trip when the engine looks
 * unavailable is left to the caller (read paths short-circuit on {@code isAvailable()}; the
 * completion write-path guard always attempts it), so this helper does not gate on availability.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EngineSubTableHydrator {

    private final WorkflowEngineClient workflowEngineClient;

    /**
     * Outcome of a merge: the enriched {@code __subTables__} map plus the engine's {@code rowCount}
     * (may be {@code null} — not every caller uses it). Present only when at least one empty slice
     * was actually filled.
     */
    public record MergeResult(Map<String, Object> mergedSubTables, Object rowCount) {}

    /**
     * Fetch the engine's {@code __subTables__} and fill only the empty/missing slices of
     * {@code currentSubTables}. Insertion order of {@code currentSubTables} is preserved.
     *
     * @param processInstanceId engine process instance id
     * @param currentSubTables  the caller's current slice map (nullable); never mutated
     * @return the merged result when something changed; empty when nothing to fill or on failure
     */
    public Optional<MergeResult> mergeFromEngine(String processInstanceId, Map<String, Object> currentSubTables) {
        if (workflowEngineClient == null) {
            return Optional.empty();
        }
        try {
            Map<String, Object> engineRow = workflowEngineClient.getProcessInstance(processInstanceId).orElse(null);
            if (engineRow == null || !(engineRow.get("variables") instanceof Map<?, ?> engineVars)) {
                return Optional.empty();
            }
            if (!(engineVars.get("__subTables__") instanceof Map<?, ?> engineSt) || engineSt.isEmpty()) {
                return Optional.empty();
            }
            Map<String, Object> merged = currentSubTables != null
                    ? new LinkedHashMap<>(currentSubTables) : new LinkedHashMap<>();
            boolean changed = false;
            for (Map.Entry<?, ?> e : engineSt.entrySet()) {
                String key = String.valueOf(e.getKey());
                Object existing = merged.get(key);
                boolean existingEmpty = !(existing instanceof List<?> l) || l.isEmpty();
                if (existingEmpty && e.getValue() instanceof List<?> incoming && !incoming.isEmpty()) {
                    merged.put(key, e.getValue());
                    changed = true;
                }
            }
            if (!changed) {
                return Optional.empty();
            }
            return Optional.of(new MergeResult(merged, engineVars.get("rowCount")));
        } catch (RuntimeException ex) {
            log.debug("mergeFromEngine skipped for {}: {}", processInstanceId, ex.getMessage());
            return Optional.empty();
        }
    }
}
