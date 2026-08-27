import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

const here = dirname(fileURLToPath(import.meta.url))

describe('list viewport freeze vs stacked pages', () => {
  it('does not freeze portal-content when the page is .page-stack', () => {
    const scss = readFileSync(join(here, '../../styles/listDataGrid.scss'), 'utf8')
    expect(scss).toContain(':has(.list-data-grid-scroll):not(:has(.page-stack))')
    expect(scss).toContain('*:has(.list-data-grid-scroll):not(.page-stack)')
  })

  it('caps the nested list pane so the table keeps an inner vertical bar', () => {
    const scss = readFileSync(join(here, '../../styles/listDataGrid.scss'), 'utf8')
    expect(scss).toContain('.page-stack .list-data-grid-scroll')
    expect(scss).toContain('min(520px, calc(100vh - 220px))')
    expect(scss).not.toMatch(/\.page-stack .list-data-grid .el-table__body-wrapper[\s\S]*height:\s*auto\s*!important/)
  })

  it('lifts overflow only on Action cells, not every right-fixed column', () => {
    const scss = readFileSync(join(here, '../../styles/listDataGrid.scss'), 'utf8')
    expect(scss).toContain('.list-data-grid .el-table__cell:has(.row-actions) .cell')
    expect(scss).not.toContain('.list-data-grid .el-table-fixed-column--right .cell')
  })

  it('marks User Profile Setup as a stacked page so the window can scroll', () => {
    const vue = readFileSync(join(here, '../permissions/index.vue'), 'utf8')
    expect(vue).toContain('class="permissions-page page-stack"')
  })

  it('fills the capped nested pane instead of sizing the table to its rows', () => {
    const vue = readFileSync(
      join(here, '../../components/permissions/PermissionRequestSharedList.vue'),
      'utf8',
    )
    expect(vue).not.toContain('fillViewport: false')
    expect(vue).toContain("gridTableHeight || '100%'")
  })
})
