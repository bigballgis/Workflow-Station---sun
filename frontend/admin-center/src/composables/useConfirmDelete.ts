/**
 * 删除确认 composable (纯逻辑，仅保留 confirm 弹窗，去除 toast)
 *
 * 统一处理 notifyConfirm + API 调用模式，但不展示成功/失败 toast。
 * 调用方根据返回的 ConfirmDeleteResult 决定 UI 展示。
 *
 * @example
 * const { handleDelete } = useConfirmDelete(
 *   (id) => userApi.delete(id),
 *   { confirmMessage: t('user.confirmDelete'), onSuccess: fetchUsers }
 * )
 * // 模板: @click="handleDelete(row.id)"
 * // 调用方处理:
 * //   const r = await handleDelete(id)
 * //   if (r.cancelled) return
 * //   if (r.ok) notifySuccess(t('user.deleteSuccess'))
 * //   else notifyError(t(errorTranslator(r.code!)))
 */

import { notifyConfirm } from '@/utils/notify'

export interface ConfirmDeleteResult {
  /** 操作是否成功 */
  ok: boolean
  /** 用户在 confirm 弹窗点击了取消 */
  cancelled?: boolean
  /** 失败时的错误码 */
  code?: string
}

export interface UseConfirmDeleteOptions {
  confirmMessage?: string
  confirmTitle?: string
  confirmButtonText?: string
  /** 删除成功后的回调（刷新列表等） */
  onSuccess?: () => void | Promise<void>
}

export function useConfirmDelete(
  deleteFn: (id: string) => Promise<unknown>,
  options: UseConfirmDeleteOptions = {}
) {
  const {
    confirmMessage = 'Confirm delete?',
    confirmTitle = 'Confirm',
    confirmButtonText = 'Delete',
    onSuccess,
  } = options

  const handleDelete = async (id: string): Promise<ConfirmDeleteResult> => {
    try {
      await notifyConfirm(confirmMessage, confirmTitle, {
        type: 'warning',
        confirmButtonText,
        confirmButtonClass: 'el-button--danger',
      })
    } catch {
      return { ok: false, cancelled: true }
    }

    try {
      await deleteFn(id)
      if (onSuccess) await onSuccess()
      return { ok: true }
    } catch (e) {
      const code = extractErrorCode(e)
      return { ok: false, code }
    }
  }

  return { handleDelete }
}

function extractErrorCode(e: unknown): string | undefined {
  if (e && typeof e === 'object') {
    const err = e as Record<string, unknown>
    if (typeof err.code === 'string') return err.code
    if (typeof err.message === 'string') return err.message
  }
  return undefined
}
