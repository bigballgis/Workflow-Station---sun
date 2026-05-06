/**
 * 公共格式化 & 枚举映射工具函数
 * 
 * 消除各组件中重复定义的 statusType / statusText / formatDate 等函数。
 * 与 i18n 相关的函数返回翻译 key 而非直接翻译文本，模板中使用 t(key)。
 */

// ==================== Tag 颜色映射（纯函数） ====================

/** Element Plus Tag type 联合类型 */
export type TagType = 'success' | 'warning' | 'danger' | 'info' | 'primary' | ''

// ---- 通用状态映射 ----
const STATUS_TYPE_MAP: Record<string, TagType> = {
  ACTIVE: 'success',
  DISABLED: 'info',
  LOCKED: 'danger',
  PENDING: 'warning',
  ENABLED: 'success',
}

/**
 * 获取状态对应的 Tag type
 * @param status 状态字符串
 * @param overrides 可选的自定义覆盖映射
 */
export const statusTagType = (status: string, overrides?: Record<string, TagType>): TagType =>
  overrides?.[status] ?? STATUS_TYPE_MAP[status] ?? 'info'

// ---- 功能单元状态 ----
export const functionUnitStatusType = (status: string): TagType =>
  statusTagType(status, {
    DEPLOYED: 'success',
    VALIDATED: 'primary',
    DRAFT: 'warning',
    DEPRECATED: 'info',
  })

// ---- 部署状态 ----
export const deployStatusType = (status: string): TagType =>
  statusTagType(status, {
    COMPLETED: 'success',
    EXECUTING: 'warning',
    PENDING: 'info',
    APPROVED: 'primary',
    FAILED: 'danger',
    ROLLED_BACK: 'danger',
    CANCELLED: 'info',
  })

// ---- 角色类型 ----
export const roleTypeTagType = (type: string): TagType =>
  ({
    BU_BOUNDED: 'warning',
    BU_UNBOUNDED: 'success',
    BUSINESS: 'success',
    ADMIN: 'danger',
    DEVELOPER: 'primary',
  } as Record<string, TagType>)[type] || 'info'

// ---- 关联表状态 ----
export const relationTableStatusType = (status: string): TagType =>
  ({
    DRAFT: 'warning',
    DEPLOYED: 'success',
    ROLLBACK: 'danger',
    INIT: 'info',
    UPDATED: 'warning',
  } as Record<string, TagType>)[status] || 'info'

// ---- BI 分配目标类型 ----
export const assignmentTargetTagType = (type: string): TagType =>
  ({ USER: 'primary', ROLE: 'success', BUSINESS_UNIT: 'warning' } as Record<string, TagType>)[type] || 'info'

/** BI 看板注册表状态（ACTIVE / MANUAL_INACTIVE / AUTO_INACTIVE）→ Tag type */
export const biDashboardStatusTagType = (
  status: string
): 'success' | 'warning' | 'danger' | 'info' | 'primary' =>
  ({
    ACTIVE: 'success',
    MANUAL_INACTIVE: 'warning',
    AUTO_INACTIVE: 'info',
  } as Record<string, 'success' | 'warning' | 'danger' | 'info' | 'primary'>)[status] ?? 'info'


// ==================== 状态 → i18n key 映射 ====================

/** 用户状态 i18n key */
export const userStatusKey = (status: string) =>
  ({ ACTIVE: 'user.active', DISABLED: 'user.disabled', LOCKED: 'user.locked', PENDING: 'user.pending' }[status] || status)

/** 功能单元状态 i18n key */
export const functionUnitStatusKey = (status: string) =>
  ({
    DEPLOYED: 'functionUnit.statusDeployed',
    VALIDATED: 'functionUnit.statusValidated',
    DRAFT: 'functionUnit.statusDraft',
    DEPRECATED: 'functionUnit.statusDeprecated',
  }[status] || status)

/** 角色类型 i18n key */
export const roleTypeKey = (type: string) =>
  ({
    BU_BOUNDED: 'role.buBounded',
    BU_UNBOUNDED: 'role.buUnbounded',
    BUSINESS: 'role.businessRole',
    ADMIN: 'role.adminRole',
    DEVELOPER: 'role.developerRole',
  }[type] || type)

/** 虚拟组类型 i18n key */
export const virtualGroupTypeKey = (type: string) =>
  ({ SYSTEM: 'virtualGroup.typeSystem', CUSTOM: 'virtualGroup.typeCustom' }[type] || type)

/** 字典类型 i18n key */
export const dictionaryTypeKey = (type: string) =>
  ({ SYSTEM: 'dictionary.typeSystem', BUSINESS: 'dictionary.typeBusiness', CUSTOM: 'dictionary.typeCustom' }[type] || type)

/** BI 分配目标类型 i18n key */
export const assignmentTargetTypeKey = (type: string) =>
  ({
    USER: 'bi.assignment.targetTypeUser',
    ROLE: 'bi.assignment.targetTypeRole',
    BUSINESS_UNIT: 'bi.assignment.targetTypeBusinessUnit',
  }[type] || type)

/** BI 布局模式 i18n key */
export const layoutModeKey = (mode: string) =>
  ({
    SINGLE: 'bi.assignment.layoutModeSingle',
    MULTI: 'bi.assignment.layoutModeMulti',
    WIDGET: 'bi.assignment.layoutModeWidget',
  }[mode] || mode)

/** BI 看板注册表状态 → i18n key（bi.dashboard.status*） */
export const biDashboardStatusKey = (status: string) =>
  ({
    ACTIVE: 'bi.dashboard.statusActive',
    MANUAL_INACTIVE: 'bi.dashboard.statusManualInactive',
    AUTO_INACTIVE: 'bi.dashboard.statusAutoInactive',
  }[status] || status)


// ==================== 日期时间格式化 ====================

/**
 * 格式化日期字符串为中文 locale 字符串
 * @param dateStr ISO 日期字符串
 */
export const formatDate = (dateStr: string): string => {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleString('zh-CN')
}


// ==================== 权限操作文本 i18n key ====================

export const permissionActionKey = (action: string) =>
  ({
    CREATE: 'permission.create',
    READ: 'permission.read',
    UPDATE: 'permission.update',
    DELETE: 'permission.delete',
    EXECUTE: 'permission.execute',
  }[action] || action)
