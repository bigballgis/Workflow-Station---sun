package com.admin.exception;

/**
 * 请求的 Automation workspace（DW 开发组）对当前用户不可用 —— 403。
 *
 * <p>触发条件：组不存在 / 不是开发团队（非 CUSTOM、DEVELOPER）/ 已停用 / 当前用户不是其成员。
 * <b>显式失败，不回落 Public</b>：静默降级会让人以为自己在看 A 团队的 flow，实际看到的是
 * 另一批数据（{@code error-handling-governance}：不吞错、不伪装成功）。</p>
 */
public class ApWorkspaceAccessDeniedException extends RuntimeException {

    private final String groupId;

    public ApWorkspaceAccessDeniedException(String groupId, String message) {
        super(message);
        this.groupId = groupId;
    }

    public String getGroupId() {
        return groupId;
    }
}
