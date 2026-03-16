# Changelog

## [Unreleased]


### Added
- **Skills tab** (new wizard step 6) — dedicated tab for exporting a reusable AI skill file
- **AI skill export** — generates a Markdown `skills.md` capturing the full wizard context (project layout, credentials, selected features, module structure) for use by AI coding agents
- **Multi-client support** — choose from Claude Code, Codex, Copilot, Cursor, OpenCode, or AmpCode as the target AI client; user-level and project-level install scopes with computed output paths
- **Canonical skill reference** — `docs/skills/skills.md` ships with the plugin as a full how-to reference covering all setup flows, DSL snippets, and feature options; can be installed into any AI client without running the wizard
- **Editable output path** — the Skills tab path field is now editable; the wizard auto-computes the default from the selected client and scope, but users can override it freely
- **↺ Reset button** — resets a manually edited path back to the auto-computed default for the selected client and scope
- **Path validation** — the wizard now validates the output path before Finish: rejects blank paths, directory-only paths (ending with `/`), and absolute paths that don't start with `~/`

### Changed
- AI skill export moved from the Features tab to its own dedicated Skills tab
- `DynatraceConfig` no longer carries AI skill fields (`exportSkillManifest`, `skillManifestPath`) — skill settings live in a separate `SkillsExportConfig` model
- Replaced JSON `SKILL_MANIFEST.json` output with Markdown `skills.md` (richer, human-readable, directly usable by coding agents)
- `SkillManifestService` renamed to `SkillsExportService` with a new Markdown-only generation API

### Removed
- `docs/skills/SKILL_MANIFEST.schema.json` — superseded by the Markdown skill format

## [1.0.0] - 2026-03-11

### Added
- Initial release of Dynatrace Mobile Wizard
- **Welcome tab** — detects Android project, Gradle DSL (Kotlin/Groovy), module structure, setup flow, and existing Dynatrace configuration
- **Modules tab** — per-module selection and approach configuration for multi-app projects (Plugin DSL coordinator vs per-module buildscript); library module SDK opt-in
- **Environment tab** — Application ID and Beacon URL input with inline validation; per-module credentials for multi-app projects
- **Technologies tab** — supported technology overview for the detected project dependencies
- **Features tab** — toggles for auto-instrumentation, crash reporting, user actions, web request monitoring, Jetpack Compose, hybrid WebView monitoring, location, rage-tap detection, privacy settings (opt-in, name masking), build-variant targeting, class/method exclusions, and more
- **Summary tab** — full Gradle code preview before applying; click Finish to write the changes
- Groovy DSL (`build.gradle`) and Kotlin DSL (`build.gradle.kts`) support
- Plugin DSL (`plugins {}`) and legacy buildscript classpath approach support
- Mixed Plugin DSL + buildscript classpath detection and cleanup
- All Gradle file writes use `WriteCommandAction` — fully undoable via Edit → Undo
- Registered in Tools menu, editor right-click menu, and Project view right-click menu
