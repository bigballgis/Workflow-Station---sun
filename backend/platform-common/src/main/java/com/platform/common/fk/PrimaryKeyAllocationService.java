package com.platform.common.fk;

import com.platform.common.dto.PkGenerationConfig;

import java.util.List;

/**
 * Backend PK allocation (PRD §5.2, S3). Implementations use DB counters for concurrency safety.
 */
public interface PrimaryKeyAllocationService {

    /**
     * @param tableId   logical table id
     * @param fieldName PK field name
     * @param config    generation config from field metadata
     * @param count     number of values to allocate (default 1)
     * @param scopeKey  function unit id or prefix scope key when applicable
     */
    List<String> allocate(Long tableId, String fieldName, PkGenerationConfig config, int count, String scopeKey);

    /**
     * Same as {@link #allocate(Long, String, PkGenerationConfig, int, String)} but with an explicit
     * sequence-counter table. Callers should pass the table that matches their domain
     * ({@code dw_pk_sequences} for Developer Workstation tables, {@code rt_pk_sequences} for Admin
     * Center relation tables) so allocations never collide across domains that reuse the same numeric
     * table id. Defaults to the single-arg behavior for implementations that don't override it.
     */
    default List<String> allocate(Long tableId, String fieldName, PkGenerationConfig config, int count,
                                  String scopeKey, String sequenceTable) {
        return allocate(tableId, fieldName, config, count, scopeKey);
    }
}
