---
name: keep-prod-compose-in-sync
description: User wants the production docker-compose kept updated as the project evolves
metadata: 
  node_type: memory
  type: project
  originSessionId: 66c6979b-a593-4ee1-a4ef-908e9a2ad921
---

The user wants `ci/docker-compose.prod.yml` kept up to date as we work — whenever we add/change infra needs (new service, new env var, config change), reflect it in the prod compose (and `ci/.env.prod.example`), not just the local dev `ci/docker-compose.yml`.

**Why:** They plan to run this in production and treat the prod compose as a living, maintained artifact rather than a one-off.

**How to apply:** When a change touches infrastructure or configuration (DB, Keycloak, mail, secrets, new backing service, new env var in application.yml), update `ci/docker-compose.prod.yml` and `ci/.env.prod.example` to match. Keep secrets out of the compose file — everything sensitive goes through the env file.

Layout: all CI/infra lives under `ci/` (compose files, keycloak realm, future pipelines). Dev = `ci/docker-compose.yml` (start-dev, ephemeral H2, mailpit). Prod = `ci/docker-compose.prod.yml` (Keycloak prod mode + own persistent Postgres, real external SMTP, no secrets in file).
