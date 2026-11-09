package com.platform.common.fk;

import com.platform.common.dto.PkGenerationConfig;

import java.util.List;

/**
 * Backend PK allocation (PRD §5.2, S3). Implementations use DB counters for concurrency safety.
 */
public interface PrimaryKeyAllocationService {

    /**
     * @param tableId       logical table id
     * @param fieldName     PK field name
     * @param config        generation config from field metadata
     * @param count         number of values to allocate (default 1)
     * @param scopeKey      function unit id or prefix scope key when applicable
     * @param sequenceTable the counter table of the caller's domain: {@code up_pk_sequences} for function
     *                      unit tables (User Portal, Developer Workstation preview), {@code rt_pk_sequences}
     *                      for Admin Center relation tables. Required, so allocations never collide across
     *                      domains that reuse the same numeric table id.
     */
    List<String> allocate(Long tableId, String fieldName, PkGenerationConfig config, int count,
                          String scopeKey, String sequenceTable);
}
