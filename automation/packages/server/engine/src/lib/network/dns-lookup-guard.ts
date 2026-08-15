import dns from 'node:dns'
import { ssrfIpClassifier } from '@activepieces/core-utils'
import { SSRFBlockedError } from '@activepieces/shared'
import type { GuardPolicy, UninstallFn } from './ssrf-guard'

export function installDnsLookupGuard(policy: GuardPolicy): UninstallFn {
    const originalLookup = dns.lookup
    const originalPromisesLookup = dns.promises.lookup
    const boundLookup = originalLookup.bind(dns) as typeof dns.lookup
    const boundPromisesLookup = originalPromisesLookup.bind(dns.promises) as typeof dns.promises.lookup

    const guardedLookup = buildGuardedCallbackLookup({ policy, boundLookup })
    // Preserve util.promisify.custom + __promisify__ that Node attaches to the original
    // callback-style `lookup` — otherwise `dns.promises.lookup` re-derivation breaks.
    Object.assign(guardedLookup, originalLookup)

    const guardedPromisesLookup = buildGuardedPromiseLookup({ policy, boundPromisesLookup })

    assignLookup(dns, guardedLookup)
    assignPromisesLookup(dns.promises, guardedPromisesLookup)

    return () => {
        assignLookup(dns, originalLookup)
        assignPromisesLookup(dns.promises, originalPromisesLookup)
    }
}

function buildGuardedCallbackLookup({ policy, boundLookup }: BuildCallbackLookupParams): GuardedCallbackLookup {
    return function lookup(
        hostname: string,
        optionsOrCallback: dns.LookupOptions | DnsLookupCallback,
        maybeCallback?: DnsLookupCallback,
    ): void {
        const callback = typeof optionsOrCallback === 'function' ? optionsOrCallback : maybeCallback
        const callerOptions = typeof optionsOrCallback === 'object' && optionsOrCallback !== null
            ? optionsOrCallback
            : undefined

        const onResolved: DnsLookupCallback = (err, address, family) => {
            if (err || !callback) {
                callback?.(err, address, family)
                return
            }
            const entries = toAddressList({ address, family })
            const blocked = isHostAllowListed({ hostname, allowList: policy.allowList })
                ? undefined
                : findBlockedEntry({ entries, allowList: policy.allowList })
            if (blocked) {
                callback(buildBlockedError({ host: hostname, ip: blocked.address }), '' as string, 0)
                return
            }
            if (callerOptions?.all) {
                callback(null, entries)
                return
            }
            const first = entries[0]
            if (!first) {
                callback(null, '' as string, 0)
                return
            }
            callback(null, first.address, first.family)
        }

        boundLookup(hostname, { ...callerOptions, all: true }, onResolved)
    } as GuardedCallbackLookup
}

function buildGuardedPromiseLookup({ policy, boundPromisesLookup }: BuildPromiseLookupParams): GuardedPromiseLookup {
    return async function promiseLookup(hostname, options) {
        const allEntries = await boundPromisesLookup(hostname, { ...options, all: true })
        const blocked = isHostAllowListed({ hostname, allowList: policy.allowList })
            ? undefined
            : findBlockedEntry({ entries: allEntries, allowList: policy.allowList })
        if (blocked) {
            throw buildBlockedError({ host: hostname, ip: blocked.address })
        }
        return options?.all ? allEntries : allEntries[0]
    }
}

function toAddressList({ address, family }: ToAddressListParams): dns.LookupAddress[] {
    if (Array.isArray(address)) return address
    return [{ address: address as string, family: family ?? 4 }]
}

/**
 * HERMES-PATCH-006: let AP_SSRF_ALLOW_LIST take hostnames, not just IPs / CIDRs.
 *
 * ssrfIpClassifier matches RESOLVED addresses, so a bare hostname in the allow list silently
 * never matched — the one thing every operator writes first. Requiring a literal IP means
 * hand-copying a Kubernetes ClusterIP that changes whenever the Service is recreated, and the
 * failure mode is a runtime SSRF block long after deploy. Matching the requested hostname here
 * keeps the config declarative (e.g. developer-workstation-service.<ns>).
 *
 * Scope is deliberately the DNS path only: a connect straight to a raw IP never passes through
 * a hostname, so those destinations still need an IP/CIDR entry — no silent widening.
 * Matching is case-insensitive with the trailing FQDN dot normalised; an entry containing '/'
 * or ':' is a CIDR / IP and is left to the IP classifier.
 */
function isHostAllowListed({ hostname, allowList }: { hostname: string, allowList: string[] }): boolean {
    const normalizedHost = normalizeHost(hostname)
    if (!normalizedHost) return false
    return allowList.some((entry) => {
        if (entry.includes('/') || entry.includes(':')) return false
        return normalizeHost(entry) === normalizedHost
    })
}

function normalizeHost(value: string): string {
    return value.trim().toLowerCase().replace(/\.$/, '')
}

function findBlockedEntry({ entries, allowList }: FindBlockedEntryParams): dns.LookupAddress | undefined {
    return entries.find((entry) => ssrfIpClassifier.isBlockedIp({ ip: entry.address, allowList }))
}

function buildBlockedError({ host, ip }: BuildBlockedErrorParams): SSRFBlockedError {
    return new SSRFBlockedError({ host, ip })
}

function assignLookup(target: typeof dns, fn: typeof dns.lookup): void {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    (target as any).lookup = fn
}

function assignPromisesLookup(target: typeof dns.promises, fn: typeof dns.promises.lookup): void {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    (target as any).lookup = fn
}

type DnsLookupCallback = (err: NodeJS.ErrnoException | null, address: string | dns.LookupAddress[], family?: number) => void

type GuardedCallbackLookup = typeof dns.lookup

type GuardedPromiseLookup = typeof dns.promises.lookup

type BuildCallbackLookupParams = {
    policy: GuardPolicy
    boundLookup: typeof dns.lookup
}

type BuildPromiseLookupParams = {
    policy: GuardPolicy
    boundPromisesLookup: typeof dns.promises.lookup
}

type ToAddressListParams = {
    address: string | dns.LookupAddress[]
    family?: number
}

type FindBlockedEntryParams = {
    entries: dns.LookupAddress[]
    allowList: string[]
}

type BuildBlockedErrorParams = {
    host: string
    ip: string
}
