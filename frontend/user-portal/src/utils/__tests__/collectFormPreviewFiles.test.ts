import { describe, expect, it } from 'vitest'
import { collectFormPreviewFiles } from '../collectFormPreviewFiles'
import type { FormField } from '@/components/formRendererHelpers'

describe('collectFormPreviewFiles', () => {
  it('walks main upload fields then sub-table rows then nested sub-tables', () => {
    const fields: FormField[] = [
      { key: 'cover', label: 'Cover', type: 'upload' },
      { key: 'lines', label: 'Lines', type: 'subTable', _bindingId: 10 },
    ]
    const files = collectFormPreviewFiles({
      fields,
      formData: {
        cover: '/upload/files/cover.pdf?originalName=cover.pdf',
      },
      bindings: [
        {
          bindingId: 10,
          tableName: 'lines',
          columns: [{ field: 'doc', type: 'upload' }],
          data: [
            {
              doc: '/upload/files/a.pdf?originalName=a.pdf',
              __subTables__: {
                20: [{ nested: '/upload/files/n.pdf?originalName=nested.pdf' }],
              },
            },
          ],
          formFields: [{ key: 'kids', label: 'Kids', type: 'subTable', _bindingId: 20 }],
        },
        {
          bindingId: 20,
          tableName: 'kids',
          columns: [{ field: 'nested', type: 'upload' }],
          data: [],
        },
      ],
    })
    expect(files.map((f) => f.name)).toEqual(['cover.pdf', 'a.pdf', 'nested.pdf'])
  })

  it('skips zip and empty urls', () => {
    const files = collectFormPreviewFiles({
      fields: [{ key: 'pack', label: 'Pack', type: 'upload' }],
      formData: { pack: '/upload/files/x.zip?originalName=pack.zip' },
    })
    expect(files).toEqual([])
  })
})
