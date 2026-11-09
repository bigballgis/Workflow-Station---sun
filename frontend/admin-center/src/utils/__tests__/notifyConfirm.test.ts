import { afterEach, describe, expect, it } from 'vitest'
import { nextTick } from 'vue'
import { notifyConfirm } from '../notify'

/** Waits until the message box is in the document, or fails. */
async function boxElement(): Promise<HTMLElement> {
  for (let i = 0; i < 50; i++) {
    const box = document.querySelector<HTMLElement>('.el-message-box')
    if (box) return box
    await nextTick()
    await new Promise(resolve => setTimeout(resolve, 10))
  }
  throw new Error('message box did not open')
}

/**
 * A confirmation closed with the X button or the Esc key rejects with 'cancel', like the Cancel button: notifyConfirm
 * does not set Element Plus's distinguishCancelAndClose, which would make them reject with 'close'. Callers that show
 * a failure toast skip both values.
 */
describe('notifyConfirm dismissal', () => {
  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('rejects when the X button closes it', async () => {
    const confirmed = notifyConfirm('Disable 2 function units?', 'Confirm')
    const box = await boxElement()

    box.querySelector<HTMLElement>('.el-message-box__headerbtn')!.click()

    await expect(confirmed).rejects.toBe('cancel')
  })

  it('rejects when Esc closes it', async () => {
    const confirmed = notifyConfirm('Archive 2 function units?', 'Confirm')
    const box = await boxElement()

    box.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', code: 'Escape', bubbles: true }))
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', code: 'Escape', bubbles: true }))

    await expect(confirmed).rejects.toBe('cancel')
  })
})
