# Changelog

## [Unreleased]

### Added
- **Step progress bar (`WizardStepBar`)** — compact custom-painted horizontal indicator mounted above the tab pane; numbered circles turn from grey → accent (current) → green (completed) as the user moves through the wizard; clicking any circle navigates to that step (subject to the same forward-navigation guard as the Next button).
- **Tab-click forward-navigation guard** — direct tab-header clicks that skip past an unfilled required tab are now intercepted in the `ChangeListener`, not only in the Next button. Attempting to jump past the Modules tab without a selection (MULTI_APP) or past the Environment tab without credentials redirects the user to the blocking tab with an inline error message. An `isRedirecting` flag prevents re-entrant listener calls.
- **`DocumentationLinks.CREATE_MOBILE_APP`** — new URL constant pointing to the Android instrumentation landing page; used by the new "New to Dynatrace?" link on the Environment tab.

### Changed
- **Dialog title** — `"Dynatrace Wizard"` → `"Configure Dynatrace Mobile SDK"` (and `"Configure Dynatrace Mobile SDK — Update"` in re-run mode); more descriptive when the dialog is visible on a colleague's screen.
- **Finish button always visible** — `Finish` is no longer hidden on all tabs except the last. It is now always shown alongside `Next →`, allowing users to apply default settings and close the dialog from any tab. Clicking it on an earlier tab still runs full validation and redirects to the first invalid tab if needed.
- **Tab order: Technologies moved before Environment** — users can now confirm their AGP / Kotlin / Gradle versions are compatible *before* typing credentials. Previous order: Welcome → Modules → Environment → Technologies; new order: Welcome → Modules → Technologies → Environment → Features → Skills → Summary.
- **Modules tab hidden for single-app flows** — the tab is only added when the flow is `MULTI_APP` or the project has library modules that can opt into the OneAgent SDK. For all other flows (single app, feature modules, single build file) the read-only module summary visible on the Welcome tab is sufficient. Tab labels are auto-numbered so the visible numbers are always consecutive regardless of which tabs are present.
- **Features tab — Recommended / Advanced toggle** — 8 "core" rows are visible by default (auto-instrumentation, auto-start, user actions, web requests, lifecycle, crash, ANR, user opt-in). 12 rows are promoted to "Advanced" (plugin enabled, native crash, Compose, rage tap, name privacy, location, hybrid, load balancing, Grail, strict mode, Session Replay, agent logging) and hidden until the user clicks the `▼ Show advanced settings` link. The live search bar overrides the mode filter — typing always reveals all matching rows regardless of the current mode. On pre-fill, the wizard auto-expands to Advanced mode if the loaded config contains any non-default advanced value.
- **Skills tab — collapsed to single opt-in checkbox by default** — all configuration fields (target client, install scope, output path, detection status) are grouped in a `detailsPanel` that is hidden until the user checks "Export AI skill file". The checkbox description is rewritten in plain English ("If you use an AI coding assistant… Skip this if you don't use AI coding tools.") to eliminate confusion for users unfamiliar with AI coding agents. On load, if existing skill files are detected the checkbox is auto-checked and the panel expands automatically.
- **Summary tab — diff-style change view replaces monolithic text area** — the primary content is now a list of per-file change cards (file path, `→` action bullets, generated code block prefixed with `+ ` in green). Warnings appear in a dedicated section above the cards. The manual-startup snippet is shown inline (only when auto-start is disabled) since it requires immediate user action. All secondary info (At a Glance, configuration values, SDK section, skills preview) is collapsed behind a `▼ Show configuration details` toggle. A "Copy full preview" button still assembles the complete text for the clipboard. A safe fallback to raw monospace text is used if the preview parser produces no results.
- **Environment tab — first-time customer guidance** — the single hint line is replaced by three components: a neutral intro, a small-font "Already have a mobile app: portal → Mobile → Settings → Instrumentation" path for existing users, and a clickable "New to Dynatrace? Create your first mobile app in the portal →" link (`CREATE_MOBILE_APP`) for new customers with no app configured yet.
- **Skills sub-skill documentation modernised** — `sdk-apis.md` and `monitoring.md` now open with a Modern vs Legacy Quick Reference table; each API section is split into `✅ Modern (RUM/Grail)` and `⚠️ Legacy (Classic)` sub-sections covering events, values, errors, and web requests. "Invoke This Skill When" triggers updated across all four files. `setup.md` gains a Version Catalog (TOML) step 2c, "When to Ask for Clarification" table, and both migration path sections. `troubleshooting.md` replaces the flat Q&A with a structured Decision Tree.
- **Skills files — single source of truth** — `src/main/resources/skills/` (a stale copy) was deleted; a `processResources` task in `build.gradle.kts` now copies `docs/skills/` to the classpath at build time, ensuring the bundled files are always in sync with the source.
- **Kotlin supported version range corrected to `1.8 – 2.3`** — both `docs/skills/skills.md` and the generated `skills.md` previously hardcoded `2.0.21`; both now show `1.8 – 2.3`.
- **Eliminated redundant `detectProject()` call on wizard open** — `DynatraceWizardAction` already ran detection before opening the dialog; the result is now forwarded into `DynatraceWizardDialog` → `WelcomeStep`, skipping the second filesystem scan.
- **Welcome tab: "What happens next" paragraph anchored under a section header** — now placed under `TitledSeparator("What This Wizard Does")`.

### Fixed
- **`anrReporting` and `nativeCrashReporting` not restored on "Update Setup"** — `readExistingConfigFromString` was missing both fields; opening the wizard on a project that had these disabled would silently reset them to `true` and re-enable them on Finish.
- **Summary tab ANR/native-crash note was backwards** — `"(Android 11+ only)"` appeared next to the *Disabled* state instead of *Enabled*; it is now shown as `"Enabled (Android 11+ only)"`.
- **`JavaVersion.VERSION_1_8` misdetected as version "1"** — the Java detection regex now handles the legacy `VERSION_1_N` form (e.g. `VERSION_1_8` → `"8"`) before the modern `VERSION_N` form, preventing a false "Unsupported version" red on Java 8 projects.
- **Duplicate "Build-specific limitations" link on Technologies tab** — the link appeared once contextually inside the competing-plugins warning block and again unconditionally in the Documentation section; the duplicate has been removed.
- **KDoc tab index in `DynatraceWizardDialog`** — the comment incorrectly skipped Tab 5 (Features), jumping straight from Tab 4 to Tab 6.

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
