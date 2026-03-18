# Changelog

## [Unreleased]

## [1.0.0] - 2026-03-18

### Added
- **Skills tab** (wizard step 6) — dedicated tab for exporting reusable AI skill files; produces **5 Markdown files** (`skills.md`, `setup.md`, `sdk-apis.md`, `monitoring.md`, `troubleshooting.md`) written to the same directory
- **Multi-client skill export** — choose from Claude Code, Codex, Copilot, Cursor, OpenCode, or AmpCode; user-level and project-level install scopes with auto-computed output paths
- **Detected Skills section** — Skills tab now scans the target directory on open and on every path/client/scope change, showing which of the 5 files are already installed (full / partial / none), including the client name, scope, and directory; auto-checks the export checkbox when existing files are found
- **Feature search** — live filter bar on the Features tab with a clear (✕) button; sections with no matching rows collapse automatically; supports alias keywords (e.g. `gdpr` → opt-in, `dtx` → debug)
- **All-clear banner** on the Technologies tab — green success notice when all detected versions are in range and no competing plugins are found
- **Kotlin "Likely compatible" state** — Kotlin versions in the 1.8–2.3 range show amber `⚠ Likely compatible` status with a soft-bound note; versions above 2.3 show the standard unsupported error
- **Canonical skill reference** — `docs/skills/` ships four static sub-skill files (`setup.md`, `sdk-apis.md`, `monitoring.md`, `troubleshooting.md`) bundled as plugin resources and copied verbatim to the install directory
- **↺ Reset button** on the Skills tab — resets a manually edited path back to the auto-computed default
- **Path validation** on the Skills tab — rejects blank, directory-only, or absolute non-home paths before Finish
- **Summary skills preview truncation** — skills preview in the Summary tab is capped at 35 lines with a `[N more lines]` note

### Changed
- **Technologies tab layout reworked** — switched from `GridLayout` (equal-height rows) to `GridBagLayout`; each row is now only as tall as its content, hint rows span all four columns, and a vertical filler keeps rows compact; column proportions adjusted (Technology 38 %, Detected 20 %, Supported 20 %, Status 22 %)
- **Technologies tab legend** shortened and broken onto a single line; amber added as a distinct status color
- **Kotlin supported range** narrowed to `1.8–2.3` (was `1.8–2.5`); versions above 2.3 are flagged as unsupported
- **Skills export** refactored from a single monolithic `skills.md` to 5 files; `generateSkillsMarkdown()` now produces only the project-specific index, with static sub-skill files loaded from plugin resources
- **`SkillsStep`** now accepts `Project` (constructor) and `ProjectInfo` (`createPanel`) for detection; `updateDetectionStatus()` shows client · scope · directory in every state
- **`SkillsExportService`** gains `SkillDetectionResult` + `detectExistingSkills()` + `ALL_SKILL_FILES` constant
- Skills tab install-locations label now shows only the selected client's two paths (not all clients)
- Dialog size increased to 760 × 640
- `SkillsStep.createPanel()` signature updated to accept optional `ProjectInfo`
- Documentation links updated: `SUPPORT_LIMITATIONS`, `ERROR_CRASH_REPORTING`, `CRASH_REPORTING`, `PRIVACY_DATA_COLLECTION`, `WEB_REQUEST_MONITORING`, `CUSTOM_EVENTS`, `USER_SESSION_MANAGEMENT`, `MANUAL_SDK_INSTRUMENTATION`, `ADJUST_COMMUNICATION`, `DEBUG_LOGGING` all point to current Dynatrace docs paths
- Removed `→` arrows from all link labels on the Technologies tab
- Technologies tab column header renamed "In Your Project" → "Detected"

### Fixed
- **Single-library-module SDK re-run bug** — `hasAgentSdk()` now correctly detects `agentDependency()` blocks generated with `filterAll = true` (no `project.name` guard); the "Add OneAgent SDK dependency" checkbox was incorrectly appearing unchecked on every re-run
- **Multi-app module checkboxes pre-fill** — on re-run only already-instrumented modules are pre-checked (was: all modules always checked); "Select all app modules" initial state now derived from actual checkbox states
- **Per-module buildscript classpath injection for Plugin DSL projects** — `addClasspathGroovy/Kts` now emits a minimal `buildscript { dependencies { classpath ... } }` block (no `repositories {}`) when a `plugins {}` block is already present; the full block with `google()` / `mavenCentral()` is only emitted for legacy projects without a `plugins {}` block; `ensureBuildscriptRepositories` is no longer called for Plugin DSL projects
- **React Native and Flutter `autoInstrumented` flag** corrected to `false`
- **Navigation hint** in the Environment tab corrected ("Mobile → (your app) → Settings → Instrumentation")
- Trim-on-blur applied to all four credential fields in the Environment tab
- Summary tab skills preview correctly capped and labelled

### Removed
- Dead code: `buildSkillId()`, `buildCapabilities()`, `SkillCapability` enum
