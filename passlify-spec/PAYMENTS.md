# Passlify — Payments & Payment Providers

> How checkout payments work: the provider model, the admin-approved capability system,
> the gateway abstraction, and the concrete Raiffeisen (UPC) integration. Source of
> truth for wiring a real processor. See also `FEATURES.md` (live feature index).

## Overview

An event carries a `PaymentProvider`. At checkout, `PaymentService.createSession`
resolves the matching **gateway** (a `PaymentGateway` implementation), which creates a
hosted checkout; the provider later calls our **webhook**, which the same gateway
verifies and normalizes into a provider-agnostic `PaymentEvent` (PAID / FAILED /
REFUNDED / IGNORED). Every event is recorded in a **webhook idempotency ledger**
(`webhook_event`, keyed on `(provider, eventId)`) before processing, so retries and
duplicates are safe.

Adding a processor = implement `PaymentGateway`; the checkout/webhook flow is unchanged.

## Providers  (`PaymentProvider`)

| Provider | Meaning | Needs admin capability? | Gateway status |
|----------|---------|--------------------------|----------------|
| `NONE` | Free event — no payment processing | no | n/a |
| `MOCK` | Fully simulated (MVP + tests) | no | ✅ `MockPaymentGateway` |
| `MANUAL` | Offline / bank-transfer / admin-confirmed | no | ⏳ not built |
| `RAIFFEISEN` | Raiffeisen Serbia via the UPC e-Commerce Connect Gateway | **yes** | 🟡 built, config-gated, needs live verification |
| `STRIPE` | Stripe Checkout | **yes** | ⏳ still `MockPaymentGateway` (pending) |

`requiresCapability()` is true for `STRIPE` and `RAIFFEISEN` (real external money
processors); `NONE`/`MOCK`/`MANUAL` are exempt.

## Payment capabilities (§10)

Organizers cannot self-assign a real processor. An **admin** grants an
`OrganizerPaymentCapability` per `(organization, provider)` with allowed currencies and
an optional validity window. A paid event on a capability-requiring provider can only
**publish** when its organization holds a *usable* capability (ACTIVE, in-window,
covering the event currency) — else publication readiness reports
`PAYMENT_PROVIDER_NOT_APPROVED` / `CURRENCY_NOT_SUPPORTED` and publish is refused.

Endpoints:
- `POST /api/v1/admin/organizations/{organizationId}/payment-capabilities` — grant (ADMIN)
- `GET  /api/v1/admin/organizations/{organizationId}/payment-capabilities` — list (ADMIN)
- `PATCH /api/v1/admin/payment-capabilities/{capabilityId}` — suspend/revoke/re-activate (ADMIN)
- `GET  /api/v1/me/payment-capabilities` — the caller's approved providers

Statuses: `PENDING · ACTIVE · SUSPENDED · REVOKED · EXPIRED`. Migration V12.

---

## Raiffeisen — UPC "e-Commerce Connect Gateway"

**Reference:** `passlify-spec/docs/rfz-ecommerce-integration-docs/Shop_Gateway_Interface_Token_eng.pdf`
(the "Upitnik…" PDF is the merchant onboarding questionnaire).

Raiffeisen Serbia runs the UPC gateway (**not** Payten/NestPay). It is redirect-based
and secured with **RSA-SHA1 signatures** over ordered `;`-delimited datafiles — the
merchant private key signs outgoing requests; the gateway's public key verifies
responses. (Matches the reference `openssl_sign($data, …)` + `base64_encode`.)

### Flow

```
createSession(order)
  └─ returns checkoutUrl → our redirect page:
       GET /api/v1/public/payments/raiffeisen/redirect/{orderRef}   (RaiffeisenRedirectController)
         └─ renders an auto-submitting HTML form that POSTs the signed
            request fields to the bank's gateway (…/go/enter)
  ┌─ buyer enters card on the bank's secure page ─┐
  ▼                                               ▼
bank → NOTIFY_URL (server-to-server, pre-registered at the bank):
   POST /api/v1/webhooks/raiffeisen/notify   (RaiffeisenNotifyController)
     ├─ verify signature (gateway public key) + record/process event (idempotent)
     └─ MUST answer "Response.action=approve" (success) or "reverse"
        — without an approve the gateway AUTO-REVERSES the payment
bank → SUCCESS_URL / FAILURE_URL (browser redirect; also pre-registered)
```

SUCCESS/FAILURE/NOTIFY URLs are configured **per-terminal at the bank**, not sent per
request.

### Configuration

| Property | Env | Notes |
|----------|-----|-------|
| `passlify.raiffeisen.enabled` | `RAIFFEISEN_ENABLED` | `true` activates the gateway + its two controllers. Default `false` (inert). |
| `passlify.raiffeisen.gateway-url` | `RAIFFEISEN_GATEWAY_URL` | e.g. `…/go/enter` |
| `passlify.raiffeisen.merchant-id` | `RAIFFEISEN_MERCHANT_ID` | assigned by the bank |
| `passlify.raiffeisen.terminal-id` | `RAIFFEISEN_TERMINAL_ID` | assigned by the bank |
| `passlify.raiffeisen.locale` | `RAIFFEISEN_LOCALE` | `en` / `sr` / … |
| `passlify.raiffeisen.private-key` | `RAIFFEISEN_PRIVATE_KEY` | merchant RSA private key, **PKCS#8 PEM** |
| `passlify.raiffeisen.gateway-public-key` | `RAIFFEISEN_GATEWAY_PUBLIC_KEY` | gateway public key or server certificate PEM |

Keys are generated per the doc (`run.bat <MerchantID>` → `.pem`/`.pub`/`.crt`); send the
`.crt` to UPC. Convert the private key to PKCS#8 if needed:
`openssl pkcs8 -topk8 -nocrypt -in merchant.pem -out merchant.pkcs8.pem`. The gateway
public key can be extracted from the server cert: `openssl x509 -pubkey -noout`.

### Signature datafiles (field order is load-bearing)

- **Request:** `MerchantId;TerminalId;PurchaseTime;OrderId;Currency;Amount;SD;`
- **Response/NOTIFY:** `MerchantId;TerminalId;PurchaseTime;OrderId;Xid;Currency;Amount;SD;TranCode;ApprovalCode;`

`PurchaseTime` = `yyMMddHHmmss`. `Amount`/`TotalAmount` are in the smallest currency
units (para/cents). `OrderId` ≤ 20 chars (we use the event/order UUID hex prefix).
Currency is ISO-4217 numeric (RSD `941`, EUR `978`, USD `840`). `TranCode` `000` =
successful authorization.

### Implementation status

- ✅ `UpcSignature` (RSA-SHA1 sign/verify, PKCS#8 + cert/public-key PEM loading)
- ✅ `RaiffeisenPaymentGateway` — signed request fields, response verification + TranCode mapping
- ✅ `RaiffeisenRedirectController` — auto-submit POST form
- ✅ `RaiffeisenNotifyController` — signature-verified `approve`/`reverse` handshake
- ✅ Config-gated (`enabled=false` default); unit + enabled-path MockMvc tests
- ✅ `webhook_event.payload` is `text` (V13) so the form-encoded NOTIFY body persists

### Open items (need the UPC test server + real merchant creds)

1. Confirm the request/response **datafile field order** byte-for-byte (a mismatch → 405 "Signature invalid").
2. Confirm the exact **NOTIFY response body** the gateway expects for approve/reverse.
3. **Form-encoding of the base64 `Signature`** on the NOTIFY POST (the `+` → space pitfall).
4. **Result codes** mapping beyond `000`.
5. Optional **tokenization** (saved cards, §12) — not implemented.

To go live: set `RAIFFEISEN_ENABLED=true` + the id/key env vars (see `ci/.env.prod.example`),
register SUCCESS/FAILURE/NOTIFY URLs at the bank, grant the org a RAIFFEISEN capability,
then run a test transaction end-to-end.

---

## MOCK provider (dev/test)

`createSession` mints fake ids + a fake checkout URL. Drive outcomes by POSTing to
`/api/v1/webhooks/mock`, e.g. `{"type":"PAID","sessionId":"mock_sess_…"}`
(`PAID`/`FAILED`/`REFUNDED`). No signature, no external account.

## Stripe (pending)

Not yet implemented — checkout still resolves to `MockPaymentGateway` for `STRIPE`.
Planned: `StripePaymentGateway` (Checkout Session + `Webhook.constructEvent` signature
verification, mapping `checkout.session.completed` / `payment_intent.succeeded` /
`.payment_failed` / `charge.refunded`), config-gated like Raiffeisen. Needs Stripe
test-mode keys.
