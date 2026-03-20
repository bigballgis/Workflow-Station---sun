import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import { markdownToXml, XmlNode } from '../markdownToXml'

/**
 * Helper: collect all text content from XmlNode tree in order.
 * Returns an array of { type: 'heading' | 'content', text: string }
 */
function collectTexts(nodes: XmlNode[]): Array<{ type: string; text: string }> {
  const result: Array<{ type: string; text: string }> = []
  for (const node of nodes) {
    if (node.tagName.startsWith('h')) {
      result.push({ type: 'heading', text: node.title })
    }
    if (node.tagName === 'content' && node.content) {
      result.push({ type: 'content', text: node.content })
    }
    if (node.children.length > 0) {
      result.push(...collectTexts(node.children))
    }
  }
  return result
}

/**
 * Helper: compute max depth of an XmlNode tree
 */
function maxDepth(nodes: XmlNode[]): number {
  if (nodes.length === 0) return 0
  return 1 + Math.max(...nodes.map(n => maxDepth(n.children)))
}

/**
 * Generator: a safe word that doesn't start with # and doesn't contain backticks or newlines
 */
const safeWord = fc.stringOf(
  fc.char().filter(c => c !== '#' && c !== '`' && c !== '\n' && c !== '\r' && c !== '~'),
  { minLength: 1, maxLength: 20 }
).filter(s => s.trim().length > 0 && !s.startsWith('#'))

/**
 * Generator: a heading line with level 1-6
 */
const headingLine = fc.tuple(
  fc.integer({ min: 1, max: 6 }),
  safeWord
).map(([level, text]) => ({
  level,
  text: text.trim(),
  line: `${'#'.repeat(level)} ${text.trim()}`
}))

/**
 * Generator: a body paragraph (non-heading, non-empty line)
 */
const bodyParagraph = safeWord.map(text => ({
  text: text.trim(),
  line: text.trim()
}))

/**
 * Generator: a random Markdown document with headings and body paragraphs
 */
const markdownDocument = fc.array(
  fc.oneof(
    headingLine.map(h => ({ type: 'heading' as const, ...h })),
    bodyParagraph.map(b => ({ type: 'body' as const, level: 0, ...b }))
  ),
  { minLength: 1, maxLength: 15 }
).filter(parts => parts.some(p => p.type === 'heading'))
  .map(parts => ({
    parts,
    markdown: parts.map(p => p.line).join('\n')
  }))

describe('Feature: xml-document-viewer', () => {
  /**
   * Property 1: Markdown→XML Round-Trip
   *
   * For any valid non-empty Markdown document, converting via markdownToXml
   * and extracting all node text should preserve all heading and body text in order.
   *
   * Validates: Requirements 5.1, 5.2, 5.5
   */
  it('Property 1: Markdown→XML Round-Trip', () => {
    fc.assert(
      fc.property(markdownDocument, ({ parts, markdown }) => {
        const nodes = markdownToXml(markdown)

        // Nodes should not be empty for non-empty markdown with headings
        expect(nodes.length).toBeGreaterThan(0)

        // Collect all texts from the tree
        const collectedTexts = collectTexts(nodes)

        // Extract heading texts from the tree
        const treeHeadings = collectedTexts
          .filter(t => t.type === 'heading')
          .map(t => t.text)

        // Extract expected headings from input parts
        const expectedHeadings = parts
          .filter(p => p.type === 'heading')
          .map(p => p.text)

        // Check if there's preamble content (body text before first heading)
        const firstHeadingIdx = parts.findIndex(p => p.type === 'heading')
        const hasPreamble = firstHeadingIdx > 0 &&
          parts.slice(0, firstHeadingIdx).some(p => p.type === 'body' && p.text.trim().length > 0)

        if (hasPreamble) {
          // When preamble exists, markdownToXml wraps it in a "Document" root node (Req 5.3)
          // So tree headings will have "Document" prepended
          expect(treeHeadings).toEqual(['Document', ...expectedHeadings])
        } else {
          // All headings should be preserved in order
          expect(treeHeadings).toEqual(expectedHeadings)
        }

        // Extract body texts from the tree (content nodes)
        const treeContentTexts = collectedTexts
          .filter(t => t.type === 'content')
          .map(t => t.text)

        // All body text from input should appear somewhere in content nodes
        const allContentJoined = treeContentTexts.join('\n')
        for (const part of parts) {
          if (part.type === 'body') {
            expect(allContentJoined).toContain(part.text)
          }
        }
      }),
      { numRuns: 100 }
    )
  })

  /**
   * Property 13: Code block # not parsed as headings
   *
   * For any Markdown with fenced code blocks containing # characters,
   * no heading nodes should be created from code block content.
   *
   * Validates: Requirements 5.6
   */
  it('Property 13: Code block # not parsed as headings', () => {
    // Generator: code block content with # characters
    const codeBlockWithHashes = fc.tuple(
      fc.constantFrom('```', '~~~'),
      fc.stringOf(fc.constantFrom('a', 'b', ' ', '1', '=', '(', ')'), { minLength: 0, maxLength: 10 }),
      fc.array(
        fc.tuple(
          fc.integer({ min: 1, max: 6 }),
          fc.stringOf(fc.constantFrom('a', 'b', 'c', ' ', '1'), { minLength: 1, maxLength: 10 })
        ).map(([level, text]) => `${'#'.repeat(level)} ${text}`),
        { minLength: 1, maxLength: 5 }
      ),
      fc.constantFrom('```', '~~~')
    ).map(([openFence, lang, hashLines, _closeFence]) => {
      // Use matching fence markers
      const fence = openFence
      return `${fence}${lang}\n${hashLines.join('\n')}\n${fence}`
    })

    const markdownWithCodeBlocks = fc.tuple(
      safeWord,
      codeBlockWithHashes
    ).map(([headingText, codeBlock]) => {
      return `# ${headingText}\n\n${codeBlock}\n`
    })

    fc.assert(
      fc.property(markdownWithCodeBlocks, (markdown) => {
        const nodes = markdownToXml(markdown)

        // Collect all heading titles from the tree
        const allHeadings = collectTexts(nodes)
          .filter(t => t.type === 'heading')
          .map(t => t.text)

        // The only heading should be the one we explicitly created (the first # heading)
        // None of the # lines inside code blocks should appear as headings
        expect(allHeadings.length).toBe(1)
      }),
      { numRuns: 100 }
    )
  })

  /**
   * Property 11: Structured template documents produce ≥3 level nesting
   *
   * For any Markdown following the structured requirements template
   * (# 需求文档 → ## 简介/术语表 → ### 需求 N → #### 验收标准),
   * the tree depth should be ≥ 3.
   *
   * Validates: Requirements 9.4
   */
  it('Property 11: Structured template documents produce ≥3 level nesting', () => {
    // Generator: structured requirements document
    const structuredDocument = fc.tuple(
      fc.integer({ min: 1, max: 5 }), // number of requirements
      fc.array(
        fc.stringOf(fc.constantFrom('a', 'b', 'c', 'd', 'e', ' '), { minLength: 1, maxLength: 20 }),
        { minLength: 1, maxLength: 3 }
      )
    ).map(([numReqs, bodyTexts]) => {
      const lines: string[] = []
      lines.push('# 需求文档')
      lines.push('')
      lines.push('## 简介')
      lines.push('')
      lines.push(bodyTexts[0] || 'Introduction text')
      lines.push('')
      lines.push('## 术语表')
      lines.push('')
      lines.push('Some terminology')
      lines.push('')

      for (let i = 1; i <= numReqs; i++) {
        lines.push(`### 需求 ${i}`)
        lines.push('')
        lines.push(`**用户故事：** 作为开发者，我希望功能${i}`)
        lines.push('')
        lines.push(`#### 验收标准`)
        lines.push('')
        lines.push(`1. WHEN 条件 THEN 结果`)
        lines.push('')
      }

      return lines.join('\n')
    })

    fc.assert(
      fc.property(structuredDocument, (markdown) => {
        const nodes = markdownToXml(markdown)

        // Should have nodes
        expect(nodes.length).toBeGreaterThan(0)

        // Tree depth should be >= 3 (h1 -> h2 -> h3 -> h4/content)
        const depth = maxDepth(nodes)
        expect(depth).toBeGreaterThanOrEqual(3)
      }),
      { numRuns: 100 }
    )
  })
})
