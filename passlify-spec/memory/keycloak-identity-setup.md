---
name: keycloak-identity-setup
description: How authentication/users work — Keycloak is the sole identity store; realm config for login/registration/Google
metadata: 
  node_type: memory
  type: project
  originSessionId: 66c6979b-a593-4ee1-a4ef-908e9a2ad921
---

Identity lives entirely in **Keycloak** — the app has **no user/account table**. The DB only stores references: `event.organizer_id` = Keycloak `sub`; `order.customer_email` / `ticket.owner_email` are plain strings (checkout is guest, no account needed). See [[keep-prod-compose-in-sync]].

**Realm files** (under `ci/keycloak/`):
- `passlify-realm.json` — DEV. registration on, email-as-username, reset-password, remember-me, `verifyEmail:false` (dev convenience), SMTP → Mailpit (`passlify-mailpit:1025`, view at http://localhost:8025).
- `passlify-realm.prod.json` — PROD. `sslRequired:external`, `verifyEmail:true`, real-domain redirect URIs (app.passlify.rs), external SMTP, **no test users**. Mounted by `ci/docker-compose.prod.yml`.

**Clients:** `passlify-web` (public, browser SPA, Authorization Code + **PKCE S256** — the real login/registration redirect flow) and `passlify-client` (public, direct-access-grant, used by Postman + tests + for manually viewing the login/registration pages since it doesn't force PKCE).

**Google login:** a `google` identity provider exists in both realms but with placeholder creds (`REPLACE_WITH_GOOGLE_CLIENT_ID/SECRET`). Keycloak realm-import does NOT reliably substitute env vars for IdP secrets, so real Google creds must be pasted into the JSON placeholders (or set in Admin Console). Google authorized redirect URI = `{keycloakBase}/realms/passlify/broker/google/endpoint`.

**Dev keycloak is ephemeral H2 + `--import-realm` (OVERWRITE):** to apply realm-file edits, recreate the container: `docker compose -f ci/docker-compose.yml up -d --force-recreate keycloak`. Admin console: http://localhost:8080/admin (admin/admin).
