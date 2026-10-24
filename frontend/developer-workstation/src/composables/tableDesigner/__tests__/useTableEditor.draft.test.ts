import { describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { useTableEditor } from '../useTableEditor'
import type { TableDefinition } from '@/api/functionUnit'

vi.mock('element-plus', () => ({ ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() }, ElMessageBox: { alert: vi.fn() } }))

describe('table editor draft isolation', () => {
  it('isolates nested field configuration both on initial selection and after a save response', async () => {
    const serverTable: TableDefinition = {
      id: 1, tableName: 'test_items', tableType: 'SUB',
      fieldDefinitions: [{ fieldName: 'id', dataType: 'VARCHAR', nullable: false, isPrimaryKey: true, pkGeneration: { strategy: 'uuid' } }],
    }
    const selectedTable = ref<TableDefinition | null>(null)
    const editor = useTableEditor({
      functionUnitId: 1, selectedTable, tableNameTouched: ref(false),
      store: { updateTable: vi.fn().mockResolvedValue(serverTable) },
      validateName: () => true, existingTableNames: () => [], assertTableNameAvailable: async () => true,
      t: key => key,
    })
    editor.handleSelectTable(serverTable)
    selectedTable.value!.fieldDefinitions[0].pkGeneration!.strategy = 'manual'
    expect(serverTable.fieldDefinitions[0].pkGeneration!.strategy).toBe('uuid')
    expect(await editor.handleSaveTable()).toBe(true)
    selectedTable.value!.fieldDefinitions[0].pkGeneration!.strategy = 'manual'
    expect(serverTable.fieldDefinitions[0].pkGeneration!.strategy).toBe('uuid')
  })
})
