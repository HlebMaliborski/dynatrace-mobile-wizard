# Changelog

## [Unreleased]

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
