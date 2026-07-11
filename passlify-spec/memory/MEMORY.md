# Memory Index

- [Don't change specs without asking](dont-change-specs-without-asking.md) — never edit passlify-spec/ domain specs unprompted; report gaps and wait for approval
- [Payments status](next-work-stripe-integration.md) — capabilities done; Raiffeisen scaffolded (config-gated, unverified); real Stripe SDK still pending
- [Keep prod compose in sync](keep-prod-compose-in-sync.md) — maintain ci/docker-compose.prod.yml + .env.prod.example as project evolves
- [Keep Postman collection in sync](keep-postman-collection-in-sync.md) — update docs/ Postman collection as API endpoints change
- [Keycloak identity setup](keycloak-identity-setup.md) — Keycloak is the only user store; realm files for login/registration/Google
- [Organization domain model](organization-domain-model.md) — company/billing data in Postgres (not Keycloak); paid events require a complete COMPANY org; Serbia PIB/MBR rules
- [Validator architecture](validator-architecture.md) — all validation lives in dedicated XxxValidator @Services, not the service layer
- [Ask when unsure, don't guess](ask-when-unsure-dont-guess.md) — stop and ask rather than shipping a plausible-but-unverified rule
