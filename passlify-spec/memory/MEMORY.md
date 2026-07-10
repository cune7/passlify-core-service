# Memory Index

- [Keep prod compose in sync](keep-prod-compose-in-sync.md) — maintain ci/docker-compose.prod.yml + .env.prod.example as project evolves
- [Keycloak identity setup](keycloak-identity-setup.md) — Keycloak is the only user store; realm files for login/registration/Google
- [Organization domain model](organization-domain-model.md) — company/billing data in Postgres (not Keycloak); paid events require a complete COMPANY org; Serbia PIB/MBR rules
- [Validator architecture](validator-architecture.md) — all validation lives in dedicated XxxValidator @Services, not the service layer
- [Ask when unsure, don't guess](ask-when-unsure-dont-guess.md) — stop and ask rather than shipping a plausible-but-unverified rule
