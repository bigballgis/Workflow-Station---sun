import { ref } from 'vue'
import type { ComputedRef, WritableComputedRef } from 'vue'
import type { SubTableFieldDTO } from '@/api/subTableView'
import type { SubTableListColumnDTO, SubTableListViewEmit } from './types'

type DragPayload = { kind: 'field'; fieldName: string } | { kind: 'linkForm' } | { kind: 'lookup' }

interface UseColumnDragOptions {
  emit: SubTableListViewEmit
  viewColumns: WritableComputedRef<SubTableListColumnDTO[]>
  allFields: ComputedRef<SubTableFieldDTO[]>
  genericLinkFormKey: ComputedRef<string>
  genericLookupKey: ComputedRef<string>
  isFieldInView: (fieldName: string) => boolean
  addLinkFormToView: () => void
  addLookupToView: () => void
}

/**
 * 拖拽状态与处理：左侧面板拖拽到网格（字段/Link Form/Lookup），以及列头拖拽重排。
 */
export function useColumnDrag(options: UseColumnDragOptions) {
  const {
    emit,
    viewColumns,
    allFields,
    genericLinkFormKey,
    genericLookupKey,
    isFieldInView,
    addLinkFormToView,
    addLookupToView,
  } = options

  // Drag state
  const dragSourceKey = ref<string | null>(null)
  const dragColIndex = ref<number | null>(null)
  const dragOverIndex = ref<number | null>(null)
  const isDraggingFromPanel = ref(false)
  const dragPayload = ref<DragPayload | null>(null)
  const dragMime = 'application/x-sub-table-list-column'

  // --- Drag from left panel to grid ---
  const onFieldDragStart = (e: DragEvent, field: SubTableFieldDTO) => {
    dragSourceKey.value = field.fieldName
    dragPayload.value = { kind: 'field', fieldName: field.fieldName }
    isDraggingFromPanel.value = true
    e.dataTransfer!.effectAllowed = 'copy'
    e.dataTransfer!.setData(dragMime, JSON.stringify(dragPayload.value))
    e.dataTransfer!.setData('text/plain', field.fieldName)
  }

  const onLinkFormDragStart = (e: DragEvent) => {
    dragSourceKey.value = genericLinkFormKey.value
    dragPayload.value = { kind: 'linkForm' }
    isDraggingFromPanel.value = true
    e.dataTransfer!.effectAllowed = 'copy'
    e.dataTransfer!.setData(dragMime, JSON.stringify(dragPayload.value))
    e.dataTransfer!.setData('text/plain', genericLinkFormKey.value)
  }

  const onLookupDragStart = (e: DragEvent) => {
    dragSourceKey.value = genericLookupKey.value
    dragPayload.value = { kind: 'lookup' }
    isDraggingFromPanel.value = true
    e.dataTransfer!.effectAllowed = 'copy'
    e.dataTransfer!.setData(dragMime, JSON.stringify(dragPayload.value))
    e.dataTransfer!.setData('text/plain', genericLookupKey.value)
  }

  const onDragEnd = () => {
    dragSourceKey.value = null
    dragPayload.value = null
    isDraggingFromPanel.value = false
  }

  const onGridDragOver = (e: DragEvent) => {
    if (isDraggingFromPanel.value) {
      e.dataTransfer!.dropEffect = 'copy'
    }
  }

  const onGridDrop = (e: DragEvent) => {
    if (!isDraggingFromPanel.value) return
    let payload = dragPayload.value
    const rawPayload = e.dataTransfer?.getData(dragMime)
    if (!payload && rawPayload) {
      try {
        payload = JSON.parse(rawPayload) as DragPayload
      } catch {
        payload = null
      }
    }

    if (payload?.kind === 'field') {
      const field = allFields.value.find(f => f.fieldName === payload.fieldName)
      if (field && !isFieldInView(payload.fieldName)) {
        emit('update:modelValue', [...viewColumns.value, { ...field, columnType: 'field' }])
        emit('save')
      }
    } else if (payload?.kind === 'linkForm') {
      addLinkFormToView()
    } else if (payload?.kind === 'lookup') {
      addLookupToView()
    }
    onDragEnd()
  }

  // --- Drag to reorder columns ---
  const onColDragStart = (e: DragEvent, index: number) => {
    dragColIndex.value = index
    isDraggingFromPanel.value = false
    e.dataTransfer!.effectAllowed = 'move'
    e.dataTransfer!.setData('text/plain', String(index))
  }

  const onColDragOver = (_e: DragEvent, index: number) => {
    if (dragColIndex.value !== null && dragColIndex.value !== index) {
      dragOverIndex.value = index
    }
  }

  const onColDragLeave = () => { dragOverIndex.value = null }

  const onColDrop = (_e: DragEvent, targetIndex: number) => {
    if (dragColIndex.value !== null && dragColIndex.value !== targetIndex) {
      const arr = [...viewColumns.value]
      const [moved] = arr.splice(dragColIndex.value, 1)
      arr.splice(targetIndex, 0, moved)
      emit('update:modelValue', arr)
      emit('save')
    }
    dragColIndex.value = null
    dragOverIndex.value = null
  }

  const onColDragEnd = () => {
    dragColIndex.value = null
    dragOverIndex.value = null
  }

  return {
    dragSourceKey,
    dragColIndex,
    dragOverIndex,
    isDraggingFromPanel,
    dragPayload,
    onFieldDragStart,
    onLinkFormDragStart,
    onLookupDragStart,
    onDragEnd,
    onGridDragOver,
    onGridDrop,
    onColDragStart,
    onColDragOver,
    onColDragLeave,
    onColDrop,
    onColDragEnd,
  }
}
