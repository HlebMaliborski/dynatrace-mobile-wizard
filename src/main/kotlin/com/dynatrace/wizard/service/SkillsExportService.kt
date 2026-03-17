package com.dynatrace.wizard.service

import com.dynatrace.wizard.model.*
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.time.Instant

/**
 * Generates and writes Markdown skill files from the dedicated Skills tab.
 *
 * Export produces **5 files** written into the same directory:
 *  - `skills.md`            — project-specific index (credentials, config, generated blocks)
 *  - `setup.md`             — plugin setup & configuration reference (static)
 *  - `sdk-apis.md`          — OneAgent SDK API reference (static)
 *  - `monitoring.md`        — monitoring features reference (static)
 *  - `troubleshooting.md`   — troubleshooting & limitations reference (static)
 */
class SkillsExportService(private val project: Project? = null) {

    companion object {
        const val SKILL_SLUG = "dynatrace-android-sdk"
        private const val GENERATOR = "dynatrace-wizard"
        private const val SKILL_FILE_NAME = "skills.md"

        /** Static sub-skill files bundled as plugin resources and written alongside skills.md. */
        val SUB_SKILL_FILES = listOf("setup.md", "sdk-apis.md", "monitoring.md", "troubleshooting.md")

        /** All 5 skill files (index + sub-skills). */
        val ALL_SKILL_FILES = listOf(SKILL_FILE_NAME) + SUB_SKILL_FILES
    }

    /**
     * Result of scanning the target directory for existing skill files.
     *
     * @param foundFiles  names of skill files that already exist on disk
     * @param totalFiles  total number of files that would be written (always 5)
     * @param directory   human-readable directory path that was scanned
     */
    data class SkillDetectionResult(
        val foundFiles: List<String>,
        val totalFiles: Int,
        val directory: String
    ) {
        val hasAny get() = foundFiles.isNotEmpty()
        val isFullInstall get() = foundFiles.size == totalFiles
        val isPartialInstall get() = foundFiles.isNotEmpty() && foundFiles.size < totalFiles
    }

    /**
     * Checks which of the 5 skill files already exist at the directory implied by [config].
     * Returns a [SkillDetectionResult] — safe to call even when the directory does not exist yet.
     */
    fun detectExistingSkills(
        projectInfo: ProjectDetectionService.ProjectInfo,
        config: SkillsExportConfig
    ): SkillDetectionResult {
        val outputPath = resolveOutputPath(config)
        val directory  = outputPath.substringBeforeLast("/")
        return try {
            val root = resolveWriteRoot(projectInfo, config.skillInstallScope)
                ?: return SkillDetectionResult(emptyList(), ALL_SKILL_FILES.size, directory)

            val relativePath = when (config.skillInstallScope) {
                SkillInstallScope.PROJECT_LEVEL -> directory
                SkillInstallScope.USER_LEVEL    -> directory.removePrefix("~/")
            }

            var current: VirtualFile? = root
            for (segment in relativePath.split('/').filter { it.isNotBlank() }) {
                current = current?.findChild(segment)
                if (current == null) break
            }

            val foundFiles = if (current != null && current.isDirectory) {
                ALL_SKILL_FILES.filter { current.findChild(it) != null }
            } else emptyList()

            SkillDetectionResult(foundFiles, ALL_SKILL_FILES.size, directory)
        } catch (_: Exception) {
            SkillDetectionResult(emptyList(), ALL_SKILL_FILES.size, directory)
        }
    }

    fun buildInstallLocations(): List<SkillInstallLocation> = SkillClient.entries.map { client ->
        SkillInstallLocation(
            client = client,
            userLevelPath = buildPathFor(client, SkillInstallScope.USER_LEVEL),
            projectLevelPath = buildPathFor(client, SkillInstallScope.PROJECT_LEVEL)
        )
    }

    fun resolveOutputPath(config: SkillsExportConfig): String {
        val fallback = buildPathFor(config.skillClient, config.skillInstallScope)
        return normalizePath(config.skillFilePath, fallback)
    }

    fun generateSkillsMarkdown(
        projectInfo: ProjectDetectionService.ProjectInfo,
        dynatraceConfig: DynatraceConfig,
        skillsConfig: SkillsExportConfig,
        sdkLibraryModules: List<ProjectDetectionService.ModuleInfo> = emptyList(),
        deselectedModules: List<ProjectDetectionService.ModuleInfo> = emptyList(),
        generatedAt: Instant = Instant.now()
    ): String {
        val selectedAppModules = projectInfo.appModules.map { it.name }.ifEmpty { listOf(projectInfo.appModuleName) }
        val featureModules = projectInfo.featureModules.map { it.name }
        val libraryModules = projectInfo.libraryModules.map { it.name }
        val isKts = projectInfo.isKotlinDsl
        val usesPluginDsl = projectInfo.usesPluginDsl

        val blockKts = GradleModificationService.buildDynatraceBlockKts(dynatraceConfig)
        val blockGroovy = GradleModificationService.buildDynatraceBlockGroovy(dynatraceConfig)

        val installTableRows = buildInstallLocations().joinToString("\n") {
            "| ${it.client.label} | `${it.userLevelPath}` | `${it.projectLevelPath}` |"
        }
        val selectedInstallPath = resolveOutputPath(skillsConfig)

        val perModuleSection = if (dynatraceConfig.moduleCredentials.isNotEmpty()) buildString {
            appendLine("## Per-Module Credentials")
            appendLine()
            appendLine("| Module | Application ID | Beacon URL |")
            appendLine("| --- | --- | --- |")
            dynatraceConfig.moduleCredentials.forEach { (name, creds) ->
                appendLine("| $name | `${creds.appId}` | `${creds.beaconUrl}` |")
            }
            appendLine()
        } else ""

        val featureSummary = buildList {
            add("Plugin enabled: ${yesNo(dynatraceConfig.pluginEnabled)}")
            add("Auto-instrumentation: ${yesNo(dynatraceConfig.autoInstrument)}")
            add("Auto-start: ${yesNo(dynatraceConfig.autoStartEnabled)}")
            add("Crash reporting: ${yesNo(dynatraceConfig.crashReporting)}")
            add("ANR reporting: ${yesNo(dynatraceConfig.anrReporting)}")
            add("Native crash reporting: ${yesNo(dynatraceConfig.nativeCrashReporting)}")
            add("Session Replay: ${yesNo(dynatraceConfig.sessionReplayEnabled)}")
            add("User opt-in: ${yesNo(dynatraceConfig.userOptIn)}")
            add("Name privacy: ${yesNo(dynatraceConfig.namePrivacy)}")
            add("Compose instrumentation: ${yesNo(dynatraceConfig.composeEnabled)}")
            add("Rage tap detection: ${yesNo(dynatraceConfig.rageTapDetection)}")
            add("Web request monitoring: ${yesNo(dynatraceConfig.webRequestsEnabled)}")
            add("Lifecycle monitoring: ${yesNo(dynatraceConfig.lifecycleEnabled)}")
            add("Hybrid WebView monitoring: ${yesNo(dynatraceConfig.hybridMonitoring)}")
            add("Location monitoring: ${yesNo(dynatraceConfig.locationMonitoring)}")
            add("Strict mode: ${yesNo(dynatraceConfig.strictMode)}")
            if (dynatraceConfig.agentLogging) add("⚠️ Debug agent logging: Enabled (remove before production)")
        }.joinToString("\n") { "- $it" }

        val exclusionsSection = buildString {
            val pkgs = dynatraceConfig.excludePackages.ifBlank { "None" }
            val cls = dynatraceConfig.excludeClasses.ifBlank { "None" }
            val mth = dynatraceConfig.excludeMethods.ifBlank { "None" }
            appendLine("- Packages: $pkgs")
            appendLine("- Classes:  $cls")
            append("- Methods:  $mth")
        }

        // Manual startup snippet pre-filled with real credentials
        val manualStartupSection = if (!dynatraceConfig.autoStartEnabled) """

## ⚠️ Manual Startup Required

Auto-start is **disabled** in this project. Add the following to your `Application.onCreate()`:

```kotlin
// Kotlin
import com.dynatrace.android.agent.Dynatrace
import com.dynatrace.android.agent.conf.DynatraceConfigurationBuilder

Dynatrace.startup(
    this,
    DynatraceConfigurationBuilder(
        "${dynatraceConfig.applicationId}",
        "${dynatraceConfig.beaconUrl}"
    ).buildConfiguration()
)
```

```java
// Java
Dynatrace.startup(this, new DynatraceConfigurationBuilder(
    "${dynatraceConfig.applicationId}",
    "${dynatraceConfig.beaconUrl}"
).buildConfiguration());
```

> Values in `DynatraceConfigurationBuilder` override those in the `dynatrace {}` DSL block.
""" else ""

        // OneAgent SDK section pre-filled with real module names
        val sdkSection = if (sdkLibraryModules.isNotEmpty()) {
            val nameList = sdkLibraryModules.map { it.name }
            val allLib = projectInfo.libraryModules.map { it.name }
            val filterAll = nameList.size == allLib.size
            val filterBlock = if (filterAll) """
subprojects {
    pluginManager.withPlugin("com.android.library") {
        dependencies {
            add("implementation", com.dynatrace.tools.android.DynatracePlugin.agentDependency())
        }
    }
}""" else """
subprojects { project ->
    pluginManager.withPlugin("com.android.library") {
        if (${nameList.joinToString(" || ") { """project.name == "${it}"""" }}) {
            dependencies {
                add("implementation", com.dynatrace.tools.android.DynatracePlugin.agentDependency())
            }
        }
    }
}"""
            val filterBlockGroovy = if (filterAll) """
subprojects {
    pluginManager.withPlugin('com.android.library') {
        dependencies {
            implementation com.dynatrace.tools.android.DynatracePlugin.agentDependency()
        }
    }
}""" else """
subprojects { project ->
    pluginManager.withPlugin('com.android.library') {
        if (${nameList.joinToString(" || ") { """project.name == "${it}"""" }}) {
            dependencies {
                implementation com.dynatrace.tools.android.DynatracePlugin.agentDependency()
            }
        }
    }
}"""
            """

## OneAgent SDK — Library Modules

The following library modules are opted into `agentDependency()` so their code can call Dynatrace APIs directly.
Selected: ${nameList.joinToString()}

**Kotlin DSL** — add to the **root** `build.gradle.kts`:

```kotlin$filterBlock
```

**Groovy** — add to the **root** `build.gradle`:

```groovy$filterBlockGroovy
```
"""
        } else ""

        val detectKnownFacts = buildString {
            appendLine("| Finding | Wizard-detected value |")
            appendLine("| --- | --- |")
            appendLine("| DSL type | ${if (isKts) "Kotlin DSL (`.kts`)" else "Groovy DSL"} |")
            appendLine("| Plugin approach | ${if (usesPluginDsl) "Plugin DSL (`plugins {}` block)" else "Buildscript classpath"} |")
            appendLine("| Setup flow | ${projectInfo.setupFlow.title} |")
            appendLine("| Application modules | ${selectedAppModules.joinToString()} |")
            if (featureModules.isNotEmpty()) appendLine("| Feature modules | ${featureModules.joinToString()} |")
            if (libraryModules.isNotEmpty()) appendLine("| Library modules | ${libraryModules.joinToString()} |")
            append("| Dynatrace already configured | ${if (projectInfo.appModules.any { it.hasDynatrace }) "Yes — update mode" else "No — fresh setup"} |")
        }

        val pluginApplySection = if (usesPluginDsl) """
**This project uses Plugin DSL.** Add to the root `build.gradle${if (isKts) ".kts" else ""}`:

${
            if (isKts) """```kotlin
plugins {
    id("com.dynatrace.instrumentation") version "8.+"
}
```""" else """```groovy
plugins {
    id 'com.dynatrace.instrumentation' version '8.+'
}
```"""
        }
""" else """
**This project uses Buildscript Classpath.** Add to the root `build.gradle${if (isKts) ".kts" else ""}`:

${
            if (isKts) """```kotlin
buildscript {
    repositories { mavenCentral(); google() }
    dependencies {
        classpath("com.dynatrace.tools.android:gradle-plugin:8.+")
    }
}
```

Then in each app module `build.gradle.kts`:
```kotlin
apply(plugin = "com.dynatrace.instrumentation")
```""" else """```groovy
buildscript {
    repositories { mavenCentral(); google() }
    dependencies {
        classpath 'com.dynatrace.tools.android:gradle-plugin:8.+'
    }
}
```

Then in each app module `build.gradle`:
```groovy
apply plugin: 'com.dynatrace.instrumentation'
```"""
        }
"""

        return """
---
name: $SKILL_SLUG
description: >
  Project-specific Dynatrace Mobile SDK skill generated by Dynatrace Wizard.
  Contains the exact Gradle configuration, feature settings, and detected project values.
  Load sub-skill files for full API reference, setup steps, and troubleshooting.
license: Apache-2.0
category: sdk-setup
disable-model-invocation: true
generated-at: $generatedAt
generated-by: $GENERATOR
---

# Dynatrace Android SDK

Project-specific skill generated by Dynatrace Wizard on $generatedAt.
$manualStartupSection
---

## Invoke This Skill When

- User asks to add or reconfigure Dynatrace in this Android project
- User wants the Application ID, Beacon URL, or list of enabled features
- User asks why Dynatrace is not capturing data in this project
- User wants to update, migrate, or remove the Dynatrace Gradle configuration
- For API reference, setup steps, monitoring details, or troubleshooting, load the relevant sub-skill below

---

## Sub-Skills

| File | Name | Use when… |
| --- | --- | --- |
| [`setup.md`](setup.md) | **Plugin Setup & Configuration** | Adding the Gradle plugin, `dynatrace {}` DSL reference, multi-module patterns, manual startup, standalone instrumentation |
| [`sdk-apis.md`](sdk-apis.md) | **OneAgent SDK APIs** | `enterAction`, `leaveAction`, `reportValue`, `sendBizEvent`, `WebRequestTiming`, hybrid WebView monitoring, `setBeaconHeaders`, `DataCollectionLevel`, `endVisit` |
| [`monitoring.md`](monitoring.md) | **Monitoring Features** | App start measurement, web requests, W3C Trace Context, OkHttp modifier, crash/ANR/native crash reporting, custom events, user tagging, session properties |
| [`troubleshooting.md`](troubleshooting.md) | **Troubleshooting & Limitations** | Build errors, missing data, missing user actions or web requests, plugin compatibility issues, instrumentation limitations |

---

## Supported Versions (quick reference)

| Component | Minimum Version |
| --- | --- |
| Android API level | 21 |
| Gradle | 7.0.2 |
| Android Gradle Plugin | 7.0 |
| Java | 11 |
| Kotlin | 2.0.21 |
| Jetpack Compose | 1.4 – 1.10 |

> ANR reporting and Native crash reporting require Android 11 or higher.

---

## This Project's Configuration

| Property | Value |
| --- | --- |
| Setup flow | `${projectInfo.setupFlow.title}` |
| Instrumentation approach | `${if (usesPluginDsl) "Plugin DSL at root" else "Buildscript classpath + per-module plugin"}` |
| Android DSL | `${if (isKts) "Kotlin DSL (.kts)" else "Groovy DSL"}` |
| Application modules | ${selectedAppModules.joinToString()} |
| Dynamic feature modules | ${if (featureModules.isEmpty()) "None" else featureModules.joinToString()} |
| Library modules | ${if (libraryModules.isEmpty()) "None" else libraryModules.joinToString()} |
| Deselected modules | ${if (deselectedModules.isEmpty()) "None" else deselectedModules.joinToString { it.name }} |
| Library modules with OneAgent SDK | ${if (sdkLibraryModules.isEmpty()) "None" else sdkLibraryModules.joinToString { it.name }} |
| Application ID | `${dynatraceConfig.applicationId}` |
| Beacon URL | `${dynatraceConfig.beaconUrl}` |
| Build variant filter | `${dynatraceConfig.buildVariant}` |

### Selected Features

$featureSummary

### Exclusions

$exclusionsSection

$perModuleSection
### Generated `dynatrace {}` Block — Kotlin DSL

```kotlin
$blockKts
```

### Generated `dynatrace {}` Block — Groovy DSL

```groovy
$blockGroovy
```
$sdkSection
---

## Phase 1: Detect

Run these commands in the project root before making any changes.
They reveal the build system, DSL type, existing Dynatrace state, and which libraries are present.

```bash
# ── Project structure ─────────────────────────────────────────────────────────
ls build.gradle build.gradle.kts settings.gradle settings.gradle.kts 2>/dev/null
find . -name "build.gradle" -o -name "build.gradle.kts" 2>/dev/null \
  | grep -v '/\.gradle/' | sort

# ── Existing Dynatrace configuration ─────────────────────────────────────────
grep -rn 'com.dynatrace.instrumentation' \
  build.gradle* app/build.gradle* */build.gradle* 2>/dev/null | head -10
grep -rn 'dynatrace {' \
  build.gradle* app/build.gradle* */build.gradle* 2>/dev/null | head -5
grep -rE 'Dynatrace\.startup|DynatraceConfigurationBuilder' app/src/ 2>/dev/null | head -5
grep -rE 'applicationId|beaconUrl' \
  build.gradle* app/build.gradle* */build.gradle* 2>/dev/null \
  | grep -v 'android {' | head -10

# ── Android Gradle Plugin and SDK ─────────────────────────────────────────────
grep -rE 'com\.android\.tools\.build:gradle|com\.android\.application' \
  build.gradle* settings.gradle* 2>/dev/null | head -5
grep -E 'compileSdk|minSdk|targetSdk|compileSdkVersion|minSdkVersion|targetSdkVersion' \
  app/build.gradle app/build.gradle.kts 2>/dev/null | head -6

# ── DSL type ──────────────────────────────────────────────────────────────────
ls app/build.gradle app/build.gradle.kts 2>/dev/null
grep -n 'plugins {' build.gradle build.gradle.kts 2>/dev/null | head -3
grep -n 'buildscript' build.gradle build.gradle.kts 2>/dev/null | head -3

# ── Repository setup ──────────────────────────────────────────────────────────
grep -rn 'mavenCentral' settings.gradle* build.gradle* 2>/dev/null | head -5

# ── Application class ─────────────────────────────────────────────────────────
find app/src/main -name "*.kt" -o -name "*.java" 2>/dev/null \
  | xargs grep -l ': Application()' 2>/dev/null | head -3

# ── Auto-instrumented libraries ───────────────────────────────────────────────
grep -rE 'okhttp|retrofit' app/build.gradle app/build.gradle.kts 2>/dev/null | head -5
grep -rE 'compose|androidx\.compose' app/build.gradle app/build.gradle.kts 2>/dev/null | head -5
grep -rE 'androidx\.navigation' app/build.gradle app/build.gradle.kts 2>/dev/null | head -3
grep -rn 'WebView\|loadUrl' app/src/main 2>/dev/null | head -5
find app/src/main -name "*.kt" 2>/dev/null | head -3
find app/src/main -name "*.java" 2>/dev/null | head -3
```

**What to do with the output:**

| Finding | Action |
| --- | --- |
| `build.gradle.kts` exists | Use Kotlin DSL snippets throughout |
| `plugins {` in root file | Use Plugin DSL approach (Step 2a) |
| `buildscript {` in root file | Use Buildscript Classpath approach (Step 2b) |
| `com.dynatrace.instrumentation` already present | Read existing config before overwriting |
| `mavenCentral()` missing | Add it to `pluginManagement.repositories` first |
| Multiple `build.gradle` files | Multi-module project — see `setup.md` Multi-Module Patterns |
| `WebView` usage found | Enable `hybridMonitoring(true)` |
| OkHttp / Retrofit found | Web request monitoring will work automatically |
| Compose dependencies found | Compose instrumentation is enabled by default (plugin 8.271+) |

**Known values detected by Dynatrace Wizard for this project:**

$detectKnownFacts

---

## Plugin Apply (this project)
$pluginApplySection

---

## Skill Installation

- Target client: `${skillsConfig.skillClient.label}`
- Install scope: `${skillsConfig.skillInstallScope.label}`
- Output path: `$selectedInstallPath`

This skill set consists of **5 files** written to the same directory:
`skills.md` (this file), `setup.md`, `sdk-apis.md`, `monitoring.md`, `troubleshooting.md`.

| Client | User-level path | Project-level path |
| --- | --- | --- |
$installTableRows

User-level = available to all projects; project-level = repository-only.

---

*Generated by Dynatrace Wizard · $generatedAt*
""".trimIndent()
    }

    fun writeSkillsFile(
        projectInfo: ProjectDetectionService.ProjectInfo,
        dynatraceConfig: DynatraceConfig,
        skillsConfig: SkillsExportConfig,
        sdkLibraryModules: List<ProjectDetectionService.ModuleInfo> = emptyList(),
        deselectedModules: List<ProjectDetectionService.ModuleInfo> = emptyList()
    ): String {
        val outputPath = resolveOutputPath(skillsConfig)
        val content =
            generateSkillsMarkdown(projectInfo, dynatraceConfig, skillsConfig, sdkLibraryModules, deselectedModules)
        val root = resolveWriteRoot(projectInfo, skillsConfig.skillInstallScope)
            ?: throw IllegalStateException("Could not resolve destination for skills export.")

        val relativePath = when (skillsConfig.skillInstallScope) {
            SkillInstallScope.PROJECT_LEVEL -> outputPath
            SkillInstallScope.USER_LEVEL -> outputPath.removePrefix("~/")
        }

        // Write the project-specific index (skills.md)
        writeTextFile(root, relativePath, content)

        // Write the 4 static sub-skill files into the same directory
        val directory = relativePath.substringBeforeLast("/")
        SUB_SKILL_FILES.forEach { subFileName ->
            val subContent = loadSubSkillFile(subFileName)
            if (subContent.isNotBlank()) {
                writeTextFile(root, "$directory/$subFileName", subContent)
            }
        }

        return outputPath
    }

    /**
     * Loads a static sub-skill file from plugin resources (`/skills/<name>`).
     * Returns an empty string when the resource is missing (graceful degradation).
     */
    private fun loadSubSkillFile(name: String): String =
        SkillsExportService::class.java
            .getResourceAsStream("/skills/$name")
            ?.bufferedReader(StandardCharsets.UTF_8)
            ?.readText()
            ?: ""

    private fun buildPathFor(client: SkillClient, scope: SkillInstallScope): String {
        val base = when (scope) {
            SkillInstallScope.USER_LEVEL -> client.userLevelDirectory
            SkillInstallScope.PROJECT_LEVEL -> client.projectLevelDirectory
        }
        val normalizedBase = if (base.endsWith('/')) base else "$base/"
        return "$normalizedBase$SKILL_SLUG/$SKILL_FILE_NAME"
    }

    private fun normalizePath(path: String, fallback: String): String {
        val candidate = path.ifBlank { fallback }
            .replace('\\', '/')
            .trim()
        return when {
            candidate.isBlank() -> fallback
            candidate.startsWith("~/") -> candidate
            else -> candidate.removePrefix("/")
        }
    }

    private fun resolveProjectRoot(projectInfo: ProjectDetectionService.ProjectInfo): VirtualFile? {
        return projectInfo.projectBuildFile?.parent
            ?: projectInfo.settingsFile?.parent
            ?: project?.guessProjectDir()
            ?: projectInfo.appBuildFile?.parent?.parent
            ?: projectInfo.appBuildFile?.parent
    }

    private fun resolveWriteRoot(
        projectInfo: ProjectDetectionService.ProjectInfo,
        scope: SkillInstallScope
    ): VirtualFile? = when (scope) {
        SkillInstallScope.PROJECT_LEVEL -> resolveProjectRoot(projectInfo)
        SkillInstallScope.USER_LEVEL -> {
            val userHome = Path.of(System.getProperty("user.home"))
            LocalFileSystem.getInstance().refreshAndFindFileByNioFile(userHome)
        }
    }

    private fun writeTextFile(projectRoot: VirtualFile, relativePath: String, content: String) {
        val segments = relativePath.split('/').filter { it.isNotBlank() }
        if (segments.isEmpty()) return
        val fileName = segments.last()
        val directorySegments = segments.dropLast(1)

        if (project == null) {
            throw IllegalStateException("Project instance is required to write skills.md")
        }

        WriteCommandAction.runWriteCommandAction(project, "Export Skill File", null, {
            var current = projectRoot
            directorySegments.forEach { segment ->
                current = current.findChild(segment) ?: current.createChildDirectory(this, segment)
            }
            val file = current.findChild(fileName) ?: current.createChildData(this, fileName)
            file.setBinaryContent(content.toByteArray(StandardCharsets.UTF_8))
        })
    }

    private fun yesNo(value: Boolean): String = if (value) "Enabled" else "Disabled"
}
