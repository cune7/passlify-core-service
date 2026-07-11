---
name: dont-change-specs-without-asking
description: Rule — never modify domain/feature spec files in passlify-spec/ without asking first; report gaps instead
metadata:
  type: feedback
---

Do **not** edit the domain/feature spec files under `passlify-spec/` (e.g. `EVENT_DOMAIN_SPEC.md` and future per-domain spec files) without asking the user first. When reviewing a spec, report what's missing or inconsistent as a list and let the user decide — never change the spec file myself unprompted.

**Why:** These spec files are the user's source of truth. The plan is to keep one folder of domain/feature specs, review the app against them, and rebuild what's missing from those files. The user owns the specs and drives what goes in them.

**How to apply:** Reviewing a spec → produce a gap/issue report, propose changes, wait for approval before touching the file. Building a feature → implement the app code to match the approved spec (that code is fair game; the spec doc is not). Related: [[ask-when-unsure-dont-guess]].
