import { describe, expect, it } from 'vitest'
import type { FormDefinition, TableDefinition } from '@/api/functionUnit'
import { buildSavedFormPreviewItems } from '@/utils/savedFormPreviewBuilder'
import {
  RECORD_NOTE_PREVIEW_TYPE,
  retypeRecordNoteRulesForPreview,
} from '../recordNotePreviewRules'

/**
 * Preview must show the portal-shaped Notes panel, not the designer canvas placeholder.
 * Two mechanisms carry that: the preview builders lift `recordNote` into its own item, and
 * surfaces rendering a whole rule through one form-create instance retype the node.
 */
describe('Record Note in Preview', () => {
  const tables = [{
    id: 30,
    tableName: 'main_table',
    tableDisplayName: 'Main Table',
    tableType: 'MAIN',
    fieldDefinitions: [{ fieldName: 'remark', dataType: 'VARCHAR' }],
  }] as TableDefinition[]

  function buildItems(rule: unknown[]) {
    const form = {
      id: 10,
      formName: 'Main Form',
      formType: 'MAIN',
      configJson: { rule, subForms: {} },
      tableBindings: [],
    } as unknown as FormDefinition
    return buildSavedFormPreviewItems({ form, tables, t: (key) => key })
  }

  it('lifts recordNote out of the fields segment into its own preview item', () => {
    const items = buildItems([
      { type: 'input', field: 'remark', title: 'Remark', props: {} },
      { type: 'recordNote', title: 'Record Note', props: { scope: 'TABLE', panelTitle: 'Notes', pageSize: 3 } },
    ])

    const note = items.find((i) => i.kind === 'recordNote')
    expect(note).toBeDefined()
    expect(note && note.kind === 'recordNote' && note.config).toMatchObject({
      scope: 'TABLE',
      panelTitle: 'Notes',
      pageSize: 3,
    })

    // The placeholder renders only when the node stays inside a form-create segment.
    const fields = items.filter((i) => i.kind === 'fields')
    expect(JSON.stringify(fields)).not.toContain('recordNote')
  })

  it('keeps field segments around a recordNote intact', () => {
    const items = buildItems([
      { type: 'input', field: 'a', title: 'A', props: {} },
      { type: 'recordNote', title: 'Record Note', props: { scope: 'TABLE' } },
      { type: 'input', field: 'b', title: 'B', props: {} },
    ])

    expect(items.map((i) => i.kind)).toEqual(['fields', 'recordNote', 'fields'])
  })

  it('retypes recordNote nodes for single-instance form-create surfaces', () => {
    const rule: any[] = [
      {
        type: 'el-row',
        children: [
          { type: 'recordNote', props: { scope: 'RECORD', panelTitle: 'Row notes', allowAttachment: false } },
        ],
      },
    ]

    retypeRecordNoteRulesForPreview(rule)

    const node = rule[0].children[0]
    expect(node.type).toBe(RECORD_NOTE_PREVIEW_TYPE)
    // The preview component takes one `config` object, mirroring the portal's RecordNoteField.
    expect(node.props).toEqual({
      config: { scope: 'RECORD', panelTitle: 'Row notes', allowAttachment: false },
    })
  })

  it('leaves non-recordNote rules untouched', () => {
    const rule: any[] = [{ type: 'input', field: 'a', props: { placeholder: 'x' } }]
    retypeRecordNoteRulesForPreview(rule)
    expect(rule[0]).toEqual({ type: 'input', field: 'a', props: { placeholder: 'x' } })
  })
})
