import { describe, expect, it } from 'vitest'
import {
  copySubFormCanvasProps,
  mapSubFormRuleToDialogColumns,
  SUB_FORM_CANVAS_COLUMN_PROP_KEYS,
} from '../subFormCanvasColumns'
import type { SubFormColumnLookupContext } from '../subFormCanvasColumns'
import { newUploadCountProps, resolveUploadMaxFiles } from '@platform-shared/upload/uploadFieldValue'

const ctx: SubFormColumnLookupContext = { lookupDbConfigs: {}, relationViewConfigs: {} }

/**
 * Portal sub-table Add/Edit maps canvas rules onto dialog columns, then
 * FormUploadDropZone uses resolveUploadMaxFiles(col.props). Dropping maxFiles
 * made Single (maxFiles:1 + leftover multiple:false/limit:1) look like the
 * unconfigured default of 10, so the sub-form accepted many files while the
 * main form (applyUploadPropsFromRule) stayed at 1.
 */
describe('mapSubFormRuleToDialogColumns — designer Single upload', () => {
  it('keeps maxFiles:1 so the dialog stays single-file', () => {
    const [col] = mapSubFormRuleToDialogColumns(
      [{
        type: 'advancedUpload',
        field: 'line_file',
        title: 'Line File',
        props: { action: '/api/v1/upload', ...newUploadCountProps() },
      }],
      ctx,
    )
    expect(col.type).toBe('upload')
    expect(col.props?.maxFiles).toBe(1)
    expect(resolveUploadMaxFiles(col.props)).toBe(1)
  })

  it('keeps an explicit Multi count', () => {
    const [col] = mapSubFormRuleToDialogColumns(
      [{
        type: 'advancedUpload',
        field: 'line_file',
        title: 'Line File',
        props: { action: '/api/v1/upload', maxFiles: 4, limit: 4, multiple: true },
      }],
      ctx,
    )
    expect(col.props?.maxFiles).toBe(4)
    expect(resolveUploadMaxFiles(col.props)).toBe(4)
  })
})

describe('copySubFormCanvasProps', () => {
  it('copies every designer key that is set, including upload flags', () => {
    const source: Record<string, unknown> = {
      maxFiles: 1,
      canNotDownload: true,
      boundSubTableBindingId: 12,
      ignored: 'no',
    }
    const copied = copySubFormCanvasProps(source)
    expect(copied.maxFiles).toBe(1)
    expect(copied.canNotDownload).toBe(true)
    expect(copied.boundSubTableBindingId).toBe(12)
    expect(copied.ignored).toBeUndefined()
    for (const key of SUB_FORM_CANVAS_COLUMN_PROP_KEYS) {
      if (source[key] !== undefined) expect(copied[key]).toBe(source[key])
    }
  })
})
