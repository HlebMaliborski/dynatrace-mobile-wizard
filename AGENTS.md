# AGENTS.md — Dynatrace Wizard

An **IntelliJ Platform plugin** (Kotlin) that adds a multi-step wizard dialog to Android Studio / IntelliJ for configuring the Dynatrace Mobile SDK. It modifies the user's Gradle build files directly and can optionally export a Markdown `skills.md` file for AI agents.

---

## Architecture & Data Flow

```
DynatraceWizardAction (AnAction, Tools menu / context menu)
  └── DynatraceWizardDialog (DialogWrapper, JBTabbedPane)
        ├── WelcomeStep        → uses ProjectDetectionService → ProjectInfo
        ├── ModuleSelectionStep → chooses app modules / multi-app approach
        ├── EnvironmentConfigStep → collects appId + beaconUrl
        ├── FeatureToggleStep  → collects feature flags
        ├── SkillsStep         → collects AI skill export settings → SkillsExportConfig
        └── SummaryStep        → calls GradleModificationService.generateChangePreview()
              [on Finish] GradleModificationService.configureGradleFiles()
                          SkillsExportService.writeSkillsFile()
```

**Central Gradle model:** `DynatraceConfig` (`model/DynatraceConfig.kt`) — a plain `data class` carrying Dynatrace Gradle configuration only.

**Separate Skills model:** `SkillsExportConfig` (`model/SkillsExportConfig.kt`) — carries only the dedicated Skills tab state and is built separately in `DynatraceWizardDialog.buildSkillsConfig()`.

**`isKotlinDsl` flag** is detected once in `ProjectDetectionService.detectProject()` and threaded through every service call to branch between Groovy DSL and Kotlin DSL codegen.

---

## Key Files

| File | Purpose |
|------|---------|
| `src/main/resources/META-INF/plugin.xml` | Registers the action in ToolsMenu, EditorPopupMenu, ProjectViewPopupMenu, and the notification group |
| `gradle.properties` | All platform metadata: `platformType=IC`, `platformVersion=2024.1.7`, `pluginVersion`, `pluginSinceBuild` |
| `service/GradleModificationService.kt` | String-manipulation-based Gradle file codegen; uses plugin version constraint `8.+` |
| `service/SkillsExportService.kt` | Generates project-specific Markdown `skills.md` snapshots; writes them into the user's Android project |
| `docs/skills/skills.md` | **Canonical AI skill** — full reference covering all plugin capabilities, flows, and DSL snippets; can be installed manually into any AI client without running the wizard |
| `service/ProjectDetectionService.kt` | Scans `baseDir` children for `build.gradle[.kts]`; checks for `com.android.application` to confirm Android project |
| `util/ValidationUtil.kt` | `ValidationResult` sealed class; Application ID regex `[A-Za-z0-9_\-]+`; Beacon URL must be HTTPS |

---

## Build & Run

```bash
./gradlew buildPlugin          # Produces build/distributions/*.zip
./gradlew runIde               # Launches a sandboxed IDE instance with the plugin loaded
./gradlew test                 # Runs JUnit 4 tests
```

Plugin signing requires env vars `CERTIFICATE_CHAIN`, `PRIVATE_KEY`, `PRIVATE_KEY_PASSWORD`. Publishing requires `PUBLISH_TOKEN`. Neither is needed for local development.

---

## Platform Conventions

- **All IDE interactions** use IntelliJ Platform APIs: `VirtualFile` for file I/O, `WriteCommandAction.runWriteCommandAction` for all Gradle file writes (enables Undo), `NotificationGroupManager` for user notifications.
- **Services are plain classes**, not IntelliJ `@Service` components — instantiated directly with a `Project` constructor arg (e.g., `GradleModificationService(project)`).
- **UI is Swing/IntelliJ components**: use `FormBuilder.createFormBuilder()` + `.addComponent()` for panel layouts; `JBLabel`, `JBScrollPane`, `JBTabbedPane` (not raw Swing equivalents).
- **New actions must be registered in `plugin.xml`** — Kotlin class alone is not enough.
- **Platform version** is set in `gradle.properties` (`platformVersion`, `platformType`), not in `build.gradle.kts`. Change the IDE target there.

---

## Adding Features

- **New wizard step**: create a `*Step.kt` in `wizard/`, add a `createPanel(): JComponent` method, register the tab in `DynatraceWizardDialog.createCenterPanel()`.
- **New Gradle config option**: add a field to `DynatraceConfig`, expose it from the relevant step, update `buildConfig()` in `DynatraceWizardDialog`, and add codegen in both `buildDynatraceBlockKts()` and `buildDynatraceBlockGroovy()` in `GradleModificationService`.
- **New Skills option**: add a field to `SkillsExportConfig`, expose it from `SkillsStep`, update `buildSkillsConfig()` in `DynatraceWizardDialog`, and thread it into `SkillsExportService`.
- **Tests** live under `src/test/` and use JUnit 4 (already in `dependencies`). Services are testable without a running IDE — pass a mock/null `Project` where needed.

