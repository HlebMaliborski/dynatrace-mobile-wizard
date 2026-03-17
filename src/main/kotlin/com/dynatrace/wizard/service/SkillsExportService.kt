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
 * Generates and writes Markdown-only `skills.md` files from the dedicated Skills tab.
 */
class SkillsExportService(private val project: Project? = null) {

    companion object {
        const val SKILL_SLUG = "dynatrace-android-sdk"
        private const val GENERATOR = "dynatrace-wizard"
        private const val SKILL_FILE_NAME = "skills.md"
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
            """

## OneAgent SDK — Library Modules

The following library modules are opted into `agentDependency()` so their code can call Dynatrace APIs directly.
Selected: ${nameList.joinToString()}

Add this block to the **root** `build.gradle.kts`:

```kotlin$filterBlock
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
  Project-specific Dynatrace Mobile SDK setup generated by Dynatrace Wizard for this Android project.
  Contains the exact Gradle configuration, selected features, and full setup reference.
  Use when asked to add, update, or troubleshoot Dynatrace in this project.
license: Apache-2.0
category: sdk-setup
parent: $SKILL_SLUG
disable-model-invocation: true
generated-at: $generatedAt
generated-by: $GENERATOR
---

# Dynatrace Android SDK

Project-specific skill generated by Dynatrace Wizard on $generatedAt.
Contains the **exact configuration for this project** plus the complete setup reference.

---

## Invoke This Skill When

- User asks to add or reconfigure Dynatrace in this Android project
- User wants to know the current Application ID, Beacon URL, or enabled features
- User wants crash reporting, ANR reporting, native crash reporting, Session Replay, user-action capture, or web-request monitoring
- User asks why Dynatrace is not capturing data
- User wants to update, migrate, or remove Dynatrace Gradle configuration
- User asks about `enterAction`, `leaveAction`, `DTXAction`, child actions, or custom user actions
- User asks about `reportValue`, `reportError`, `reportEvent`, or `sendBizEvent`
- User asks about manual web request instrumentation, `WebRequestTiming`, `getRequestTag`, or WebSocket
- User asks about hybrid app monitoring, `instrumentWebView`, `withMonitoredDomains`, or `restoreCookies`
- User asks about `setBeaconHeaders`, `CommunicationProblemListener`, or custom auth headers
- User wants standalone instrumentation without the Gradle plugin
- User asks why a Dynatrace-related build fails or sees a Dynatrace Gradle plugin error message
- User asks why OneAgent is not sending data or monitoring data is missing
- User asks why a user action or UI component is not captured
- User asks about compatibility with other performance monitoring plugins
- User wants to troubleshoot missing web requests, user actions, crashes, or ANR events
$manualStartupSection
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

**Known values detected by Dynatrace Wizard for this project:**

$detectKnownFacts

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

## Step 0 — Identify the Project Layout

Before writing any code, determine:

| Question | How to check |
| --- | --- |
| **Single-app or multi-module?** | Count `com.android.application` plugin declarations across all `build.gradle(.kts)` files |
| **Feature modules present?** | Look for `com.android.dynamic-feature` plugin in any module |
| **Multiple app modules?** | More than one `com.android.application` module |
| **Kotlin DSL or Groovy?** | File extension: `.kts` = Kotlin DSL; no extension = Groovy |
| **Plugin DSL or Buildscript?** | Root build file has `plugins { }` block → Plugin DSL; has `buildscript { dependencies { classpath … } }` → Buildscript Classpath |

---

## Step 1 — Add mavenCentral() Repository

The Dynatrace plugin is distributed via Maven Central. Ensure it is present in all repository blocks that resolve plugins.

**Kotlin DSL (`settings.gradle.kts` — preferred for AGP 7+):**
```kotlin
pluginManagement {
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}
```

**Groovy (`settings.gradle`):**
```groovy
pluginManagement {
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
    }
}
```

For projects that still use a `buildscript {}` block in the root `build.gradle(.kts)`, add `mavenCentral()` there too:

```kotlin
// Kotlin DSL
buildscript {
    repositories { mavenCentral(); google() }
}
```

```groovy
// Groovy
buildscript {
    repositories { mavenCentral(); google() }
}
```

---

## Step 2 — Apply the Plugin
$pluginApplySection

> **Important:** The coordinator plugin (`com.dynatrace.instrumentation`) **must** be declared at the project root, never inside an app module.

---

## Step 3 — Add the `dynatrace { }` Configuration Block

The `dynatrace { }` block lives in the same file where the plugin is applied (root for Plugin DSL, app module for Buildscript Classpath).

**Kotlin DSL — minimal:**
```kotlin
dynatrace {
    configurations {
        create("sampleConfig") {
            variantFilter(".*")
            autoStart {
                applicationId("${dynatraceConfig.applicationId.ifBlank { "<YOUR_APPLICATION_ID>" }}")
                beaconUrl("${dynatraceConfig.beaconUrl.ifBlank { "<YOUR_BEACON_URL>" }}")
            }
        }
    }
}
```

**Groovy — minimal:**
```groovy
dynatrace {
    configurations {
        sampleConfig {
            variantFilter '.*'
            autoStart {
                applicationId '${dynatraceConfig.applicationId.ifBlank { "<YOUR_APPLICATION_ID>" }}'
                beaconUrl '${dynatraceConfig.beaconUrl.ifBlank { "<YOUR_BEACON_URL>" }}'
            }
        }
    }
}
```

---

## Step 4 — Full Feature Reference

### Top-level switches

| Option (Kotlin DSL) | Option (Groovy DSL) | Default | Description |
| --- | --- | --- | --- |
| `strictMode(false)` | `strictMode false` | `false` | When `true`: build fails if no variant matches `variantFilter` |
| `pluginEnabled(false)` | `pluginEnabled false` | `true` | Global kill-switch — disables all bytecode instrumentation without removing config |

### Inside `configurations { create("name") { … } }`

| Option | Default | Description |
| --- | --- | --- |
| `variantFilter("regex")` | — | **Required.** Regex matching variant names (`".*"` = all) |
| `enabled(false)` | `true` | Disables auto-instrumentation for this variant config |

#### `autoStart { }` block

| Option | Default | Description |
| --- | --- | --- |
| `applicationId("…")` | — | **Required.** Dynatrace Application ID |
| `beaconUrl("…")` | — | **Required.** Dynatrace Beacon URL (HTTPS) |
| `userOptIn(true)` | `false` | Require explicit user consent before data capture begins |
| `enabled(false)` | `true` | Disable auto-start (Direct Boot apps call `Dynatrace.startup()` manually) |

#### `userActions { }` block

| Option | Default | Description |
| --- | --- | --- |
| `enabled(false)` | `true` | Disable automatic user-action capture |
| `namePrivacy(true)` | `false` | Mask PII in action names |
| `composeEnabled(false)` | `true` | Disable Jetpack Compose instrumentation |

#### Other monitoring toggles

| Option | Default | Description |
| --- | --- | --- |
| `webRequests { enabled(false) }` | `true` | Disable HTTP/network request monitoring |
| `lifecycle { enabled(false) }` | `true` | Disable Activity/Fragment lifecycle monitoring |
| `crashReporting(false)` | `true` | Disable Java/Kotlin crash reporting |
| `anrReporting(false)` | `true` | Disable ANR reporting (Android 11+) |
| `nativeCrashReporting(false)` | `true` | Disable C/C++ NDK crash reporting (Android 11+) |
| `hybridMonitoring(true)` | `false` | Enable WebView hybrid monitoring |
| `locationMonitoring(true)` | `false` | Enable GPS location capture |
| `sessionReplay.enabled(true)` | `false` | Enable Session Replay (requires Privacy approval) |

#### `behavioralEvents { }` block

| Option | Default | Description |
| --- | --- | --- |
| `detectRageTaps(true)` | `false` | Detect frustrated user tap patterns |

#### `agentBehavior { }` block (advanced)

| Option | Default | Description |
| --- | --- | --- |
| `startupLoadBalancing(true)` | `false` | Client-side ActiveGate load balancing at agent startup |
| `startupWithGrailEnabled(true)` | `false` | New RUM Experience / Grail data pipeline |

#### `exclude { }` block

| Option | Example | Description |
| --- | --- | --- |
| `packages("…", "…")` | `"com.example.heavy"` | Exclude entire packages from bytecode transformation |
| `classes("…", "…")` | `"com.example.Util"` | Exclude specific classes |
| `methods("…", "…")` | `"com.example.Util.expensiveMethod"` | Exclude specific methods |

---

## Multi-Module Project Patterns

### Feature Modules (`com.android.dynamic-feature`)

Dynamic feature modules are instrumented **automatically** — no changes needed in feature module build files.

```
root/
  build.gradle.kts          ← plugin declaration (Plugin DSL)
  app/build.gradle.kts      ← dynatrace { } block + autoStart credentials
  feature_login/            ← no changes needed
  feature_checkout/         ← no changes needed
```

### Multi-App — Plugin DSL Coordinator (recommended)

The coordinator at root instruments all app submodules automatically:

```kotlin
// root build.gradle.kts
plugins {
    id("com.dynatrace.instrumentation") version "8.+"
}
dynatrace {
    configurations {
        create("sampleConfig") {
            variantFilter(".*")
            autoStart {
                applicationId("SHARED_APP_ID")
                beaconUrl("https://tenant.live.dynatrace.com/mbeacon")
            }
        }
    }
}
```

### Multi-App — Per-Module Buildscript Classpath

Use when each module needs its own Application ID / Beacon URL:

```kotlin
// root build.gradle.kts
buildscript {
    dependencies { classpath("com.dynatrace.tools.android:gradle-plugin:8.+") }
}

// each app module build.gradle.kts
plugins {
    id("com.android.application")
    id("com.dynatrace.instrumentation.module") // no version — inherited from classpath
}
dynatrace {
    configurations {
        create("sampleConfig") {
            variantFilter(".*")
            autoStart {
                applicationId("MODULE_SPECIFIC_APP_ID")
                beaconUrl("https://tenant.live.dynatrace.com/mbeacon")
            }
        }
    }
}
```

> `com.dynatrace.instrumentation.module` must be declared **without** a version — the classpath entry supplies it.

---

## OneAgent SDK for Library Modules

If a library module's code needs to call Dynatrace APIs directly (e.g. `Dynatrace.enterAction()`):

```kotlin
// All library modules
subprojects {
    pluginManager.withPlugin("com.android.library") {
        dependencies {
            add("implementation", com.dynatrace.tools.android.DynatracePlugin.agentDependency())
        }
    }
}

// Specific modules only
subprojects { project ->
    pluginManager.withPlugin("com.android.library") {
        if (project.name == "lib-analytics" || project.name == "lib-network") {
            dependencies {
                add("implementation", com.dynatrace.tools.android.DynatracePlugin.agentDependency())
            }
        }
    }
}
```

---

## Manual Startup (when `autoStart.enabled(false)`)

```kotlin
// Kotlin — Application.onCreate()
import com.dynatrace.android.agent.Dynatrace
import com.dynatrace.android.agent.conf.DynatraceConfigurationBuilder

Dynatrace.startup(
    this,
    DynatraceConfigurationBuilder(
        "${dynatraceConfig.applicationId.ifBlank { "<YOUR_APPLICATION_ID>" }}",
        "${dynatraceConfig.beaconUrl.ifBlank { "<YOUR_BEACON_URL>" }}"
    ).buildConfiguration()
)
```

```java
// Java
Dynatrace.startup(this, new DynatraceConfigurationBuilder(
    "${dynatraceConfig.applicationId.ifBlank { "<YOUR_APPLICATION_ID>" }}",
    "${dynatraceConfig.beaconUrl.ifBlank { "<YOUR_BEACON_URL>" }}"
).buildConfiguration());
```

---

## OneAgent SDK — Custom User Actions

```kotlin
// Basic action
val action: DTXAction = Dynatrace.enterAction("Tap on Search")
action.leaveAction()                          // end normally
action.cancel()                               // discard all data (v8.231+)
val done: Boolean = action.isFinished()       // check state (v8.231+)

// Child action
val child = Dynatrace.enterAction("Parse result", parentAction)
child.leaveAction()
```

Max action name: 250 chars. Max duration: 9 minutes (longer actions are discarded).

---

## OneAgent SDK — Custom Value Reporting

```kotlin
action.reportEvent("button_tapped")
action.reportValue("query", searchText)       // String
action.reportValue("result_count", 42)        // Int
action.reportValue("latency_ms", 350L)        // Long
action.reportValue("score", 4.8)              // Double
action.reportError("network_error", -1)       // error code
action.reportError("parse_failed", exception) // exception

// Standalone (not tied to any action):
Dynatrace.reportError("sync_failed", exception)
```

---

## OneAgent SDK — Business Events (v8.253+)

```kotlin
Dynatrace.sendBizEvent("com.example.booking-finished", JSONObject().apply {
    put("event.name", "Confirmed Booking")
    put("amount", 358.35)
    put("currency", "USD")
})
```

Requires an active monitored session — not sent when OneAgent is disabled.

---

## OneAgent SDK — Manual Web Request Instrumentation

For HTTP libraries not auto-instrumented, or for WebSocket / non-HTTP protocols:

```kotlin
// Attached to a user action:
val tag = webAction.getRequestTag()
val timing = Dynatrace.getWebRequestTiming(tag)
// Add header: .addHeader(Dynatrace.getRequestTagHeader(), tag)
timing.startWebRequestTiming()
timing.stopWebRequestTiming(url, responseCode, responseMessage)

// Standalone (auto-associates with open action if one exists):
val tag = Dynatrace.getRequestTag()

// WebSocket — use original URI, not OkHttp's (it rewrites wss:// to https://)
val uri = URI.create("wss://websocket.example.com")
timing.stopWebRequestTiming(uri, code, reason)   // in onClosing/onFailure
```

---

## OneAgent SDK — Hybrid App Monitoring

```kotlin
// 1. Enable in builder:
DynatraceConfigurationBuilder("<id>", "<url>")
    .withHybridMonitoring(true)
    .withMonitoredDomains(".example.com")
    .buildConfiguration()

// 2. Instrument every WebView BEFORE loadUrl:
Dynatrace.instrumentWebView(webView)
webView.loadUrl("https://www.example.com")

// 3. Restore Dynatrace cookies after clearing:
CookieManager.getInstance().removeAllCookies(null)
Dynatrace.restoreCookies()
```

---

## OneAgent SDK — Network & Communication

```kotlin
// Custom auth headers (set before startup when token is known):
Dynatrace.setBeaconHeaders(mapOf("Authorization" to "Bearer ${'$'}token"))
Dynatrace.startup(this, ...)

// Token refresh on 4xx — use CommunicationProblemListener:
DynatraceConfigurationBuilder("<id>", "<url>")
    .withCommunicationProblemListener(object : CommunicationProblemListener {
        override fun onFailure(code: Int, message: String, body: String) {
            Dynatrace.setBeaconHeaders(mapOf("Authorization" to "Bearer ${'$'}{refreshToken()}"))
        }
        override fun onError(t: Throwable) { /* network error — OneAgent retries automatically */ }
    })
    .buildConfiguration()

// Remove headers:
Dynatrace.setBeaconHeaders(null)
```

---

## Removing Dynatrace Configuration

1. Delete the `dynatrace { }` block from all Gradle files
2. Remove the plugin from `plugins { }` or `buildscript { dependencies { classpath … } }`
3. Remove any `apply plugin: 'com.dynatrace.instrumentation'` lines
4. Sync Gradle

---

## Privacy & Data Collection

OneAgent supports three `DataCollectionLevel` values:

| Level | Description |
| --- | --- |
| `OFF` | No data is collected |
| `PERFORMANCE` | Collects performance data (crashes, ANRs) |
| `USER_BEHAVIOR` | Collects performance data + user behavior (user actions, sessions) |

When `userOptIn(true)` is set and the user has not yet consented, defaults are `OFF` with crash reporting disabled.

```kotlin
import com.dynatrace.android.agent.Dynatrace
import com.dynatrace.android.agent.conf.DataCollectionLevel

// Apply after user consents
val updatedOptions = Dynatrace.getUserPrivacyOptions().newBuilder()
    .withDataCollectionLevel(DataCollectionLevel.USER_BEHAVIOR)
    .withCrashReportingOptedIn(true)
    .build()
Dynatrace.applyUserPrivacyOptions(updatedOptions)
```

---

## App Performance Monitoring

OneAgent automatically captures:
- **App Start events** — cold, warm, and hot starts; duration from process creation to first `onResume`
- **Views** — one active view per screen (Activities auto-tracked; others need `startView`)
- **Navigation events** — transitions between views
- **View summaries** — aggregated events on view end

Manual view tracking for fragments, Compose screens, or other non-Activity UI:

```kotlin
Dynatrace.startView("Login") // ends the previous view automatically
```

---

## Web Request Monitoring

OneAgent auto-instruments `HttpURLConnection` and `OkHttp` v3/4/5 (includes Retrofit 2).

### W3C Trace Context (Distributed Tracing)

Automatically propagated on every outgoing request when auto-instrumentation is enabled. For custom networking stacks:

```kotlin
val traceContext = Dynatrace.generateTraceContext(
    request.header("traceparent"),
    request.header("tracestate")
) // returns null if invalid — do NOT modify headers in that case

if (traceContext != null) {
    request = request.newBuilder()
        .header("traceparent", traceContext.traceparent)
        .header("tracestate", traceContext.tracestate)
        .build()
}
```

### Manual Web Request Reporting

```kotlin
import com.dynatrace.android.agent.HttpRequestEventData

val requestData = HttpRequestEventData("https://api.example.com/data", "GET")
    .withDuration(250)
    .withStatusCode(200)
    .addEventProperty("event_properties.api_version", "v2")

Dynatrace.sendHttpRequestEvent(requestData)
```

### OkHttp Event Modifier

```kotlin
val modifier: OkHttpEventModifier = object : OkHttpEventModifier {
    override fun modifyEvent(request: Request, response: Response): JSONObject {
        val event = JSONObject()
        // Always use peekBody() — never body() — to avoid consuming the response stream
        event.put("event_properties.body_preview", response.peekBody(500).toString())
        return event
    }
    override fun modifyEvent(request: Request, throwable: Throwable): JSONObject = JSONObject()
}
Dynatrace.addHttpEventModifier(modifier)
// Return null from either method to drop the event entirely
```

---

## Error and Crash Reporting

### ANR Reporting (Android 11+)

Automatic. The app must be restarted within 10 minutes for the event to be sent. Disable:

```kotlin
// DSL
anrReporting(false)

// Manual startup
DynatraceConfigurationBuilder("<id>", "<url>").withAnrReporting(false).buildConfiguration()
```

### Native Crash Reporting (Android 11+)

Automatic for C/C++ NDK crashes. Disable:

```kotlin
// DSL
nativeCrashReporting(false)

// Manual startup
DynatraceConfigurationBuilder("<id>", "<url>").withNativeCrashReporting(false).buildConfiguration()
```

### Manual Error Reporting

```kotlin
try {
    // ...
} catch (exception: Exception) {
    Dynatrace.sendExceptionEvent(
        ExceptionEventData(exception)
            .addEventProperty("event_properties.context", "checkout")
    )
}
```

---

## Custom Events

Properties must be defined in the Dynatrace UI first (Experience Vitals → frontend → Settings → Event and session properties). Keys must be prefixed with `event_properties.`.

```kotlin
Dynatrace.sendEvent(
    EventData()
        .withDuration(250)
        .addEventProperty("event_properties.checkout_step", "payment_confirmed")
        .addEventProperty("event_properties.cart_value", 149.99)
)
```

### Event Modifiers

```kotlin
val modifier = EventModifier { event ->
    event.put("event_properties.build_type", BuildConfig.BUILD_TYPE)
    event // return null to drop the event
}
Dynatrace.addEventModifier(modifier)

// Remove when no longer needed
Dynatrace.removeEventModifier(modifier)
```

Modifiable fields: `event_properties.*`, `session_properties.*`, `url.full`, `exception.stack_trace`.

---

## User and Session Management

```kotlin
// Tag session after login
Dynatrace.identifyUser("user@example.com")

// Clear on logout
Dynatrace.identifyUser(null)
```

Session properties (must be pre-configured in Dynatrace UI, keys prefixed with `session_properties.`):

```kotlin
Dynatrace.sendSessionPropertyEvent(
    SessionPropertyEventData()
        .addSessionProperty("session_properties.product_tier", "premium")
        .addSessionProperty("session_properties.cart_value", 149.99)
)
```

> The user tag is **not persisted** — call `identifyUser()` on every new session. Session splits re-tag automatically; logout/privacy changes do not.

---

## Common Issues

| Symptom | Likely cause | Fix |
| --- | --- | --- |
| Build fails: "Could not resolve `com.dynatrace.tools.android:gradle-plugin`" | `mavenCentral()` missing from plugin repositories | Add `mavenCentral()` to `pluginManagement.repositories` in `settings.gradle(.kts)` |
| No data in Dynatrace after build | `beaconUrl` is HTTP instead of HTTPS | Use `https://` URL |
| `dynatrace { }` red in IDE | Plugin applied in app module instead of root (Plugin DSL) | Move plugin declaration and config to root `build.gradle(.kts)` |
| Build fails: "No matching variant found" | `variantFilter` regex doesn't match any variant | Use `".*"` or match the exact variant name |
| Duplicate plugin error | Both `com.dynatrace.instrumentation` and `.module` in same module | Remove the coordinator from the app module |
| Session Replay not capturing | Missing privacy consent or flag disabled | Ensure `sessionReplay.enabled(true)` and runtime consent is granted |
| ANR events not appearing | Android version < 11, or app not restarted within 10 min | ANR reporting requires Android 11+; relaunch app within 10 minutes |
| Native crash events not appearing | Android version < 11, or app not restarted within 10 min | Native crash reporting requires Android 11+; relaunch app within 10 minutes |
| No data after opt-in is enabled | `applyUserPrivacyOptions` not called after user consent | Call `Dynatrace.applyUserPrivacyOptions()` with `USER_BEHAVIOR` level after consent |
| Event/session properties dropped | Property not configured in Dynatrace UI, or missing prefix | Define properties in UI first; use `event_properties.` / `session_properties.` prefix |
| `OkHttpEventModifier` body exception | Using `body()` instead of `peekBody()` consumes the stream | Always use `response.peekBody(n)` inside the modifier |
| App start duration shorter than expected | Agent started too late in manual startup | Call `Dynatrace.startup()` as early as possible in `Application.onCreate()` |

---

## Limitations

### Runtime limitations

- **HTTP** — Only `HttpURLConnection` and `OkHttp` (v3/4/5) are auto-instrumented; all other frameworks require manual instrumentation
- **WebSocket and non-HTTP protocols** — require manual instrumentation; connections must close within ~9 minutes
- **Custom actions** — maximum duration 9 minutes; actions open longer are discarded. Maximum name length 250 characters
- **Business events** — only captured in active monitored sessions; not sent when OneAgent is disabled
- **ANR and native crash reporting** — available only on Android 11+; app must be restarted within 10 minutes
- **Events per minute** — default limit is 1,000 events per minute; exceeding this may result in dropped events
- **Direct Boot** — do not call `Dynatrace.startup` from a Direct Boot aware component
- **Offline data** — Dynatrace discards monitoring data older than 10 minutes when the app is offline
- **Truncated values** — action names, reported values, and web request URLs are truncated at 250 characters

### Instrumentation-specific limitations

The Dynatrace Android Gradle plugin instruments `AndroidManifest.xml` and `.class` files only. The following are **not** instrumented:

- **Native code (NDK)** — code written with the Android NDK
- **Web components** — `.html` and `.js` files
- **Resource files** — layout `.xml` files and other Android resources
- **WebView content** — JavaScript inside a WebView; use hybrid monitoring to correlate WebView sessions

### Compatibility with other monitoring tools

Using multiple performance monitoring plugins simultaneously may cause compatibility issues, especially when other plugins also instrument the Android runtime. Use only one monitoring plugin, or verify compatibility through manual testing before releasing to production.

### Build-specific limitations

- **Android library projects** — the plugin auto-instruments only `com.android.application` projects; library code is instrumented when the library is a dependency of an app module
- **Android Gradle plugin `excludes` property** — disables instrumentation for ALL specified classes including Dynatrace-critical ones; use Dynatrace's `exclude { }` block instead
- **`com.dynatrace.instrumentation` must be at root** — applying the coordinator plugin inside an app module build file causes a build error
- **`com.dynatrace.instrumentation` and `com.dynatrace.instrumentation.module` are mutually exclusive** — using both in the same module causes a build error

---

## Troubleshooting

### General checklist

Before investigating a specific symptom, verify:

1. **Technology is supported** — check the Supported Versions table. ANR and native-crash reporting require Android 11+.
2. **Plugin version is current** — ensure you are on a supported `8.x` release.
3. **Debug logging is enabled** — add `debug { agentLogging(true) }`, reproduce the issue, review Logcat filtered by tag `dtx|caa`. Remove before production.
4. **Credentials are correct** — `applicationId` and `beaconUrl` match values from Dynatrace → Mobile → Settings → Instrumentation. Beacon URL must use `https://`.
5. **Network Security Configuration** includes system CA certificates and does not block the beacon endpoint.
6. **Dynatrace endpoint is reachable** from the test device's network.
7. **No other monitoring plugin is interfering** — see Compatibility with other monitoring tools above.

---

### Why is OneAgent not sending monitoring data?

- `applicationId` and `beaconUrl` are correct (copy from Dynatrace → Mobile → Settings → Instrumentation)
- Beacon URL uses `https://`
- `userOptIn` is `false`, or `Dynatrace.applyUserPrivacyOptions()` has been called with `USER_BEHAVIOR` after user consent
- `pluginEnabled` is not set to `false`
- `autoStart { enabled(false) }` is not set unless calling `Dynatrace.startup()` manually

For hybrid apps: `Dynatrace.instrumentWebView(webView)` is called **before** `webView.loadUrl(...)`, and `hybridMonitoring(true)` plus `withMonitoredDomains(...)` are configured.

---

### Why are some web requests missing?

- **Unsupported HTTP library** — only `HttpURLConnection` and `OkHttp` (v3/4/5) are auto-instrumented; use manual `HttpRequestEventData` or `WebRequestTiming` for others
- **Firebase plugin conflict** — the Firebase Gradle plugin can interfere with OkHttp instrumentation
- **Request made outside an active session** — requests are only captured when OneAgent is running

---

### Why are web requests not associated with a user action?

OneAgent attaches web requests to a user action only within a window from when the action opens until **500 ms after** the action closes. Requests outside this window are captured as standalone events. Extend the window via `userActions.timeout` in the `dynatrace {}` block.

---

### Why does my UI component not generate a user action?

- **WebView component** — UI inside a WebView is not auto-captured; use hybrid monitoring
- **Unsupported listener type** — only standard Android listeners are instrumented
- **Unsupported Jetpack Compose component** — not all composables are instrumented; check the supported component list in Dynatrace documentation

---

### Why does Dynatrace not monitor Jetpack Compose in Android Studio's interactive preview?

Interactive preview runs in a sandbox with no network access. Bytecode instrumentation is skipped and OneAgent is never started. This is expected — run on a real device or emulator to verify instrumentation.

---

### Build error reference

| Error message | Cause | Fix |
| --- | --- | --- |
| `OneAgent SDK version does not match Dynatrace Android Gradle plugin version` | SDK JAR and plugin version mismatch | Remove the explicit SDK dependency version; let the plugin inject it via `agentDependency()` |
| `Plugin with id 'com.dynatrace.instrumentation' not found` | Plugin classpath entry missing | Add plugin to root build file; ensure `mavenCentral()` is in plugin repositories |
| `Could not find com.dynatrace.tools.android:gradle-plugin:<version>` | Version not found on Maven Central, or `mavenCentral()` missing | Verify version on Maven Central; add `mavenCentral()` to `pluginManagement.repositories` |
| `Could not get unknown property 'dynatrace' for DefaultDependencyHandler` | `dynatrace {}` block appears before `apply plugin` | Always apply the plugin **before** the `dynatrace {}` block |
| `No configuration for the Dynatrace Android Gradle plugin found!` | Plugin applied but no `dynatrace {}` block provided | Add a `dynatrace { configurations { … } }` block |
| `Task 'printVariantAffiliation' not found in project :<module>` | Plugin only generates tasks for app modules | Run `printVariantAffiliation` on an app module, not a library or root module |
| `The Dynatrace Android Gradle Plugin can only be applied to Android projects` | Plugin applied to a non-Android module | Only apply to modules using `com.android.application` or `com.android.library` |
| `The Dynatrace Android Gradle plugin must be applied in the top-level build.gradle` | Coordinator plugin applied in an app module | Move the coordinator plugin to the root build file |
| `It is not possible to use both 'com.dynatrace.instrumentation' and 'com.dynatrace.instrumentation.module'` | Both plugins applied to the same module | Remove the coordinator from the module; use only `com.dynatrace.instrumentation.module` |

---

## Skill Installation

- Target client: `${skillsConfig.skillClient.label}`
- Install scope: `${skillsConfig.skillInstallScope.label}`
- Output path: `$selectedInstallPath`

| Client | User-level path | Project-level path |
| --- | --- | --- |
$installTableRows

User-level = available to all projects; project-level = repository-only.

The wizard uses `8.+` as the version constraint, allowing automatic minor and patch updates within the `8.x` major line. Bump the major version manually after reviewing Dynatrace release notes.

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
            ?: throw IllegalStateException("Could not resolve destination for skills.md export.")

        val relativePath = when (skillsConfig.skillInstallScope) {
            SkillInstallScope.PROJECT_LEVEL -> outputPath
            SkillInstallScope.USER_LEVEL -> outputPath.removePrefix("~/")
        }

        writeTextFile(root, relativePath, content)
        return outputPath
    }

    private fun buildSkillId(projectInfo: ProjectDetectionService.ProjectInfo): String {
        val base = projectInfo.appModuleName.ifBlank { "app" }
            .lowercase()
            .replace(Regex("[^a-z0-9._-]"), "-")
            .trim('-')
            .ifBlank { "app" }
        return "com.dynatrace.skill.$base.instrumentation"
    }

    private fun buildCapabilities(config: DynatraceConfig): List<SkillCapability> {
        val capabilities = mutableListOf(
            SkillCapability.CONFIGURE_DYNATRACE_GRADLE,
            SkillCapability.UPDATE_DYNATRACE_SETTINGS,
            SkillCapability.EXPORT_SUMMARY_PREVIEW
        )
        if (config.crashReporting) capabilities += SkillCapability.ENABLE_CRASH_REPORTING
        if (config.sessionReplayEnabled) capabilities += SkillCapability.ENABLE_SESSION_REPLAY
        if (config.userOptIn || config.namePrivacy) capabilities += SkillCapability.ENFORCE_PRIVACY_MODE
        return capabilities
    }

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
