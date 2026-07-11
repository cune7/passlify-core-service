---
name: next-work-stripe-integration
description: Payments status — capabilities done, Raiffeisen scaffolded (config-gated), real Stripe SDK still pending
metadata:
  type: project
---

Payments state as of Phase 3 (see [[FEATURES.md]] / `passlify-spec/FEATURES.md`):

- **Payment capabilities (§10) — DONE.** `OrganizerPaymentCapability`: admins grant/suspend/revoke which providers an org may use + allowed currencies; a paid event on a capability-requiring provider (STRIPE/RAIFFEISEN) only publishes with a usable, currency-covering capability. NONE/MOCK/MANUAL are exempt (so MOCK dev/test flows are unchanged).
- **Raiffeisen (Payten/NestPay) — SCAFFOLDED, config-gated.** `RaiffeisenPaymentGateway` + `NestPayHash` (ver3) implemented and unit-tested, but inert until `passlify.raiffeisen.enabled=true` + store key. NOT verified against the bank: field set / hash version / result codes and the form-POST-vs-GET redirect must be confirmed against the Raiffeisen merchant integration doc, with test-env creds (tracked as its own task).
- **Real Stripe SDK — STILL PENDING.** Only `MockPaymentGateway` fully works. User chose Raiffeisen first; Stripe SDK gateway (Checkout Session + webhook `constructEvent` signature verification, mapping `checkout.session.completed`/`payment_intent.succeeded`/`.payment_failed`/`charge.refunded`) is the remaining real processor. Needs Stripe test-mode keys to verify.

**How to apply:** implement gateways against the existing `PaymentGateway` abstraction + `PaymentGatewayRegistry`; keep them config-gated (conditional bean) so the build runs without creds; keep secrets in env and update `ci/.env.prod.example` per [[keep-prod-compose-in-sync]]. Real money code should be verified against the provider's test environment, not shipped blind.
