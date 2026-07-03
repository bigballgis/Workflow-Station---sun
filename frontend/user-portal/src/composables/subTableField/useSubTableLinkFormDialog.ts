import { computed, ref, unref, type Ref } from 'vue'
import { mergeSubTableRowsByRowId, stripLinkFormDesignerTableLabel } from '@/composables/tasks/shared'
import type { Column, SubTableBinding, SubTableFieldEmit, SubTableFieldProps, SubTableFieldT } from './subTableFieldTypes'
import {
  isMiStyleParentRowForLinkForm,
  linkFormTableMatchKey,
  normalizeFkIdForMatch,
  subTableBindingMatches
} from './subTableLinkFormRowMatch'
import {
  collectLinkTargetFormFieldKeys,
  linkFormRowsLackFormPayload,
  maxFormFieldOverlapScore,
  peerSubTableDataByFormFieldOverlap,
  resolveLinkFormFieldValueForModal,
  rowValueForLinkedFormField
} from './subTableLinkFormFields'

/** Link Form detail modal: dialog state, binding resolution and save/close flow. */
export function useSubTableLinkFormDialog(
  props: SubTableFieldProps,
  rows: Ref<any[]>,
  emit: SubTableFieldEmit,
  t: SubTableFieldT,
  deps: {
    resolveSubTableRowPk: (row: Record<string, unknown> | null | undefined) => string | number | null
    filterRowsByMiLinkFormParent: (parentRow: Record<string, any>, rows: any[], binding?: SubTableBinding) => any[]
  },
) {
  const { resolveSubTableRowPk, filterRowsByMiLinkFormParent } = deps

  const linkFormDialogVisible = ref(false)
  const linkFormModalPanelRef = ref<HTMLElement | null>(null)
  const activeLinkColumn = ref<Column | null>(null)
  const activeLinkRowIndex = ref<number | null>(null)
  const linkedSubTableRows = ref<any[]>([])
  const linkedFormData = ref<Record<string, any>>({})
  /** When footer is shown (To Do), restore on Cancel/X without persisting to parent row. */
  const linkFormDialogSnapshot = ref<{ linkedFormData: Record<string, any>; linkedSubTableRows: any[] } | null>(null)

  /**
   * Multiple bindings can share the same bindingId (prev vs current). `.find` always picked the first;
   * for read-only / snapshot UI (`suppressLinkFormInitialData` false), prefer the first match that
   * already has row data so Details is not blank. MI todo (`suppress` true) keeps the first match
   * so isolated empty binding wins → blank Details for a new sub-task.
   */
  function resolveLinkBindingForColumn(col: Column | null | undefined): SubTableBinding | undefined {
    if (!col) return undefined
    const list = props.linkedSubTableBindings ?? []
    const boundId = col.props?.boundSubTableBindingId
    const boundNameRaw = col.props?.boundSubTableName ? String(col.props.boundSubTableName).trim() : ''
    const boundNameStripped = stripLinkFormDesignerTableLabel(boundNameRaw)
    const boundKey = linkFormTableMatchKey(boundNameRaw)

    const matches = list.filter(item => {
      if (boundId != null && Number(item.bindingId) === Number(boundId)) return true
      if (boundNameRaw && item.tableName === boundNameRaw) return true
      if (boundKey && linkFormTableMatchKey(item.tableName) === boundKey) return true
      if (boundNameStripped || boundId != null) {
        const pid = boundId != null ? Number(boundId) : -2147483648
        return subTableBindingMatches(
          {
            bindingId: pid,
            tableName: boundNameStripped || boundNameRaw,
            tableId: null,
            physicalTableName: undefined
          },
          {
            bindingId: item.bindingId,
            tableName: item.tableName,
            tableId: item.tableId ?? null,
            physicalTableName: item.physicalTableName
          }
        )
      }
      return false
    })
    if (matches.length === 0) return undefined
    if (props.suppressLinkFormInitialData) return matches[0]
    const withData = matches.find(m => Array.isArray(m.data) && m.data.length > 0)
    return withData ?? matches[0]
  }

  const selectedLinkBinding = computed(() => {
    const col = activeLinkColumn.value
    return resolveLinkBindingForColumn(col) ?? null
  })

  /** Modal title: bound sub-table name + i18n (do not use list column label — avoids stale "ADD + …" text). */
  const linkFormModalTitle = computed(() => {
    const col = activeLinkColumn.value
    const fromProp = col?.props?.boundSubTableName ? String(col.props.boundSubTableName).trim() : ''
    const fromBinding = selectedLinkBinding.value?.tableName ? String(selectedLinkBinding.value.tableName).trim() : ''
    const tableName = fromProp || fromBinding
    if (tableName) return t('linkForm.dialogTitleAddTable', { tableName })
    return t('linkForm.linkedForm')
  })

  const linkedFormFields = computed(() => selectedLinkBinding.value?.formFields || [])
  const linkedFormLabelWidth = computed(() => {
    const width = selectedLinkBinding.value?.formOptions?.form?.labelWidth
    // fallback 'auto'：弹窗内各行输入框左对齐
    return typeof width === 'string' && width.trim() ? width : 'auto'
  })
  const canEditSelectedLinkBinding = computed(() => !!(props.editable && selectedLinkBinding.value?.bindingMode === 'EDITABLE'))

  /** Field-layout link form only; grid fallback has no footer. */
  const showLinkFormDetailActionFooter = computed(
    () =>
      !!props.showLinkFormDialogFooter &&
      canEditSelectedLinkBinding.value &&
      !!selectedLinkBinding.value &&
      linkedFormFields.value.length > 0
  )

  function resolveLinkedFallbackRows(binding?: SubTableBinding): any[] {
    if (!binding) return []
    let pool: any[] = Array.isArray(binding.data) ? [...binding.data] : []
    const mergeAllMatchingPeers = () => {
      for (const item of props.linkedSubTableBindings ?? []) {
        if (item === binding || !subTableBindingMatches(item, binding)) continue
        if (!Array.isArray(item.data) || item.data.length === 0) continue
        pool = mergeSubTableRowsByRowId(pool, item.data, binding.primaryKeyFields ?? null)
      }
    }
    if (pool.length === 0) {
      const sameTableBinding = props.linkedSubTableBindings?.find(item =>
        item !== binding &&
        Array.isArray(item.data) &&
        item.data.length > 0 &&
        subTableBindingMatches(item, binding)
      )
      pool = Array.isArray(sameTableBinding?.data) ? [...sameTableBinding.data] : []
    }
    if (
      pool.length === 0
      || (binding.formFields?.length && linkFormRowsLackFormPayload(pool, binding.formFields))
    ) {
      mergeAllMatchingPeers()
      if (binding.formFields?.length && linkFormRowsLackFormPayload(pool, binding.formFields)) {
        const overlap = peerSubTableDataByFormFieldOverlap(binding, props.linkedSubTableBindings ?? [])
        if (overlap.length > 0) {
          pool = mergeSubTableRowsByRowId(pool, overlap, binding.primaryKeyFields ?? null)
        }
      }
    }
    return pool
  }

  function buildLinkFormPeerMap(): Map<number, number | null> {
    const peerMap = new Map<number, number | null>()
    for (const b of props.linkedSubTableBindings ?? []) {
      const tid = b.tableId != null ? Number(b.tableId) : null
      if (tid != null && Number.isFinite(tid)) peerMap.set(Number(b.bindingId), tid)
    }
    return peerMap
  }

  function collectLinkFormRowsFromProcessVariables(
    binding: SubTableBinding,
    parentRow: Record<string, any> | null | undefined,
  ): any[] {
    const pd = unref(props.primaryFormData) as Record<string, unknown> | undefined
    const raw = pd?.__subTables__
    if (!raw || typeof raw !== 'object') return []
    const fieldKeys = collectLinkTargetFormFieldKeys(binding)
    if (fieldKeys.size === 0) return []
    const threshold =
      fieldKeys.size <= 2 ? 1 : Math.min(fieldKeys.size, Math.max(2, Math.ceil(fieldKeys.size * 0.25)))
    let merged: any[] = []
    for (const v of Object.values(raw as Record<string, unknown>)) {
      if (!Array.isArray(v) || v.length === 0) continue
      if (maxFormFieldOverlapScore(v, fieldKeys) < threshold) continue
      merged = mergeSubTableRowsByRowId(merged, v as any[], binding.primaryKeyFields ?? null)
    }
    if (parentRow && merged.length > 0 && isMiStyleParentRowForLinkForm(parentRow as Record<string, unknown>)) {
      merged = filterRowsByMiLinkFormParent(parentRow, merged, binding)
    }
    return merged
  }

  function buildLinkedFormData(
    binding?: SubTableBinding,
    opts?: { readonly?: boolean },
  ): Record<string, any> {
    const raw =
      binding?.data?.[0] && typeof binding.data[0] === 'object'
        ? (binding.data[0] as Record<string, any>)
        : {}
    const next: Record<string, any> = {}
    const modalOpts = { readonly: opts?.readonly ?? !props.editable }
    if (binding?.formFields?.length) {
      binding.formFields.forEach(field => {
        if (field.type === 'card') {
          field.children?.forEach(child => {
            const v = rowValueForLinkedFormField(raw, child.key)
            next[child.key] = resolveLinkFormFieldValueForModal(child, v, modalOpts)
          })
        } else {
          const v = rowValueForLinkedFormField(raw, field.key)
          next[field.key] = resolveLinkFormFieldValueForModal(field, v, modalOpts)
        }
      })
      return next
    }
    return { ...raw }
  }

  function updateLinkedFormField(key: string, value: any) {
    linkedFormData.value = { ...linkedFormData.value, [key]: value }
  }

  /** Header close / Cancel: with To Do footer, discard edits; otherwise auto-save field link form when editable. */
  function closeLinkFormDetailDialog() {
    if (showLinkFormDetailActionFooter.value) {
      const snap = linkFormDialogSnapshot.value
      if (snap) {
        linkedFormData.value = JSON.parse(JSON.stringify(snap.linkedFormData)) as Record<string, any>
        linkedSubTableRows.value = JSON.parse(JSON.stringify(snap.linkedSubTableRows)) as any[]
      }
      linkFormDialogVisible.value = false
      linkFormDialogSnapshot.value = null
      return
    }
    if (
      canEditSelectedLinkBinding.value &&
      selectedLinkBinding.value &&
      linkedFormFields.value.length > 0
    ) {
      saveLinkedFormData()
      return
    }
    linkFormDialogVisible.value = false
  }

  function saveLinkedFormData() {
    const linkRowIndex = activeLinkRowIndex.value
    const col = activeLinkColumn.value
    if (linkRowIndex == null || !col) {
      linkFormDialogSnapshot.value = null
      linkFormDialogVisible.value = false
      return
    }

    const boundId = col.props?.boundSubTableBindingId
    const binding = selectedLinkBinding.value
    const boundName = col.props?.boundSubTableName || binding?.tableName

    const currentRows = linkedSubTableRows.value.length > 0 ? [...linkedSubTableRows.value] : [{}]
    currentRows[0] = { ...currentRows[0], ...linkedFormData.value }
    const parentRow = linkRowIndex != null ? rows.value[linkRowIndex] : null
    if (parentRow && isMiStyleParentRowForLinkForm(parentRow as Record<string, unknown>)) {
      const parentKey =
        normalizeFkIdForMatch(parentRow.id_idw)
        ?? normalizeFkIdForMatch(resolveSubTableRowPk(parentRow as Record<string, unknown>))
      if (parentKey != null) {
        const curId = normalizeFkIdForMatch(currentRows[0]?.id)
        if (curId == null || curId !== parentKey) {
          const n = Number(parentKey)
          currentRows[0] = {
            ...currentRows[0],
            id: Number.isFinite(n) && String(n) === parentKey ? n : parentKey
          }
        }
      }
    }
    linkedSubTableRows.value = [...currentRows]

    const nextMainRows = rows.value.map((r, idx) => {
      if (idx !== linkRowIndex) return r
      const base = (r && typeof r === 'object') ? { ...r } : {}
      const sub = { ...((base.__subTables__ && typeof base.__subTables__ === 'object') ? base.__subTables__ : {}) } as Record<string, any>
      if (boundId != null) {
        sub[boundId] = currentRows
        sub[String(boundId)] = currentRows
      }
      if (boundName) {
        sub[boundName] = currentRows
        sub[String(boundName)] = currentRows
      }
      base.__subTables__ = sub
      return base
    })

    emit('update:modelValue', nextMainRows)
    linkFormDialogSnapshot.value = null
    linkFormDialogVisible.value = false
  }

  function handleLinkedSubTableUpdate(rows: any[]) {
    linkedSubTableRows.value = [...rows]
    const bindingId = selectedLinkBinding.value?.bindingId
    if (bindingId != null) {
      emit('update:linkedSubTableData', bindingId, rows)
    }
  }

  return {
    linkFormDialogVisible,
    linkFormModalPanelRef,
    activeLinkColumn,
    activeLinkRowIndex,
    linkedSubTableRows,
    linkedFormData,
    linkFormDialogSnapshot,
    resolveLinkBindingForColumn,
    selectedLinkBinding,
    linkFormModalTitle,
    linkedFormFields,
    linkedFormLabelWidth,
    canEditSelectedLinkBinding,
    showLinkFormDetailActionFooter,
    resolveLinkedFallbackRows,
    buildLinkFormPeerMap,
    collectLinkFormRowsFromProcessVariables,
    buildLinkedFormData,
    updateLinkedFormField,
    closeLinkFormDetailDialog,
    saveLinkedFormData,
    handleLinkedSubTableUpdate
  }
}
