package com.portal.enums;

/**
 * 委托类型枚举
 */
public enum DelegationType {
    /** 全部委托 - 所有任务 */
    ALL,
    /** 部分委托 - 按流程类型或条件 */
    PARTIAL,
    /** 临时委托 - 指定时间段 */
    TEMPORARY,
    /** 紧急委托 - 仅紧急任务 */
    URGENT
}
