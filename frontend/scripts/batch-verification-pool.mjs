import assert from 'node:assert/strict'

async function read(page, path) {
  const response = await page.request.get(`http://localhost:3000${path}`)
  assert.ok(response.ok(), `Role directory HTTP ${response.status()}`)
  const body = await response.json()
  if (body && typeof body === 'object' && 'success' in body) {
    assert.equal(body.success, true, 'Role directory request failed')
    return body.data
  }
  return body
}
function flatten(nodes) { return nodes.flatMap(node => [node, ...flatten(node.children || [])]) }

/** Read existing admitted pools only. Never create users, role membership or directory fixtures. */
export async function selectExistingBatchRolePool(portal, admin, multiple = false) {
  const units = flatten(await read(portal, '/api/portal/permissions/business-units/tree'))
  assert.ok(units.length > 0 && units.length <= 50, 'Bounded directory fixture expected')
  for (const bu of units) {
    assert.ok(bu.id && bu.code)
    const roles = await read(portal, `/api/portal/permissions/business-units/${encodeURIComponent(bu.id)}/roles`)
    assert.ok(roles.length <= 50)
    for (const role of roles) {
      assert.ok(role.code)
      const path = `/api/v1/admin/task-assignment/business-units/${encodeURIComponent(bu.code)}/roles/${encodeURIComponent(role.code)}`
      const users = await read(admin, `${path}/users`)
      assert.ok(Array.isArray(users) && users.every(user => typeof user === 'string' && user.length > 0))
      const unique = [...new Set(users)]
      if (multiple ? unique.length > 1 && unique.length <= 200 : unique.length === 1) {
        const admission = await read(admin, `${path}/eligible`)
        assert.equal(typeof admission.eligible, 'boolean')
        if (admission.eligible === true) return { bu, role, users: unique }
      }
    }
  }
  throw new Error('No existing admitted role pool of the requested size; no directory data was changed')
}
