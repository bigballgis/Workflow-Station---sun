import { describe, expect, it } from 'vitest'
import {
  attachmentOptionValue,
  parseAttachments,
  type EmailAttachmentRef,
} from '../useSendTaskEmailAttachments'

describe('parseAttachments', () => {
  it('parses main, sub and lookup field refs', () => {
    const raw = JSON.stringify([
      { source: 'main', fieldName: 'invoice_file' },
      { source: 'sub', bindingId: 271, fieldName: 'file' },
      { source: 'lookup', lookupField: 'customer', targetField: 'contract_file', tableId: 9 },
    ])
    expect(parseAttachments(raw)).toEqual([
      { source: 'main', fieldName: 'invoice_file' },
      { source: 'sub', bindingId: 271, fieldName: 'file' },
      { source: 'lookup', lookupField: 'customer', targetField: 'contract_file', tableId: 9 },
    ])
  })

  it('drops legacy name/content free-form items', () => {
    const raw = JSON.stringify([{ name: 'a.pdf', content: 'AAAA' }])
    expect(parseAttachments(raw)).toEqual([])
  })

  it('returns empty for invalid json', () => {
    expect(parseAttachments('{')).toEqual([])
    expect(parseAttachments('')).toEqual([])
  })

  it('accepts already-parsed arrays from parsePropertyValue', () => {
    expect(parseAttachments([
      { source: 'main', fieldName: 'file' },
    ])).toEqual([{ source: 'main', fieldName: 'file' }])
  })
})

describe('attachmentOptionValue', () => {
  it('builds stable select values', () => {
    const main: EmailAttachmentRef = { source: 'main', fieldName: 'f1' }
    const lookup: EmailAttachmentRef = {
      source: 'lookup',
      lookupField: 'cust',
      targetField: 'file',
    }
    const sub: EmailAttachmentRef = {
      source: 'sub',
      bindingId: 271,
      fieldName: 'file',
    }
    expect(attachmentOptionValue(main)).toBe('main:f1')
    expect(attachmentOptionValue(lookup)).toBe('lookup:cust@file')
    expect(attachmentOptionValue(sub)).toBe('sub:271:file')
  })
})
