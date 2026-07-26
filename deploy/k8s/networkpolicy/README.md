# Activepieces egress NetworkPolicy (C-1)

Compensating control **C-1** for decision **D6** (sandbox downgrade to `SANDBOX_CODE_ONLY`,
which removed the kernel-level iptables egress lockdown). See
[`docs/ap-integration/DECISIONS.md#d6`](../../../docs/ap-integration/DECISIONS.md) and
[`OPEN_GATES.md`](../../../docs/ap-integration/OPEN_GATES.md) (AG-05).

## What it does

Denies **all egress** from `app: activepieces` pods except:

| # | Target | Port | Selector kind |
|---|---|---|---|
| 1 | kube-dns / CoreDNS (kube-system) | 53 UDP+TCP | namespace + pod |
| 2 | Istio control plane (istio-system) | 15012 / 15010 / 15014 | namespace |
| 3 | Redis (same namespace) | 6379 | podSelector `app: redis` |
| 4 | External Postgres | 5432 | `ipBlock` (env-specific CIDR) |
| 5 | LLM endpoint (AI Generate) | 443 | `ipBlock` (env-specific CIDR) |

Everything else is denied — crucially **AP → admin-center / user-portal /
developer-workstation / workflow-engine / kafka** (all same-namespace), which the Istio
Sidecar (`egress: ./*`) otherwise allows. This is the lateral-movement surface C-1 cuts.

**Egress only.** Ingress to AP (bridge, browser builder, HERMES webhooks) is untouched.

## Why it is operator-gated (not auto-applied)

Rules 4 & 5 need **environment-specific CIDRs** that are not known at author time
(external DB IP, internal/public LLM endpoint). The file therefore lives outside the
`apply-workflow-station-istio-generated.ps1` scan path and is **not** in `kustomization.yaml`,
so it can never apply with unresolved placeholders and break the pipeline. Fill the CIDRs,
then apply explicitly.

## Render + apply

```bash
sed -e "s/__NAMESPACE__/<ns>/g" \
    -e "s#__AP_EGRESS_POSTGRES_CIDR__#<db-ip>/32#g" \
    -e "s#__AP_EGRESS_LLM_CIDR__#<llm-cidr>#g" \
    deploy/k8s/networkpolicy/activepieces-egress-networkpolicy.yaml | kubectl apply -f -
```

Air-gapped production: `<llm-cidr>` MUST be the internal model-gateway CIDR only — never
`0.0.0.0/0`. If an environment has no LLM egress, delete rule 5 before applying.

## Prerequisite

The cluster CNI must **enforce** NetworkPolicy (Calico, Cilium, …). On a CNI that ignores
NetworkPolicy this manifest is inert — confirm enforcement first.

## In-cluster verification (run before trusting in production)

`AP` = an `app: activepieces` pod; `NS` = the namespace.

```bash
AP=$(kubectl -n "$NS" get pod -l app=activepieces -o jsonpath='{.items[0].metadata.name}')

# MUST FAIL (blocked) — lateral movement to HERMES backends:
kubectl -n "$NS" exec "$AP" -c activepieces -- \
  sh -c 'wget -q -T5 -O- http://admin-center-service:8080/api/v1/admin/actuator/health; echo "rc=$?"'
kubectl -n "$NS" exec "$AP" -c activepieces -- \
  sh -c 'nc -z -w5 workflow-engine-service 8080; echo "rc=$?"'   # expect non-zero / timeout

# MUST SUCCEED (allowed) — AP's own dependencies:
kubectl -n "$NS" exec "$AP" -c activepieces -- sh -c 'nc -z -w5 redis-service 6379; echo redis rc=$?'
# DNS resolves (rule 1); AI Generate end-to-end still returns HTTP 200 (rules 4+5+DNS).
```

Expected: HERMES-backend probes fail (timeout/refused), Redis + DNS + AI Generate succeed.
If a HERMES-backend probe SUCCEEDS, the CNI is not enforcing egress policy — stop and fix
the CNI before relying on C-1.

## Note on Istio

Istio's Sidecar egress (`./*`, `istio-system/*`) is an L7 mesh control and does not by
itself stop raw L3/L4 connections that bypass the sidecar. This NetworkPolicy is the L3/L4
enforcement. Keep both. Tightening the Istio Sidecar `egress.hosts` to only AP's real
dependencies is a complementary hardening, tracked separately.
