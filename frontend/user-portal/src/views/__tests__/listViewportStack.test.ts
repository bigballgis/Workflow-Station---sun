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
    expect(scss).toContain('.page-stack .list-data-grid .el-table__body-wrapper')
  })

  it('marks User Profile Setup as a stacked page so the window can scroll', () => {
    const vue = readFileSync(join(here, '../permissions/index.vue'), 'utf8')
    expect(vue).toContain('class="permissions-page page-stack"')
  })

  it('lets the permission request grid size to its rows instead of the leftover viewport', () => {
    const vue = readFileSync(
      join(here, '../../components/permissions/PermissionRequestSharedList.vue'),
      'utf8',
    )
    expect(vue).toContain('fillViewport: false')
    expect(vue).toContain(':height="gridTableHeight"')
    expect(vue).not.toContain("gridTableHeight || '100%'")
  })
})
