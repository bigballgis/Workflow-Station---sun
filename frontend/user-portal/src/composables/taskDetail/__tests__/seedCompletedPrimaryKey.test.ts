import { describe, expect, it } from 'vitest'
import { seedMissingConfiguredPrimaryKey } from '../useTaskDetailSubTableHydration'

describe('seedMissingConfiguredPrimaryKey', () => {
  it('copies the configured primary key and leaves other process fields alone', () => {
    const target: Record<string, unknown> = { title: 'kept' }
    seedMissingConfiguredPrimaryKey(
      target,
      { primaryKeyFields: ['case_key'] },
      [{ case_key: 'C1', title: 'from-process', id: 'not-configured' }],
    )
    expect(target).toEqual({ title: 'kept', case_key: 'C1' })
  })

  it('does not overwrite a primary key the snapshot already has', () => {
    const target: Record<string, unknown> = { case_key: 'from-snapshot' }
    seedMissingConfiguredPrimaryKey(
      target,
      { primaryKeyFields: ['case_key'] },
      [{ case_key: 'from-process' }],
    )
    expect(target.case_key).toBe('from-snapshot')
  })

  it('uses isPrimaryKey field definitions when primaryKeyFields is empty', () => {
    const target: Record<string, unknown> = {}
    seedMissingConfiguredPrimaryKey(
      target,
      {
        primaryKeyFields: [],
        fieldDefinitions: [{ fieldName: 'record_pk', isPrimaryKey: true }],
      },
      [undefined, { record_pk: 'R1', id: 'not-configured' }],
    )
    expect(target).toEqual({ record_pk: 'R1' })
  })
})
