import { describe, expect, it } from 'vitest'
import {
  flattenFilterConditions,
  parseFilterConfigToEditorRoot,
  removeFlattenedConditionAt,
  serializeFilterEditorRoot,
} from '../mainTableViewFilter'

describe('mainTableViewFilter', () => {
  it('parses legacy flat conditions as root AND group', () => {
    const root = parseFilterConfigToEditorRoot({
      conditions: [{ fieldName: 'status', operator: 'eq', value: 'Active' }],
    })
    expect(root.logic).toBe('and')
    expect(root.conditions).toHaveLength(1)
    expect(root.groups).toHaveLength(0)
  })

  it('serializes nested OR group inside AND root', () => {
    const root = parseFilterConfigToEditorRoot({
      logic: 'and',
      conditions: [{ fieldName: 'a', operator: 'eq', value: '1' }],
      groups: [
        {
          logic: 'or',
          conditions: [
            { fieldName: 'b', operator: 'eq', value: '2' },
            { fieldName: 'c', operator: 'eq', value: '3' },
          ],
        },
      ],
    })
    const serialized = serializeFilterEditorRoot(root)
    expect(serialized.logic).toBe('and')
    expect(serialized.groups?.[0].logic).toBe('or')
    expect(serialized.groups?.[0].conditions).toHaveLength(2)
    expect(flattenFilterConditions(root)).toHaveLength(3)
  })

  it('removeFlattenedConditionAt removes by flattened index', () => {
    const root = parseFilterConfigToEditorRoot({
      conditions: [
        { fieldName: 'a', operator: 'eq', value: '1' },
        { fieldName: 'b', operator: 'eq', value: '2' },
      ],
    })
    expect(removeFlattenedConditionAt(root, 1)).toBe(true)
    expect(flattenFilterConditions(root)).toHaveLength(1)
    expect(flattenFilterConditions(root)[0].fieldName).toBe('a')
  })
})
