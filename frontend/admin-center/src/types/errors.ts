/**
 * 标准化错误类型与错误码体系
 *
 * API 层用 ApiError (HTTP 状态码→标准化 code)
 * 业务层用 AppError  (业务错误码，来自 AppErrorCode 枚举)
 *
 * UI 层统一通过 errorTranslator 将错误码转为 i18n key 展示。
 */

// ============================================================
// HTTP API 层错误
// ============================================================

export class ApiError extends Error {
  public readonly code: string
  public readonly status: number
  public readonly backendMessage?: string

  constructor(code: string, status: number, backendMessage?: string) {
    super(backendMessage || `HTTP ${status}: ${code}`)
    this.name = 'ApiError'
    this.code = code
    this.status = status
    this.backendMessage = backendMessage
  }

  get userMessage(): string {
    return this.backendMessage || this.code
  }

  get isClientError(): boolean {
    return this.status >= 400 && this.status < 500
  }

  get isServerError(): boolean {
    return this.status >= 500 && this.status < 600
  }
}

// HTTP 状态码 → 标准错误码
const HTTP_CODE_MAP: Record<number, string> = {
  400: 'INVALID_PARAMS',
  401: 'UNAUTHORIZED',
  403: 'NO_PERMISSION',
  404: 'NOT_FOUND',
  409: 'CONFLICT',
  422: 'BUSINESS_ERROR',
  429: 'TOO_MANY_REQUESTS',
  500: 'SERVER_ERROR',
  502: 'SERVICE_UNAVAILABLE',
  503: 'SERVICE_MAINTENANCE',
}

export function httpCodeToErrorCode(status: number): string {
  return HTTP_CODE_MAP[status] || `HTTP_${status}`
}

// ============================================================
// 业务层错误码枚举
// ============================================================

export const AppErrorCode = {
  // ---- BI Dashboard ----
  BI_DASHBOARD_QUERY_FAILED:          'BI_DASHBOARD_QUERY_FAILED',
  BI_DASHBOARD_SYNC_FAILED:           'BI_DASHBOARD_SYNC_FAILED',
  BI_DASHBOARD_UPDATE_FAILED:         'BI_DASHBOARD_UPDATE_FAILED',
  BI_DASHBOARD_DELETE_FAILED:         'BI_DASHBOARD_DELETE_FAILED',
  BI_DASHBOARD_STATUS_CHANGE_FAILED:  'BI_DASHBOARD_STATUS_CHANGE_FAILED',

  // ---- BI RBAC ----
  BI_RBAC_QUERY_FAILED:               'BI_RBAC_QUERY_FAILED',
  BI_RBAC_SYNC_FAILED:                'BI_RBAC_SYNC_FAILED',
  BI_RBAC_UPDATE_FAILED:              'BI_RBAC_UPDATE_FAILED',
  BI_RBAC_CREATE_FAILED:              'BI_RBAC_CREATE_FAILED',
  BI_RBAC_DELETE_FAILED:              'BI_RBAC_DELETE_FAILED',
  BI_RBAC_LOAD_SUPERSET_FAILED:       'BI_RBAC_LOAD_SUPERSET_FAILED',
  BI_RBAC_LOAD_UNMAPPED_FAILED:       'BI_RBAC_LOAD_UNMAPPED_FAILED',

  // ---- BI Assignment ----
  BI_ASSIGNMENT_DELETE_FAILED:        'BI_ASSIGNMENT_DELETE_FAILED',

  // ---- Config ----
  CONFIG_SAVE_FAILED:                 'CONFIG_SAVE_FAILED',

  // ---- Permission ----
  PERMISSION_LOAD_ROLE_FAILED:        'PERMISSION_LOAD_ROLE_FAILED',
  PERMISSION_SAVE_FAILED:             'PERMISSION_SAVE_FAILED',
  PERMISSION_LOAD_LIST_FAILED:        'PERMISSION_LOAD_LIST_FAILED',

  // ---- Permission Request ----
  PERMISSION_REQUEST_FAILED:          'PERMISSION_REQUEST_FAILED',

  // ---- Dictionary ----
  DICTIONARY_LOAD_LIST_FAILED:        'DICTIONARY_LOAD_LIST_FAILED',
  DICTIONARY_LOAD_ITEMS_FAILED:       'DICTIONARY_LOAD_ITEMS_FAILED',

  // ---- Function Unit ----
  FUNCTION_UNIT_LOAD_FAILED:          'FUNCTION_UNIT_LOAD_FAILED',
  FUNCTION_UNIT_DEPLOY_FAILED:        'FUNCTION_UNIT_DEPLOY_FAILED',
  FUNCTION_UNIT_ROLLBACK_FAILED:      'FUNCTION_UNIT_ROLLBACK_FAILED',
  FUNCTION_UNIT_DELETE_PREVIEW_FAILED:'FUNCTION_UNIT_DELETE_PREVIEW_FAILED',
  FUNCTION_UNIT_DELETE_FAILED:        'FUNCTION_UNIT_DELETE_FAILED',
  FUNCTION_UNIT_IMPORT_FAILED:        'FUNCTION_UNIT_IMPORT_FAILED',
  FUNCTION_UNIT_TOGGLE_FAILED:        'FUNCTION_UNIT_TOGGLE_FAILED',

  // ---- User ----
  USER_ACTION_FAILED:                 'USER_ACTION_FAILED',
  USER_RESET_PASSWORD_FAILED:         'USER_RESET_PASSWORD_FAILED',
  USER_DELETE_FAILED:                 'USER_DELETE_FAILED',

  // ---- Profile ----
  PROFILE_PASSWORD_CHANGE_FAILED:     'PROFILE_PASSWORD_CHANGE_FAILED',

  // ---- Audit ----
  AUDIT_EXPORT_FAILED:                'AUDIT_EXPORT_FAILED',

  // ---- Virtual Group ----
  VIRTUAL_GROUP_LOAD_FAILED:          'VIRTUAL_GROUP_LOAD_FAILED',

  // ---- Business Unit ----
  BUSINESS_UNIT_OPERATION_FAILED:     'BUSINESS_UNIT_OPERATION_FAILED',

  // ---- Relation Table ----
  RELATION_TABLE_TOGGLE_FAILED:       'RELATION_TABLE_TOGGLE_FAILED',
  RELATION_TABLE_DEPLOY_FAILED:       'RELATION_TABLE_DEPLOY_FAILED',
  RELATION_TABLE_DELETE_FAILED:       'RELATION_TABLE_DELETE_FAILED',

  // ---- General ----
  COMMON_FAILED:                      'COMMON_FAILED',
  COMMON_QUERY_FAILED:                'COMMON_QUERY_FAILED',
} as const

export type AppErrorCodeType = typeof AppErrorCode[keyof typeof AppErrorCode]

// ============================================================
// 业务层错误类
// ============================================================

export class AppError extends Error {
  public readonly code: AppErrorCodeType
  public readonly details?: Record<string, unknown>

  constructor(code: AppErrorCodeType, details?: Record<string, unknown>, message?: string) {
    super(message || code)
    this.name = 'AppError'
    this.code = code
    this.details = details
  }
}

/** 创建 AppError 的便捷工厂 */
export function createError(code: AppErrorCodeType, details?: Record<string, unknown>, message?: string): AppError {
  return new AppError(code, details, message)
}
