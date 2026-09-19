# Tomi BMA Field demo/revision rules

These instructions apply to `tomi-bma-field-demo/`.

- Treat existing numbered revisions (`r*.html`, `r*-patch.js`, etc.) as history. Do not overwrite an older revision for a new feature unless the task explicitly names that revision.
- For new development, extend the active/current revision or create the next revision according to the task and repository pattern.
- Do not silently change `index.html` to point at a different revision unless the task requires promotion of that revision.
- Keep the app usable on iPad Safari and touch-first screens.
- Preserve local/offline storage behavior and existing data whenever possible.
- UI and all user-visible wording must be German.
- Koko and Eli, when present as application personas/assistants, speak German only.
- Avoid desktop-only interactions that require hover, right-click, or a physical keyboard.
- Before finishing, check the browser console paths mentally/staticly for undefined functions, missing element IDs, duplicate listeners, and storage schema mismatches.
