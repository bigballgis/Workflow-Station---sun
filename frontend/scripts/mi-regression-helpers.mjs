/**
 * Shared helpers for MI regression Playwright scripts.
 */
import { mkdirSync } from 'fs'
import { join } from 'path'

export const OUT_DIR = join(process.cwd(), 'user-portal', 'verification-screenshots')

export function todayPrefix() {
  return new Date().toISOString().slice(0, 10)
}

export function screenshotPath(slug) {
  mkdirSync(OUT_DIR, { recursive: true })
  return join(OUT_DIR, `${todayPrefix()}_${slug}.png`)
}

/** @param {import('playwright').Page} page */
export async function countSubTableRows(page, titleMatch) {
  return page.evaluate((reSource) => {
    const re = new RegExp(reSource, 'i')
    const block = [...document.querySelectorAll('.sub-table-field')].find(el =>
      re.test(el.querySelector('.title, .sub-table-header')?.textContent?.trim() ?? ''),
    )
    if (!block) return { found: false, count: -1 }
    const rows = [...block.querySelectorAll('.el-table__body-wrapper tbody tr.el-table__row')].filter(tr =>
      (tr.querySelector('td')?.textContent?.trim() ?? '').length > 0,
    )
    return { found: true, count: rows.length }
  }, titleMatch)
}

/** @param {import('playwright').Page} page */
export async function readPeopleInlineFields(page) {
  return page.evaluate(() => {
    const root =
      document.querySelector('.sub-table-inline-form')
      || [...document.querySelectorAll('.el-card, .form-layout-card, .sub-table-field')].find(c =>
        /people/i.test(c.textContent || ''),
      )
    if (!root) return null
    return [...root.querySelectorAll('.el-form-item')].map(i => ({
      label: i.querySelector('.el-form-item__label')?.textContent?.trim() ?? '',
      val: i.querySelector('input')?.value ?? '',
      checked: i.querySelector('.el-switch.is-checked') != null,
    }))
  })
}

export function fieldByLabel(fields, labelRe) {
  return fields?.find(f => labelRe.test(f.label || ''))?.val ?? ''
}

export const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
