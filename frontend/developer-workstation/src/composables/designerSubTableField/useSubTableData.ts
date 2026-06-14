import { computed, ref, watch } from 'vue'
import {
  getFilenameFromUrl,
  isUploadColumn,
  normalizeSubTableColumns,
  resolveUploadCellUrl,
} from '@/components/designer/uploadFieldUtils'
import type { SubTableFieldProps } from './types'

/**
 * 子表字段的数据模型：表格行数据、分页、上传文件名缓存与显示列计算。
 * 监听 modelValue 同步行数据，并为上传单元格回填文件名。
 */
export function useSubTableData(props: SubTableFieldProps) {
  const loading = ref(false)
  const tableData = ref<any[]>([])
  const currentPage = ref(1)
  const total = ref(0)
  const uploadNames = ref<Record<string, string>>({})

  // 计算属性：显示的列（FILE / file 字段归一为 upload，便于文件名展示与下载）
  const displayColumns = computed(() =>
    normalizeSubTableColumns(props.config.columns || [], tableData.value),
  )

  // 监听 modelValue 变化
  watch(() => props.modelValue, (newVal) => {
    if (newVal) {
      tableData.value = [...newVal]
      total.value = newVal.length
      const nextNames: Record<string, string> = {}
      newVal.forEach((row: Record<string, unknown>, rowIndex: number) => {
        for (const col of displayColumns.value) {
          if (!isUploadColumn(col, row[col.field])) continue
          const url = resolveUploadCellUrl(row[col.field])
          if (!url) continue
          nextNames[`${rowIndex}_${col.field}`] = getFilenameFromUrl(String(url))
        }
      })
      uploadNames.value = nextNames
    }
  }, { immediate: true, deep: true })

  // 分页变化
  function handlePageChange(page: number) {
    currentPage.value = page
  }

  // 暴露方法
  const exposed = {
    getData: () => tableData.value,
    setData: (data: any[]) => {
      tableData.value = [...data]
      total.value = data.length
    },
    refresh: () => {},
  }

  return {
    loading,
    tableData,
    currentPage,
    total,
    uploadNames,
    displayColumns,
    handlePageChange,
    exposed,
  }
}
