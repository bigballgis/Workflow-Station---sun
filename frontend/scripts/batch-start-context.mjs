import assert from 'node:assert/strict'

/** Read through the normal authorized Portal endpoint, never synthesize a design digest. */
export async function readBatchStartContext(page, catalogId) {
  const response = await page.request.get(`http://localhost:3000/api/portal/processes/function-units/${encodeURIComponent(catalogId)}/content?forStart=true`)
  assert.equal(response.ok(), true, 'Published start content must be available')
  const body = await response.json()
  assert.equal(body.success, true)
  const context = body.data.batchStartContext
  if (context !== undefined && context !== null) {
    assert.equal(context.schemaVersion, 1)
    assert.equal(context.catalogId, catalogId)
    assert.match(context.definitionDigest, /^[a-f0-9]{64}$/)
  }
  return context
}
