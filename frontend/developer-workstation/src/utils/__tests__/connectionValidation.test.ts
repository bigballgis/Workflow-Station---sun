import { describe, expect, it } from 'vitest'
import {
  formatConnectionTestFailureMessage,
  hasConnectionNameValidationError,
  isMessageBoxCancel,
  isValidSenderEmail,
  resolveConnectionSaveErrorMessage,
  shouldMapRawMessageToInvalidSenderEmail,
} from '../connectionValidation'

describe('connectionValidation', () => {
  const t = (key: string, params?: Record<string, unknown>) => {
    if (key === 'connection.emailAddressInvalid') return 'invalid sender email'
    if (key === 'connection.testFailed') return 'test failed'
    if (key === 'connection.testFailedDetail') return `test failed: ${params?.detail}`
    if (key === 'common.saveFailed') return 'save failed'
    return key
  }

  it('isValidSenderEmail accepts full addresses and rejects local-part only', () => {
    expect(isValidSenderEmail('user@example.com')).toBe(true)
    expect(isValidSenderEmail('  notify@corp.example.co.uk  ')).toBe(true)
    expect(isValidSenderEmail('1527598351')).toBe(false)
    expect(isValidSenderEmail('user@')).toBe(false)
    expect(isValidSenderEmail('')).toBe(false)
  })

  it('hasConnectionNameValidationError detects backend details.name', () => {
    expect(hasConnectionNameValidationError({ name: 'must be a well-formed email address' })).toBe(true)
    expect(hasConnectionNameValidationError({ host: 'required' })).toBe(false)
    expect(hasConnectionNameValidationError(null)).toBe(false)
  })

  it('shouldMapRawMessageToInvalidSenderEmail maps legacy validation text', () => {
    expect(shouldMapRawMessageToInvalidSenderEmail('Validation failed: (name=must be a valid email address)')).toBe(true)
    expect(shouldMapRawMessageToInvalidSenderEmail('SMTP host is required')).toBe(false)
  })

  it('resolveConnectionSaveErrorMessage prefers connection.emailAddressInvalid for name field errors', () => {
    const error = {
      response: {
        data: {
          error: {
            details: { name: 'must be a well-formed email address' },
          },
        },
      },
    }
    expect(resolveConnectionSaveErrorMessage(error, t, () => 'raw')).toBe('invalid sender email')
  })

  it('formatConnectionTestFailureMessage uses detail when present', () => {
    expect(formatConnectionTestFailureMessage({ detail: 'Authentication failed' }, t)).toBe(
      'test failed: Authentication failed',
    )
    expect(formatConnectionTestFailureMessage({ success: false }, t)).toBe('test failed')
  })

  it('isMessageBoxCancel recognizes Element Plus cancel actions', () => {
    expect(isMessageBoxCancel('cancel')).toBe(true)
    expect(isMessageBoxCancel({ action: 'cancel' })).toBe(true)
    expect(isMessageBoxCancel(new Error('network'))).toBe(false)
  })
})
