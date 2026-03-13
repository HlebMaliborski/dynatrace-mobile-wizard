# Dynatrace Wizard

An **Android Studio / IntelliJ IDEA** plugin that simplifies [Dynatrace Mobile SDK](https://www.dynatrace.com/support/help/technology-support/real-user-monitoring/mobile-and-custom-applications/mobile-app-instrumentation/android/android-instrumentation-setup) configuration for Android projects through a guided wizard dialog.

---

## Features

- 🧙 **6-step wizard UI** — guided tab-based dialog built on IntelliJ's `DialogWrapper`
- 🔍 **Auto project detection** — detects Android projects, locates `build.gradle(.kts)` files, and classifies every module automatically
- 🏗️ **Multi-module support** — handles single-app, feature-module, and multi-app projects with appropriate instrumentation strategies per setup flow
- 🔀 **Two plugin approaches** — Plugin DSL (`plugins {}` block) and buildscript classpath (`buildscript { dependencies { classpath } }`) with seamless migration between them
- 🔑 **Per-module credentials** — for multi-app projects using the per-module approach, each app module can have its own Application ID and Beacon URL
- 🔄 **Update / re-run mode** — when Dynatrace is already configured the wizard pre-fills all fields from the existing setup, including per-module credentials
- 🔬 **Technology compatibility scan** — detects 20+ libraries and frameworks in the project and reports Dynatrace compatibility against known version ranges
- ✏️ **Gradle file modification** — adds the Dynatrace Gradle plugin and `dynatrace {}` configuration block with correct placement and deduplication
- 🧹 **Approach migration** — switches cleanly between Plugin DSL and per-module by removing stale coordinator declarations, classpath entries, and orphaned `dynatrace {}` blocks
- 🔀 **Groovy & Kotlin DSL support** — handles both `build.gradle` and `build.gradle.kts`
- ✅ **Input validation** — real-time validation for Application ID and Beacon URL (with per-module field support)
- 📋 **Change preview** — the Summary tab shows exactly what will be written before applying
- 🔔 **IDE notifications** — success and error notifications via IntelliJ's notification system
- ↩️ **Undo support** — all Gradle file writes use `WriteCommandAction` and can be undone with Ctrl/Cmd+Z

---

## Wizard Steps

| # | Tab | Description |
|---|-----|-------------|
| 1 | **Welcome** | Detects the Android project; shows module list, plugin approach, setup flow, and mavenCentral() status |
| 2 | **Environment** | Enter Dynatrace **Application ID** and **Beacon URL**; for multi-app projects a toggle switches to per-module credential entry |
| 3 | **Modules** | Select which app modules to instrument; choose Plugin DSL vs per-module approach for multi-app projects; opt library modules into the OneAgent SDK dependency |
| 4 | **Technologies** | Scans the project for 20+ libraries and shows Dynatrace compatibility status with detected version numbers |
| 5 | **Features** | Fine-grained instrumentation toggles — monitoring sections, privacy, exclusions, build variant, and advanced agent behavior |
| 6 | **Summary** | Full change preview (file paths + generated blocks) before writing anything |

---

## Setup Flows

The wizard detects the project structure and routes configuration accordingly:

| Flow | When detected | Strategy |
|------|--------------|----------|
| **Single app** | One `com.android.application` module | Plugin DSL or classpath at root; `dynatrace {}` block in root (Plugin DSL) or app module (classpath) |
| **Feature modules** | Base app + dynamic-feature modules | Same as single app; feature and library modules are instrumented automatically — no per-module changes needed |
| **Multi-app** | Two or more `com.android.application` modules | **Plugin DSL**: coordinator at root instruments all app modules automatically. **Per-module**: classpath at root + `com.dynatrace.instrumentation.module` in each app module |
| **Single build file** | Root file also acts as the app module | All changes applied to the single root build file |
| **Unknown** | Structure unclear | Best-effort single-app treatment |

### Plugin Approach Migration

When switching approaches on a re-run:

- **Per-module → Plugin DSL**: removes the classpath entry from `buildscript {}`, removes `dynatrace {}` blocks from each app module, and adds the coordinator plugin + root `dynatrace {}` block.
- **Plugin DSL → Per-module**: removes the coordinator plugin declaration and root `dynatrace {}` block, adds the classpath entry to `buildscript {}`, and adds `com.dynatrace.instrumentation.module` + `dynatrace {}` to each app module.

> **Mixed state detected**: when both a `plugins {}` block and a `buildscript { classpath }` entry are found, the Welcome tab highlights this with a warning and describes what the wizard will clean up.

---

## Environment Tab — Per-Module Credentials

For **multi-app + per-module** projects, check **"Configure each app module individually"** to enter a separate Application ID and Beacon URL for each app module. When unchecked (default), all modules share the same credentials.

On re-open, per-module credentials are read back from each module's build file and pre-filled automatically, and the toggle switches to individual mode.

---

## Features Tab

| Section | Options |
|---------|---------|
| **Global** | Plugin enabled (global kill-switch for all instrumentation) |
| **Instrumentation** | Auto-instrumentation (bytecode transform), auto-start on app launch |
| **Monitoring** | User actions, web requests (OkHttp / HttpURLConnection), lifecycle, crash reporting |
| **Compose & Behavioral** | Jetpack Compose instrumentation, rage tap detection |
| **Privacy** | User opt-in mode (GDPR), name privacy masking, location monitoring, hybrid WebView monitoring |
| **Advanced** | Client-side ActiveGate load balancing, New RUM Experience (Grail), strict mode |
| **Exclusions** | Exclude packages / classes / methods from bytecode transformation (comma-separated) |
| **Build variant** | Restrict instrumentation to a specific Gradle build variant (regex) |

---

## Technology Compatibility

The **Technologies tab** scans all Gradle build files, `libs.versions.toml`, and `gradle-wrapper.properties` and reports compatibility status for:

| Category | Technologies |
|----------|-------------|
| Build & Toolchain | Android Gradle Plugin (8.0+), Kotlin (1.6+), Gradle Wrapper, Android SDK API level |
| HTTP & Networking | HttpURLConnection, OkHttp (v3+), Retrofit 2, Apache HTTP Client |
| Jetpack & UI | Jetpack Compose (1.4–1.10), Android Views / Activities |
| Async | Kotlin Coroutines (1.10.2–2.1) |
| Crash & Exceptions | Java / Kotlin uncaught exceptions |
| Hybrid | Android WebView |
| Behavioral | Rage tap detection |
| Privacy | Opt-in mode, name privacy masking |

Each entry shows one of:
- ✅ **Compatible** — detected and within the supported version range
- ❌ **Unsupported version** — detected but outside the supported range
- 💡 **Not in project** — library not found; no action required
- 🔷 **Built-in / Informational** — always available; detected version shown for reference

---

## Compatibility

| IDE | Minimum Version |
|-----|----------------|
| IntelliJ IDEA (Community / Ultimate) | 2024.1 |
| Android Studio | 2025.1 (Meerkat) |

- Uses the modern `guessProjectDir()` API — the deprecated `project.baseDir` is not used.
- Supports **Kotlin DSL** (`build.gradle.kts`) and **Groovy DSL** (`build.gradle`).
- Supports **Version Catalogs** — recognises `alias(libs.plugins.android.application)` and other catalog-based plugin declarations.

---

## Android Project Detection

The wizard scans subdirectory build files for any of the following patterns:

| Pattern | Description |
|---------|-------------|
| `com.android.application` | Standard Android application plugin |
| `com.android.library` | Android library module |
| `com.android.dynamic-feature` | Dynamic feature module |
| `com.android.test` | Android test module |
| `android { }` block | Gradle `android` extension |
| `androidApplication()` | Version Catalog Kotlin DSL alias |
| `android.application` / `android.library` | Dot-notation catalog aliases |

---

## Installation

### From JetBrains Marketplace _(coming soon)_

1. Open Android Studio / IntelliJ IDEA
2. Go to **Settings → Plugins → Marketplace**
3. Search for **Dynatrace Wizard**
4. Click **Install** and restart the IDE

### From Source

See [Build & Run from Source](#build--run-from-source) below.

---

## Usage

1. Open an Android project in Android Studio or IntelliJ IDEA
2. Go to **Tools → Dynatrace Wizard…**  
   _(or right-click in the Project view / Editor → Dynatrace Wizard…)_
3. Follow the wizard steps:
   - **Welcome** — verify project and module detection
   - **Environment** — enter Application ID + Beacon URL (optionally per-module for multi-app)
   - **Modules** — select modules and instrumentation approach
   - **Technologies** — review compatibility of detected libraries
   - **Features** — configure instrumentation options
   - **Summary** — review all changes and click **Finish**
4. **Sync** your Gradle project to activate the Dynatrace agent

> If Dynatrace is already configured the wizard detects it, asks whether to update the existing setup, and pre-fills all fields — including per-module credentials — from the current build files.

### Where to Find Your Dynatrace Credentials

1. Log in to your Dynatrace environment
2. Navigate to **Settings → Mobile → Mobile apps**
3. Select or create a mobile app
4. Copy the **Application ID** and **Beacon URL**

---

## Build & Run from Source

### Prerequisites

- JDK 17 or newer
- IntelliJ IDEA (any edition) or Android Studio

### Steps

```bash
# Clone the repository
git clone https://github.com/HlebMaliborski/dynatrace_wizard.git
cd dynatrace_wizard

# Build the plugin
./gradlew buildPlugin

# Run in a sandboxed IDE instance
./gradlew runIde

# Run unit tests
./gradlew test
```

The built plugin `.zip` is placed in `build/distributions/`.

To install manually:
1. **Settings → Plugins → ⚙ → Install Plugin from Disk…**
2. Select the generated `.zip` file

---

## Project Structure

```
dynatrace_wizard/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties                          # Plugin metadata & target IDE version
├── CHANGELOG.md
└── src/main/
    ├── kotlin/com/dynatrace/wizard/
    │   ├── DynatraceWizardAction.kt           # Action (Tools menu / context menu)
    │   ├── wizard/
    │   │   ├── DynatraceWizardDialog.kt       # 6-tab wizard dialog + navigation
    │   │   ├── WelcomeStep.kt                 # Tab 1: project detection overview
    │   │   ├── EnvironmentConfigStep.kt       # Tab 2: App ID + Beacon URL (per-module support)
    │   │   ├── ModuleSelectionStep.kt         # Tab 3: module selection + approach toggle
    │   │   ├── SupportedTechnologiesStep.kt   # Tab 4: technology compatibility scan
    │   │   ├── FeatureToggleStep.kt           # Tab 5: instrumentation feature toggles
    │   │   └── SummaryStep.kt                 # Tab 6: change preview
    │   ├── model/
    │   │   └── DynatraceConfig.kt             # Configuration data model (incl. ModuleCredentials)
    │   ├── service/
    │   │   ├── GradleModificationService.kt   # Gradle file codegen + approach migration
    │   │   └── ProjectDetectionService.kt     # Project structure + module type detection
    │   └── util/
    │       ├── ValidationUtil.kt              # App ID + Beacon URL validation
    │       ├── DocumentationLinks.kt          # Dynatrace docs URL constants
    │       └── WizardColors.kt                # Shared UI color palette
    └── resources/META-INF/
        ├── plugin.xml                         # Plugin descriptor + action registrations
        └── pluginIcon.svg
```

---

## Contributing

Contributions are welcome! Please:

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/my-feature`
3. Commit your changes: `git commit -m "Add my feature"`
4. Push and open a Pull Request

### Code Style

- Kotlin with standard JetBrains conventions
- All IDE interactions use IntelliJ Platform APIs (`VirtualFile`, `WriteCommandAction`, `NotificationGroupManager`)
- New wizard steps: add a `*Step.kt` in `wizard/`, implement `createPanel()`, register in `DynatraceWizardDialog`
- New config options: add a field to `DynatraceConfig`, expose from the relevant step, update `buildConfig()`, and add codegen in both `buildDynatraceBlockKts()` and `buildDynatraceBlockGroovy()`
- Tests live under `src/test/` and use JUnit 4

---

## License

This project is licensed under the **Apache License 2.0**.  
See [LICENSE](LICENSE) for details.

---

## Acknowledgements

- [IntelliJ Platform Plugin SDK](https://plugins.jetbrains.com/docs/intellij/welcome.html)
- [Dynatrace Android Instrumentation Documentation](https://www.dynatrace.com/support/help/technology-support/real-user-monitoring/mobile-and-custom-applications/mobile-app-instrumentation/android/)
