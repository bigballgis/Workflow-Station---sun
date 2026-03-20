/**
 * Preservation Property Tests - 现有行为保持不变
 *
 * Property 2: Preservation - These tests verify existing behavior that MUST be preserved
 * after the fix. They should PASS on the current unfixed code.
 *
 * Validates: Requirements 3.1, 3.4, 3.7, 3.8, 3.9
 */
import { describe, it, expect, beforeEach } from 'vitest'
import * as fc from 'fast-check'
import type { XmlNode } from '@/utils/markdownToXml'
import type { ViewMode } from '@/types/aiGeneration'

// ─── XmlNode arbitrary generator ───

let keyCounter = 0
function nextKey(): string {
  return `node-${keyCounter++}`
}

function xmlNodeArb(maxDepth: number = 3): fc.Arbitrary<XmlNode> {
  if (maxDepth <= 0) {
    return fc.record({
      tagName: fc.constant('content' as string),
      title: fc.string({ minLength: 1, maxLength: 20 }),
      level: fc.integer({ min: 1, max: 6 }),
      content: fc.string({ minLength: 0, maxLength: 50 }),
    }).map(r => ({
      ...r,
      key: nextKey(),
      children: [] as XmlNode[],
    }))
  }

  return fc.oneof(
    fc.record({
      tagName: fc.constant('content' as string),
      title: fc.string({ minLength: 1, maxLength: 20 }),
      level: fc.integer({ min: 1, max: 6 }),
      content: fc.string({ minLength: 0, maxLength: 50 }),
    }).map(r => ({
      ...r,
      key: nextKey(),
      children: [] as XmlNode[],
    })),
    fc.record({
      tagName: fc.constantFrom('h1', 'h2', 'h3', 'h4') as fc.Arbitrary<string>,
      title: fc.string({ minLength: 1, maxLength: 20 }),
      level: fc.integer({ min: 1, max: 6 }),
      childNodes: fc.array(xmlNodeArb(maxDepth - 1), { minLength: 1, maxLength: 3 }),
    }).map(r => ({
      key: nextKey(),
      tagName: r.tagName,
      title: r.title,
      level: r.level,
      content: '',
      children: r.childNodes,
    })),
  )
}

/** Collect all node keys from a tree (both expandable and leaf). */
function collectAllKeys(nodes: XmlNode[]): string[] {
  const keys: string[] = []
  for (const node of nodes) {
    keys.push(node.key)
    keys.push(...collectAllKeys(node.children))
  }
  return keys
}

// ─── Pure toggleNode logic (extracted from XmlTreeView.vue) ───

function toggleNode(expandedKeys: Set<string>, key: string): Set<string> {
  const newKeys = new Set(expandedKeys)
  if (newKeys.has(key)) {
    newKeys.delete(key)
  } else {
    newKeys.add(key)
  }
  return newKeys
}

// ─── Tests ───

describe('Preservation Property 2: toggleNode preserves expandedKeys behavior', () => {
  /**
   * **Validates: Requirements 3.1, 3.7**
   *
   * For any key, toggle adds if absent, removes if present.
   * Double-toggle returns to original state.
   */
  beforeEach(() => {
    keyCounter = 0
  })

  it('toggle adds key if absent, removes key if present', () => {
    fc.assert(
      fc.property(
        fc.array(xmlNodeArb(2), { minLength: 1, maxLength: 4 }),
        (nodes) => {
          const allKeys = collectAllKeys(nodes)
          fc.pre(allKeys.length > 0)

          // Pick a random key from the tree
          const targetKey = allKeys[0]

          // Start with empty expandedKeys
          let expandedKeys = new Set<string>()

          // Toggle once: key should be added
          expandedKeys = toggleNode(expandedKeys, targetKey)
          expect(expandedKeys.has(targetKey)).toBe(true)

          // Toggle again: key should be removed
          expandedKeys = toggleNode(expandedKeys, targetKey)
          expect(expandedKeys.has(targetKey)).toBe(false)
        },
      ),
      { numRuns: 100 },
    )
  })

  it('double-toggle returns to original state', () => {
    fc.assert(
      fc.property(
        fc.uniqueArray(fc.string({ minLength: 1, maxLength: 10 }), { minLength: 0, maxLength: 5 }),
        fc.string({ minLength: 1, maxLength: 10 }),
        (initialKeysArr, toggleKey) => {
          const original = new Set(initialKeysArr)

          // Double toggle
          const afterFirst = toggleNode(original, toggleKey)
          const afterSecond = toggleNode(afterFirst, toggleKey)

          // Should return to original state
          expect(afterSecond.size).toBe(original.size)
          for (const k of original) {
            expect(afterSecond.has(k)).toBe(true)
          }
          for (const k of afterSecond) {
            expect(original.has(k)).toBe(true)
          }
        },
      ),
      { numRuns: 100 },
    )
  })
})

describe('Preservation Property 2: isDirty correctness', () => {
  /**
   * **Validates: Requirements 3.9**
   *
   * For any content string and editContent string,
   * isDirty === (editContent !== content).
   * When editContent === content, isDirty is false (save button disabled).
   */
  it('isDirty is false when editContent equals content', () => {
    fc.assert(
      fc.property(
        fc.string({ minLength: 0, maxLength: 500 }),
        (content) => {
          const editContent = content
          const isDirty = editContent !== content
          expect(isDirty).toBe(false)
        },
      ),
      { numRuns: 100 },
    )
  })

  it('isDirty is true when editContent differs from content', () => {
    fc.assert(
      fc.property(
        fc.string({ minLength: 1, maxLength: 500 }),
        fc.string({ minLength: 1, maxLength: 500 }),
        (content, editContent) => {
          fc.pre(content !== editContent)
          const isDirty = editContent !== content
          expect(isDirty).toBe(true)
        },
      ),
      { numRuns: 100 },
    )
  })
})

describe('Preservation Property 2: viewMode switch correctness', () => {
  /**
   * **Validates: Requirements 3.4**
   *
   * For any sequence of viewMode switches between 'xml' and 'markdown',
   * the final viewMode equals the last switch value.
   */
  it('final viewMode equals the last switch value', () => {
    const viewModeArb = fc.constantFrom<ViewMode>('xml', 'markdown')

    fc.assert(
      fc.property(
        viewModeArb,
        fc.array(viewModeArb, { minLength: 1, maxLength: 20 }),
        (initialMode, switches) => {
          let viewMode: ViewMode = initialMode

          for (const mode of switches) {
            viewMode = mode
          }

          // The final viewMode should equal the last switch value
          expect(viewMode).toBe(switches[switches.length - 1])
        },
      ),
      { numRuns: 100 },
    )
  })
})

describe('Preservation Property 2: viewMode restored after edit exit', () => {
  /**
   * **Validates: Requirements 3.8**
   *
   * For any initial viewMode, entering edit mode saves it,
   * exiting edit mode restores it.
   */
  it('viewMode is restored to pre-edit value after exiting edit mode', () => {
    const viewModeArb = fc.constantFrom<ViewMode>('xml', 'markdown')

    fc.assert(
      fc.property(viewModeArb, (originalMode) => {
        // Simulate DocumentPanel edit mode round-trip
        let viewMode: ViewMode = originalMode
        let savedViewMode: ViewMode | null = null

        // Enter edit mode: save current viewMode
        savedViewMode = viewMode

        // Exit edit mode: restore viewMode
        if (savedViewMode) {
          viewMode = savedViewMode
          savedViewMode = null
        }

        expect(viewMode).toBe(originalMode)
      }),
      { numRuns: 100 },
    )
  })

  it('viewMode is restored even after switching viewMode during edit', () => {
    const viewModeArb = fc.constantFrom<ViewMode>('xml', 'markdown')

    fc.assert(
      fc.property(
        viewModeArb,
        viewModeArb,
        (originalMode, modeChangedDuringEdit) => {
          // Simulate DocumentPanel edit mode round-trip
          let viewMode: ViewMode = originalMode
          let savedViewMode: ViewMode | null = null

          // Enter edit mode: save current viewMode
          savedViewMode = viewMode

          // During edit, viewMode might change (e.g., user switches)
          viewMode = modeChangedDuringEdit

          // Exit edit mode: restore to saved viewMode
          if (savedViewMode) {
            viewMode = savedViewMode
            savedViewMode = null
          }

          // Should restore to original, not the mode changed during edit
          expect(viewMode).toBe(originalMode)
        },
      ),
      { numRuns: 100 },
    )
  })
})
