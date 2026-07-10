---
name: keep-postman-collection-in-sync
description: Keep the Postman collection under docs/ updated as API endpoints change
metadata:
  type: feedback
---

Keep the Postman collection `docs/passlify-core.postman_collection.json` (with its `docs/passlify-core.local.postman_environment.json` and `docs/passlify-core.staging.postman_environment.json` environments) updated as the API evolves — whenever an endpoint is added, changed, or removed, reflect it in the collection.

**Why:** The user uses this collection to import and test all APIs, and treats it as a living, maintained artifact — same spirit as [[keep-prod-compose-in-sync]]. A stale collection means manual re-work when testing.

**How to apply:** When adding/changing a controller endpoint, add or update the matching request in the collection (correct method, path, auth, body, and a folder per module — e.g. the Organization folder). Auth uses the `passlify-client` direct-access-grant flow (see [[keycloak-identity-setup]]).
