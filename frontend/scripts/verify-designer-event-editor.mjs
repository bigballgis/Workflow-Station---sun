/** Local UI regression: real existing MCY design (read-only) plus a newly cloned test FU. */
import assert from 'node:assert/strict'
import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'
import { loginViaDwPassword } from './playwright-login.mjs'

const origin = 'http://localhost:3000'
const browser = await chromium.launch()
try {
  const page = await browser.newPage({ viewport: { width: 1800, height: 1200 } })
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  await loginViaDwPassword(page)
  const api = async (method, path, data) => {
    const response = await page.request.fetch(`${origin}/api/v1${path}`, { method, data })
    const body = await response.json()
    assert.ok(response.ok() && body.success !== false, `${path}: ${body.message || body.error?.message}`)
    return body.data
  }
  const forms = await api('GET', '/function-units/50125/forms')
  const source = forms.find(form => form.formName === 'Transaction Request Form - Transaction Assignment')
  assert.ok(source)
  const sourceSnapshot = JSON.stringify(source.configJson)
  const clone = process.env.EVENT_EDITOR_FIXTURE_ID
    ? await api('GET', `/function-units/${process.env.EVENT_EDITOR_FIXTURE_ID}`)
    : await api('POST', `/function-units/50125/clone?newName=${encodeURIComponent(`Event editor verification ${Date.now()}`)}`)
  assert.ok(clone.name.startsWith('Event editor verification '))
  console.log(JSON.stringify({ step: 'clone', unitId: clone.id }))
  const clonedForms = await api('GET', `/function-units/${clone.id}/forms`)
  const target = clonedForms.find(form => form.formName === source.formName)
  assert.ok(target)
  const screenshotDir = new URL('../developer-workstation/verification-screenshots/', import.meta.url)
  mkdirSync(screenshotDir, { recursive: true })
  const capture = async (name, locator) => {
    const path = new URL(`2026-11-03-event-editor-${name}.png`, screenshotDir).pathname.replace(/^\/(.:)/, '$1')
    await locator.screenshot({ path })
    console.log(JSON.stringify({ step: 'screenshot', path }))
  }
  const dialog = page.locator('._fd-event-dialog:visible')
  const methods = () => dialog.locator('._fd-event-method > span:first-child').allTextContents()
  const editor = page.locator('._fd-event:visible').first()
  const open = () => editor.getByRole('button', { name: 'Edit', exact: true }).click()
  const footer = name => dialog.locator('.el-dialog__footer').getByRole('button', { name, exact: true })
  let hookName = 'hook_load'
  let capturedDraft = false
  const selectAssignee = async () => {
    const label = page.locator('.sub-table-design-wrapper .fc-form-title:visible').filter({ hasText: /^Assignee$/ }).first()
    await label.evaluate(element => element.scrollIntoView({ block: 'center', inline: 'center' }))
    const box = await label.boundingBox()
    assert.ok(box)
    await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2)
    await editor.waitFor()
    await page.waitForTimeout(200)
  }
  const setBody = async body => {
    await dialog.locator('.CodeMirror').evaluate((element, value) => {
      element.CodeMirror.setValue(value)
    }, body)
    await dialog.locator('._fd-event-r .el-header').getByRole('button', { name: 'Save', exact: true }).click()
  }
  const addHook = async () => {
    await dialog.locator('button').filter({ hasText: /^Add event$/ }).click()
    await page.locator('.el-dropdown-menu:visible .el-dropdown-menu__item').filter({ hasText: new RegExp(`^${hookName}`) }).click()
    await page.locator('._fd-event-dropdown:visible').waitFor({ state: 'hidden' })
    if (!capturedDraft) {
      await capture('new-event-draft', dialog)
      capturedDraft = true
    }
    await setBody('api.setValue("merchant_name", "event-editor-test")')
  }
  for (const [unitId, form, writable] of [[50125, source, false], [clone.id, target, true]]) {
    await page.goto(`${origin}/dev/function-units/${unitId}`)
    await page.getByRole('tab', { name: /Form/ }).click()
    await page.getByRole('row').filter({ hasText: form.formName }).getByRole('button', { name: 'Edit', exact: true }).click()
    await page.locator('.designer-grouped-nav .el-dropdown').first().locator('.designer-nav-btn').click()
    await page.locator('.el-dropdown-menu:visible .el-dropdown-menu__item').filter({ hasText: /HMDC transaction/i }).click()
    for (const mode of ['Form Design', 'Batch Form']) {
      await page.getByRole('button', { name: mode, exact: true }).click()
      await page.waitForTimeout(500)
      await selectAssignee()
      const count = await editor.locator('.el-badge__content').textContent()
      await open()
      const initial = await methods()
      console.log(JSON.stringify({ step: 'initial', unitId, mode, count, methods: initial }))
      assert.equal(initial.length, Number(count))
      hookName = ['hook_load', 'hook_mounted', 'hook_value', 'hook_titleClick'].find(name => !initial.some(method => method.includes(name)))
      assert.ok(hookName, 'Need an unconfigured hook to exercise additions')
      await capture(`${unitId}-${mode.replaceAll(' ', '-').toLowerCase()}`, dialog)
      for (const close of ['x', 'Cancel', 'Escape']) {
        await addHook()
        assert.ok((await methods()).some(name => name.includes(hookName)))
        if (close === 'x') await dialog.locator('.el-dialog__headerbtn').click()
        else if (close === 'Escape') await page.keyboard.press('Escape')
        else await footer(close).click()
        await dialog.waitFor({ state: 'hidden' })
        assert.equal(await editor.locator('.el-badge__content').textContent(), count)
        await open()
        assert.deepEqual(await methods(), initial)
      }
      if (writable) {
        if (mode === 'Batch Form') {
          await dialog.locator('.CodeMirror').evaluate(element => {
            const cm = element.CodeMirror
            cm.setValue(cm.getValue() + '\nvoid "current-editor-draft";')
          })
        }
        await addHook()
        await footer('Ok').click()
        await dialog.waitFor({ state: 'hidden' })
        assert.equal(Number(await editor.locator('.el-badge__content').textContent()), Number(count) + 1)
        await open()
        assert.ok((await methods()).some(name => name.includes(hookName)))
        await dialog.locator('.el-menu-item').filter({ hasText: hookName }).locator('.icon-delete').click()
        await footer('Ok').click()
        await dialog.waitFor({ state: 'hidden' })
        assert.equal(Number(await editor.locator('.el-badge__content').textContent()), Number(count))
        await open()
        assert.ok(!(await methods()).some(name => name.includes(hookName)))
        await addHook()
        await footer('Ok').click()
        await dialog.waitFor({ state: 'hidden' })
        await open()
        // Deletion must also remain a draft until OK.
        await dialog.locator('.el-menu-item').filter({ hasText: hookName }).locator('.icon-delete').click()
        await footer('Cancel').click()
        await open()
        assert.ok((await methods()).some(name => name.includes(hookName)))
      }
      await footer('Cancel').click()
      await page.locator('.sub-table-design-wrapper:visible ._fd-drag-mask:visible').last().click()
      await selectAssignee()
      assert.equal(Number(await editor.locator('.el-badge__content').textContent()), Number(count) + Number(writable))
      console.log(JSON.stringify({ step: 'component-events', unitId, mode, initialCount: Number(count), cancel: ['x', 'Cancel', 'Escape'], ok: writable }))
      await page.locator('.fc-designer-wrapper:visible ._fc-r-tab').filter({ hasText: /^\s*Form\s*$/ }).click()
      const formEditor = page.locator('._fd-fn-list:visible').first()
      const formCount = Number(await formEditor.locator('.el-badge__content').textContent())
      await formEditor.getByRole('button', { name: 'Edit', exact: true }).click()
      const formDialog = page.locator('._fd-fn-list-dialog:visible')
      const configuredGroup = formDialog.locator('.el-menu-item-group').filter({ has: page.locator('.el-menu-item-group__title', { hasText: 'Configured scripts' }) })
      assert.equal(await configuredGroup.locator('.el-menu-item').count(), formCount)
      const availableGroup = formDialog.locator('.el-menu-item-group').filter({ has: page.locator('.el-menu-item-group__title', { hasText: 'Unconfigured events' }) })
      assert.ok(await availableGroup.locator('.el-menu-item').count())
      const writeFormDraft = async () => {
        await availableGroup.locator('.el-menu-item').first().locator('._fd-fn-list-method').click()
        await formDialog.locator('.CodeMirror').evaluate(element => {
          const cm = element.CodeMirror
          cm.setValue('void "event-editor-form-test";')
        })
        await formDialog.locator('._fd-fn-list-r .el-header').getByRole('button', { name: 'Save', exact: true }).click()
      }
      await writeFormDraft()
      await formDialog.locator('.el-dialog__headerbtn').click()
      await formDialog.waitFor({ state: 'hidden' })
      assert.equal(Number(await formEditor.locator('.el-badge__content').textContent()), formCount)
      await formEditor.getByRole('button', { name: 'Edit', exact: true }).click()
      assert.equal(await configuredGroup.locator('.el-menu-item').count(), formCount)
      if (writable) {
        await writeFormDraft()
        await formDialog.locator('.el-dialog__footer').getByRole('button', { name: 'Ok', exact: true }).click()
        await formDialog.waitFor({ state: 'hidden' })
        assert.equal(Number(await formEditor.locator('.el-badge__content').textContent()), formCount + 1)
        await formEditor.getByRole('button', { name: 'Edit', exact: true }).click()
        await configuredGroup.locator('.el-menu-item').first().locator('._fd-fn-list-method').click()
      }
      await capture(`${unitId}-${mode.replaceAll(' ', '-').toLowerCase()}-form-events`, formDialog)
      await formDialog.locator('.el-dialog__footer').getByRole('button', { name: 'Cancel', exact: true }).click()
      console.log(JSON.stringify({ step: 'form-events', unitId, mode, initialCount: formCount, configuredAndAvailable: true, ok: writable }))
    }
    if (writable) {
      const savedResponse = page.waitForResponse(response => response.url().endsWith(`/forms/${form.id}`) && response.request().method() === 'PUT')
      await page.locator('.form-editor-view .header-actions').getByRole('button', { name: 'Save', exact: true }).click()
      assert.equal((await (await savedResponse).json()).success, true)
      const saved = await api('GET', `/function-units/${unitId}/forms/${form.id}`)
      for (const bucket of ['subForms', 'batchForms']) {
        assert.ok(JSON.stringify(saved.configJson[bucket]).includes('event-editor-test'), `${bucket}: saved hook missing`)
        assert.ok(JSON.stringify(saved.configJson[bucket]).includes('event-editor-form-test'), `${bucket}: saved form event missing`)
      }
      assert.ok(JSON.stringify(saved.configJson.batchForms).includes('current-editor-draft'), 'Adding an event must retain the previous editor draft')
      console.log(JSON.stringify({ step: 'save-load', unitId, formId: form.id, buckets: ['subForms', 'batchForms'] }))
    }
  }
  assert.equal(JSON.stringify((await api('GET', `/function-units/50125/forms/${source.id}`)).configJson), sourceSnapshot)
  assert.deepEqual(errors, [])
  console.log('PASS: existing design unchanged; cloned Form Design and Batch Form scripts saved; no browser runtime errors')
} finally {
  await browser.close()
}
