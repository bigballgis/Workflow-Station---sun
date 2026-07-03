import { ref } from 'vue'
import type { ComputedRef, WritableComputedRef } from 'vue'
import type {
  SubTableBindingOption,
  SubTableListColumnDTO,
  SubTableListViewEmit,
  SubTableListViewProps,
  TFn,
} from './types'

interface UseActionColumnConfigOptions {
  props: SubTableListViewProps
  emit: SubTableListViewEmit
  viewColumns: WritableComputedRef<SubTableListColumnDTO[]>
  subTableBindingOptions: ComputedRef<SubTableBindingOption[]>
  isLookupColumn: (column: SubTableListColumnDTO) => boolean
  isConfigurableActionColumn: (column: SubTableListColumnDTO) => boolean
  resolveSubTableBindingDisplayName: (bindingId: unknown) => string
  t: TFn
}

/**
 * Link Form / Lookup 操作列的配置弹层：打开时回填、保存时回写视图列。
 */
export function useActionColumnConfig(options: UseActionColumnConfigOptions) {
  const {
    props,
    emit,
    viewColumns,
    subTableBindingOptions,
    isLookupColumn,
    isConfigurableActionColumn,
    resolveSubTableBindingDisplayName,
    t,
  } = options

  const showActionColumnConfig = ref(false)
  const editingActionColumnIndex = ref<number | null>(null)
  const editingActionColumnType = ref<'linkForm' | 'lookup'>('linkForm')
  const linkColumnConfig = ref({ boundSubTableBindingId: 0, boundSubTableName: '', columnLabel: '', linkText: '' })
  const lookupColumnConfig = ref({ columnLabel: 'Lookup', lookupConfig: '{}' })

  function openActionColumnConfig(column: SubTableListColumnDTO, index: number) {
    editingActionColumnIndex.value = index
    editingActionColumnType.value = isLookupColumn(column) ? 'lookup' : 'linkForm'
    if (isLookupColumn(column)) {
      lookupColumnConfig.value = {
        columnLabel: column.columnLabel || column.displayName || 'Lookup',
        lookupConfig: column.lookupConfig || '{}'
      }
    } else {
      linkColumnConfig.value = {
        boundSubTableBindingId: column.boundSubTableBindingId || props.binding.bindingId,
        // Persisted display name — the bound binding may live on ANOTHER form of this unit,
        // in which case the current form's options cannot label it (would show the raw id).
        boundSubTableName: column.boundSubTableName
          || resolveSubTableBindingDisplayName(column.boundSubTableBindingId),
        columnLabel: column.columnLabel || column.displayName || 'Link Form',
        linkText: column.linkText || t('linkForm.defaultLinkText')
      }
    }
    showActionColumnConfig.value = true
  }

  function saveActionColumnConfig() {
    if (editingActionColumnIndex.value === null) return
    const columns = [...viewColumns.value]
    const current = columns[editingActionColumnIndex.value]
    if (!current || !isConfigurableActionColumn(current)) return
    columns[editingActionColumnIndex.value] = isLookupColumn(current)
      ? {
        ...current,
        displayName: lookupColumnConfig.value.columnLabel || 'Lookup',
        columnLabel: lookupColumnConfig.value.columnLabel || 'Lookup',
        lookupConfig: lookupColumnConfig.value.lookupConfig || '{}'
      }
      : {
        ...current,
        displayName: linkColumnConfig.value.columnLabel || 'Link Form',
        columnLabel: linkColumnConfig.value.columnLabel || 'Link Form',
        linkText: linkColumnConfig.value.linkText || t('linkForm.defaultLinkText'),
        boundSubTableBindingId: linkColumnConfig.value.boundSubTableBindingId || props.binding.bindingId,
        boundSubTableName: subTableBindingOptions.value.find(
          option => option.bindingId === linkColumnConfig.value.boundSubTableBindingId
        )?.tableDisplayName
          || subTableBindingOptions.value.find(
            option => option.bindingId === linkColumnConfig.value.boundSubTableBindingId
          )?.tableName
          || resolveSubTableBindingDisplayName(linkColumnConfig.value.boundSubTableBindingId)
          // Cross-form binding: keep the persisted name instead of blanking it.
          || linkColumnConfig.value.boundSubTableName
          || current.boundSubTableName
    }
    emit('update:modelValue', columns)
    emit('save')
    showActionColumnConfig.value = false
    editingActionColumnIndex.value = null
  }

  return {
    showActionColumnConfig,
    editingActionColumnIndex,
    editingActionColumnType,
    linkColumnConfig,
    lookupColumnConfig,
    openActionColumnConfig,
    saveActionColumnConfig,
  }
}
