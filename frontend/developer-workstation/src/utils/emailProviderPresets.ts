export type EmailProviderType = 'GMAIL' | 'OUTLOOK' | 'YAHOO' | 'QQ' | 'NETEASE_163' | 'SMTP'

export interface EmailProviderPreset {
  host: string
  port: number
  useTls: boolean
}

const PRESETS: Record<EmailProviderType, EmailProviderPreset> = {
  GMAIL: { host: 'smtp.gmail.com', port: 587, useTls: true },
  OUTLOOK: { host: 'smtp.office365.com', port: 587, useTls: true },
  YAHOO: { host: 'smtp.mail.yahoo.com', port: 587, useTls: true },
  QQ: { host: 'smtp.qq.com', port: 587, useTls: true },
  NETEASE_163: { host: 'smtp.163.com', port: 465, useTls: true },
  SMTP: { host: '', port: 587, useTls: true }
}

export const EMAIL_PROVIDER_OPTIONS: EmailProviderType[] = [
  'GMAIL',
  'OUTLOOK',
  'YAHOO',
  'QQ',
  'NETEASE_163',
  'SMTP'
]

export function getEmailProviderPreset(type: string | undefined): EmailProviderPreset {
  const key = (type || 'GMAIL') as EmailProviderType
  return PRESETS[key] ?? PRESETS.GMAIL
}

export function normalizeEmailProviderType(type: string | undefined): EmailProviderType {
  if (type && type in PRESETS) {
    return type as EmailProviderType
  }
  return 'GMAIL'
}
