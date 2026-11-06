# CAMP:ON project rules

- Preserve existing Kotlin / Compose versions and single app module.
- Korean UI and reports. Image-led cabin workspace: visual climate/light/seat entry cards and selectable seat regions, not a long text-only settings list. Restrained charcoal surfaces, accessible touch targets and system insets.
- Keep current vehicle values distinct from pending edits. Pin the explicit apply action and its result outside scrolling content; never imply +/- has already actuated the vehicle.
- Pretendard Regular, Medium, ExtraBold only. Avoid green-heavy multicolor styling.
- Complete implementation before one final verification phase. Do not repeatedly build while editing.
- Push completed work to https://github.com/gomh4417/camp-on.git; never publish local credentials or IDE caches.
- Consult official Android and Pleos documentation before API changes; never invent APIs or permissions.
- Vehicle reads and writes have separate capabilities. Unknown parking / restrictions / stale state blocks control.
- Actual Pleos mode uses connected values only; connection errors never substitute data. Competition demo mode explicitly uses assumed data and separate persisted state. Keep user-facing copy concise; show the demo source once, not repeated disclaimers.
- Missing values stay unknown. Every observation retains source and timestamp.
- One shared camping session. Persist presets, initial values, changed values and unresolved restore results.
- Restore only app-changed values still matching last app writes; manual changes require review.
- Energy calculation uses usable capacity, explicit units and separate return-energy shortfall.
- No automatic physical actuation on driving, process death, or session end.
- Use IDE file tools to preserve editor buffers. Local Graphify AST only, refresh after edits.
- Verify calculation boundaries, partial failure, duplicate commands, restart and restoration.
