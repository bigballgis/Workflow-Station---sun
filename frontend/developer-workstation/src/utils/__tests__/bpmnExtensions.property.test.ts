import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import { parsePropertyValue, stringifyPropertyValue } from '../bpmnExtensions'

describe('bpmnExtensions property tests', () => {
  describe('parsePropertyValue', () => {
    it('should parse boolean strings correctly', () => {
      expect(parsePropertyValue('true')).toBe(true)
      expect(parsePropertyValue('false')).toBe(false)
    })

    it('should parse numeric strings correctly', () => {
      fc.assert(
        fc.property(fc.integer(), (num) => {
          const result = parsePropertyValue(String(num))
          return result === num
        })
      )
    })

    it('should parse float strings correctly', () => {
      fc.assert(
        fc.property(fc.float({ noNaN: true, noDefaultInfinity: true }), (num) => {
          const result = parsePropertyValue(String(num))
          return Math.abs(result - num) < 0.0001 || (num === 0 && result === 0)
        })
      )
    })

    it('should return empty string for empty input', () => {
      expect(parsePropertyValue('')).toBe('')
    })

    it('should return null/undefined as-is', () => {
      expect(parsePropertyValue(null as any)).toBe(null)
      expect(parsePropertyValue(undefined as any)).toBe(undefined)
    })

    it('should parse JSON objects correctly', () => {
      fc.assert(
        fc.property(
          fc.record({
            key1: fc.string(),
            key2: fc.integer(),
            key3: fc.boolean()
          }),
          (obj) => {
            const jsonStr = JSON.stringify(obj)
            const result = parsePropertyValue(jsonStr)
            return JSON.stringify(result) === jsonStr
          }
        )
      )
    })

    it('should parse JSON arrays correctly', () => {
      fc.assert(
        fc.property(fc.array(fc.integer()), (arr) => {
          const jsonStr = JSON.stringify(arr)
          const result = parsePropertyValue(jsonStr)
          return JSON.stringify(result) === jsonStr
        })
      )
    })

    it('should return non-JSON strings as-is', () => {
      fc.assert(
        fc.property(
          fc.string().filter(s => !s.startsWith('{') && !s.startsWith('[') && s !== 'true' && s !== 'false' && isNaN(Number(s))),
          (str) => {
            const result = parsePropertyValue(str)
            return result === str
          }
        )
      )
    })
  })

  describe('stringifyPropertyValue', () => {
    it('should stringify booleans correctly', () => {
      expect(stringifyPropertyValue(true)).toBe('true')
      expect(stringifyPropertyValue(false)).toBe('false')
    })

    it('should stringify numbers correctly', () => {
      fc.assert(
        fc.property(fc.integer(), (num) => {
          const result = stringifyPropertyValue(num)
          return result === String(num)
        })
      )
    })

    it('should stringify objects as JSON', () => {
      fc.assert(
        fc.property(
          fc.record({
            key1: fc.string(),
            key2: fc.integer()
          }),
          (obj) => {
            const result = stringifyPropertyValue(obj)
            return result === JSON.stringify(obj)
          }
        )
      )
    })

    it('should stringify arrays as JSON', () => {
      fc.assert(
        fc.property(fc.array(fc.integer()), (arr) => {
          const result = stringifyPropertyValue(arr)
          return result === JSON.stringify(arr)
        })
      )
    })

    it('should return empty string for null/undefined', () => {
      expect(stringifyPropertyValue(null)).toBe('')
      expect(stringifyPropertyValue(undefined)).toBe('')
    })

    it('should stringify strings as-is', () => {
      fc.assert(
        fc.property(fc.string(), (str) => {
          const result = stringifyPropertyValue(str)
          return result === str
        })
      )
    })
  })

  describe('roundtrip property', () => {
    it('should roundtrip booleans', () => {
      fc.assert(
        fc.property(fc.boolean(), (bool) => {
          const stringified = stringifyPropertyValue(bool)
          const parsed = parsePropertyValue(stringified)
          return parsed === bool
        })
      )
    })

    it('should roundtrip integers', () => {
      fc.assert(
        fc.property(fc.integer(), (num) => {
          const stringified = stringifyPropertyValue(num)
          const parsed = parsePropertyValue(stringified)
          return parsed === num
        })
      )
    })

    it('should roundtrip simple objects', () => {
      fc.assert(
        fc.property(
          fc.record({
            name: fc.string(),
            count: fc.integer(),
            active: fc.boolean()
          }),
          (obj) => {
            const stringified = stringifyPropertyValue(obj)
            const parsed = parsePropertyValue(stringified)
            return JSON.stringify(parsed) === JSON.stringify(obj)
          }
        )
      )
    })

    it('should roundtrip arrays of primitives', () => {
      fc.assert(
        fc.property(
          fc.array(fc.oneof(fc.string(), fc.integer(), fc.boolean())),
          (arr) => {
            const stringified = stringifyPropertyValue(arr)
            const parsed = parsePropertyValue(stringified)
            return JSON.stringify(parsed) === JSON.stringify(arr)
          }
        )
      )
    })
  })
})
