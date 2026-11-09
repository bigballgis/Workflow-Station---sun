package com.developer.security;

/**
 * 功能单元工作区操作类型（用于鉴权分支，平台访问模型 §6.2）。
 * VIEW 是查看与导出；MODIFY 是 Technical Lead 与 Developer 都能做的设计、发布、部署、回滚；其余都只有本团队
 * Technical Lead 与 SYS_ADMIN 能做。
 */
public enum WorkspaceAccessAction {
    /** 只读 */
    VIEW,
    /** 修改元数据、设计、发布、部署、回滚等 */
    MODIFY,
    /** 删除（归档）、彻底删除设计站功能单元 */
    DELETE,
    /** 恢复已归档功能单元 */
    RESTORE,
    /** 把功能单元移到另一个 Workspace（移出这一侧） */
    ASSIGN_DEV_GROUPS,
    /** 导入同名包，作为已有功能单元的新版本 */
    IMPORT_VERSION
}
