/**
 * 统一通知层 (Unified Notification Layer)
 *
 * 所有 toast / confirm 的单一入口。管理 i18n、duration、icon、logging、analytics、风格。
 *
 * 禁止直接使用 ElMessage / ElMessageBox / ElNotification。
 *
 * @example
 * // 简单成功/失败
 * notifySuccess(t('common.success'))
 * notifyError(error.message || t('common.failed'))
 *
 * // 确认弹窗
 * try {
 *   await notifyConfirm(t('user.confirmDeleteMsg'), t('user.confirmDelete'))
 *   await userApi.delete(id)
 *   notifySuccess(t('user.deleteSuccess'))
 * } catch (e) {
 *   if (e !== 'cancel') notifyError(t('user.deleteFailed'))
 * }
 */

import { ElMessage, ElMessageBox } from 'element-plus'

// ============================================================
// Types
// ============================================================

export interface NotifyOptions {
  /** 显示时长 (ms)，默认 3000 */
  duration?: number
  /** 操作标识，用于 logging/analytics */
  action?: string
}

export interface ConfirmOptions {
  /** 弹窗类型，默认 'warning' */
  type?: 'success' | 'warning' | 'info' | 'error'
  /** 确认按钮文字 */
  confirmButtonText?: string
  /** 确认按钮 class */
  confirmButtonClass?: string
  /** 取消按钮文字 */
  cancelButtonText?: string
}

// ============================================================
// Defaults (统一管理 duration / icon / 风格)
// ============================================================

const DEFAULT_SUCCESS_DURATION = 2000
const DEFAULT_ERROR_DURATION = 5000
const DEFAULT_WARNING_DURATION = 4000
const DEFAULT_INFO_DURATION = 3000

// ============================================================
// Public API
// ============================================================

/** 成功通知 */
export function notifySuccess(message: string, options?: NotifyOptions): void {
  log('success', message, options?.action)
  ElMessage.success({
    message,
    duration: options?.duration ?? DEFAULT_SUCCESS_DURATION,
  })
}

/** 错误通知 */
export function notifyError(message: string, options?: NotifyOptions): void {
  log('error', message, options?.action)
  ElMessage.error({
    message,
    duration: options?.duration ?? DEFAULT_ERROR_DURATION,
    showClose: true,
  })
}

/** 警告通知 */
export function notifyWarning(message: string, options?: NotifyOptions): void {
  log('warning', message, options?.action)
  ElMessage.warning({
    message,
    duration: options?.duration ?? DEFAULT_WARNING_DURATION,
  })
}

/** 信息通知 */
export function notifyInfo(message: string, options?: NotifyOptions): void {
  log('info', message, options?.action)
  ElMessage.info({
    message,
    duration: options?.duration ?? DEFAULT_INFO_DURATION,
  })
}

/**
 * 确认弹窗。成功时 resolve，用户取消时 reject('cancel')。
 * 保持与 ElMessageBox.confirm 的 reject 行为一致，便于现有 try/catch 迁移。
 */
export async function notifyConfirm(
  message: string,
  title?: string,
  options?: ConfirmOptions,
): Promise<void> {
  log('confirm', message, undefined)
  try {
    await ElMessageBox.confirm(message, title ?? '', {
      type: options?.type ?? 'warning',
      confirmButtonText: options?.confirmButtonText,
      confirmButtonClass: options?.confirmButtonClass,
      cancelButtonText: options?.cancelButtonText,
    })
  } catch (e) {
    // 保持与原始 ElMessageBox 一致的 reject 行为
    throw e
  }
}

// ============================================================
// Internal logging (预留 analytics 插口)
// ============================================================

function log(level: string, message: string, action?: string): void {
  // 当前阶段: console 输出。后续可替换为 analytics 服务。
  const prefix = action ? `[notify:${level}](${action})` : `[notify:${level}]`
  // 仅开发环境输出，避免生产日志噪音
  if (import.meta.env.DEV) {
    console.debug(prefix, message)
  }
}
