package com.portal.dto;

import java.util.List;

/**
 * 批量处理结果：逐条独立提交，单条失败不影响其他任务。
 */
public record BatchCompleteResponse(List<String> succeeded, List<Failure> failed) {

    public BatchCompleteResponse {
        succeeded = succeeded == null ? List.of() : List.copyOf(succeeded);
        failed = failed == null ? List.of() : List.copyOf(failed);
    }

    public record Failure(String taskId, String reason) {
    }
}
