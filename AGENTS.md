# Tomi BMA Field — Codex project instructions

## Purpose
Maintain the Tomi BMA Field web/iPad project as a practical offline-first BMA field application.

## Core rules
- All application UI, labels, dialogs, notifications, generated user-facing text, and mascot/persona speech must be in German unless explicitly requested otherwise.
- Preserve offline-first behavior and Safari/iPad usability.
- Do not remove existing working features while implementing a new one.
- Do not invent backend tables, APIs, credentials, routes, or data fields. Inspect the current code and connected backend conventions first.
- Never hard-code secrets or private tokens.
- Keep changes narrowly scoped to the requested Tomi BMA feature. Do not refactor unrelated Android/demo code in this repository.
- Preserve previous working revisions unless the task explicitly asks to alter that revision.
- When the user corrects a recurring rule, update the nearest relevant `AGENTS.md`.

## Work style
- Read the affected HTML/JS/CSS before changing behavior.
- Prefer a complete working implementation over placeholders.
- Check for syntax errors, missing DOM selectors, broken event handlers, storage-key mismatches, and offline regressions.
- For every completed task, report changed files and the checks performed.
