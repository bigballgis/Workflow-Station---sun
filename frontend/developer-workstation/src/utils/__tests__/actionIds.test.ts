import { describe, expect, it } from 'vitest'
import { parseActionIds } from '../actionIds'

describe('BPMN Action ID formats', () => {
  it.each([
    [[12, '22'], [12, '22']],
    ['[" 12 ",22]', ['12',22]],
    [[' legacy-save ',22], ['legacy-save',22]],
    ['[12,"22"]', [12, '22']],
    ['[legacy-save,reject]', ['legacy-save', 'reject']],
    [' legacy-save, reject ', ['legacy-save', 'reject']],
    ['[ 12, legacy-save, 22 ]', ['12', 'legacy-save', '22']],
    ['[12,12,"12"]', [12, 12, '12']],
    [undefined, []], [null, []], ['', []], ['  ', []], ['[]', []],
  ])('preserves the sequence and identity in %j', (value, expected) => {
    expect(parseActionIds(value)).toEqual(expected)
  })
  it.each([{}, true, 12, '12', 'true', '[reject,,save]', '[reject', 'reject]', '[{"id":12}]', '[null]', '[""]', '["  "]'])('rejects malformed bindings %j instead of hiding them', value => {
    expect(() => parseActionIds(value)).toThrow('Invalid action ID list')
  })
})
