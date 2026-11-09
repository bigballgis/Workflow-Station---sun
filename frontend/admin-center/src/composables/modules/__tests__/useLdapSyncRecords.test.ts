import { defineComponent } from 'vue'
import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import en from '@/i18n/locales/en'
import zhCN from '@/i18n/locales/zh-CN'
import zhTW from '@/i18n/locales/zh-TW'
import { messageTooltip, useLdapSyncRecords } from '../useLdapSyncRecords'

vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('@/api/ldapSync', () => ({ ldapSyncApi: { queryRecords: vi.fn() } }))

function setup() {
  let state!: ReturnType<typeof useLdapSyncRecords>
  mount(defineComponent({ setup() { state = useLdapSyncRecords(); return () => null } }))
  return state
}

describe('LDAP sync records: status', () => {
  it('shows a run that read only some AD groups apart from a clean one and a failed one', () => {
    const { statusTone } = setup()
    expect(statusTone('SUCCESS')).toBe('success')
    expect(statusTone('PARTIAL')).toBe('warning')
    expect(statusTone('FAILED')).toBe('danger')
    expect(statusTone('RUNNING')).toBe('info')
  })

  it('has a label for every status the backend declares, in en, zh-CN and zh-TW', () => {
    // LdapSyncAuditColumnSpec: RUNNING / SUCCESS / PARTIAL / FAILED -> ldapSync.status*
    for (const messages of [en, zhCN, zhTW]) {
      for (const key of ['statusRunning', 'statusSuccess', 'statusPartial', 'statusFailed'] as const) {
        expect(messages.ldapSync[key]).toBeTruthy()
      }
    }
  })
})

describe('LDAP sync records: Message tooltip', () => {
  it('puts each finding of a group run on its own line', () => {
    expect(messageTooltip('Not read, lists unchanged: HK/Ops Approvers; '
      + 'Read incompletely, nobody removed: HERMES-SLASH-MEMBER; '
      + 'No members found (missing or empty AD group): HERMES-NO-SUCH-GROUP'))
      .toBe('Not read, lists unchanged: HK/Ops Approvers\n'
        + 'Read incompletely, nobody removed: HERMES-SLASH-MEMBER\n'
        + 'No members found (missing or empty AD group): HERMES-NO-SUCH-GROUP')
  })

  it('keeps the AD group list of one finding together and shows nothing for no message', () => {
    expect(messageTooltip('Not read, lists unchanged: AD-A, AD-B')).toBe('Not read, lists unchanged: AD-A, AD-B')
    expect(messageTooltip(null)).toBe('')
  })
})
