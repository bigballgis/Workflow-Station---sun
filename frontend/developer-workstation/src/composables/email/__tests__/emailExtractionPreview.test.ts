import { describe, expect, it } from 'vitest'
import {
  combinedSampleTextAndHtml,
  sourceText,
  type ExtractionSamplePreview,
} from '../emailExtractionPreview'

function sample(partial: Partial<ExtractionSamplePreview>): ExtractionSamplePreview {
  return {
    subject: '',
    from: '',
    to: '',
    cc: '',
    replyTo: '',
    date: '',
    messageId: '',
    text: '',
    html: '',
    ...partial,
  }
}

describe('emailExtractionPreview body merge', () => {
  it('does not duplicate multipart/alternative plain + html', () => {
    const mail = sample({
      text: 'hello all,\r\n\r\nthis is a test email',
      html: '<div>hello all,<br>this is a test email</div>',
    })
    expect(combinedSampleTextAndHtml(mail)).toBe('hello all,\r\n\r\nthis is a test email')
    expect(sourceText(mail, 'TEXT_AND_HTML')).toBe('hello all,\r\n\r\nthis is a test email')
  })

  it('TEXT keeps the plain part when HTML alternative exists', () => {
    const mail = sample({
      text: 'hello all,\n\nthis is a test email',
      html: '<p>hello all, this is a test email</p>',
    })
    expect(sourceText(mail, 'TEXT')).toBe('hello all,\n\nthis is a test email')
  })
})
