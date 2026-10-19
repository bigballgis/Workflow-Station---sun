import { describe, expect, it } from 'vitest'
import { createBatchPreviewKeyAllocator } from '../batchPreviewKeyAllocator'

describe('local batch preview PK formatting', () => {
  it('preserves UUID, increment and prefixed sequence formats without any server calls', async () => {
    const allocate = createBatchPreviewKeyAllocator({
      1: [{ fieldName: 'token', isPrimaryKey: true, pkGeneration: { strategy: 'uuid' } }],
      2: [{ fieldName: 'code', isPrimaryKey: true, pkGeneration: { strategy: 'prefixedSequence', prefix: 'S-', padWidth: 3, startValue: 8 } }],
      3: [{ fieldName: 'sequence', isPrimaryKey: true, pkGeneration: { strategy: 'autoIncrement', startValue: 4 } }],
    }, { 2: [{ code: 'S-008' }] })
    const uuids = await allocate({ tableId: 1, fieldName: 'token', count: 2 })
    expect(uuids).toHaveLength(2); expect(new Set(uuids).size).toBe(2)
    expect(uuids.every(value => /^[0-9a-f-]{36}$/.test(value))).toBe(true)
    expect(await allocate({ tableId: 2, fieldName: 'code', count: 2 })).toEqual(['S-009', 'S-010'])
    expect(await allocate({ tableId: 3, fieldName: 'sequence', count: 2 })).toEqual(['4', '5'])
  })
  it('uses the existing calendar/custom preview format rather than a UUID replacement', async () => {
    const allocate = createBatchPreviewKeyAllocator({ 1: [
      { fieldName: 'dated', isPrimaryKey: true, pkGeneration: { strategy: 'dailyDateSequence', padWidth: 4 } },
      { fieldName: 'custom', isPrimaryKey: true, pkGeneration: { strategy: 'customFormat', format: 'PRE-{SEQNUM:4}', startValue: 11 } },
    ] }, {})
    expect((await allocate({ tableId: 1, fieldName: 'dated' }))[0]).toMatch(/^\d{8}0001$/)
    expect(await allocate({ tableId: 1, fieldName: 'custom' })).toEqual(['PRE-0011'])
  })
  it('rejects manual, non-PK or unknown targets instead of allocating a guessed identity', async () => {
    const allocate = createBatchPreviewKeyAllocator({ 1: [
      { fieldName: 'manual', isPrimaryKey: true, pkGeneration: { strategy: 'manual' } },
      { fieldName: 'business' },
    ] }, {})
    for (const fieldName of ['manual', 'business', 'absent']) {
      await expect(allocate({ tableId: 1, fieldName })).rejects.toThrow('form.batchFormInvalid')
    }
    await expect(allocate({ tableId: 1, fieldName: 'manual', count: 201 })).rejects.toThrow()
  })
})
