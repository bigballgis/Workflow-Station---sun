package com.developer.enums;

/**
 * 表单类型枚举
 */
public enum FormType {
    /** 流程表单（完整视图） */
    PROCESS,
    /** 任务表单（局部视图） */
    TASK,
    /** 动作表单 */
    ACTION,
    /** 明细表单 —— View 列表点行进入的只读展示页，不绑流程节点 */
    DETAIL
}
