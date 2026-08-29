package com.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Batch evaluate whether a user may force-unclaim the given claim-pool tasks.
 */
@Data
public class ForceUnclaimEvaluateRequest {

    @NotBlank
    private String userId;

    @NotEmpty
    private List<Item> items = new ArrayList<>();

    @Data
    public static class Item {
        @NotBlank
        private String taskId;
        private String businessUnitId;
        private List<String> roleIds = new ArrayList<>();
    }
}
