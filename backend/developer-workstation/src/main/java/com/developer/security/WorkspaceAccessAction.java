package com.developer.security;

/**
 * 功能单元工作区操作类型（用于鉴权分支）
 */
public enum WorkspaceAccessAction {
    /** 只读 */
    VIEW,
    /** 修改元数据、设计、发布、部署等 */
    MODIFY,
    /** 删除设计站功能单元 */
    DELETE,
    /** 克隆为新的功能单元（需要团队创建能力） */
    CLONE,
    /** 恢复已归档功能单元（与删除使用相同团队能力） */
    RESTORE,
    /** 维护分配给虚拟开发组的列表 */
    ASSIGN_DEV_GROUPS
}
