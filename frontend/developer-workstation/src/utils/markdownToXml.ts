export interface XmlNode {
  key: string
  tagName: string
  title: string
  level: number
  content: string
  children: XmlNode[]
}

/**
 * 将 Markdown 文本按标题层级解析为 XmlNode 树。
 *
 * 算法：
 * 1. 预处理：识别并保护代码块（``` 和 4 空格缩进），将其内容替换为占位符
 * 2. 逐行扫描，识别标题行（# ATX 风格，不支持 Setext === / --- 下划线风格）
 * 3. 使用栈维护当前层级上下文
 * 4. 正文内容作为当前标题节点的子内容节点
 * 5. 还原代码块占位符
 * 6. 如果文档以非标题内容开头，包裹在根节点中
 * 7. ATX 标题尾部 # 字符自动去除
 * 8. --- 水平分割线作为当前标题节点的内容处理
 * 9. 空/纯空白输入返回空数组
 */
export function markdownToXml(markdown: string): XmlNode[] {
  if (!markdown || !markdown.trim()) {
    return []
  }

  // Step 1: Protect code blocks by replacing with placeholders
  const codeBlocks: string[] = []
  let processed = markdown

  // Protect fenced code blocks (``` or ~~~)
  processed = processed.replace(/^(```|~~~).*\n([\s\S]*?)^\1\s*$/gm, (match) => {
    const index = codeBlocks.length
    codeBlocks.push(match)
    return `%%CODEBLOCK_${index}%%`
  })

  // Protect 4-space indented code blocks (consecutive lines starting with 4+ spaces or tab,
  // preceded by a blank line or start of string)
  processed = processed.replace(/(^|\n\s*\n)((?:(?:    |\t).+\n?)+)/g, (_match, prefix, code) => {
    const index = codeBlocks.length
    codeBlocks.push(code)
    return `${prefix}%%CODEBLOCK_${index}%%`
  })

  const lines = processed.split('\n')

  // ATX heading regex: 1-6 # chars, followed by space, then title text
  const headingRegex = /^(#{1,6})\s+(.+)$/

  // Parse headings and body content
  interface ParsedSection {
    level: number
    title: string
    bodyLines: string[]
  }

  const sections: ParsedSection[] = []
  let currentSection: ParsedSection | null = null
  let preambleLines: string[] = []
  let hasPreamble = false

  for (const line of lines) {
    const headingMatch = line.match(headingRegex)
    if (headingMatch) {
      // Strip trailing # characters from heading title
      let title = headingMatch[2].replace(/\s+#+\s*$/, '').trim()
      if (!title) title = headingMatch[2].trim()

      const level = headingMatch[1].length

      currentSection = { level, title, bodyLines: [] }
      sections.push(currentSection)
    } else {
      if (currentSection) {
        currentSection.bodyLines.push(line)
      } else {
        // Content before any heading
        preambleLines.push(line)
        hasPreamble = true
      }
    }
  }

  // Check if preamble has actual content (not just whitespace)
  hasPreamble = hasPreamble && preambleLines.some(l => l.trim().length > 0)

  // Step 3: Build tree using stack-based algorithm
  const roots: XmlNode[] = []

  // Stack entries: each is a node with its level
  const stack: XmlNode[] = []

  // Counter for generating keys at each level
  // We track the index of children added to each parent
  let rootIndex = 0

  // If there's preamble content, wrap it in a root node
  if (hasPreamble) {
    const preambleContent = restoreCodeBlocks(preambleLines.join('\n').trim(), codeBlocks)
    const rootNode: XmlNode = {
      key: `h1-${rootIndex}`,
      tagName: 'h1',
      title: 'Document',
      level: 1,
      content: '',
      children: []
    }
    if (preambleContent) {
      rootNode.children.push({
        key: `content-${rootIndex}-0`,
        tagName: 'content',
        title: preambleContent.substring(0, 50),
        level: 2,
        content: preambleContent,
        children: []
      })
    }
    roots.push(rootNode)
    stack.push(rootNode)
    rootIndex++
  }

  for (const section of sections) {
    const { level, title, bodyLines } = section

    // Pop stack until we find a parent with level < current level
    while (stack.length > 0 && stack[stack.length - 1].level >= level) {
      stack.pop()
    }

    const parent = stack.length > 0 ? stack[stack.length - 1] : null
    const siblingIndex = parent ? parent.children.length : rootIndex
    const keyPath = parent ? parent.key.replace(/^[^-]+-/, '') + `-${siblingIndex}` : `${siblingIndex}`

    const node: XmlNode = {
      key: `h${level}-${keyPath}`,
      tagName: `h${level}`,
      title,
      level,
      content: '',
      children: []
    }

    // Add body content as child content node
    const bodyText = bodyLines.join('\n').trim()
    if (bodyText) {
      const restoredBody = restoreCodeBlocks(bodyText, codeBlocks)
      node.children.push({
        key: `content-${keyPath}-0`,
        tagName: 'content',
        title: restoredBody.substring(0, 50),
        level: level + 1,
        content: restoredBody,
        children: []
      })
    }

    if (parent) {
      parent.children.push(node)
    } else {
      roots.push(node)
      rootIndex++
    }

    stack.push(node)
  }

  return roots
}

function restoreCodeBlocks(text: string, codeBlocks: string[]): string {
  return text.replace(/%%CODEBLOCK_(\d+)%%/g, (_, index) => {
    return codeBlocks[parseInt(index, 10)] || ''
  })
}
