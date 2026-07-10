---
name: validator-architecture
description: Convention — all validation lives in dedicated XxxValidator @Services, not in the service layer
metadata:
  type: feedback
---

Every module keeps its validation logic in a dedicated `XxxValidator` Spring `@Service` injected into the matching `XxxService` (e.g. `EventValidator` → `EventService`, `CheckoutValidator` → `CheckoutService`, `OrganizationValidator`, `PaymentValidator`, `ScanValidator`, `CustomFieldValidator`, `TicketTypeValidator`).

**Why:** The user explicitly asked to move ALL validation out of the services into these validators, applied uniformly across every module — services orchestrate, validators enforce rules.

**How to apply:** When adding a new module or a new rule, put the validation in (or create) the module's `*Validator` rather than inline in the service. Keep this pattern consistent across every module. See [[organization-domain-model]].
