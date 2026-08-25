/**
 * Property 18: Snapshot diff detection (frontend)
 * **Validates: Requirements 10.2, 10.3**
 *
 * For any completed Task_Instance, and for each field in the Task Form,
 * if the snapshot value differs from the current live process variable value,
 * the diff should be flagged. The number of flagged diffs should equal the
 * number of fields where snapshot.fieldValues[key] != liveValues[key].
 *
 * Feature: process-task-form-separation, Property 18: Snapshot diff detection (frontend)
 */
import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import type { FormField } from '../components/formRendererHelpers'
import { computeDiffRows, formatSnapshotDisplayValue, isSnapshotValueChanged } from '../components/snapshotDiffHelpers'

describe('Property 18: Snapshot diff detection (frontend)', () => {
  // Generator for simple scalar values (string, number, boolean, null)
  const scalarArb = fc.oneof(
    fc.string({ minLength: 0, maxLength: 50 }),
    fc.integer({ min: -10000, max: 10000 }),
    fc.boolean(),
    fc.constant(null)
  )

  // Generator for field keys
  const fieldKeyArb = fc.stringMatching(/^[a-zA-Z][a-zA-Z0-9_]{0,15}$/)

  // Generator for a set of unique field keys
  const fieldKeysArb = fc.uniqueArray(fieldKeyArb, { minLength: 1, maxLength: 10 })

  it('diff count equals number of fields where snapshot != live', () => {
    fc.assert(
      fc.property(
        fieldKeysArb,
        fc.array(scalarArb, { minLength: 1, maxLength: 10 }),
        fc.array(scalarArb, { minLength: 1, maxLength: 10 }),
        (fieldKeys, snapshotVals, liveVals) => {
          // Build fields, snapshot, and live values
          const fields: FormField[] = fieldKeys.map(key => ({
            key,
            label: key,
            type: 'text',
          }))

          const snapshotValues: Record<string, unknown> = {}
          const liveValues: Record<string, unknown> = {}

          fieldKeys.forEach((key, i) => {
            snapshotValues[key] = snapshotVals[i % snapshotVals.length]
            liveValues[key] = liveVals[i % liveVals.length]
          })

          const rows = computeDiffRows(snapshotValues, liveValues, fields)

          // Count expected diffs
          const expectedDiffCount = fieldKeys.filter(key =>
            isSnapshotValueChanged(snapshotValues[key], liveValues[key])
          ).length

          const actualDiffCount = rows.filter(r => r.changed).length

          expect(actualDiffCount).toBe(expectedDiffCount)
        }
      ),
      { numRuns: 100 }
    )
  })

  it('identical snapshot and live values produce zero diffs', () => {
    fc.assert(
      fc.property(
        fieldKeysArb,
        fc.array(scalarArb, { minLength: 1, maxLength: 10 }),
        (fieldKeys, values) => {
          const fields: FormField[] = fieldKeys.map(key => ({
            key,
            label: key,
            type: 'text',
          }))

          const sharedValues: Record<string, unknown> = {}
          fieldKeys.forEach((key, i) => {
            sharedValues[key] = values[i % values.length]
          })

          const rows = computeDiffRows(sharedValues, { ...sharedValues }, fields)
          const diffCount = rows.filter(r => r.changed).length

          expect(diffCount).toBe(0)
        }
      ),
      { numRuns: 100 }
    )
  })

  it('each row corresponds to exactly one field', () => {
    fc.assert(
      fc.property(
        fieldKeysArb,
        fc.array(scalarArb, { minLength: 1, maxLength: 10 }),
        fc.array(scalarArb, { minLength: 1, maxLength: 10 }),
        (fieldKeys, snapshotVals, liveVals) => {
          const fields: FormField[] = fieldKeys.map(key => ({
            key,
            label: key,
            type: 'text',
          }))

          const snapshotValues: Record<string, unknown> = {}
          const liveValues: Record<string, unknown> = {}
          fieldKeys.forEach((key, i) => {
            snapshotValues[key] = snapshotVals[i % snapshotVals.length]
            liveValues[key] = liveVals[i % liveVals.length]
          })

          const rows = computeDiffRows(snapshotValues, liveValues, fields)

          // Number of rows should equal number of fields
          expect(rows.length).toBe(fieldKeys.length)

          // Each row key should match a field key
          const rowKeys = rows.map(r => r.key)
          expect(rowKeys).toEqual(fieldKeys)
        }
      ),
      { numRuns: 100 }
    )
  })

  it('omits sub-table placeholders and layout widgets from snapshot rows', () => {
    const rows = computeDiffRows(
      { case_number: 'ATM-DC-PW-000002', '__subTable_1127': [] },
      { case_number: 'ATM-DC-PW-000002' },
      [
        { key: 'case_number', label: 'Case Number', type: 'text' },
        { key: '__subTable_1127', label: '', type: 'subTable', _bindingId: 1127 },
        { key: '__subTable_1128', label: '__subTable_1128', type: 'input' },
        {
          key: '__layout_card_1',
          label: 'Case Info',
          type: 'card',
          children: [{ key: 'card_number', label: 'Card Number', type: 'text' }],
        },
      ],
    )
    expect(rows.map(r => r.key)).toEqual(['case_number', 'card_number'])
  })

  it('formats lookup and dictionary objects as display names instead of JSON', () => {
    expect(formatSnapshotDisplayValue({
      id: 'Case Submission',
      stage_code: 'CS',
      stage_name: 'Case Submission',
    })).toBe('Case Submission')
    expect(formatSnapshotDisplayValue({
      id: 'hmdc-st-cs-open',
      stage_code: 'CS',
      status_name: 'Open',
    })).toBe('Open')
    expect(formatSnapshotDisplayValue({
      id: 'hmdc-dd-urge-normal',
      dropdown_name: 'Normal',
      dropdown_category: 'Urge Type',
    })).toBe('Normal')
    expect(formatSnapshotDisplayValue(
      { id: 'hmdc-st-cs-open', status_name: 'Open' },
      {
        key: 'case_status',
        label: 'Case Status',
        type: 'lookup',
        _lookupSelectedDisplayField: 'status_name',
        _lookupDisplayFields: ['status_name'],
      } as FormField,
    )).toBe('Open')
    expect(formatSnapshotDisplayValue({
      id: 'hmdc-corr-type-cust',
      objectives: 'Correspondence type',
      standardizations: 'Customer Notification',
    })).toBe('Customer Notification')
  })

  it('formats upload URLs as the original file name, matching Change History', () => {
    expect(formatSnapshotDisplayValue(
      '/api/v1/upload/files/bc7a8506-aeb4-428a-881e-fe6887b65ed7.jpg?originalName=MSI_MEG_GODLIKE.jpg',
    )).toBe('MSI_MEG_GODLIKE.jpg')
    expect(formatSnapshotDisplayValue(
      '/api/v1/upload/files/336fd6f4.jpg?originalName=lilong.JPG',
    )).toBe('lilong.JPG')
  })

  it('marks Changed only when the displayed text differs, not extra lookup JSON keys', () => {
    const field: FormField = { key: 'case_stage', label: 'Case Stage', type: 'lookup' }
    expect(isSnapshotValueChanged(
      { id: 'a', stage_name: 'Investigation' },
      { id: 'a', stage_name: 'Investigation', extra: true },
      field,
    )).toBe(false)
    expect(isSnapshotValueChanged(
      { stage_name: 'Case Submission' },
      { stage_name: 'Investigation' },
      field,
    )).toBe(true)
    expect(isSnapshotValueChanged(null, undefined)).toBe(false)

    const rows = computeDiffRows(
      { case_stage: { id: 's1', stage_name: 'Investigation' } },
      { case_stage: { id: 's1', stage_name: 'Investigation', extra: 1 } },
      [field],
    )
    expect(rows[0]?.changed).toBe(false)
  })
})
