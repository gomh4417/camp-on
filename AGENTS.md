# CAMP:ON project rules

- Preserve existing Kotlin / Compose versions and single app module.
- Korean UI and reports. Restrained charcoal glass panels, accessible touch targets and system insets.
- Pretendard Regular, Medium, ExtraBold only. Avoid green-heavy multicolor styling.
- Complete implementation before one final verification phase. Do not repeatedly build while editing.
- Push completed work to https://github.com/gomh4417/camp-on.git; never publish local credentials or IDE caches.
- Consult official Android and Pleos documentation before API changes; never invent APIs or permissions.
- Vehicle reads and writes have separate capabilities. Unknown parking / restrictions / stale state blocks control.
- UI uses connected Pleos values only. Connection errors never silently substitute data. Keep user-facing copy concise; no repeated test-environment disclaimers.
- Missing values stay unknown. Every observation retains source and timestamp.
- One shared camping session. Persist presets, initial values, changed values and unresolved restore results.
- Restore only app-changed values still matching last app writes; manual changes require review.
- Energy calculation uses usable capacity, explicit units and separate return-energy shortfall.
- No automatic physical actuation on driving, process death, or session end.
- Use IDE file tools to preserve editor buffers. Local Graphify AST only, refresh after edits.
- Verify calculation boundaries, partial failure, duplicate commands, restart and restoration.
