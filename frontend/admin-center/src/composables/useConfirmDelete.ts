/**
 * 删除确认 composable
 * 
 * 统一处理 ElMessageBox.confirm + API 调用 + 成功/失败消息的模式。
 * 
 * @example
 * const { handleDelete } = useConfirmDelete(
 *   (id) => userApi.delete(id),
 *   { confirmMessage: '确定要删除吗？', successMessage: '删除成功' }
 * )
 * // 在模板中: @click="handleDelete(row.id)"
 */

import { ElMessage, ElMessageBox } from 'element-plus'

export interface UseConfirmDeleteOptions {
  /** 确认弹窗消息 */
  confirmMessage?: string
  /** 确认弹窗标题 */
  confirmTitle?: string
  /** 成功消息 */
  successMessage?: string
  /** 失败消息 */
  errorMessage?: string
  /** 确认按钮文字 */
  confirmButtonText?: string
  /** 删除成功后的回调（刷新列表等） */
  onSuccess?: () => void | Promise<void>
}

export function useConfirmDelete(
  deleteFn: (id: string) => Promise<any>,
  options: UseConfirmDeleteOptions = {}
) {
  const {
    confirmMessage = '确定要删除吗？',
    confirmTitle = '确认',
    successMessage = '删除成功',
    errorMessage = '删除失败',
    confirmButtonText = '删除',
    onSuccess,
  } = options

  const handleDelete = async (id: string) => {
    try {
      await ElMessageBox.confirm(confirmMessage, confirmTitle, {
        type: 'warning',
        confirmButtonText,
        confirmButtonClass: 'el-button--danger',
      })
      await deleteFn(id)
      ElMessage.success(successMessage)
      if (onSuccess) await onSuccess()
    } catch (error: any) {
      if (error !== 'cancel') {
        ElMessage.error(error.message || errorMessage)
      }
    }
  }

  return { handleDelete }
}
