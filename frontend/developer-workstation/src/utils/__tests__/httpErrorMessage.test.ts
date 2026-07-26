import { describe, expect, it } from 'vitest'
import {
  isGatewayUpstreamDnsFailure,
  pickHttpErrorBodyMessage,
  resolveUserFacingHttpMessage,
} from '@/utils/httpErrorMessage'

describe('httpErrorMessage', () => {
  it('isGatewayUpstreamDnsFailure detects Kong upstream DNS errors', () => {
    expect(isGatewayUpstreamDnsFailure('name resolution failed')).toBe(true)
    expect(isGatewayUpstreamDnsFailure('failed to retry the dns/balancer resolver')).toBe(true)
    expect(isGatewayUpstreamDnsFailure('System SMTP host is not configured')).toBe(false)
  })

  it('resolveUserFacingHttpMessage maps Kong DNS body to gatewayUpstreamUnavailable', () => {
    const t = (key: string) =>
      key === 'api.gatewayUpstreamUnavailable' ? 'gateway retry hint' : key
    const msg = resolveUserFacingHttpMessage(
      { response: { status: 503, data: { message: 'name resolution failed' } } },
      t,
    )
    expect(msg).toBe('gateway retry hint')
  })

  it('pickHttpErrorBodyMessage reads Kong JSON message field', () => {
    expect(pickHttpErrorBodyMessage({ message: 'name resolution failed' })).toBe(
      'name resolution failed',
    )
  })
})
