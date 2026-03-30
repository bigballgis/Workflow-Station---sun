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
import { computeDiffRows } from '../components/snapshotDiffHelpers'

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
            JSON.stringify(snapshotValues[key]) !== JSON.stringify(liveValues[key])
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
})
