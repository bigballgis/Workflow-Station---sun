import { computed, ref } from 'vue'
import { subTableViewApi, type SubTableFieldDTO } from '@/api/subTableView'
import type {
  SubTableListColumnDTO,
  SubTableListViewEmit,
  SubTableListViewProps,
  TFn,
} from './types'

interface UseViewColumnsOptions {
  props: SubTableListViewProps
  emit: SubTableListViewEmit
  fieldSearchKeyword: { value: string }
  isLinkColumn: (column: SubTableListColumnDTO) => boolean
  isLookupColumn: (column: SubTableListColumnDTO) => boolean
  getLinkColumnKey: (componentId: number) => string
  t: TFn
}

/**
 * 视图列（modelValue 双向绑定）、可用字段加载/过滤，以及字段、Link Form、Lookup
 * 列的添加/移除/清空。Link Form 与 Lookup 列工厂同时供面板点击与拖拽落点复用。
 */
export function useViewColumns(options: UseViewColumnsOptions) {
  const { props, emit, fieldSearchKeyword, isLinkColumn, isLookupColumn, getLinkColumnKey, t } = options

  // Local fallback: when parent doesn't yet have allFields, store the loaded value here
  const localAvailableFields = ref<SubTableFieldDTO[]>([])
  const loadingFields = ref(false)

  const genericLinkFormComponentId = computed(() => -Math.abs(props.binding.bindingId || 0))
  const genericLinkFormKey = computed(() => getLinkColumnKey(genericLinkFormComponentId.value))
  const genericLookupKey = computed(() => `lookup:${props.binding.bindingId || 0}`)

  // All available fields: prefer prop (parent-managed), fall back to locally loaded
  const allFields = computed(() => props.availableFields?.length ? props.availableFields : localAvailableFields.value)

  // Fields currently in the view (user-selected, ordered)
  const viewColumns = computed({
    get: () => props.modelValue || [],
    set: (val) => emit('update:modelValue', val)
  })

  async function loadFields() {
    if (!props.formId || !props.binding?.bindingId) return
    loadingFields.value = true
    try {
      const res = await subTableViewApi.getAvailableFields(props.formId, props.binding.bindingId)
      const fields: SubTableFieldDTO[] = res.data || []
      localAvailableFields.value = fields
      emit('update:availableFields', fields)
    } catch (e) {
      console.error('[SubTableListView] failed to load fields:', e)
    } finally {
      loadingFields.value = false
    }
  }

  const filteredAvailableFields = computed(() => {
    const kw = fieldSearchKeyword.value.trim().toLowerCase()
    // Only show fields NOT already in the view
    const inView = new Set(viewColumns.value.filter(c => !isLinkColumn(c)).map(f => f.fieldName))
    let list = allFields.value.filter(f => !inView.has(f.fieldName))
    if (kw) {
      list = list.filter(f => f.fieldName.toLowerCase().includes(kw) || (f.displayName || '').toLowerCase().includes(kw))
    }
    return list
  })

  const isFieldInView = (fieldName: string) => viewColumns.value.some(f => !isLinkColumn(f) && f.fieldName === fieldName)

  // --- Field operations ---
  const addFieldToView = (field: SubTableFieldDTO) => {
    if (!isFieldInView(field.fieldName)) {
      emit('update:modelValue', [...viewColumns.value, { ...field, columnType: 'field' }])
      emit('save')
    }
  }

  const makeLinkFormColumn = (): SubTableListColumnDTO => ({
    columnType: 'linkForm',
    fieldName: genericLinkFormKey.value,
    dataType: 'LINK_FORM',
    nullable: true,
    isPrimaryKey: false,
    componentId: genericLinkFormComponentId.value,
    displayName: 'Link Form',
    columnLabel: 'Link Form',
    linkText: t('linkForm.defaultLinkText'),
    boundSubTableBindingId: props.binding.bindingId,
    boundSubTableName: props.binding.tableName
  })

  const addLinkFormToView = () => {
    if (!viewColumns.value.some(c => isLinkColumn(c) && c.componentId === genericLinkFormComponentId.value)) {
      emit('update:modelValue', [...viewColumns.value, makeLinkFormColumn()])
      emit('save')
    }
  }

  const makeLookupColumn = (): SubTableListColumnDTO => ({
    columnType: 'lookup',
    fieldName: genericLookupKey.value,
    dataType: 'LOOKUP',
    nullable: true,
    isPrimaryKey: false,
    displayName: 'Lookup',
    columnLabel: 'Lookup',
    lookupConfig: '{}'
  })

  const addLookupToView = () => {
    if (!viewColumns.value.some(c => isLookupColumn(c))) {
      emit('update:modelValue', [...viewColumns.value, makeLookupColumn()])
      emit('save')
    }
  }

  const removeField = (index: number) => {
    emit('update:modelValue', viewColumns.value.filter((_, i) => i !== index))
    emit('save')
  }

  const handleClear = () => {
    emit('update:modelValue', [])
    emit('save')
  }

  return {
    localAvailableFields,
    loadingFields,
    genericLinkFormComponentId,
    genericLinkFormKey,
    genericLookupKey,
    allFields,
    viewColumns,
    loadFields,
    filteredAvailableFields,
    isFieldInView,
    addFieldToView,
    addLinkFormToView,
    addLookupToView,
    removeField,
    handleClear,
  }
}
