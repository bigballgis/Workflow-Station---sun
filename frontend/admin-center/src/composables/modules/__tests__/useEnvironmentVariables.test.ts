import { describe, expect, it } from 'vitest'
import {
  buildEnvironmentPayload,
  type EnvironmentFormState,
} from '@/composables/modules/useEnvironmentVariables'

function vaultForm(overrides: Partial<EnvironmentFormState> = {}): EnvironmentFormState {
  return {
    varKey: 'email.qq.inbound',
    displayName: 'QQ inbound',
    description: '',
    valueKind: 'VAULT',
    defaultValue: '',
    currentValue: '',
    vaultSecretPath: 'workflow/email/qq',
    vaultPassword: '',
    ...overrides,
  }
}

describe('buildEnvironmentPayload', () => {
  it('rejects missing key or display name', () => {
    const result = buildEnvironmentPayload(vaultForm({ varKey: '  ', displayName: '' }), false)
    expect(result).toEqual({ errorKey: 'config.envKeyRequired' })
  })

  it('create VAULT with password includes vaultPassword', () => {
    const result = buildEnvironmentPayload(vaultForm({ vaultPassword: 'secret' }), false)
    expect(result).toEqual({
      payload: {
        varKey: 'email.qq.inbound',
        displayName: 'QQ inbound',
        description: undefined,
        valueKind: 'VAULT',
        vaultSecretPath: 'workflow/email/qq',
        vaultPassword: 'secret',
      },
    })
  })

  it('create VAULT without password still builds payload', () => {
    const result = buildEnvironmentPayload(vaultForm(), false)
    expect('payload' in result).toBe(true)
    if ('payload' in result) {
      expect(result.payload.vaultPassword).toBeUndefined()
      expect(result.payload.vaultSecretPath).toBe('workflow/email/qq')
    }
  })

  it('edit VAULT never sends vaultPassword', () => {
    const result = buildEnvironmentPayload(vaultForm({ vaultPassword: 'new-secret' }), true)
    expect('payload' in result).toBe(true)
    if ('payload' in result) {
      expect(result.payload.vaultPassword).toBeUndefined()
    }
  })
})
