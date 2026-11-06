# CAMP:ON project rules

- Preserve existing Kotlin / Compose versions and single app module.
- MVP_C screenshots are the controlling UI reference: preserve the home vehicle-list/briefing left column and large map right column; separate vehicle and briefing detail pages, reuse original avatar/tent assets, generous white panels on a very pale blue-gray background. Apply these shared tokens to camping, energy, departure and dialogs. Do not flatten the flow into one long form. Do not duplicate the Pleos system vehicle/sidebar or HVAC dock inside the app.
- Korean UI and reports. Light, flat, friendly casual service: soft gray background, white cards, restrained blue accent, no glass/blur, heavy shadows, dark charcoal panels or 3D decorations. This project brief overrides solid-glass defaults. Use ui-ux-pro-max for hierarchy/accessibility. Preserve system insets.
- Make the overhead cabin large, using available vertical space. Compose a separate transparent shell and independently selectable seat layers. Selection uses a flat pressed fill, outline and check, and opens the matching controls beside the map. Selecting a seat never actuates the vehicle. Explicit +/- or switch sends immediately; temperature slider sends once on release. Keep at least 56dp touch targets and responsive reflow.
- Distinguish SDK acceptance, pending read-back and confirmed values; never display requested values as observed values. Mode batches retain explicit apply.
- Pretendard Regular, Medium, ExtraBold only. Avoid green-heavy multicolor styling.
- Complete implementation before one final verification phase. Do not repeatedly build while editing.
- Push completed work to https://github.com/gomh4417/camp-on.git; never publish local credentials or IDE caches.
- Consult official Android and Pleos documentation before API changes; never invent APIs or permissions.
- Vehicle reads and writes have separate capabilities. Unknown parking / restrictions / stale state blocks control. Individual climate controls need a known gear and platform permission, not necessarily P. Seat/light, mode batches and restoration require P; never bypass Pleos driving restrictions.
- Actual Pleos mode uses connected values only; connection errors never substitute data. Competition demo mode explicitly uses assumed data and separate persisted state. Keep user-facing copy concise; show the demo source once, not repeated disclaimers.
- Missing values stay unknown. Every observation retains source and timestamp.
- One shared camping session. Persist presets, initial values, changed values and unresolved restore results.
- Restore only app-changed values still matching last app writes; manual changes require review.
- Energy calculation uses usable capacity, explicit units and separate return-energy shortfall.
- No automatic physical actuation on driving, process death, or session end.
- Use IDE file tools to preserve editor buffers. Local Graphify AST only, refresh after edits.
- Verify calculation boundaries, partial failure, duplicate commands, restart and restoration.
