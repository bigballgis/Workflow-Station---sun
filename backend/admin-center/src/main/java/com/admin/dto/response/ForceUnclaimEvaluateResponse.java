package com.admin.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForceUnclaimEvaluateResponse {

    /** taskId -> whether the caller may force-unclaim that hold. */
    @Builder.Default
    private Map<String, Boolean> flags = new LinkedHashMap<>();
}
