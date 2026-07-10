---
name: ask-when-unsure-dont-guess
description: Working preference — when unsure what to build/validate, ask rather than ship a plausible-but-unverified implementation
metadata:
  type: feedback
---

When unsure what to validate or build, the user wants me to stop and ask rather than guess. Stated directly as "take a break and ask me."

**Why:** A plausible-but-unverified implementation (e.g. an unproven validation rule) can be worse than none — it silently rejects valid input or encodes wrong assumptions. This is exactly why the Serbian PIB check-digit was deferred rather than shipped unverified (see [[organization-domain-model]]).

**How to apply:** Surface uncertainty and ask a focused question before committing to a design or a rule I'm not confident about, instead of shipping something that merely looks right.
