---
name: next-work-stripe-integration
description: Agreed next task — replace the mock payment gateway with real Stripe integration
metadata:
  type: project
---

The agreed next piece of work (decided 2026-07-11) is to wire up **real Stripe** payments, replacing the current `MockPaymentGateway`. Everything else in the MVP spine is already built (see [[FEATURES.md]] / `passlify-spec/FEATURES.md`).

**Current state:** the payment layer has a clean abstraction ready to receive Stripe — `PaymentGateway` + `PaymentGatewayRegistry` + `PaymentProvider` enum, plus a webhook idempotency ledger (`WebhookEvent` / `WebhookEventKey`) — but no Stripe SDK is present; only `MockPaymentGateway` implements the gateway.

**How to apply:** implement a real Stripe `PaymentGateway` (Checkout Session creation + webhook signature verification and event handling: `checkout.session.completed`, `payment_intent.succeeded/.payment_failed`, `charge.refunded`), register it via `PaymentGatewayRegistry`, and keep secrets in env (update `ci/.env.prod.example` per [[keep-prod-compose-in-sync]]).
