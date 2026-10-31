export async function auditMetrics(page, dialog) {
 const field=title=>dialog.locator('.el-form-item').filter({has:page.locator('label',{hasText:new RegExp('^'+title+'$')})}).first()
 const left=await field('Audit left column').boundingBox(),right=await field('Audit right column').boundingBox()
 const result={columnsSameRow:Math.abs(left.y-right.y)<5,range:await dialog.locator('.el-date-editor--daterange').count(),time:await dialog.locator('.el-date-editor--time').count(),tabs:await dialog.locator('.el-tabs').count(),collapse:await dialog.locator('.el-collapse').count(),nestedCardTitle:await dialog.getByText('Audit inner card',{exact:true}).count(),sectionTitle:await dialog.getByText('Audit section title',{exact:true}).count(),switchLabels:(await field('Audit switch').innerText()).includes('Audit enabled'),wordCountDisabled:await field('Audit no word counter').locator('.el-input__count').count(),readOnlyDisabled:await field('Audit read only').locator('input').isDisabled()}
 const number=field('Audit number step 5');await number.locator('input').fill('0');await number.locator('.el-input-number__increase').click();result.incrementValue=await number.locator('input').inputValue()
 const select=field('Audit searchable select');result.defaultSelectPlaceholder=await select.locator('.el-select__placeholder').innerText();result.searchInputReadOnly=await select.locator('input').first().getAttribute('readonly');await select.locator('.el-select').click()
 const option=page.locator('.el-select-dropdown:visible .el-select-dropdown__item').filter({hasText:'Audit disabled option'});await option.waitFor();result.disabledOptionClass=await option.getAttribute('class');await page.keyboard.press('Escape')
 const title=dialog.getByText('Audit section title',{exact:true}).first();result.sectionFontSize=await title.count()?await title.evaluate(node=>getComputedStyle(node).fontSize):null
 return result
}
