import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

const here = dirname(fileURLToPath(import.meta.url))
const frontendRoot = join(here, '../../../../../')

describe('admin audit list viewport fill', () => {
  it('shrinks Audit filter and batch bars instead of the grid', () => {
    const scss = readFileSync(join(here, '../../../styles/listDataGrid.scss'), 'utf8')
    expect(scss).toContain('.page-container:has(.list-data-grid-scroll) > .filter-card')
    expect(scss).toContain('.page-container:has(.list-data-grid-scroll) > .batch-bar')
  })

  it('hosts Admin Center Audit on table-card so the grid fills leftover height', () => {
    const vue = readFileSync(join(here, '../index.vue'), 'utf8')
    expect(vue).toContain('class="table-card"')
    expect(vue).not.toContain('audit-grid-shell')
    expect(vue).toContain('<template #empty>')
    expect(vue).not.toContain('class="empty-state"')
  })

  it('hosts User Portal Audit on table-card so the grid fills leftover height', () => {
    const vue = readFileSync(join(here, '../user-portal/index.vue'), 'utf8')
    expect(vue).toContain('class="table-card"')
    expect(vue).not.toContain('up-audit-grid-shell')
    expect(vue).toContain('<template #empty>')
    expect(vue).not.toContain('class="empty-state"')
  })

  it('waits for pinned layout instead of a fixed sleep', () => {
    const script = readFileSync(
      join(frontendRoot, 'scripts/verify-admin-audit-list-viewport.mjs'),
      'utf8',
    )
    expect(script).toContain('waitUntilLayoutPinned')
    expect(script).not.toContain('waitForTimeout')
  })
})
