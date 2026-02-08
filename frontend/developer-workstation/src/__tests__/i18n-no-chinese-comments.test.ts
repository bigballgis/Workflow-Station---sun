import { describe, it, expect } from 'vitest'
import * as fs from 'fs'
import * as path from 'path'

/**
 * Property-Based Test for Frontend I18n Hardcoded Chinese Feature
 * Feature: frontend-i18n-hardcoded-chinese
 *
 * Property 2: 代码注释无中文字符
 * Scans the specific files listed in Requirements 8.1 and 8.2, extracts
 * comments (JS single-line //, multi-line /* *​/, and HTML <!-- -->),
 * and verifies that no comment contains Chinese characters (Unicode range \u4e00-\u9fff).
 *
 * **Validates: Requirements 8.1, 8.2**
 */

// Chinese character regex (CJK Unified Ideographs)
const CHINESE_CHAR_REGEX = /[\u4e00-\u9fff]/

// Root paths for the two frontend projects (relative to this test file)
const FRONTEND_ROOT = path.resolve(__dirname, '..', '..', '..')
const ADMIN_CENTER_SRC = path.join(FRONTEND_ROOT, 'admin-center', 'src')
const DEV_WORKSTATION_SRC = path.join(FRONTEND_ROOT, 'developer-workstation', 'src')

/**
 * In-scope files per Requirements 8.1 and 8.2.
 * These files should have had their Chinese comments replaced with English.
 */
const IN_SCOPE_FILES: string[] = [
  // Admin Center (Requirement 8.1)
  path.join(ADMIN_CENTER_SRC, 'views', 'user', 'UserList.vue'),
  path.join(ADMIN_CENTER_SRC, 'layouts', 'AdminLayout.vue'),
  // Developer Workstation (Requirement 8.2)
  path.join(DEV_WORKSTATION_SRC, 'api', 'index.ts'),
  path.join(DEV_WORKSTATION_SRC, 'api', 'functionUnit.ts'),
  path.join(DEV_WORKSTATION_SRC, 'api', 'user.ts'),
  path.join(DEV_WORKSTATION_SRC, 'api', 'auth.ts'),
  path.join(DEV_WORKSTATION_SRC, 'main.ts'),
  path.join(DEV_WORKSTATION_SRC, 'views', 'Login.vue'),
  path.join(DEV_WORKSTATION_SRC, 'components', 'icon', 'IconPreview.vue'),
]

interface CommentViolation {
  file: string
  line: number
  comment: string
}

/**
 * Extract comments from source code and return those containing Chinese characters.
 *
 * Handles three comment styles:
 * - JS single-line comments: // ...
 * - JS multi-line comments: /* ... *​/
 * - HTML comments: <!-- ... -->
 */
function extractChineseComments(content: string): Array<{ line: number; comment: string }> {
  const results: Array<{ line: number; comment: string }> = []
  const lines = content.split('\n')

  let inMultiLineComment = false
  let multiLineCommentStart = 0
  let multiLineCommentBuffer = ''

  let inHtmlComment = false
  let htmlCommentStart = 0
  let htmlCommentBuffer = ''

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]
    const lineNum = i + 1
    let cursor = 0

    while (cursor < line.length) {
      // Currently inside a multi-line JS comment
      if (inMultiLineComment) {
        const endIdx = line.indexOf('*/', cursor)
        if (endIdx !== -1) {
          multiLineCommentBuffer += line.substring(cursor, endIdx)
          inMultiLineComment = false
          if (CHINESE_CHAR_REGEX.test(multiLineCommentBuffer)) {
            results.push({ line: multiLineCommentStart, comment: multiLineCommentBuffer.trim() })
          }
          multiLineCommentBuffer = ''
          cursor = endIdx + 2
        } else {
          multiLineCommentBuffer += line.substring(cursor) + '\n'
          break
        }
        continue
      }

      // Currently inside an HTML comment
      if (inHtmlComment) {
        const endIdx = line.indexOf('-->', cursor)
        if (endIdx !== -1) {
          htmlCommentBuffer += line.substring(cursor, endIdx)
          inHtmlComment = false
          if (CHINESE_CHAR_REGEX.test(htmlCommentBuffer)) {
            results.push({ line: htmlCommentStart, comment: htmlCommentBuffer.trim() })
          }
          htmlCommentBuffer = ''
          cursor = endIdx + 3
        } else {
          htmlCommentBuffer += line.substring(cursor) + '\n'
          break
        }
        continue
      }

      // Check for start of HTML comment: <!--
      if (line.startsWith('<!--', cursor)) {
        const endIdx = line.indexOf('-->', cursor + 4)
        if (endIdx !== -1) {
          // Single-line HTML comment
          const commentText = line.substring(cursor + 4, endIdx)
          if (CHINESE_CHAR_REGEX.test(commentText)) {
            results.push({ line: lineNum, comment: commentText.trim() })
          }
          cursor = endIdx + 3
          continue
        } else {
          // Multi-line HTML comment starts
          inHtmlComment = true
          htmlCommentStart = lineNum
          htmlCommentBuffer = line.substring(cursor + 4) + '\n'
          break
        }
      }

      // Check for start of multi-line JS comment: /*
      if (line[cursor] === '/' && cursor + 1 < line.length && line[cursor + 1] === '*') {
        const endIdx = line.indexOf('*/', cursor + 2)
        if (endIdx !== -1) {
          // Single-line block comment
          const commentText = line.substring(cursor + 2, endIdx)
          if (CHINESE_CHAR_REGEX.test(commentText)) {
            results.push({ line: lineNum, comment: commentText.trim() })
          }
          cursor = endIdx + 2
          continue
        } else {
          // Multi-line block comment starts
          inMultiLineComment = true
          multiLineCommentStart = lineNum
          multiLineCommentBuffer = line.substring(cursor + 2) + '\n'
          break
        }
      }

      // Check for single-line JS comment: //
      if (line[cursor] === '/' && cursor + 1 < line.length && line[cursor + 1] === '/') {
        const commentText = line.substring(cursor + 2)
        if (CHINESE_CHAR_REGEX.test(commentText)) {
          results.push({ line: lineNum, comment: commentText.trim() })
        }
        break // Rest of line is comment
      }

      // Skip string literals to avoid false positives from Chinese in strings
      if (line[cursor] === "'" || line[cursor] === '"' || line[cursor] === '`') {
        const quote = line[cursor]
        cursor++
        while (cursor < line.length) {
          if (line[cursor] === '\\') {
            cursor += 2 // Skip escaped character
            continue
          }
          if (line[cursor] === quote) {
            cursor++
            break
          }
          cursor++
        }
        continue
      }

      cursor++
    }
  }

  return results
}

/**
 * Normalize a file path for display (relative to frontend root).
 */
function displayPath(filePath: string): string {
  return path.relative(FRONTEND_ROOT, filePath).replace(/\\/g, '/')
}

describe('Property 2: 代码注释无中文字符', () => {
  /**
   * Property 2: Code comments have no Chinese characters
   *
   * For the specific files listed in Requirements 8.1 and 8.2,
   * scanning their comments (JS single-line //, multi-line /* *​/,
   * and HTML <!-- -->) should find no Chinese characters
   * (Unicode range \u4e00-\u9fff).
   *
   * **Validates: Requirements 8.1, 8.2**
   */
  it('should not contain Chinese characters in comments of in-scope files', () => {
    const violations: CommentViolation[] = []

    for (const filePath of IN_SCOPE_FILES) {
      expect(
        fs.existsSync(filePath),
        `In-scope file should exist: ${displayPath(filePath)}`
      ).toBe(true)

      const content = fs.readFileSync(filePath, 'utf-8')
      const chineseComments = extractChineseComments(content)

      for (const { line, comment } of chineseComments) {
        violations.push({
          file: displayPath(filePath),
          line,
          comment,
        })
      }
    }

    if (violations.length > 0) {
      const report = violations
        .map((v) => `  ${v.file}:${v.line} => "${v.comment}"`)
        .join('\n')
      expect.fail(
        `Found ${violations.length} comment(s) containing Chinese characters in in-scope files:\n${report}`
      )
    }
  })
})
