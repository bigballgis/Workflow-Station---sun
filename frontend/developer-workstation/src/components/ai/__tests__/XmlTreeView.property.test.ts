import { describe, it, expect, beforeEach } from 'vitest'
import * as fc from 'fast-check'
import type { XmlNode } from '@/utils/markdownToXml'
import { markdownToXml } from '@/utils/markdownToXml'
import {
  computeDefaultExpandedKeys,
  computeAllExpandableKeys,
  isExpandable
} from '../xmlTreeUtils'

/**
 * Generator: a random XmlNode tree for property testing.
 * Uses unique keys to match real markdownToXml output behavior.
 */
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
      children: [] as XmlNode[]
    }))
  }

  return fc.oneof(
    // Leaf node
    fc.record({
      tagName: fc.constant('content' as string),
      title: fc.string({ minLength: 1, maxLength: 20 }),
      level: fc.integer({ min: 1, max: 6 }),
      content: fc.string({ minLength: 0, maxLength: 50 }),
    }).map(r => ({
      ...r,
      key: nextKey(),
      children: [] as XmlNode[]
    })),
    // Parent node with children
    fc.record({
      tagName: fc.constantFrom('h1', 'h2', 'h3', 'h4') as fc.Arbitrary<string>,
      title: fc.string({ minLength: 1, maxLength: 20 }),
      level: fc.integer({ min: 1, max: 6 }),
      childNodes: fc.array(xmlNodeArb(maxDepth - 1), { minLength: 1, maxLength: 3 })
    }).map(r => ({
      key: nextKey(),
      tagName: r.tagName,
      title: r.title,
      level: r.level,
      content: '',
      children: r.childNodes
    }))
  )
}

/**
 * Generator: a random XmlNode tree (array of root nodes).
 */
const xmlNodeTreeArb = fc.array(xmlNodeArb(3), { minLength: 1, maxLength: 4 })

/**
 * Helper: collect all expandable (non-leaf) node keys from a tree.
 */
function collectExpandableKeys(nodes: XmlNode[]): string[] {
  const keys: string[] = []
  for (const node of nodes) {
    if (node.children.length > 0) {
      keys.push(node.key)
      keys.push(...collectExpandableKeys(node.children))
    }
  }
  return keys
}

/**
 * Helper: collect keys of only level-1 (top-level) expandable nodes.
 */
function collectLevel1ExpandableKeys(nodes: XmlNode[]): string[] {
  const keys: string[] = []
  for (const node of nodes) {
    if (node.children.length > 0) {
      keys.push(node.key)
    }
  }
  return keys
}

describe('Feature: xml-document-viewer', () => {
  beforeEach(() => {
    keyCounter = 0
  })

  /**
   * Property 3: Default/collapse state is first-level-only expanded
   *
   * For any XmlNode tree, the default expanded keys should contain
   * only level-1 (top-level) expandable nodes.
   *
   * Validates: Requirements 1.3, 6.3
   */
  it('Property 3: Default/collapse state is first-level-only expanded', () => {
    fc.assert(
      fc.property(xmlNodeTreeArb, (nodes) => {
        const defaultKeys = computeDefaultExpandedKeys(nodes, 1)

        // All level-1 expandable nodes should be in the set
        const level1Keys = collectLevel1ExpandableKeys(nodes)
        for (const key of level1Keys) {
          expect(defaultKeys.has(key)).toBe(true)
        }

        // No deeper-level keys should be in the set
        for (const key of defaultKeys) {
          expect(level1Keys).toContain(key)
        }
      }),
      { numRuns: 100 }
    )
  })

  /**
   * Property 5: Expand All expands every node with children
   *
   * After expandAll(), all non-leaf nodes should be in expandedKeys.
   *
   * Validates: Requirements 6.2, 6.4
   */
  it('Property 5: Expand All expands every node with children', () => {
    fc.assert(
      fc.property(xmlNodeTreeArb, (nodes) => {
        const allKeys = computeAllExpandableKeys(nodes)
        const expectedKeys = collectExpandableKeys(nodes)

        // Every expandable node should be in the set
        for (const key of expectedKeys) {
          expect(allKeys.has(key)).toBe(true)
        }

        // The set should contain exactly the expandable keys
        expect(allKeys.size).toBe(expectedKeys.length)
      }),
      { numRuns: 100 }
    )
  })

  /**
   * Property 4: Parent nodes show arrow, leaf nodes show text
   *
   * For any XmlNode, children.length > 0 means expandable (arrow),
   * children.length === 0 means leaf node (content text).
   *
   * Validates: Requirements 1.4, 1.5
   */
  it('Property 4: Parent nodes show arrow, leaf nodes show text', () => {
    fc.assert(
      fc.property(xmlNodeArb(3), (node) => {
        if (node.children.length > 0) {
          // Parent node: should be expandable
          expect(isExpandable(node)).toBe(true)
        } else {
          // Leaf node: should not be expandable
          expect(isExpandable(node)).toBe(false)
        }
      }),
      { numRuns: 100 }
    )
  })

  /**
   * Property 12: Parse failure falls back to plain text
   *
   * Pass null, undefined, non-string inputs to markdownToXml wrapped in try-catch,
   * verify it throws or returns empty for invalid inputs.
   *
   * Validates: Requirements 1.6
   */
  it('Property 12: Parse failure falls back to plain text', () => {
    const invalidInputs = fc.oneof(
      fc.constant(null),
      fc.constant(undefined),
      fc.integer().map(n => n as any),
      fc.boolean().map(b => b as any),
      fc.constant({} as any),
      fc.constant([] as any),
      fc.constant(Symbol('test') as any),
      fc.constant(NaN as any)
    )

    fc.assert(
      fc.property(invalidInputs, (input) => {
        let threw = false
        let result: any
        try {
          result = markdownToXml(input as any)
        } catch {
          threw = true
        }

        // For invalid inputs, markdownToXml should either throw or return empty array
        if (!threw) {
          expect(Array.isArray(result)).toBe(true)
          expect(result.length).toBe(0)
        }
        // If it threw, that's also acceptable — XmlTreeView catches it and shows fallback
      }),
      { numRuns: 100 }
    )
  })
})
