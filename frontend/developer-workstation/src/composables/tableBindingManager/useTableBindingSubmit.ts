import { type Ref, type ComputedRef } from 'vue'
import { ElMessage, type FormInstance } from 'element-plus'
import { functionUnitApi, type TableBinding, type TableBindingRequest } from '@/api/functionUnit'
import { type RelationTableDTO } from '@/api/relationTable'
import { pickHttpErrorBodyMessage, resolveUserFacingHttpMessage } from '@/utils/httpErrorMessage'

interface UseTableBindingSubmitOptions {
  functionUnitId: number
  getFormId: () => number
  formRef: Ref<FormInstance | undefined>
  bindingForm: Ref<TableBindingRequest>
  submitting: Ref<boolean>
  editingBinding: Ref<TableBinding | null>
  showAddDialog: Ref<boolean>
  structuralFkFieldNames: ComputedRef<string[]>
  deployedRelationTables: Ref<RelationTableDTO[]>
  toRelationTableOptionId: (tableId: number) => number
  reloadBindings: () => void
  emitUpdate: () => void
  emitAdd?: (payload: { tableId: number; bindingType: string; bindingId: number }) => void
  t: (key: string, params?: Record<string, unknown>) => string
}

/**
 * 绑定表单提交与后端错误映射逻辑。
 */
export function useTableBindingSubmit(options: UseTableBindingSubmitOptions) {
  const {
    functionUnitId,
    getFormId,
    formRef,
    bindingForm,
    submitting,
    editingBinding,
    showAddDialog,
    structuralFkFieldNames,
    deployedRelationTables,
    toRelationTableOptionId,
    reloadBindings,
    emitUpdate,
    emitAdd,
    t,
  } = options

  /** 从响应体取出业务错误码（platform.common 与手写 JSON） */
  function extractBindingErrorCode(data: unknown): string | undefined {
    if (!data || typeof data !== 'object') return undefined
    const o = data as Record<string, unknown>
    const nested = o.error
    if (nested && typeof nested === 'object') {
      const e = nested as Record<string, unknown>
      const c = e.code ?? e.errorCode
      if (typeof c === 'string' && c.trim()) return c.trim()
    }
    const top = o.code ?? o.errorCode
    if (typeof top === 'string' && top.trim()) return top.trim()
    return undefined
  }

  /** 把后端业务错误码映射成对用户友好的提示 */
  function mapBackendError(err: any): string {
    const data = err?.response?.data
    const code = extractBindingErrorCode(data)
    const codeMap: Record<string, string> = {
      SUB_REQUIRES_PRIMARY: t('tableBinding.primaryFirstHint'),
      PRIMARY_BINDING_EXISTS: t('tableBinding.primaryBindingExists'),
      BINDING_EXISTS: t('tableBinding.bindingExists'),
      PRIMARY_REQUIRES_MAIN_TABLE: t('tableBinding.primaryRequiresMainTable'),
      SUB_BINDING_REQUIRES_SUB_TABLE: t('tableBinding.subBindingRequiresSubTable'),
      SUB_REQUIRES_FOREIGN_KEY: t('tableBinding.foreignKeyRequired'),
      INVALID_FOREIGN_KEY: t('tableBinding.invalidForeignKey'),
      RELATED_BINDING_REQUIRES_RELATION_TABLE: t('tableBinding.relatedBindingRequiresRelationTable'),
      SYS_INTERNAL_ERROR: t('api.serverError'),
      RES_NOT_FOUND: t('api.notFound'),
      VAL_INVALID_INPUT: t('api.invalidParams')
    }
    if (code && codeMap[code]) return codeMap[code]

    const fromBody = pickHttpErrorBodyMessage(data)
    if (fromBody) return fromBody

    return resolveUserFacingHttpMessage(err, t)
  }

  // Submit form
  async function handleSubmit() {
    if (!formRef.value) return

    try {
      await formRef.value.validate()
    } catch {
      return
    }

    submitting.value = true
    try {
      // For deployed/system relation tables (negative ID), convert to relationTableId
      const requestData = { ...bindingForm.value }
      if (
        requestData.bindingType === 'SUB'
        && requestData.bindingLinkMode !== 'miParticipantRow'
        && !requestData.foreignKeyField
        && structuralFkFieldNames.value.length > 0
      ) {
        requestData.foreignKeyField = structuralFkFieldNames.value[0]
      }
      // tableId < 0 means it's a deployed/system relation table option
      if (requestData.tableId && requestData.tableId < 0) {
        const remoteTable = deployedRelationTables.value.find(t => toRelationTableOptionId(t.id) === requestData.tableId)
        requestData.relationTableId = remoteTable ? remoteTable.id : -requestData.tableId
        requestData.tableId = undefined
      }

      if (editingBinding.value) {
        await functionUnitApi.updateFormBinding(
          functionUnitId,
          getFormId(),
          editingBinding.value.id!,
          requestData
        )
        ElMessage.success(t('tableBinding.updateSuccess'))
        showAddDialog.value = false
        reloadBindings()
        emitUpdate()
      } else {
        const res = await functionUnitApi.createFormBinding(functionUnitId, getFormId(), requestData)
        ElMessage.success(t('tableBinding.addSuccess'))
        showAddDialog.value = false
        reloadBindings()
        emitUpdate()
        const created = res?.data
        // For SUB/PRIMARY the server echoes tableId; for RELATED it may use relationTableId.
        // Fall back to the request's tableId so the handler always receives a usable id.
        const resolvedTableId = created?.tableId ?? requestData.tableId
        if (created?.id && resolvedTableId && emitAdd) {
          emitAdd({
            tableId: resolvedTableId,
            bindingType: created.bindingType ?? requestData.bindingType,
            bindingId: created.id,
          })
        }
      }
    } catch (e: any) {
      console.error('[TableBindingManager] submit failed:', e?.response?.data || e)
      ElMessage({ type: 'error', message: mapBackendError(e), duration: 5000 })
    } finally {
      submitting.value = false
    }
  }

  return {
    handleSubmit,
  }
}
