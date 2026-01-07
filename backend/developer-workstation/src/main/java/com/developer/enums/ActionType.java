package com.developer.enums;

/**
 * 动作类型枚举
 */
public enum ActionType {
    /** 默认动作 - 同意 */
    APPROVE,
    /** 默认动作 - 拒绝 */
    REJECT,
    /** 默认动作 - 转办 */
    TRANSFER,
    /** 默认动作 - 委托 */
    DELEGATE,
    /** 默认动作 - 回退 */
    ROLLBACK,
    /** 默认动作 - 撤回 */
    WITHDRAW,
    /** 自定义动作 - API调用 */
    API_CALL,
    /** 自定义动作 - 表单弹出 */
    FORM_POPUP,
    /** 自定义动作 - 脚本执行 */
    SCRIPT,
    /** 自定义动作 - 自定义脚本 */
    CUSTOM_SCRIPT,
    /** 流程提交 */
    PROCESS_SUBMIT,
    /** 流程驳回 */
    PROCESS_REJECT,
    /** 组合动作 */
    COMPOSITE
}
