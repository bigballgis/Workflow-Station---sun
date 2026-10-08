#!/usr/bin/env node
/**
 * Prove D05 coexistence: a 1.0.1-pinned instance must not receive 1.0.2 fkFillSources;
 * a 1.0.2-pinned start catalog must keep portable PARENT/PRIMARY fill sources.
 */
import { chromium } from 'playwright'
import { loginViaPortalPassword } from './playwright-login.mjs'

const ORIGIN = process.env.ORIGIN ?? 'http://localhost:3000'
const P0_CODE = 'p0-dual-binding-test'
const PIN_101 = process.env.P0_CATALOG_101 ?? '116e5204-de07-41d9-9344-e74c8d697cda'
const PIN_102 = process.env.P0_CATALOG_102 ?? 'c5c16356-8cd7-43f0-8acc-ac62c4341bd3'

function unwrap(res, body) {
  if (!res.ok()) {
    throw new Error(`HTTP ${res.status()} ${JSON.stringify(body).slice(0, 800)}`)
  }
  return body.data ?? body
}

async function unwrapRes(res) {
  return unwrap(res, await res.json().catch(() => ({})))
}

function fillKinds(content, { includeDetail = false } = {}) {
  const kinds = []
  for (const form of content.forms ?? []) {
    const formType = String(form.formType ?? '').toUpperCase()
    if (!includeDetail && formType === 'DETAIL') continue
    for (const binding of form.tableBindings ?? []) {
      for (const source of binding.fkFillSources ?? []) {
        if (source?.kind) kinds.push(String(source.kind))
      }
    }
  }
  return kinds
}

async function main() {
  const browser = await chromium.launch({ headless: true })
  const page = await browser.newPage()
  try {
    await loginViaPortalPassword(page, { loginOrigin: ORIGIN })
    const list = await unwrapRes(
      await page.request.get(`${ORIGIN}/api/portal/processes/my-applications`, {
        params: { page: 0, size: 100 },
      }),
    )
    const rows = list.records ?? list.content ?? (Array.isArray(list) ? list : [])
    const oldRow = (Array.isArray(rows) ? rows : []).find(
      (row) =>
        (row.functionUnitCode === P0_CODE || row.processDefinitionKey === P0_CODE)
        && (row.functionUnitCatalogId === PIN_101 || row.functionUnitVersionLabel === '1.0.1'),
    ) ?? { id: process.env.P0_INSTANCE_101 ?? '753adffc-b1b7-11f1-a18e-1e379a045316' }
    if (!oldRow?.id) {
      throw new Error(`no my-application pinned to p0 1.0.1 catalog ${PIN_101}`)
    }
    const oldContent = await unwrapRes(
      await page.request.get(`${ORIGIN}/api/portal/processes/function-units/${PIN_101}/content`, {
        params: { processInstanceId: oldRow.id },
      }),
    )
    const oldKinds = fillKinds(oldContent)
    if (oldKinds.length > 0) {
      throw new Error(`1.0.1 assembled bindings leaked fill sources: ${oldKinds.join(',')}`)
    }
    const newContent = await unwrapRes(
      await page.request.get(`${ORIGIN}/api/portal/processes/function-units/${PIN_102}/content`),
    )
    const newKinds = fillKinds(newContent)
    if (!newKinds.includes('PARENT') || !newKinds.includes('PRIMARY')) {
      throw new Error(`1.0.2 assembled bindings missing PARENT/PRIMARY, got ${newKinds.join(',') || 'none'}`)
    }
    console.log(`[ok] 1.0.1 process ${oldRow.id} has no fill sources; 1.0.2 has ${newKinds.join(',')}`)
  } finally {
    await browser.close()
  }
}

main().catch((err) => {
  console.error(err)
  process.exit(1)
})
