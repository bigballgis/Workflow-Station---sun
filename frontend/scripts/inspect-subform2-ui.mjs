import { chromium } from 'playwright'
import { loginViaUnifiedSso } from './playwright-login.mjs'

const TASK_ID = process.argv[2] || '61a40a76-632b-11f1-ba54-9a0f0d4cc145'
const browser = await chromium.launch({ headless: true })
const page = await (await browser.newContext({ viewport: { width: 1600, height: 1400 } })).newPage()
await loginViaUnifiedSso(page, 'portal')
await page.goto(`http://localhost:3000/portal/tasks/${TASK_ID}`, { waitUntil: 'domcontentloaded' })
await page.waitForTimeout(12000)

const info = await page.evaluate(() => {
  const cards = [...document.querySelectorAll('.el-card, .sub-table-field')]
  return cards.map(card => {
    const title = card.querySelector('.el-card__header, .sub-table-header, h3, h4, .panel-title')?.textContent?.trim() || ''
    const rows = card.querySelectorAll('.el-table__body-wrapper tbody tr').length
    const inputs = [...card.querySelectorAll('.el-form-item')].map(i => ({
      label: i.querySelector('.el-form-item__label')?.textContent?.trim(),
      val: i.querySelector('input')?.value?.slice(0, 40),
    })).filter(x => x.label)
    return { title: title.slice(0, 60), rows, inputs: inputs.slice(0, 8) }
  }).filter(c => c.title || c.rows > 0 || c.inputs.length > 0)
})

console.log(JSON.stringify(info, null, 2))
const ageItem = info.find(c => c.inputs?.some(i => i.label?.toLowerCase() === 'age'))
console.log('AGE', ageItem?.inputs?.find(i => i.label?.toLowerCase() === 'age')?.val)
const att = info.find(c => /attachment/i.test(c.title || ''))
console.log('ATTACHMENT_ROWS', att?.rows)
await browser.close()
