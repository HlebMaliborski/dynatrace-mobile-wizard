# Changelog

## [1.0.0] - 2026-06-16

### Added
- **7-step wizard dialog** — guided tab-based dialog titled "Configure Dynatrace Mobile SDK" with a custom horizontal step progress bar (`WizardStepBar`); numbered circles show completed / current / future state and are clickable for direct navigation
- **Tab-click forward-navigation guard** — jumping past an unfilled Environment or Modules tab redirects with an inline error; an `isRedirecting` flag prevents re-entrant listener calls
- **Finish button always visible** — visible on every tab; validation still runs and redirects to the first invalid tab if needed
- **Skills tab** (wizard step 6) — dedicated tab for exporting reusable AI skill files; produces **5 Markdown files** (`skills.md`, `setup.md`, `sdk-apis.md`, `monitoring.md`, `troubleshooting.md`); collapsed to a single opt-in checkbox by default; auto-expands when existing skill files are detected in the target directory
- **Multi-client skill export** — choose from Claude Code, Codex, Copilot, Cursor, OpenCode, or AmpCode; user-level and project-level install scopes with auto-computed output paths
- **Detected Skills section** — scans the target directory on open and on every path/client/scope change; shows which of the 5 files are already installed
- **Feature search** — live filter bar on the Features tab with a clear (✕) button; sections with no matching rows collapse automatically; supports alias keywords (e.g. `gdpr` → opt-in, `dtx` → debug)
- **Features tab — Recommended / Advanced toggle** — 8 core rows visible by default; 12 Advanced rows revealed on demand; search bar always overrides the mode filter
- **Technologies tab** — scans 20+ libraries and frameworks; reports ✅ Compatible / ⚠️ Likely compatible / ❌ Unsupported / 💡 Not in project; all-clear banner when everything is in range
- **All-clear banner** on the Technologies tab — green success notice when all detected versions are in range and no competing plugins are found
- **Kotlin "Likely compatible" state** — Kotlin versions in the 1.8–2.3 range show amber status; versions above 2.3 show the standard unsupported error
- **Multi-module support** — single-app, multi-app, and dynamic feature module projects; per-module Application ID and Beacon URL; optional OneAgent SDK for library modules
- **Two plugin approaches** — Plugin DSL (`plugins {}`) and buildscript classpath; approach migration removes stale declarations cleanly
- **Re-run / Update mode** — pre-fills all fields from the existing Gradle configuration, including per-module credentials
- **Canonical skill reference** — `docs/skills/` ships four static sub-skill files (`setup.md`, `sdk-apis.md`, `monitoring.md`, `troubleshooting.md`) bundled as plugin resources
- **↺ Reset button** on the Skills tab — resets a manually edited path back to the auto-computed default
- **Path validation** on the Skills tab — rejects blank, directory-only, or absolute non-home paths before Finish
- **Summary tab** — diff-style per-file change cards with `+` prefixed generated code; secondary details collapsed behind a toggle; "Copy full preview" clipboard button
- **`DocumentationLinks.CREATE_MOBILE_APP`** — URL constant for the "New to Dynatrace?" link on the Environment tab

### Changed
- **Tab order** — Welcome → Modules → Technologies → Environment → Features → Skills → Summary (Technologies moved before Environment so users can confirm compatibility before entering credentials)
- **Modules tab hidden for single-app flows** — only shown when the flow is MULTI_APP or the project has library modules; tab labels auto-numbered to stay consecutive
- **Skills sub-skill documentation** — `sdk-apis.md` and `monitoring.md` open with a Modern vs Legacy Quick Reference table; split into `✅ Modern (RUM/Grail)` and `⚠️ Legacy (Classic)` sub-sections; `setup.md` gains a Version Catalog (TOML) step, "When to Ask for Clarification" table, and migration path sections; `troubleshooting.md` uses a structured Decision Tree
- **Skills files — single source of truth** — a `processResources` task in `build.gradle.kts` copies `docs/skills/` to the classpath at build time
- **Kotlin supported version range** set to `1.8 – 2.3`
- **Eliminated redundant `detectProject()` call on wizard open** — detection result forwarded from `DynatraceWizardAction` into the dialog, skipping a second filesystem scan
- Technologies tab column header renamed "In Your Project" → "Detected"; column proportions adjusted (Technology 38 %, Detected 20 %, Supported 20 %, Status 22 %)
- Dialog size increased to 760 × 640

### Fixed
- `anrReporting` and `nativeCrashReporting` not restored on "Update Setup"
- Summary tab ANR/native-crash note was backwards (`"(Android 11+ only)"` now appears next to the *Enabled* state)
- `JavaVersion.VERSION_1_8` misdetected as version "1"
- Duplicate "Build-specific limitations" link on the Technologies tab
- Multi-app module checkboxes pre-fill — on re-run only already-instrumented modules are pre-checked
- Per-module buildscript classpath injection now emits a minimal block when a `plugins {}` block is already present
- React Native and Flutter `autoInstrumented` flag corrected to `false`
- Trim-on-blur applied to all credential fields in the Environment tab
