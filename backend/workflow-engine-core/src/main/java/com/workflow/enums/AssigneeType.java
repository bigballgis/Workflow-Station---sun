package com.workflow.enums;

/**
 * 任务处理人分配类型枚举
 * 定义了9种标准的任务分配方式
 * 
 * 规则：非具体到人的分配都采用认领机制
 * 
 * 直接分配类型（3种）：
 * - FUNCTION_MANAGER: 职能经理
 * - ENTITY_MANAGER: 实体经理
 * - INITIATOR: 流程发起人
 * 
 * 认领类型（6种）：
 * - CURRENT_BU_ROLE: 当前人业务单元角色
 * - CURRENT_PARENT_BU_ROLE: 当前人上级业务单元角色
 * - INITIATOR_BU_ROLE: 发起人业务单元角色
 * - INITIATOR_PARENT_BU_ROLE: 发起人上级业务单元角色
 * - FIXED_BU_ROLE: 指定业务单元角色
 * - BU_UNBOUNDED_ROLE: BU无关型角色
 */
public enum AssigneeType {
    
    // ==================== 直接分配类型（不需要认领） ====================
    
    /**
     * 1. 当前人的职能经理
     * 直接分配给流程发起人的职能经理
     */
    FUNCTION_MANAGER("FUNCTION_MANAGER", "职能经理", false, false, false),
    
    /**
     * 2. 当前人的实体经理
     * 直接分配给流程发起人的实体经理
     */
    ENTITY_MANAGER("ENTITY_MANAGER", "实体经理", false, false, false),
    
    /**
     * 3. 流程发起人
     * 直接分配给流程发起人
     */
    INITIATOR("INITIATOR", "流程发起人", false, false, false),
    
    // ==================== 基于当前人业务单元的角色分配（需要认领） ====================
    
    /**
     * 4. 当前人业务单元的某个BU绑定型角色
     * 分配给当前处理人所在业务单元中拥有指定角色的用户，需要认领
     * 需要配合 roleId 指定角色
     */
    CURRENT_BU_ROLE("CURRENT_BU_ROLE", "当前人业务单元角色", true, true, false),
    
    /**
     * 5. 当前人上级业务单元的某个BU绑定型角色
     * 分配给当前处理人上级业务单元中拥有指定角色的用户，需要认领
     * 需要配合 roleId 指定角色
     */
    CURRENT_PARENT_BU_ROLE("CURRENT_PARENT_BU_ROLE", "当前人上级业务单元角色", true, true, false),
    
    // ==================== 基于发起人业务单元的角色分配（需要认领） ====================
    
    /**
     * 6. 发起人业务单元的某个BU绑定型角色
     * 分配给流程发起人所在业务单元中拥有指定角色的用户，需要认领
     * 需要配合 roleId 指定角色
     */
    INITIATOR_BU_ROLE("INITIATOR_BU_ROLE", "发起人业务单元角色", true, true, false),
    
    /**
     * 7. 发起人上级业务单元的某个BU绑定型角色
     * 分配给流程发起人上级业务单元中拥有指定角色的用户，需要认领
     * 需要配合 roleId 指定角色
     */
    INITIATOR_PARENT_BU_ROLE("INITIATOR_PARENT_BU_ROLE", "发起人上级业务单元角色", true, true, false),
    
    // ==================== 指定业务单元角色分配（需要认领） ====================
    
    /**
     * 8. 某个业务单元的某个BU绑定型角色
     * 分配给指定业务单元中拥有指定角色的用户，需要认领
     * 需要配合 businessUnitId 和 roleId 指定业务单元和角色
     * 角色必须是该业务单元的准入角色
     */
    FIXED_BU_ROLE("FIXED_BU_ROLE", "指定业务单元角色", true, true, true),
    
    // ==================== BU无关型角色分配（需要认领） ====================
    
    /**
     * 9. 某个BU无关型角色
     * 分配给拥有指定BU无关型角色的用户（通过虚拟组），需要认领
     * 需要配合 roleId 指定角色（必须是 BU_UNBOUNDED 类型）
     */
    BU_UNBOUNDED_ROLE("BU_UNBOUNDED_ROLE", "BU无关型角色", true, true, false);
    
    private final String code;
    private final String name;
    private final boolean requiresClaim;
    private final boolean requiresRoleId;
    private final boolean requiresBusinessUnitId;
    
    AssigneeType(String code, String name, boolean requiresClaim, 
                 boolean requiresRoleId, boolean requiresBusinessUnitId) {
        this.code = code;
        this.name = name;
        this.requiresClaim = requiresClaim;
        this.requiresRoleId = requiresRoleId;
        this.requiresBusinessUnitId = requiresBusinessUnitId;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getName() {
        return name;
    }
    
    /**
     * 是否需要认领（非具体到人的分配）
     */
    public boolean requiresClaim() {
        return requiresClaim;
    }
    
    /**
     * 是否需要角色ID参数
     */
    public boolean requiresRoleId() {
        return requiresRoleId;
    }
    
    /**
     * 是否需要业务单元ID参数
     */
    public boolean requiresBusinessUnitId() {
        return requiresBusinessUnitId;
    }
    
    /**
     * 是否是直接分配类型
     */
    public boolean isDirectAssignment() {
        return !requiresClaim;
    }
    
    /**
     * 是否是基于BU绑定型角色的分配
     */
    public boolean isBuBoundedRoleType() {
        return this == CURRENT_BU_ROLE || this == CURRENT_PARENT_BU_ROLE ||
               this == INITIATOR_BU_ROLE || this == INITIATOR_PARENT_BU_ROLE ||
               this == FIXED_BU_ROLE;
    }
    
    /**
     * 是否是基于当前处理人的分配（需要 currentUserId）
     */
    public boolean isCurrentUserBased() {
        return this == CURRENT_BU_ROLE || this == CURRENT_PARENT_BU_ROLE;
    }
    
    /**
     * 是否是基于发起人的分配
     */
    public boolean isInitiatorBased() {
        return this == INITIATOR || this == INITIATOR_BU_ROLE || 
               this == INITIATOR_PARENT_BU_ROLE ||
               this == FUNCTION_MANAGER || this == ENTITY_MANAGER;
    }
    
    /**
     * 根据代码获取分配类型
     */
    public static AssigneeType fromCode(String code) {
        if (code == null) return null;
        for (AssigneeType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        // 兼容旧的类型名称
        return fromLegacyCode(code);
    }
    
    /**
     * 兼容旧的类型代码（向后兼容）
     * 注意：旧类型已废弃，仅用于数据迁移
     */
    private static AssigneeType fromLegacyCode(String code) {
        return switch (code.toLowerCase()) {
            case "functionmanager", "function_manager" -> FUNCTION_MANAGER;
            case "entitymanager", "entity_manager", "manager" -> ENTITY_MANAGER;
            case "initiator" -> INITIATOR;
            // 旧类型映射到新类型（最佳匹配）
            case "deptothers", "dept_others" -> CURRENT_BU_ROLE;
            case "parentdept", "parent_dept" -> CURRENT_PARENT_BU_ROLE;
            case "fixeddept", "fixed_dept" -> FIXED_BU_ROLE;
            case "virtualgroup", "virtual_group" -> BU_UNBOUNDED_ROLE;
            default -> null;
        };
    }
}
