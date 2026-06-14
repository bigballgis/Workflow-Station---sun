import { ElMessage } from 'element-plus'
import { functionUnitApi } from '@/api/functionUnit'

interface UseTableNamingOptions {
  functionUnitId: number
  store: { tables: Array<{ id: number; tableName: string }> }
  t: (key: string, params?: Record<string, unknown>) => string
}

/**
 * Shared technical-name primitives for TableDesigner: identifier regex
 * validation, existing-name lookups, and the backend availability check.
 */
export function useTableNaming(options: UseTableNamingOptions) {
  const { functionUnitId, store, t } = options

  const NAME_REGEX = /^[a-zA-Z][a-zA-Z0-9_]*$/

  function validateName(name: string): boolean {
    return NAME_REGEX.test(name)
  }

  function existingTableNames(excludeId?: number): string[] {
    return store.tables
      .filter(t => t.id !== excludeId)
      .map(t => t.tableName)
      .filter(Boolean)
  }

  async function assertTableNameAvailable(tableName: string, excludeTableId?: number): Promise<boolean> {
    const trimmed = tableName?.trim()
    if (!trimmed) return false
    try {
      const res = await functionUnitApi.checkTableNameAvailable(functionUnitId, trimmed, excludeTableId)
      if (!res?.data?.available) {
        ElMessage.warning(t('table.nameAlreadyExists', { name: trimmed }))
        return false
      }
      return true
    } catch {
      ElMessage.error(t('common.error'))
      return false
    }
  }

  return {
    validateName,
    existingTableNames,
    assertTableNameAvailable,
  }
}
