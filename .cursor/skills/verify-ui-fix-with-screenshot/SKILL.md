---
name: verify-ui-fix-with-screenshot
description: >-
  After any UI or visual bug fix, rebuild if needed, run Playwright (or equivalent)
  to capture before/after screenshots, save artifacts under verification-screenshots/,
  and cite paths in the response. Use when fixing frontend bugs, layout/parity issues,
  modal/form empty states, or when the user asks for screenshot verification.
---

# Verify UI Fixes With Screenshots

See the canonical copy at `~/.cursor/skills/verify-ui-fix-with-screenshot/SKILL.md` (personal, all projects).

## Workflow Station quick path

1. Fix code → `npm run build` in affected app → rebuild `*-frontend` Docker service.
2. `cd frontend && npm run verify:screenshot -- --app portal|admin|dw --url ... --name ...`
3. PNG → `frontend/<app>/verification-screenshots/` — **never delete**.
4. Cite paths in PR / issue / chat.

Rule: `.cursor/rules/frontend-screenshot-verification.mdc`
