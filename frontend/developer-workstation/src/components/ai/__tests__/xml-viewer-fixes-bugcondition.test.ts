/**
 * Bug Condition Exploration Test - XML 查看器四项体验缺陷验证
 *
 * Property 1: Bug Condition - These tests encode the EXPECTED (fixed) behavior.
 * They MUST FAIL on the current unfixed code — failure confirms the bugs exist.
 *
 * Validates: Requirements 1.1, 1.2, 1.3, 1.4
 */
import { describe, it, expect, beforeEach } from 'vitest'
import * as fc from 'fast-check'
import { mount } from '@vue/test-utils'
import type { XmlNode } from '@/utils/markdownToXml'
import XmlTreeNode from '../XmlTreeNode.vue'

// ─── XmlNode arbitrary generator (reused pattern from XmlTreeView.property.test.ts) ───

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
      content: fc.string({ minLength: 1, maxLength: 50 }),
    }).map(r => ({
      ...r,
      key: nextKey(),
      children: [] as XmlNode[]
    }))
  }

  return fc.oneof(
    fc.record({
      tagName: fc.constant('content' as string),
      title: fc.string({ minLength: 1, maxLength: 20 }),
      level: fc.integer({ min: 1, max: 6 }),
      content: fc.string({ minLength: 1, maxLength: 50 }),
    }).map(r => ({
      ...r,
      key: nextKey(),
      children: [] as XmlNode[]
    })),
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


// ─── Defect 1: 标签暴露 (Tag exposure) ───
// Expected behavior: DOM should NOT contain .xml-tree-node__tag elements.
// For leaf nodes (tagName === 'content'), only node.content is shown, not node.title.
// This test will FAIL on unfixed code because XmlTreeNode currently renders the tag span.

describe('Defect 1: 标签暴露 - XmlTreeNode should NOT render tag spans', () => {
  beforeEach(() => {
    keyCounter = 0
  })

  it('no .xml-tree-node__tag elements exist for any XmlNode tree (PBT)', () => {
    fc.assert(
      fc.property(xmlNodeArb(2), (node) => {
        const expandedKeys = new Set<string>()
        if (node.children.length > 0) {
          expandedKeys.add(node.key)
        }

        const wrapper = mount(XmlTreeNode, {
          props: { node, expandedKeys, depth: 0 },
          global: {
            stubs: { ElIcon: true, ArrowRight: true },
          },
        })

        // Expected: no .xml-tree-node__tag elements in the DOM
        const tagElements = wrapper.findAll('.xml-tree-node__tag')
        expect(tagElements.length).toBe(0)

        wrapper.unmount()
      }),
      { numRuns: 100 }
    )
  })

  it('leaf nodes (tagName=content) show node.content, not node.title', () => {
    fc.assert(
      fc.property(
        fc.stringMatching(/^[a-zA-Z0-9][a-zA-Z0-9 ]{3,48}[a-zA-Z0-9]$/),
        (contentText) => {
          const leafNode: XmlNode = {
            key: nextKey(),
            tagName: 'content',
            title: contentText.substring(0, 20),
            level: 2,
            content: contentText,
            children: [],
          }

          const wrapper = mount(XmlTreeNode, {
            props: {
              node: leafNode,
              expandedKeys: new Set<string>(),
              depth: 0,
            },
            global: {
              stubs: { ElIcon: true, ArrowRight: true },
            },
          })

          const text = wrapper.text()
          // Expected: leaf node shows content text (use .text() to avoid HTML entity escaping issues)
          expect(text).toContain(contentText)

          // The .xml-tree-node__title should NOT display node.title for content nodes
          const titleSpan = wrapper.find('.xml-tree-node__title')
          if (titleSpan.exists()) {
            expect(titleSpan.text()).not.toBe(leafNode.title)
          }

          wrapper.unmount()
        }
      ),
      { numRuns: 100 }
    )
  })
})


// ─── Defect 2: 编辑器内嵌 (Inline editor) ───
// Expected behavior: DocumentEditor should render inside el-dialog, not inline in .document-panel__doc.
// On unfixed code, DocumentEditor is rendered inline with v-show → test will FAIL.

describe('Defect 2: 编辑器内嵌 - DocumentEditor should be in el-dialog', () => {
  it('DocumentPanel should use el-dialog to wrap DocumentEditor', async () => {
    // Read the DocumentPanel source to verify structural expectation
    // The fixed code should import and use el-dialog; the unfixed code does not
    const fs = await import('fs')
    const path = await import('path')
    const source = fs.readFileSync(
      path.resolve(__dirname, '../DocumentPanel.vue'),
      'utf-8'
    )

    // Expected: template contains el-dialog wrapping DocumentEditor
    // On unfixed code, there is no el-dialog → FAIL
    expect(source).toMatch(/el-dialog/i)

    // Expected: DocumentEditor should NOT be inside .document-panel__doc
    // On unfixed code, DocumentEditor is inside .document-panel__doc with v-show
    // After fix, DocumentEditor should be inside el-dialog, outside .document-panel__doc
    const docSectionMatch = source.match(
      /class="document-panel__doc"[\s\S]*?<\/div>/
    )
    if (docSectionMatch) {
      expect(docSectionMatch[0]).not.toMatch(/DocumentEditor/)
    }
  })
})

// ─── Defect 3: 版本历史位置 (Version history position) ───
// Expected behavior: Version history should be in .document-panel__sidebar, not in bottom .document-panel__versions.
// On unfixed code, version history is in .document-panel__versions at bottom → test will FAIL.

describe('Defect 3: 版本历史位置 - Version history should be in sidebar', () => {
  it('DocumentPanel should have .document-panel__sidebar for version history', async () => {
    const fs = await import('fs')
    const path = await import('path')
    const source = fs.readFileSync(
      path.resolve(__dirname, '../DocumentPanel.vue'),
      'utf-8'
    )

    // Expected: template contains .document-panel__sidebar
    // On unfixed code, there is no sidebar class → FAIL
    expect(source).toMatch(/document-panel__sidebar/)

    // Expected: version history content should be inside .document-panel__sidebar
    // not in a standalone .document-panel__versions at the bottom of el-tab-pane
    // On unfixed code, .document-panel__versions is a sibling of .document-panel__content
    // After fix, version items should be inside .document-panel__sidebar
    const sidebarMatch = source.match(
      /document-panel__sidebar[\s\S]*?document-panel__version-item/
    )
    expect(sidebarMatch).not.toBeNull()
  })
})

// ─── Defect 4: 工具栏 Sticky (Toolbar sticky) ───
// Expected behavior: .document-panel__toolbar and .xml-tree-view__toolbar should have position: sticky.
// On unfixed code, neither toolbar has sticky positioning → test will FAIL.

describe('Defect 4: 工具栏 Sticky - Toolbars should have position: sticky', () => {
  it('.document-panel__toolbar CSS should include position: sticky', async () => {
    const fs = await import('fs')
    const path = await import('path')
    const source = fs.readFileSync(
      path.resolve(__dirname, '../DocumentPanel.vue'),
      'utf-8'
    )

    // Extract the style section
    const styleMatch = source.match(/<style[\s\S]*?>([\s\S]*?)<\/style>/)
    expect(styleMatch).not.toBeNull()
    const styleContent = styleMatch![1]

    // Expected: .document-panel__toolbar should have position: sticky
    // On unfixed code, it does NOT have sticky → FAIL
    const toolbarStyleMatch = styleContent.match(
      /\.document-panel__toolbar\s*\{[^}]*\}/
    )
    expect(toolbarStyleMatch).not.toBeNull()
    expect(toolbarStyleMatch![0]).toMatch(/position:\s*sticky/)
  })

  it('.xml-tree-view__toolbar CSS should include position: sticky', async () => {
    const fs = await import('fs')
    const path = await import('path')
    const source = fs.readFileSync(
      path.resolve(__dirname, '../XmlTreeView.vue'),
      'utf-8'
    )

    const styleMatch = source.match(/<style[\s\S]*?>([\s\S]*?)<\/style>/)
    expect(styleMatch).not.toBeNull()
    const styleContent = styleMatch![1]

    // Expected: .xml-tree-view__toolbar should have position: sticky
    // On unfixed code, it does NOT have sticky → FAIL
    const toolbarStyleMatch = styleContent.match(
      /\.xml-tree-view__toolbar\s*\{[^}]*\}/
    )
    expect(toolbarStyleMatch).not.toBeNull()
    expect(toolbarStyleMatch![0]).toMatch(/position:\s*sticky/)
  })
})
