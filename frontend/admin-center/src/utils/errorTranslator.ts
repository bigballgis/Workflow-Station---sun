/**
 * 错误码 → i18n key 翻译器
 *
 * 将 AppErrorCode 映射为 vue-i18n 的翻译 key。
 * UI 层调用: notifyError(t(errorTranslator(error.code)))
 *
 * 每个错误码对应 en.ts / zh-CN.ts / zh-TW.ts 中的 errors.* 命名空间。
 */

import { AppErrorCode, type AppErrorCodeType } from '@/types/errors'

const ERROR_I18N_MAP: Record<AppErrorCodeType, string> = {
  // ---- BI Dashboard ----
  [AppErrorCode.BI_DASHBOARD_QUERY_FAILED]:          'errors.biDashboardQueryFailed',
  [AppErrorCode.BI_DASHBOARD_SYNC_FAILED]:           'errors.biDashboardSyncFailed',
  [AppErrorCode.BI_DASHBOARD_UPDATE_FAILED]:         'errors.biDashboardUpdateFailed',
  [AppErrorCode.BI_DASHBOARD_DELETE_FAILED]:         'errors.biDashboardDeleteFailed',
  [AppErrorCode.BI_DASHBOARD_STATUS_CHANGE_FAILED]:  'errors.biDashboardStatusChangeFailed',

  // ---- BI RBAC ----
  [AppErrorCode.BI_RBAC_QUERY_FAILED]:               'errors.biRbacQueryFailed',
  [AppErrorCode.BI_RBAC_SYNC_FAILED]:                'errors.biRbacSyncFailed',
  [AppErrorCode.BI_RBAC_UPDATE_FAILED]:              'errors.biRbacUpdateFailed',
  [AppErrorCode.BI_RBAC_CREATE_FAILED]:              'errors.biRbacCreateFailed',
  [AppErrorCode.BI_RBAC_DELETE_FAILED]:              'errors.biRbacDeleteFailed',
  [AppErrorCode.BI_RBAC_LOAD_SUPERSET_FAILED]:       'errors.biRbacLoadSupersetFailed',
  [AppErrorCode.BI_RBAC_LOAD_UNMAPPED_FAILED]:       'errors.biRbacLoadUnmappedFailed',

  // ---- BI Assignment ----
  [AppErrorCode.BI_ASSIGNMENT_DELETE_FAILED]:        'errors.biAssignmentDeleteFailed',

  // ---- Config ----
  [AppErrorCode.CONFIG_SAVE_FAILED]:                 'errors.configSaveFailed',

  // ---- Permission ----
  [AppErrorCode.PERMISSION_LOAD_ROLE_FAILED]:        'errors.permissionLoadRoleFailed',
  [AppErrorCode.PERMISSION_SAVE_FAILED]:             'errors.permissionSaveFailed',
  [AppErrorCode.PERMISSION_LOAD_LIST_FAILED]:        'errors.permissionLoadListFailed',

  // ---- Permission Request ----
  [AppErrorCode.PERMISSION_REQUEST_FAILED]:          'errors.permissionRequestFailed',

  // ---- Dictionary ----
  [AppErrorCode.DICTIONARY_LOAD_LIST_FAILED]:        'errors.dictionaryLoadListFailed',
  [AppErrorCode.DICTIONARY_LOAD_ITEMS_FAILED]:       'errors.dictionaryLoadItemsFailed',

  // ---- Function Unit ----
  [AppErrorCode.FUNCTION_UNIT_LOAD_FAILED]:          'errors.functionUnitLoadFailed',
  [AppErrorCode.FUNCTION_UNIT_DEPLOY_FAILED]:        'errors.functionUnitDeployFailed',
  [AppErrorCode.FUNCTION_UNIT_ROLLBACK_FAILED]:      'errors.functionUnitRollbackFailed',
  [AppErrorCode.FUNCTION_UNIT_DELETE_PREVIEW_FAILED]:'errors.functionUnitDeletePreviewFailed',
  [AppErrorCode.FUNCTION_UNIT_DELETE_FAILED]:        'errors.functionUnitDeleteFailed',
  [AppErrorCode.FUNCTION_UNIT_IMPORT_FAILED]:        'errors.functionUnitImportFailed',
  [AppErrorCode.FUNCTION_UNIT_TOGGLE_FAILED]:        'errors.functionUnitToggleFailed',

  // ---- User ----
  [AppErrorCode.USER_ACTION_FAILED]:                 'errors.userActionFailed',
  [AppErrorCode.USER_RESET_PASSWORD_FAILED]:         'errors.userResetPasswordFailed',
  [AppErrorCode.USER_DELETE_FAILED]:                 'errors.userDeleteFailed',

  // ---- Profile ----
  [AppErrorCode.PROFILE_PASSWORD_CHANGE_FAILED]:     'errors.profilePasswordChangeFailed',

  // ---- Audit ----
  [AppErrorCode.AUDIT_EXPORT_FAILED]:                'errors.auditExportFailed',

  // ---- Virtual Group ----
  [AppErrorCode.VIRTUAL_GROUP_LOAD_FAILED]:          'errors.virtualGroupLoadFailed',

  // ---- Business Unit ----
  [AppErrorCode.BUSINESS_UNIT_OPERATION_FAILED]:     'errors.businessUnitOperationFailed',

  // ---- Relation Table ----
  [AppErrorCode.RELATION_TABLE_TOGGLE_FAILED]:       'errors.relationTableToggleFailed',
  [AppErrorCode.RELATION_TABLE_DEPLOY_FAILED]:       'errors.relationTableDeployFailed',
  [AppErrorCode.RELATION_TABLE_DELETE_FAILED]:       'errors.relationTableDeleteFailed',

  // ---- General ----
  [AppErrorCode.COMMON_FAILED]:                      'errors.commonFailed',
  [AppErrorCode.COMMON_QUERY_FAILED]:                'errors.commonQueryFailed',
}

/**
 * 将 AppErrorCode 转为 vue-i18n 的翻译 key。
 * UI 层: t(errorTranslator(err.code)) → 用户可见文案
 */
export function errorTranslator(code: AppErrorCodeType): string {
  return ERROR_I18N_MAP[code] || 'errors.unknown'
}

/**
 * 提取后端消息或 error.message 作为补充信息。
 * 组合展示: `${t(errorTranslator(code))}${detail ? ': ' + detail : ''}`
 */
export function extractErrorDetail(error: unknown): string | undefined {
  if (!error || typeof error !== 'object') return undefined
  const e = error as Record<string, unknown>
  // ApiError.backendMessage
  if (typeof e.backendMessage === 'string' && e.backendMessage) return e.backendMessage
  // AppError.details
  if (e.details && typeof e.details === 'object') {
    const d = e.details as Record<string, unknown>
    if (typeof d.message === 'string' && d.message) return d.message
  }
  // Generic error.message
  if (typeof e.message === 'string' && e.message) return e.message
  return undefined
}
