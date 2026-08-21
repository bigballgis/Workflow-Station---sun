import { computed } from 'vue'
import type { WritableComputedRef } from 'vue'
import type {
  SubTableListColumnDTO,
} from './types'
import type { PreviewColumn } from '@/components/designer/sub-table-list/SubTablePreviewDialog.vue'

const MOCK_ROW_COUNT = 3

interface UsePortalPreviewOptions {
  viewColumns: WritableComputedRef<SubTableListColumnDTO[]>
  isLinkColumn: (column: SubTableListColumnDTO) => boolean
  isLookupColumn: (column: SubTableListColumnDTO) => boolean
  getLinkText: (column: SubTableListColumnDTO) => string
  getColumnLabel: (column: SubTableListColumnDTO) => string
  getMockValue: (field: SubTableListColumnDTO) => string
}

/** User Portal 双视图（To Do / My Requests）预览：预览弹层的列与行数据。 */
export function usePortalPreview(options: UsePortalPreviewOptions) {
  const {
    viewColumns,
    isLinkColumn,
    isLookupColumn,
    getLinkText,
    getColumnLabel,
    getMockValue,
  } = options

  function cellMockValue(col: SubTableListColumnDTO): string {
    if (isLinkColumn(col)) return getLinkText(col)
    if (isLookupColumn(col)) return 'Lookup'
    return getMockValue(col)
  }

  const previewColumns = computed<PreviewColumn[]>(() =>
    viewColumns.value.map(col => ({
      key: col.fieldName ?? String(col.componentId ?? Math.random()),
      label: getColumnLabel(col),
      mockValues: Array.from({ length: MOCK_ROW_COUNT }, () => cellMockValue(col)),
    }))
  )

  return {
    previewColumns,
  }
}
