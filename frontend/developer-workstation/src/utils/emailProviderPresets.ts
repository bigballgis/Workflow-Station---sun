export type EmailProviderType = 'GMAIL' | 'OUTLOOK' | 'YAHOO' | 'QQ' | 'NETEASE_163' | 'SMTP'

/** Provider label only — SMTP host/port/TLS are always entered manually in the UI. */
export const EMAIL_PROVIDER_OPTIONS: EmailProviderType[] = [
  'GMAIL',
  'OUTLOOK',
  'YAHOO',
  'QQ',
  'NETEASE_163',
  'SMTP'
]

const KNOWN: ReadonlySet<string> = new Set(EMAIL_PROVIDER_OPTIONS)

export function normalizeEmailProviderType(type: string | undefined): EmailProviderType {
  if (type && KNOWN.has(type)) {
    return type as EmailProviderType
  }
  return 'SMTP'
}
