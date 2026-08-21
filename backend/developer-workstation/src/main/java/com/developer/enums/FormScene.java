package com.developer.enums;

/**
 * 表单场景 —— 同一个步骤在不同 Portal 场景下可以各有一份独立设计。
 *
 * <p>My Requests 不对应任何 userTask，过去只能借用 To Do 的表单再靠子表配置裁剪，
 * 该轴让两者各自拥有完整的表单设计。
 */
public enum FormScene {
    /** To Do / Completed —— 办理人填写视角 */
    TASK,
    /** My Requests / 审计入口 —— 只读展示视角 */
    REQUEST
}
