import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

const here = dirname(fileURLToPath(import.meta.url))

describe('admin audit list viewport', () => {
  it('puts both audit grids in table-card so pagination sits at the pane bottom', () => {
    const adminAudit = readFileSync(join(here, '../audit/index.vue'), 'utf8')
    const portalAudit = readFileSync(join(here, '../audit/user-portal/index.vue'), 'utf8')
    expect(adminAudit).toContain('class="page-container audit-list-page"')
    expect(portalAudit).toContain('class="page-container audit-list-page"')
    expect(adminAudit).toContain('class="table-card"')
    expect(portalAudit).toContain('class="table-card"')
    expect(adminAudit).not.toContain('audit-grid-shell')
    expect(portalAudit).not.toContain('up-audit-grid-shell')
  })

  it('fits table height to rows instead of stretching to 100%', () => {
    const adminAudit = readFileSync(join(here, '../audit/index.vue'), 'utf8')
    const portalAudit = readFileSync(join(here, '../audit/user-portal/index.vue'), 'utf8')
    expect(adminAudit).toContain(':height="tableHeight"')
    expect(portalAudit).toContain(':height="tableHeight"')
    expect(adminAudit).not.toContain("gridTableHeight || '100%'")
    expect(portalAudit).not.toContain("gridTableHeight || '100%'")
  })

  it('does not shrink the filter chrome when the grid fills leftover height', () => {
    const scss = readFileSync(join(here, '../../styles/listDataGrid.scss'), 'utf8')
    expect(scss).toContain('> .filter-card')
    expect(scss).toContain('> .batch-bar')
    expect(scss).toContain('.audit-list-page .table-card')
    expect(scss).toContain('.audit-list-page .list-data-grid-scroll')
    expect(scss).toContain('flex: 0 1 auto')
    expect(scss).toContain('.audit-list-page .list-data-grid-inner')
  })
})
