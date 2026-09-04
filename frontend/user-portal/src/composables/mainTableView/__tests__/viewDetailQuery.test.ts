import { describe, expect, it } from 'vitest'
import { viewDetailRowQuery } from '../viewDetailQuery'

describe('viewDetailRowQuery', () => {
  it('asks for the issued rowKey instead of using it as a keyword search', () => {
    const rowKey = '944b041c-a781-11f1-bb05-0e9e01a266af|row_id=ATM-DC-PW-TRANS-000030'
    expect(viewDetailRowQuery(rowKey)).toEqual({ page: 0, size: 1, rowKey })
    expect(viewDetailRowQuery(rowKey)).not.toHaveProperty('search')
  })
})
