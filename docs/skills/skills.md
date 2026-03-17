---
name: dynatrace-android-sdk
description: >
  Complete reference for configuring the Dynatrace Mobile SDK in Android projects via the
  Dynatrace Gradle plugin and OneAgent SDK. Covers Plugin DSL and Buildscript Classpath
  approaches, single-app, feature-module, and multi-app project layouts, Kotlin DSL and
  Groovy DSL, all supported monitoring features, privacy and data collection, app performance
  monitoring, web request monitoring, error and crash reporting, custom events, user/session
  management, custom user actions, business events, manual web request instrumentation,
  hybrid WebView monitoring, network/communication configuration, and standalone manual
  instrumentation without the Gradle plugin.
license: Apache-2.0
category: sdk-setup
generated-by: dynatrace-wizard
disable-model-invocation: true
---

# Dynatrace Android SDK — Setup Skill

This skill teaches you how to configure the [Dynatrace Gradle plugin](https://docs.dynatrace.com/docs/shortlink/android-instrumentation-plugin) in any Android project. It mirrors every capability of the **Dynatrace Mobile Wizard** IntelliJ plugin so you can apply or explain changes without running the wizard.

---

## Invoke This Skill When

- User asks to add Dynatrace monitoring to an Android app
- User asks about `com.dynatrace.instrumentation`, Dynatrace Gradle plugin, or Dynatrace Mobile SDK
- User wants crash reporting, ANR reporting, native crash reporting, Session Replay, user-action capture, web-request monitoring, or privacy mode in Android
- User asks why Dynatrace isn't capturing data after setup
- User wants to update, remove, or migrate their Dynatrace Gradle configuration
- User has a multi-module, feature-module, or multi-app Android project and needs per-module setup
- User asks about `DataCollectionLevel`, `applyUserPrivacyOptions`, or user opt-in/consent flows
- User asks about manual view tracking, app start measurement, or navigation events
- User asks about W3C Trace Context, distributed tracing, `OkHttpEventModifier`, or `HttpRequestEventData`
- User asks about `ExceptionEventData`, custom events, `EventData`, `EventModifier`, or session properties
- User asks about `identifyUser`, `SessionPropertyEventData`, or user tagging
- User asks about `Dynatrace.enterAction`, `leaveAction`, `DTXAction`, child actions, or custom user actions
- User asks about `reportEvent`, `reportValue`, `reportError`, or `sendBizEvent`
- User asks about `WebRequestTiming`, `getRequestTag`, manual web request instrumentation, or WebSocket monitoring
- User asks about hybrid app monitoring, `instrumentWebView`, `withMonitoredDomains`, or `withHybridMonitoring`
- User asks about `setBeaconHeaders`, `CommunicationProblemListener`, certificate pinning, or custom auth headers
- User wants to instrument without the Gradle plugin (standalone manual instrumentation)
- User asks about `DynatraceConfigurationBuilder` options, `withCrashReporting`, `withUserOptIn`, `withActivityMonitoring`
- User asks about `Dynatrace.endVisit` or forcing a session to end
- User asks why a Dynatrace-related build fails or sees an error message from the Dynatrace Gradle plugin
- User asks why OneAgent is not sending data, or monitoring data is missing after setup
- User asks about compatibility issues with other performance monitoring plugins
- User asks why a user action or specific UI component is not captured
- User asks why Jetpack Compose is not monitored in Android Studio interactive preview
- User asks about `userActions.timeout` or web requests not associating with user actions
- User wants to troubleshoot missing web requests, user actions, crashes, or ANR events
- User asks about instrumentation limitations (NDK, WebView, resource files, library projects)

---

## Supported Versions

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

## Phase 1: Detect

Run these commands in the project root before making any recommendations or changes.
They reveal the build system, DSL type, existing Dynatrace state, and which libraries are present.

```bash
# ── Project structure ─────────────────────────────────────────────────────────
# Identify which root build files exist and what DSL is used
ls build.gradle build.gradle.kts settings.gradle settings.gradle.kts 2>/dev/null

# List all build files to discover module layout
find . -name "build.gradle" -o -name "build.gradle.kts" 2>/dev/null \
  | grep -v '/\.gradle/' | sort

# ── Existing Dynatrace configuration ─────────────────────────────────────────
# Check if the plugin is already applied anywhere
grep -rn 'com.dynatrace.instrumentation' \
  build.gradle* app/build.gradle* */build.gradle* 2>/dev/null | head -10

# Check if a dynatrace {} block already exists
grep -rn 'dynatrace {' \
  build.gradle* app/build.gradle* */build.gradle* 2>/dev/null | head -5

# Check if Dynatrace.startup() is called manually
grep -rE 'Dynatrace\.startup|DynatraceConfigurationBuilder' app/src/ 2>/dev/null | head -5

# Read existing applicationId and beaconUrl from dynatrace {} block
grep -rE 'applicationId|beaconUrl' \
  build.gradle* app/build.gradle* */build.gradle* 2>/dev/null \
  | grep -v 'android {' | head -10

# ── Android Gradle Plugin and SDK ─────────────────────────────────────────────
# Detect AGP version (must be 7.0+ for Dynatrace plugin 8.x)
grep -rE 'com\.android\.tools\.build:gradle|com\.android\.application' \
  build.gradle* settings.gradle* 2>/dev/null | head -5

# Check compile/min/target SDK
grep -E 'compileSdk|minSdk|targetSdk|compileSdkVersion|minSdkVersion|targetSdkVersion' \
  app/build.gradle app/build.gradle.kts 2>/dev/null | head -6

# ── DSL type ──────────────────────────────────────────────────────────────────
# Kotlin DSL (.kts) or Groovy (no extension)?
ls app/build.gradle app/build.gradle.kts 2>/dev/null

# Plugin DSL (plugins {}) or Buildscript classpath?
grep -n 'plugins {' build.gradle build.gradle.kts 2>/dev/null | head -3
grep -n 'buildscript' build.gradle build.gradle.kts 2>/dev/null | head -3

# ── Repository setup ──────────────────────────────────────────────────────────
# Check if mavenCentral() is already present
grep -rn 'mavenCentral' settings.gradle* build.gradle* 2>/dev/null | head -5

# ── Application class ─────────────────────────────────────────────────────────
# Find Application subclass (needed for manual Dynatrace.startup())
find app/src/main -name "*.kt" -o -name "*.java" 2>/dev/null \
  | xargs grep -l ': Application()' 2>/dev/null | head -3

# ── Auto-instrumented libraries ───────────────────────────────────────────────
# OkHttp / Retrofit — HTTP monitoring
grep -rE 'okhttp|retrofit' \
  app/build.gradle app/build.gradle.kts 2>/dev/null | head -5

# Jetpack Compose — UI action capture
grep -rE 'compose|androidx\.compose' \
  app/build.gradle app/build.gradle.kts 2>/dev/null | head -5

# Jetpack Navigation — affects user action naming
grep -rE 'androidx\.navigation' \
  app/build.gradle app/build.gradle.kts 2>/dev/null | head -3

# WebView usage — relevant for hybrid monitoring
grep -rn 'WebView\|loadUrl' app/src/main 2>/dev/null | head -5

# Kotlin vs Java source files
find app/src/main -name "*.kt"   2>/dev/null | head -3
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
| Multiple `build.gradle` files | Multi-module project — see Multi-Module Patterns |
| `WebView` usage found | Enable `hybridMonitoring(true)` |
| OkHttp / Retrofit found | Web request monitoring will work automatically |
| Compose dependencies found | Compose instrumentation is enabled by default (plugin 8.271+) |

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
    repositories {
        mavenCentral()
        google()
    }
}
```

```groovy
// Groovy
buildscript {
    repositories {
        mavenCentral()
        google()
    }
}
```

---

## Step 2 — Apply the Plugin

### 2a. Plugin DSL (recommended — root `build.gradle.kts` or `build.gradle`)

Use this when the root build file already has a `plugins { }` block.

**Kotlin DSL:**
```kotlin
plugins {
    id("com.android.application") version "8.3.0" apply false
    id("com.dynatrace.instrumentation") version "8.+" apply false
}
```

**Groovy:**
```groovy
plugins {
    id 'com.android.application' version '8.3.0' apply false
    id 'com.dynatrace.instrumentation' version '8.+' apply false
}
```

> **Important:** The Dynatrace coordinator plugin **must** be declared at the project root. Do not apply it inside an app module's `build.gradle`.

---

### 2b. Buildscript Classpath (legacy — root build file)

Use this when the root build file uses `buildscript { dependencies { classpath … } }` with no top-level `plugins { }` block.

**Kotlin DSL:**
```kotlin
buildscript {
    repositories { mavenCentral(); google() }
    dependencies {
        classpath("com.android.tools.build:gradle:8.3.0")
        classpath("com.dynatrace.tools.android:gradle-plugin:8.+")
    }
}
```

**Groovy:**
```groovy
buildscript {
    repositories { mavenCentral(); google() }
    dependencies {
        classpath 'com.android.tools.build:gradle:8.3.0'
        classpath 'com.dynatrace.tools.android:gradle-plugin:8.+'
    }
}
```

Then in the **app module** `build.gradle(.kts)`:

```kotlin
// Kotlin DSL
apply(plugin = "com.dynatrace.instrumentation")
```

```groovy
// Groovy
apply plugin: 'com.dynatrace.instrumentation'
```

---

## Step 3 — Add the `dynatrace { }` Configuration Block

The `dynatrace { }` block lives in the same file where the plugin is applied (root for Plugin DSL, app module for Buildscript Classpath).

### Minimal configuration

**Kotlin DSL:**
```kotlin
dynatrace {
    configurations {
        create("sampleConfig") {
            variantFilter(".*")
            autoStart {
                applicationId("<YOUR_APPLICATION_ID>")
                beaconUrl("<YOUR_BEACON_URL>")
            }
        }
    }
}
```

**Groovy:**
```groovy
dynatrace {
    configurations {
        sampleConfig {
            variantFilter '.*'
            autoStart {
                applicationId '<YOUR_APPLICATION_ID>'
                beaconUrl '<YOUR_BEACON_URL>'
            }
        }
    }
}
```

- `applicationId` — found in Dynatrace → Mobile → (your app) → Settings → Instrumentation
- `beaconUrl` — found in Dynatrace → Mobile → (your app) → Settings → Instrumentation (must be HTTPS)
- `variantFilter` — regex matching build variant names; `".*"` instruments all variants

---

## Step 4 — Full Feature Reference

The `dynatrace { }` block supports the options below. Only add the keys that differ from the defaults — the wizard and this skill omit keys that are at their default value.

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
| `enabled(false)` | `true` | Disable auto-start (for Direct Boot apps that call `Dynatrace.startup()` manually) |

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
| `anrReporting(false)` | `true` | Disable ANR (Application Not Responding) reporting (Android 11+) |
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

#### `debug { }` block ⚠ NOT for production

| Option | Default | Description |
| --- | --- | --- |
| `agentLogging(true)` | `false` | Write verbose OneAgent output to Android Logcat (filter tag: `dtx\|caa`). **Remove before Play Store / release builds.** |

> Do not enable debug logging in production applications. Extra logging may slow down the app and expose sensitive data in device logs.

**Kotlin DSL:**
```kotlin
dynatrace {
    configurations {
        create("sampleConfig") {
            variantFilter(".*")
            autoStart {
                applicationId("<YOUR_APPLICATION_ID>")
                beaconUrl("<YOUR_BEACON_URL>")
            }
            debug {
                agentLogging(true)
            }
        }
    }
}
```

**Groovy:**
```groovy
dynatrace {
    configurations {
        sampleConfig {
            variantFilter '.*'
            autoStart {
                applicationId '<YOUR_APPLICATION_ID>'
                beaconUrl '<YOUR_BEACON_URL>'
            }
            debug {
                agentLogging true
            }
        }
    }
}
```

**Via OneAgent SDK** (alternative — no Gradle plugin required):

```kotlin
// Kotlin
Dynatrace.startup(this, DynatraceConfigurationBuilder(
    "<YourApplicationID>", "<ProvidedBeaconURL>"
).withDebugLogging(true).buildConfiguration())
```

```java
// Java
Dynatrace.startup(this, new DynatraceConfigurationBuilder(
    "<YourApplicationID>", "<ProvidedBeaconURL>"
).withDebugLogging(true).buildConfiguration());
```

Retrieve logs via **Logcat** (Android Studio → View → Tool Windows → Logcat) and filter by tag `dtx|caa`.

#### `exclude { }` block

| Option | Example | Description |
| --- | --- | --- |
| `packages("…", "…")` | `"com.example.heavy"` | Exclude entire packages from bytecode transformation |
| `classes("…", "…")` | `"com.example.Util"` | Exclude specific classes |
| `methods("…", "…")` | `"com.example.Util.expensiveMethod"` | Exclude specific methods |

---

### Complete example — all features enabled (Kotlin DSL)

```kotlin
dynatrace {
    strictMode(false)
    configurations {
        create("releaseConfig") {
            variantFilter("release")
            autoStart {
                applicationId("ABCD1234")
                beaconUrl("https://tenant.live.dynatrace.com/mbeacon")
                userOptIn(true)
            }
            userActions {
                namePrivacy(true)
                composeEnabled(false)
            }
            webRequests { enabled(false) }
            lifecycle { enabled(false) }
            crashReporting(false)
            anrReporting(false)
            nativeCrashReporting(false)
            hybridMonitoring(true)
            locationMonitoring(true)
            sessionReplay.enabled(true)
            behavioralEvents {
                detectRageTaps(true)
            }
            agentBehavior {
                startupLoadBalancing(true)
                startupWithGrailEnabled(true)
            }
            exclude {
                packages("com.example.analytics", "com.example.legacy")
                classes("com.example.HeavyProcessor")
                methods("com.example.Util.expensiveMethod")
            }
        }
    }
}
```

**Groovy:**
```groovy
dynatrace {
    strictMode false
    configurations {
        releaseConfig {
            variantFilter 'release'
            autoStart {
                applicationId 'ABCD1234'
                beaconUrl 'https://tenant.live.dynatrace.com/mbeacon'
                userOptIn true
            }
            userActions {
                namePrivacy true
                composeEnabled false
            }
            webRequests { enabled false }
            lifecycle { enabled false }
            crashReporting false
            anrReporting false
            nativeCrashReporting false
            hybridMonitoring true
            locationMonitoring true
            sessionReplay.enabled true
            behavioralEvents {
                detectRageTaps true
            }
            agentBehavior {
                startupLoadBalancing true
                startupWithGrailEnabled true
            }
            exclude {
                packages 'com.example.analytics', 'com.example.legacy'
                classes 'com.example.HeavyProcessor'
                methods 'com.example.Util.expensiveMethod'
            }
        }
    }
}
```

---

## Multi-Module Project Patterns

### Feature Modules (`com.android.dynamic-feature`)

Dynamic feature modules are instrumented **automatically** — no changes needed in feature module build files.

Apply the plugin at the root and add `dynatrace { }` to the **base app module** only:

```
root/
  build.gradle.kts          ← plugin declaration (Plugin DSL)
  app/build.gradle.kts      ← dynatrace { } block + autoStart credentials
  feature_login/build.gradle.kts   ← no changes needed
  feature_checkout/build.gradle.kts ← no changes needed
```

### Multi-App Modules (multiple `com.android.application`)

#### Option A — Plugin DSL Coordinator (recommended)

Apply the coordinator at root with `apply true`. It automatically instruments all app submodules. No per-module changes needed.

**Root `build.gradle.kts`:**
```kotlin
plugins {
    id("com.dynatrace.instrumentation") version "8.+" // apply true (default)
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

**Root `build.gradle` (Groovy):**
```groovy
plugins {
    id 'com.dynatrace.instrumentation' version '8.+'
}

dynatrace {
    configurations {
        sampleConfig {
            variantFilter '.*'
            autoStart {
                applicationId 'SHARED_APP_ID'
                beaconUrl 'https://tenant.live.dynatrace.com/mbeacon'
            }
        }
    }
}
```

#### Option B — Per-Module Buildscript Classpath

Add classpath to root; apply `com.dynatrace.instrumentation.module` individually in each app module. Use this when different app modules need different Application IDs or Beacon URLs.

**Root `build.gradle.kts`:**
```kotlin
buildscript {
    dependencies {
        classpath("com.dynatrace.tools.android:gradle-plugin:8.+")
    }
}
```

**Root `build.gradle` (Groovy):**
```groovy
buildscript {
    dependencies {
        classpath 'com.dynatrace.tools.android:gradle-plugin:8.+'
    }
}
```

**Each app module `build.gradle.kts`:**
```kotlin
plugins {
    id("com.android.application")
    id("com.dynatrace.instrumentation.module") // no version — inherited from root classpath
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

**Each app module `build.gradle` (Groovy):**
```groovy
plugins {
    id 'com.android.application'
    id 'com.dynatrace.instrumentation.module'
}

dynatrace {
    configurations {
        sampleConfig {
            variantFilter '.*'
            autoStart {
                applicationId 'MODULE_SPECIFIC_APP_ID'
                beaconUrl 'https://tenant.live.dynatrace.com/mbeacon'
            }
        }
    }
}
```

> `com.dynatrace.instrumentation.module` must be declared **without** a version when the root classpath entry supplies it. Adding a version makes Gradle resolve from a remote repository instead.

---

## OneAgent SDK for Library Modules

If a library module's own code needs to call Dynatrace APIs directly (e.g. `Dynatrace.enterAction()`), inject `agentDependency()` via a `subprojects { }` block in the root build file.

**All library modules (Kotlin DSL):**
```kotlin
subprojects {
    pluginManager.withPlugin("com.android.library") {
        dependencies {
            add("implementation", com.dynatrace.tools.android.DynatracePlugin.agentDependency())
        }
    }
}
```

**All library modules (Groovy):**
```groovy
subprojects {
    pluginManager.withPlugin('com.android.library') {
        dependencies {
            implementation com.dynatrace.tools.android.DynatracePlugin.agentDependency()
        }
    }
}
```

**Specific library modules only (Kotlin DSL):**
```kotlin
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

**Specific library modules only (Groovy):**
```groovy
subprojects { project ->
    pluginManager.withPlugin('com.android.library') {
        if (project.name == 'lib-analytics' || project.name == 'lib-network') {
            dependencies {
                implementation com.dynatrace.tools.android.DynatracePlugin.agentDependency()
            }
        }
    }
}
```

---

## Manual Startup (when `autoStart.enabled(false)`)

When auto-start is disabled, call `Dynatrace.startup()` in your `Application.onCreate()` as early as possible to ensure accurate app start duration measurement:

```kotlin
// Kotlin
import com.dynatrace.android.agent.Dynatrace
import com.dynatrace.android.agent.conf.DynatraceConfigurationBuilder

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Dynatrace.startup(
            this,
            DynatraceConfigurationBuilder(
                "<YOUR_APPLICATION_ID>",
                "<YOUR_BEACON_URL>"
            ).buildConfiguration()
        )
    }
}
```

```java
// Java
Dynatrace.startup(this, new DynatraceConfigurationBuilder(
    "<YOUR_APPLICATION_ID>",
    "<YOUR_BEACON_URL>"
).buildConfiguration());
```

> Values set in `DynatraceConfigurationBuilder` override DSL values in `dynatrace { }`.

---

## Standalone Manual Instrumentation (No Gradle Plugin)

Use this approach when you cannot use the Dynatrace Android Gradle plugin (e.g. technical limitations with the build system).

### Step 1 — Add the OneAgent dependency

```kotlin
// Kotlin DSL — app/build.gradle.kts
dependencies {
    implementation("com.dynatrace.agent:agent-android:8.+")
}
```

```groovy
// Groovy — app/build.gradle
dependencies {
    implementation 'com.dynatrace.agent:agent-android:8.+'
}
```

For multi-module projects with feature modules, add it as `api` in the base app module so feature modules can access the SDK.

### Step 2 — Start OneAgent manually

```kotlin
// Kotlin
class YourApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Dynatrace.startup(this, DynatraceConfigurationBuilder(
            "<YourApplicationID>",
            "<ProvidedBeaconUrl>"
        ).buildConfiguration())
    }
}
```

```java
// Java
public class YourApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Dynatrace.startup(this, new DynatraceConfigurationBuilder(
            "<YourApplicationID>",
            "<ProvidedBeaconUrl>"
        ).buildConfiguration());
    }
}
```

When auto-instrumentation is **also** active, disable the injected auto-start first with `autoStart { enabled(false) }` in the DSL — otherwise the injected call runs before yours and your configuration is ignored.

---

## DynatraceConfigurationBuilder Reference

Key builder methods (chained before `.buildConfiguration()`):

| Method | Default | Description |
| --- | --- | --- |
| `.withUserOptIn(true)` | `false` | Enable user consent / opt-in mode |
| `.withCrashReporting(false)` | `true` | Disable crash reporting |
| `.withAnrReporting(false)` | `true` | Disable ANR reporting (Android 11+) |
| `.withNativeCrashReporting(false)` | `true` | Disable NDK crash reporting (Android 11+) |
| `.withActivityMonitoring(false)` | `true` | Disable Activity lifecycle monitoring |
| `.withHybridMonitoring(true)` | `false` | Enable WebView hybrid monitoring |
| `.withStartupLoadBalancing(true)` | `false` | Client-side ActiveGate load balancing |
| `.withMonitoredDomains(".<domain>")` | — | Domains for hybrid cookie injection |
| `.withMonitoredHttpsDomains("https://.<domain>")` | — | Same but adds Secure cookie flag (v8.237+) |
| `.fileDomainCookies(false)` | `true` | Disable cookies for `file://` domains (v8.271+) |
| `.withCommunicationProblemListener(listener)` | — | Token-refresh callback |

---

## Custom User Actions

Custom actions let you measure time spans around meaningful user interactions and attach additional data.

```kotlin
// Kotlin
val action: DTXAction = Dynatrace.enterAction("Tap on Search")
// ... do work ...
action.leaveAction()
```

```java
// Java
DTXAction action = Dynatrace.enterAction("Tap on Search");
// ... do work ...
action.leaveAction();
```

- Maximum action name length: 250 characters
- Maximum action duration: 9 minutes (actions open longer are discarded)

### Child Actions

```kotlin
// Kotlin
val parentAction = Dynatrace.enterAction("Tap on Search")
val childAction = Dynatrace.enterAction("Parse result", parentAction)
childAction.leaveAction()
parentAction.leaveAction() // closing parent also closes any open children
```

```java
// Java
DTXAction parentAction = Dynatrace.enterAction("Tap on Search");
DTXAction childAction = Dynatrace.enterAction("Parse result", parentAction);
childAction.leaveAction();
parentAction.leaveAction();
```

Up to 9 levels of nesting are supported.

### Cancel an Action (v8.231+)

Cancelling discards all data associated with the action (reported values, child actions).

```kotlin
// Kotlin
val action = Dynatrace.enterAction("Tap on Purchase")
try {
    performWork()
    action.leaveAction()          // success — data is kept and sent
} catch (e: Exception) {
    action.cancel()               // failure — all data is discarded
}
```

```java
// Java
DTXAction action = Dynatrace.enterAction("Tap on Purchase");
try {
    performWork();
    action.leaveAction();
} catch (Exception e) {
    action.cancel();
}
```

### Check Action State (v8.231+)

```kotlin
// Kotlin
if (!action.isFinished()) {
    action.reportValue("step", "payment")
}
```

```java
// Java
if (!action.isFinished()) {
    action.reportValue("step", "payment");
}
```

An action is finished after `leaveAction()`, `cancel()`, or termination by OneAgent.

---

## Custom Value Reporting

All reporting methods work on an open `DTXAction`. Values appear in the waterfall analysis for that action.

### Report an Event

```kotlin
// Kotlin
action.reportEvent("button_tapped")
```

```java
// Java
action.reportEvent("button_tapped");
```

### Report Values

```kotlin
// Kotlin
action.reportValue("query", searchText)          // String
action.reportValue("result_count", 42)           // Int
action.reportValue("latency_ms", 350L)           // Long
action.reportValue("score", 4.8)                 // Double
```

```java
// Java
action.reportValue("query", searchText);         // String
action.reportValue("result_count", 42);          // int
action.reportValue("latency_ms", 350L);          // long
action.reportValue("score", 4.8);               // double
```

### Report Errors

```kotlin
// Kotlin
// Error code (attached to action)
action.reportError("network_error", -1)

// Exception (attached to action)
action.reportError("parse_failed", exception)

// Standalone errors (not tied to an action)
Dynatrace.reportError("background_sync_failed", -2)
Dynatrace.reportError("unhandled_state", exception)
```

```java
// Java
action.reportError("network_error", -1);
action.reportError("parse_failed", exception);
Dynatrace.reportError("background_sync_failed", -2);
Dynatrace.reportError("unhandled_state", exception);
```

---

## Business Events (v8.253+)

Business events are standalone events sent separately from user actions or sessions. They require an active monitored session — if OneAgent is disabled, business events are not reported.

```kotlin
// Kotlin
import org.json.JSONObject

val attributes = JSONObject().apply {
    put("event.name", "Confirmed Booking")
    put("product", "Danube Anna Hotel")
    put("amount", 358.35)
    put("currency", "USD")
    put("arrivalDate", "2022-11-05")
    put("departureDate", "2022-11-15")
    put("journeyDuration", 10)
    put("adultTravelers", 2)
}

Dynatrace.sendBizEvent("com.easytravel.funnel.booking-finished", attributes)
```

```java
// Java
import org.json.JSONObject;

JSONObject attributes = new JSONObject();
try {
    attributes.put("event.name", "Confirmed Booking");
    attributes.put("product", "Danube Anna Hotel");
    attributes.put("amount", 358.35);
    attributes.put("currency", "USD");
    attributes.put("arrivalDate", "2022-11-05");
    attributes.put("departureDate", "2022-11-15");
    attributes.put("journeyDuration", 10);
    attributes.put("adultTravelers", 2);
} catch (JSONException e) { /* ignore */ }

Dynatrace.sendBizEvent("com.easytravel.funnel.booking-finished", attributes);
```

---

## Manual Web Request Instrumentation (SDK)

Use this when your HTTP library is not auto-instrumented, or when you need to instrument non-HTTP protocols.

> For auto-instrumented frameworks (HttpURLConnection, OkHttp), do **not** combine automatic and manual instrumentation.

### Attach a Web Request to a User Action

```kotlin
// Kotlin
val url = URL("https://api.example.com/search")
val webAction = Dynatrace.enterAction("Search request")
val tag = webAction.getRequestTag()
val timing = Dynatrace.getWebRequestTiming(tag)

val request = Request.Builder()
    .url(url)
    .addHeader(Dynatrace.getRequestTagHeader(), tag)
    .build()

timing.startWebRequestTiming()
try {
    val response = client.newCall(request).execute()
    timing.stopWebRequestTiming(url, response.code, response.message)
} catch (e: IOException) {
    timing.stopWebRequestTiming(url, -1, e.toString())
} finally {
    webAction.leaveAction()
}
```

```java
// Java
URL url = new URL("https://api.example.com/search");
DTXAction webAction = Dynatrace.enterAction("Search request");
String tag = webAction.getRequestTag();
WebRequestTiming timing = Dynatrace.getWebRequestTiming(tag);

Request request = new Request.Builder()
    .url(url)
    .addHeader(Dynatrace.getRequestTagHeader(), tag)
    .build();

timing.startWebRequestTiming();
try {
    Response response = client.newCall(request).execute();
    timing.stopWebRequestTiming(url, response.code(), response.message());
} catch (IOException e) {
    timing.stopWebRequestTiming(url, -1, e.toString());
} finally {
    webAction.leaveAction();
}
```

### Standalone Web Request (no parent action)

```kotlin
// Kotlin
val tag = Dynatrace.getRequestTag()          // not tied to any action
val timing = Dynatrace.getWebRequestTiming(tag)
// ... attach header, start/stop timing the same way ...
```

```java
// Java
String tag = Dynatrace.getRequestTag();
WebRequestTiming timing = Dynatrace.getWebRequestTiming(tag);
// ... attach header, start/stop timing the same way ...
```

### WebSocket / Non-HTTP Requests (v8.249+)

> Pass the **original URI** — do not retrieve it from the OkHttp object (it rewrites `wss://` to `https://`).

```kotlin
// Kotlin
val uri = URI.create("wss://websocket.example.com")
val wsAction = Dynatrace.enterAction("WebSocket")
val timing = Dynatrace.getWebRequestTiming(wsAction.getRequestTag())

val request = Request.Builder().url(uri.toString()).build()
timing.startWebRequestTiming()

client.newWebSocket(request, object : WebSocketListener() {
    override fun onClosing(ws: WebSocket, code: Int, reason: String) {
        timing.stopWebRequestTiming(uri, code, reason)
        wsAction.leaveAction()
    }
    override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
        timing.stopWebRequestTiming(uri, 1011, "ERROR")
        wsAction.leaveAction()
    }
})
```

```java
// Java
URI uri = URI.create("wss://websocket.example.com");
DTXAction wsAction = Dynatrace.enterAction("WebSocket");
WebRequestTiming timing = Dynatrace.getWebRequestTiming(wsAction.getRequestTag());

Request request = new Request.Builder().url(uri.toString()).build();
timing.startWebRequestTiming();

client.newWebSocket(request, new WebSocketListener() {
    @Override
    public void onClosing(WebSocket ws, int code, String reason) {
        timing.stopWebRequestTiming(uri, code, reason);
        wsAction.leaveAction();
    }
    @Override
    public void onFailure(WebSocket ws, Throwable t, Response response) {
        timing.stopWebRequestTiming(uri, 1011, "ERROR");
        wsAction.leaveAction();
    }
});
```

WebSocket connections must close within ~9 minutes or they may not be reported.

---

## Hybrid App Monitoring (WebView)

### Enable in DynatraceConfigurationBuilder

```kotlin
// Kotlin
DynatraceConfigurationBuilder("<id>", "<url>")
    .withHybridMonitoring(true)
    .withMonitoredDomains(".example.com", ".api.example.com")
    .buildConfiguration()

// Or with Secure cookie flag (v8.237+):
DynatraceConfigurationBuilder("<id>", "<url>")
    .withHybridMonitoring(true)
    .withMonitoredHttpsDomains("https://.example.com")
    .buildConfiguration()
```

```java
// Java
new DynatraceConfigurationBuilder("<id>", "<url>")
    .withHybridMonitoring(true)
    .withMonitoredDomains(".example.com", ".api.example.com")
    .buildConfiguration();

// Or with Secure cookie flag (v8.237+):
new DynatraceConfigurationBuilder("<id>", "<url>")
    .withHybridMonitoring(true)
    .withMonitoredHttpsDomains("https://.example.com")
    .buildConfiguration();
```

Domain names must start with a period (`.`).

### Instrument Every WebView

Call `instrumentWebView` **before** `loadUrl`:

```kotlin
// Kotlin
val webView = findViewById<WebView>(R.id.webview)
Dynatrace.instrumentWebView(webView)  // must come before loadUrl
webView.loadUrl("https://www.example.com")
```

```java
// Java
WebView webView = findViewById(R.id.webview);
Dynatrace.instrumentWebView(webView);   // must come before loadUrl
webView.loadUrl("https://www.example.com");
```

### Preserve Dynatrace Cookies

When clearing cookies, restore Dynatrace cookies immediately after:

```kotlin
// Kotlin
CookieManager.getInstance().removeAllCookies(null)
Dynatrace.restoreCookies()   // must be called after clearing cookies
```

```java
// Java
CookieManager.getInstance().removeAllCookies(null);
Dynatrace.restoreCookies();
```

### Disable file:// Domain Cookies (v8.271+)

```kotlin
// Kotlin
DynatraceConfigurationBuilder("<id>", "<url>")
    .withHybridMonitoring(true)
    .fileDomainCookies(false)
    .buildConfiguration()
```

```java
// Java
new DynatraceConfigurationBuilder("<id>", "<url>")
    .withHybridMonitoring(true)
    .fileDomainCookies(false)
    .buildConfiguration();
```

---

## Network & Communication Configuration

### Custom Beacon Headers (Authorization, Cookies)

Set headers **before** `Dynatrace.startup` when the token is known at startup:

```kotlin
// Kotlin
Dynatrace.setBeaconHeaders(mapOf(
    "Authorization" to "Basic $encodedCredentials",
    "Cookie" to "session=abc123"
))

Dynatrace.startup(this, DynatraceConfigurationBuilder("<id>", "<url>")
    .buildConfiguration())
```

```java
// Java
Map<String, String> headers = new HashMap<>();
headers.put("Authorization", "Basic " + encodedCredentials);
headers.put("Cookie", "session=abc123");
Dynatrace.setBeaconHeaders(headers);

Dynatrace.startup(this, new DynatraceConfigurationBuilder("<id>", "<url>")
    .buildConfiguration());
```

To remove all custom headers: `Dynatrace.setBeaconHeaders(null)`

### Token Refresh with CommunicationProblemListener

Use when the auth token expires and needs to be refreshed at runtime:

```kotlin
// Kotlin
Dynatrace.startup(this, DynatraceConfigurationBuilder("<id>", "<url>")
    .withCommunicationProblemListener(object : CommunicationProblemListener {
        override fun onFailure(responseCode: Int, responseMessage: String, body: String) {
            // Called when the server returns an error (e.g. 403 Forbidden)
            val newToken = refreshToken()
            Dynatrace.setBeaconHeaders(mapOf("Authorization" to "Bearer $newToken"))
        }
        override fun onError(throwable: Throwable) {
            // Network-level errors (timeout, SSL) — OneAgent retries automatically
        }
    })
    .buildConfiguration())
```

```java
// Java
Dynatrace.startup(this, new DynatraceConfigurationBuilder("<id>", "<url>")
    .withCommunicationProblemListener(new CommunicationProblemListener() {
        @Override
        public void onFailure(int responseCode, String responseMessage, String body) {
            String newToken = refreshToken();
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + newToken);
            Dynatrace.setBeaconHeaders(headers);
        }
        @Override
        public void onError(Throwable throwable) {
            // Network-level errors — OneAgent retries automatically
        }
    })
    .buildConfiguration());
```

When `CommunicationProblemListener` is set, OneAgent waits for `setBeaconHeaders` instead of retrying automatically on 4xx responses.

### Network Security (HTTPS / Certificates)

For clusters with self-signed or private CA certificates, add a `domain-config` to `network_security_config.xml`:

```xml
<domain-config>
  <domain includeSubdomains="true">your.cluster.domain.com</domain>
  <trust-anchors>
    <certificates src="@raw/your_server_certificate" />
  </trust-anchors>
</domain-config>
```

Ensure `GET` and `POST` to the `beaconUrl` endpoint are not blocked by a firewall.

---

## Session Management

### Force-End a Session

```kotlin
// Kotlin — ends current session, closes all open actions, starts a new session
Dynatrace.endVisit()
```

```java
// Java
Dynatrace.endVisit();
```

> After `endVisit`, sessions are **not** re-tagged automatically. Call `identifyUser()` again in the new session if needed.

OneAgent also ends sessions automatically on: idle timeout, duration timeout, privacy changes, or app force-stop. Sessions split by timeout re-tag automatically (v8.237+).

---

### Data Collection Levels

OneAgent supports three `DataCollectionLevel` values controlling what data is collected:

| Level | Description |
| --- | --- |
| `OFF` | No data is collected |
| `PERFORMANCE` | Collects performance data (crashes, ANRs) |
| `USER_BEHAVIOR` | Collects performance data + user behavior (user actions, sessions) |

When `userOptIn(true)` is configured and the user has not yet consented, defaults are `OFF` with crash reporting disabled.

### Set Data Collection Level at Runtime

```kotlin
// Kotlin
import com.dynatrace.android.agent.Dynatrace
import com.dynatrace.android.agent.conf.UserPrivacyOptions
import com.dynatrace.android.agent.conf.DataCollectionLevel

val privacyOptions = Dynatrace.getUserPrivacyOptions()
val updatedOptions = privacyOptions.newBuilder()
    .withDataCollectionLevel(DataCollectionLevel.PERFORMANCE)
    .build()
Dynatrace.applyUserPrivacyOptions(updatedOptions)
```

```java
// Java
UserPrivacyOptions privacyOptions = Dynatrace.getUserPrivacyOptions();
UserPrivacyOptions updatedOptions = privacyOptions.newBuilder()
    .withDataCollectionLevel(DataCollectionLevel.PERFORMANCE)
    .build();
Dynatrace.applyUserPrivacyOptions(updatedOptions);
```

### Enable or Disable Crash Reporting at Runtime

```kotlin
// Kotlin
val updatedOptions = Dynatrace.getUserPrivacyOptions().newBuilder()
    .withCrashReportingOptedIn(true) // or false to disable
    .build()
Dynatrace.applyUserPrivacyOptions(updatedOptions)
```

```java
// Java
UserPrivacyOptions updatedOptions = Dynatrace.getUserPrivacyOptions().newBuilder()
    .withCrashReportingOptedIn(true)
    .build();
Dynatrace.applyUserPrivacyOptions(updatedOptions);
```

### Apply Combined Privacy Options

```kotlin
// Kotlin
val updatedOptions = Dynatrace.getUserPrivacyOptions().newBuilder()
    .withDataCollectionLevel(DataCollectionLevel.USER_BEHAVIOR)
    .withCrashReportingOptedIn(false)
    .build()
Dynatrace.applyUserPrivacyOptions(updatedOptions)
```

```java
// Java
UserPrivacyOptions updatedOptions = Dynatrace.getUserPrivacyOptions().newBuilder()
    .withDataCollectionLevel(DataCollectionLevel.USER_BEHAVIOR)
    .withCrashReportingOptedIn(false)
    .build();
Dynatrace.applyUserPrivacyOptions(updatedOptions);
```

### User Opt-In Mode (GDPR)

Enable in the Gradle DSL:

```kotlin
// Kotlin DSL
dynatrace {
    configurations {
        create("defaultConfig") {
            autoStart {
                userOptIn(true)
            }
        }
    }
}
```

```groovy
// Groovy
dynatrace {
    configurations {
        defaultConfig {
            userOptIn true
        }
    }
}
```

Enable via manual startup:

```kotlin
// Kotlin
DynatraceConfigurationBuilder("<YourApplicationID>", "<ProvidedBeaconURL>")
    .withUserOptIn(true)
    .buildConfiguration()
```

```java
// Java
new DynatraceConfigurationBuilder("<YourApplicationID>", "<ProvidedBeaconURL>")
    .withUserOptIn(true)
    .buildConfiguration();
```

**When opt-in is enabled:**
- Default data collection level: `OFF`
- Default crash reporting: disabled
- You must implement a consent dialog yourself (Dynatrace does not provide one)
- After the user consents, call `applyUserPrivacyOptions` with the desired `DataCollectionLevel`

---

## App Performance Monitoring

OneAgent for Android automatically captures and stores the following as events in Grail:

- **App Start events** — cold, warm, and hot starts; measures duration from process/Activity creation to first `onResume`
- **Views** — one active view per screen (Activities tracked automatically; others require manual tracking)
- **Navigation events** — transitions between views; app-start navigation has no source view; backgrounding has no current view
- **View summaries** — aggregated events on view end: start time, duration, error counts

### Ensure Accurate App Start Measurement

For auto-instrumented apps, measurement begins in `Application.onCreate`. When using manual startup, call `Dynatrace.startup()` as early as possible — measurement begins only when the agent starts.

### Manual View Tracking

For fragments, Jetpack Compose screens, or other non-Activity UI components:

```kotlin
// Kotlin — starts "Login" view, automatically ends the previous view
Dynatrace.startView("Login")
```

```java
// Java
Dynatrace.startView("Login");
```

Only one view can be active at a time. `startView` automatically closes the previous view and generates a navigation event.

---

## Web Request Monitoring

### Automatic Instrumentation

OneAgent automatically captures web requests made via:
- `HttpURLConnection`
- `OkHttp` versions 3, 4, and 5 (includes Retrofit 2, which is based on OkHttp)

Captured data: URL, HTTP method, response status code, request duration, exception (if failed).

### Disable Automatic Web Request Monitoring

```kotlin
// Kotlin DSL
dynatrace {
    configurations {
        create("sampleConfig") {
            webRequests { enabled(false) }
        }
    }
}
```

```groovy
// Groovy
dynatrace {
    configurations {
        sampleConfig {
            webRequests.enabled false
        }
    }
}
```

### W3C Trace Context (Distributed Tracing)

When automatic instrumentation is enabled, OneAgent automatically propagates W3C Trace Context headers (`traceparent`, `tracestate`) on outgoing requests. No extra configuration is needed.

**Automatic behavior:**
- No existing headers → OneAgent generates a new `traceparent` and a corresponding `tracestate`
- Existing valid `traceparent` → OneAgent keeps it and adds Dynatrace vendor data to `tracestate` without overwriting existing vendor entries

### Manually Propagate Trace Context

For custom networking stacks, use `Dynatrace.generateTraceContext()`:

```kotlin
// Kotlin
val existingTraceparent = request.header("traceparent")
val existingTracestate = request.header("tracestate")

// Returns null if traceparent is invalid or capture is not allowed — do NOT modify headers in that case
val traceContext = Dynatrace.generateTraceContext(existingTraceparent, existingTracestate)
val traceparentForReporting: String? = traceContext?.traceparent

if (traceContext != null) {
    request = request.newBuilder()
        .header("traceparent", traceContext.traceparent)
        .header("tracestate", traceContext.tracestate)
        .build()
}

val requestData = HttpRequestEventData(request.url.toString(), request.method)
    .withDuration(duration)
    .withStatusCode(statusCode)

if (traceparentForReporting != null) {
    requestData.withTraceparentHeader(traceparentForReporting)
}

Dynatrace.sendHttpRequestEvent(requestData)
```

```java
// Java
String existingTraceparent = request.header("traceparent");
String existingTracestate  = request.header("tracestate");

TraceContext traceContext = Dynatrace.generateTraceContext(existingTraceparent, existingTracestate);
String traceparentForReporting = traceContext != null ? traceContext.getTraceparent() : null;

if (traceContext != null) {
    request = request.newBuilder()
        .header("traceparent", traceContext.getTraceparent())
        .header("tracestate",  traceContext.getTracestate())
        .build();
}

HttpRequestEventData requestData = new HttpRequestEventData(request.url().toString(), request.method())
    .withDuration(duration)
    .withStatusCode(statusCode);

if (traceparentForReporting != null) {
    requestData.withTraceparentHeader(traceparentForReporting);
}

Dynatrace.sendHttpRequestEvent(requestData);
```

### Manual Web Request Reporting

For networking libraries not supported by automatic instrumentation:

```kotlin
// Kotlin
val requestData = HttpRequestEventData("https://api.example.com/data", "GET")
    .withDuration(250)
    .withStatusCode(200)
    .withBytesSent(128)
    .withBytesReceived(4096)
Dynatrace.sendHttpRequestEvent(requestData)

val failedRequest = HttpRequestEventData("https://api.example.com/data", "POST")
    .withDuration(1500)
    .withThrowable(exception)
Dynatrace.sendHttpRequestEvent(failedRequest)
```

```java
// Java
HttpRequestEventData requestData = new HttpRequestEventData("https://api.example.com/data", "GET")
    .withDuration(250)
    .withStatusCode(200)
    .withBytesSent(128)
    .withBytesReceived(4096);
Dynatrace.sendHttpRequestEvent(requestData);

HttpRequestEventData failedRequest = new HttpRequestEventData("https://api.example.com/data", "POST")
    .withDuration(1500)
    .withThrowable(exception);
Dynatrace.sendHttpRequestEvent(failedRequest);
```

### Add Custom Properties to Web Requests

```kotlin
// Kotlin
val requestData = HttpRequestEventData(url, "POST")
    .withDuration(300)
    .withStatusCode(201)
    .addEventProperty("event_properties.api_version", "v2")
    .addEventProperty("event_properties.endpoint", "users")
Dynatrace.sendHttpRequestEvent(requestData)
```

```java
// Java
HttpRequestEventData requestData = new HttpRequestEventData(url, "POST")
    .withDuration(300)
    .withStatusCode(201)
    .addEventProperty("event_properties.api_version", "v2")
    .addEventProperty("event_properties.endpoint", "users");
Dynatrace.sendHttpRequestEvent(requestData);
```

> Custom property keys **must** be prefixed with `event_properties.` — properties without this prefix are dropped.

### OkHttp Event Modifier

Enrich, redact, or filter OkHttp web request events before they are sent:

```kotlin
// Kotlin
val modifier: OkHttpEventModifier = object : OkHttpEventModifier {
    override fun modifyEvent(request: Request, response: Response): JSONObject {
        val event = JSONObject()
        val serverTiming = response.header("Server-Timing")
        if (serverTiming != null) event.put("event_properties.server_timing", serverTiming)
        // Always use peekBody() — never body() — to avoid consuming the response stream
        event.put("event_properties.body_preview", response.peekBody(1000).toString())
        return event
    }
    override fun modifyEvent(request: Request, throwable: Throwable): JSONObject {
        val event = JSONObject()
        val customHeader = request.header("X-Custom-Header")
        if (customHeader != null) event.put("event_properties.custom_header", customHeader)
        return event
    }
}
Dynatrace.addHttpEventModifier(modifier)
```

```java
// Java
OkHttpEventModifier modifier = new OkHttpEventModifier() {
    @Override
    public JSONObject modifyEvent(Request request, Response response) throws JSONException {
        JSONObject event = new JSONObject();
        String serverTiming = response.header("Server-Timing");
        if (serverTiming != null) event.put("event_properties.server_timing", serverTiming);
        event.put("event_properties.body_preview", response.peekBody(1000).string());
        return event;
    }
    @Override
    public JSONObject modifyEvent(Request request, Throwable throwable) throws JSONException {
        JSONObject event = new JSONObject();
        String customHeader = request.header("X-Custom-Header");
        if (customHeader != null) event.put("event_properties.custom_header", customHeader);
        return event;
    }
};
Dynatrace.addHttpEventModifier(modifier);
```

**Filter a request** (return `null` to drop the event):

```kotlin
// Kotlin
val filterModifier: OkHttpEventModifier = object : OkHttpEventModifier {
    override fun modifyEvent(request: Request, response: Response?): JSONObject? {
        return if (request.url.toString().contains("analytics.example.com")) null
        else JSONObject()
    }
    override fun modifyEvent(request: Request?, throwable: Throwable?): JSONObject? {
        return if (throwable is IOException) JSONObject() else null
    }
}
Dynatrace.addHttpEventModifier(filterModifier)
```

```java
// Java
OkHttpEventModifier filterModifier = new OkHttpEventModifier() {
    @Override
    public JSONObject modifyEvent(Request request, Response response) {
        return request.url().toString().contains("analytics.example.com") ? null : new JSONObject();
    }
    @Override
    public JSONObject modifyEvent(Request request, Throwable throwable) {
        return throwable instanceof IOException ? new JSONObject() : null;
    }
};
Dynatrace.addHttpEventModifier(filterModifier);
```

**Remove a modifier:**

```kotlin
Dynatrace.removeEventModifier(modifier)
```

---

## Error and Crash Reporting

### Automatic Crash Reporting

OneAgent captures all uncaught Java/Kotlin exceptions with the full stack trace. Reports are sent immediately after the crash, or on next relaunch within 10 minutes. Reports older than 10 minutes are not sent.

Disable in DSL:

```kotlin
// Kotlin DSL
dynatrace {
    configurations {
        create("sampleConfig") {
            crashReporting(false)
        }
    }
}
```

```groovy
// Groovy
dynatrace {
    configurations {
        sampleConfig {
            crashReporting false
        }
    }
}
```

### ANR Reporting (Android 11+)

OneAgent automatically captures Application Not Responding (ANR) events on Android 11+. The app must be restarted within 10 minutes for the event to be sent.

Disable via Gradle DSL:

```kotlin
// Kotlin DSL
dynatrace {
    configurations {
        create("sampleConfig") {
            anrReporting(false)
        }
    }
}
```

```groovy
// Groovy
dynatrace {
    configurations {
        sampleConfig {
            anrReporting false
        }
    }
}
```

Disable via manual startup:

```kotlin
// Kotlin
Dynatrace.startup(this, DynatraceConfigurationBuilder("<YourApplicationID>", "<ProvidedBeaconUrl>")
    .withAnrReporting(false)
    .buildConfiguration()
)
```

```java
// Java
Dynatrace.startup(this, new DynatraceConfigurationBuilder("<YourApplicationID>", "<ProvidedBeaconUrl>")
    .withAnrReporting(false)
    .buildConfiguration());
```

**ANR limitations:**
- Android 11+ only
- Multiple ANRs in one session generate separate events
- Some ANRs may lack stack traces due to OS limitations (overwritten shared storage, severe crashes, custom firmware)

### Native Crash Reporting (Android 11+)

OneAgent captures C/C++ NDK crashes on Android 11+. The app must be restarted within 10 minutes for the event to be sent.

Disable via Gradle DSL:

```kotlin
// Kotlin DSL
dynatrace {
    configurations {
        create("sampleConfig") {
            nativeCrashReporting(false)
        }
    }
}
```

```groovy
// Groovy
dynatrace {
    configurations {
        sampleConfig {
            nativeCrashReporting false
        }
    }
}
```

Disable via manual startup:

```kotlin
// Kotlin
Dynatrace.startup(this, DynatraceConfigurationBuilder("<YourApplicationID>", "<ProvidedBeaconUrl>")
    .withNativeCrashReporting(false)
    .buildConfiguration()
)
```

```java
// Java
Dynatrace.startup(this, new DynatraceConfigurationBuilder("<YourApplicationID>", "<ProvidedBeaconUrl>")
    .withNativeCrashReporting(false)
    .buildConfiguration());
```

**Native crash limitations:**
- Android 11+ only
- Empty stack traces may occur due to OS limitations
- Multiple native crashes in one session generate separate events

### Manual Error Reporting

Report handled exceptions with optional custom properties:

```kotlin
// Kotlin
try {
    // ...
} catch (exception: Exception) {
    Dynatrace.sendExceptionEvent(
        ExceptionEventData(exception)
            .addEventProperty("event_properties.<YourPropertyName>", "<YourPropertyValue>")
    )
}
```

```java
// Java
try {
    // ...
} catch (Exception exception) {
    Dynatrace.sendExceptionEvent(
        new ExceptionEventData(exception)
            .addEventProperty("event_properties.<YourPropertyName>", "<YourPropertyValue>")
    );
}
```

---

## Custom Events

### Configure Event and Session Properties

Before sending event or session properties, define them in the Dynatrace UI:

1. Go to **Experience Vitals** → select your frontend → **Settings** → **Event and session properties**
2. Select **Add** under **Defined event properties** or **Defined session properties**
3. Enter a field name (e.g. `cart.total_value`) — Dynatrace prefixes it automatically with `event_properties.` or `session_properties.`

> Properties **not configured** in the UI are dropped during ingest.

### Send Custom Events

```kotlin
// Kotlin
Dynatrace.sendEvent(EventData())
Dynatrace.sendEvent(EventData().withDuration(150))
Dynatrace.sendEvent(
    EventData()
        .withDuration(250)
        .addEventProperty("event_properties.checkout_step", "payment_confirmed")
        .addEventProperty("event_properties.cart_value", 149.99)
        .addEventProperty("event_properties.item_count", 3)
)
```

```java
// Java
Dynatrace.sendEvent(new EventData());
Dynatrace.sendEvent(new EventData().withDuration(150));
Dynatrace.sendEvent(
    new EventData()
        .withDuration(250)
        .addEventProperty("event_properties.checkout_step", "payment_confirmed")
        .addEventProperty("event_properties.cart_value", 149.99)
        .addEventProperty("event_properties.item_count", 3)
);
```

### Event Modifiers

Intercept all events before they are sent to add context, redact PII, or filter events.

**Add a modifier:**

```kotlin
// Kotlin
val modifier = EventModifier { event ->
    event.put("event_properties.build_type", BuildConfig.BUILD_TYPE)
    event.put("event_properties.flavor", BuildConfig.FLAVOR)
    event
}
Dynatrace.addEventModifier(modifier)
```

```java
// Java
EventModifier modifier = event -> {
    event.put("event_properties.build_type", BuildConfig.BUILD_TYPE);
    event.put("event_properties.flavor", BuildConfig.FLAVOR);
    return event;
};
Dynatrace.addEventModifier(modifier);
```

**Filter events** (return `null` to discard):

```kotlin
// Kotlin
val modifier = EventModifier { event ->
    if (event.optString("view.detected_name") == "com.example.MainActivity") {
        return@EventModifier null
    }
    event
}
Dynatrace.addEventModifier(modifier)
```

```java
// Java
EventModifier modifier = event -> {
    if ("com.example.MainActivity".equals(event.optString("view.detected_name"))) return null;
    return event;
};
Dynatrace.addEventModifier(modifier);
```

**Redact sensitive data:**

```kotlin
// Kotlin
val modifier = EventModifier { event ->
    val url = event.optString("url.full", null)
    if (url != null) {
        event.put("url.full", url.replace(Regex("/users/\\w+/"), "/users/{id}/"))
    }
    event
}
Dynatrace.addEventModifier(modifier)
```

```java
// Java
EventModifier modifier = event -> {
    String url = event.optString("url.full", null);
    if (url != null) {
        event.put("url.full", url.replaceAll("/users/\\w+/", "/users/{id}/"));
    }
    return event;
};
Dynatrace.addEventModifier(modifier);
```

**Conditional enrichment (HTTP and error events only):**

```kotlin
// Kotlin
fun setupConditionalEnrichment(apiClientName: String) {
    val modifier = EventModifier { event ->
        if (event.optBoolean("characteristics.has_request")) {
            event.put("event_properties.api_client", apiClientName)
            event.put("event_properties.api_kind", "backend")
        }
        if (event.optBoolean("characteristics.has_error")) {
            event.put("event_properties.triage_owner", "mobile")
            event.put("event_properties.triage_severity", "error")
        }
        event
    }
    Dynatrace.addEventModifier(modifier)
}
```

```java
// Java
void setupConditionalEnrichment(String apiClientName) {
    EventModifier modifier = event -> {
        if (event.optBoolean("characteristics.has_request")) {
            event.put("event_properties.api_client", apiClientName);
            event.put("event_properties.api_kind", "backend");
        }
        if (event.optBoolean("characteristics.has_error")) {
            event.put("event_properties.triage_owner", "mobile");
            event.put("event_properties.triage_severity", "error");
        }
        return event;
    };
    Dynatrace.addEventModifier(modifier);
}
```

**Remove a modifier:**

```kotlin
Dynatrace.removeEventModifier(modifier)
```

**Modifiable fields** (all other fields are read-only):
- `event_properties.*`
- `session_properties.*` (session property events only)
- `url.full`
- `exception.stack_trace`

---

## User and Session Management

### Identify Users

Tag the current session with a user identifier to track individuals across sessions and devices:

```kotlin
// Kotlin
Dynatrace.identifyUser("user@example.com")
Dynatrace.identifyUser("user-12345")
```

```java
// Java
Dynatrace.identifyUser("user@example.com");
Dynatrace.identifyUser("user-12345");
```

**Important:**
- The user tag is **not persisted** — call `identifyUser()` for every new session
- When a session splits (idle/duration timeout), the next session is automatically re-tagged with the same identifier
- After logout or privacy changes, sessions are **not** re-tagged automatically

**Remove user identification (on logout):**

```kotlin
// Kotlin
Dynatrace.identifyUser(null)
```

```java
// Java
Dynatrace.identifyUser(null);
```

### Session Properties

Attach key-value pairs that apply to all events in the current session. Properties must be configured in the Dynatrace UI before use — unconfigured properties are dropped.

```kotlin
// Kotlin
Dynatrace.sendSessionPropertyEvent(
    SessionPropertyEventData()
        .addSessionProperty("session_properties.product_tier", "premium")
        .addSessionProperty("session_properties.loyalty_status", "gold")
        .addSessionProperty("session_properties.onboarding_complete", true)
)
```

```java
// Java
Dynatrace.sendSessionPropertyEvent(
    new SessionPropertyEventData()
        .addSessionProperty("session_properties.product_tier", "premium")
        .addSessionProperty("session_properties.loyalty_status", "gold")
        .addSessionProperty("session_properties.onboarding_complete", true)
);
```

**Update session properties during a session:**

```kotlin
// Kotlin — initial value
Dynatrace.sendSessionPropertyEvent(
    SessionPropertyEventData().addSessionProperty("session_properties.cart_value", 0)
)
// Update after user adds items
Dynatrace.sendSessionPropertyEvent(
    SessionPropertyEventData().addSessionProperty("session_properties.cart_value", 149.99)
)
```

```java
// Java — initial value
Dynatrace.sendSessionPropertyEvent(
    new SessionPropertyEventData().addSessionProperty("session_properties.cart_value", 0)
);
// Update after user adds items
Dynatrace.sendSessionPropertyEvent(
    new SessionPropertyEventData().addSessionProperty("session_properties.cart_value", 149.99)
);
```

> If the same property is sent multiple times, session aggregation keeps only one value (first or last, depending on server configuration).

---

## Enable the New RUM Experience

To enable the New RUM Experience for a mobile frontend in the Dynatrace UI:

1. Go to **Experience Vitals** → select your mobile frontend → **Settings**
2. Under **Enablement and cost control**, turn on **New Real User Monitoring Experience**

Or enable at the environment level: **Settings** → **Collect and capture** → **Real User Monitoring** → **Mobile frontends** → **Traffic and cost control** → **Enable New Real User Monitoring Experience**.

To activate via agent configuration at first app start, use `agentBehavior { startupWithGrailEnabled(true) }` in the DSL.

---

## Removing Dynatrace Configuration

To cleanly remove Dynatrace:
1. Delete the `dynatrace { }` block from all Gradle files
2. Remove the plugin from `plugins { }` or `buildscript { dependencies { classpath … } }`
3. Remove `apply plugin: 'com.dynatrace.instrumentation'` / `apply(plugin = "…")` lines
4. Sync Gradle

---

## Limitations

### Runtime limitations

- **HTTP** — Only `HttpURLConnection` and `OkHttp` (v3/4/5) are automatically instrumented; all other HTTP frameworks require manual instrumentation
- **WebSocket and non-HTTP protocols** — require manual instrumentation; connections must close within ~9 minutes
- **Custom actions** — maximum duration 9 minutes; actions open longer are discarded. Maximum name length 250 characters
- **Business events** — only captured in active monitored sessions; not sent when OneAgent is disabled
- **ANR and native crash reporting** — available only on Android 11+; app must be restarted within 10 minutes
- **Events per minute** — default limit is 1,000 events per minute; exceeding this may result in dropped events
- **Direct Boot** — do not call `Dynatrace.startup` from a Direct Boot aware component
- **Offline data** — Dynatrace discards monitoring data older than 10 minutes when the app is offline
- **Truncated values** — action names, reported values, and web request URLs are truncated at 250 characters

### Instrumentation-specific limitations

The Dynatrace Android Gradle plugin instruments `AndroidManifest.xml` and `.class` files only. The following are **not** instrumented by bytecode transformation:

- **Native code (NDK)** — code written with the Android NDK is not instrumented
- **Web components** — `.html` and `.js` files are not instrumented
- **Resource files** — layout `.xml` files and other Android resources are not instrumented
- **WebView content** — JavaScript inside a WebView is not captured; use hybrid monitoring to correlate WebView sessions

### Compatibility with other monitoring tools

Using multiple performance monitoring plugins simultaneously may cause compatibility issues, especially when other plugins also instrument the Android runtime. Either use only one monitoring plugin at a time, or verify compatibility through manual testing before releasing to production.

### Build-specific limitations

- **Android library projects** — The Dynatrace Gradle plugin auto-instruments only Android *application* projects (`com.android.application`). Stand-alone Android library projects are not directly supported; their code is instrumented when the library is added as a dependency to an app module.
- **Android Gradle plugin `excludes` property** — The AGP's own `excludes` property disables instrumentation for **all** specified classes, including Dynatrace-critical classes, which may silently break instrumentation. Use Dynatrace's own `exclude { }` block instead — it protects essential Dynatrace classes regardless of exclusion rules.
- **`com.dynatrace.instrumentation` must be at root** — The coordinator plugin must be applied in the top-level build file; applying it in an app module build file causes a build error.
- **`com.dynatrace.instrumentation` and `com.dynatrace.instrumentation.module` are mutually exclusive** — Using both in the same module causes a build error; use only the module plugin when you need per-module configuration.

---

## Common Issues

| Symptom | Likely cause | Fix |
| --- | --- | --- |
| Build fails: "Could not resolve `com.dynatrace.tools.android:gradle-plugin`" | `mavenCentral()` missing from plugin repositories | Add `mavenCentral()` to `pluginManagement.repositories` in `settings.gradle(.kts)` |
| No data in Dynatrace after build | `beaconUrl` is HTTP instead of HTTPS | Use `https://` URL |
| `dynatrace { }` red in IDE | Plugin applied in app module instead of root (Plugin DSL) | Move plugin declaration and config to root `build.gradle(.kts)` |
| Build fails with "No matching variant found" and `strictMode(true)` | `variantFilter` regex doesn't match any variant | Use `".*"` or match the exact variant name |
| Module plugin causes "duplicate plugin" error | Both `com.dynatrace.instrumentation` and `com.dynatrace.instrumentation.module` in same module | Remove the coordinator plugin from the app module — use only the module plugin |
| Session Replay not capturing | Missing privacy consent / feature flag disabled | Ensure `sessionReplay.enabled(true)` and privacy consent is granted at runtime |
| ANR events not appearing | Android version < 11, or app not restarted within 10 min | ANR reporting requires Android 11+; relaunch app within 10 minutes |
| Native crash events not appearing | Android version < 11, or app not restarted within 10 min | Native crash reporting requires Android 11+; relaunch app within 10 minutes |
| No data after opt-in is enabled | `applyUserPrivacyOptions` not called after user consent | Call `Dynatrace.applyUserPrivacyOptions()` with `USER_BEHAVIOR` level after consent |
| Event/session properties dropped | Property not configured in Dynatrace UI, or missing prefix | Define properties in Dynatrace UI first; use `event_properties.` / `session_properties.` prefix |
| `OkHttpEventModifier` causes exception on response body read | Using `body()` instead of `peekBody()` consumes the response stream | Always use `response.peekBody(n)` inside the modifier |
| App start duration shorter than expected | Agent started too late in manual startup | Call `Dynatrace.startup()` as early as possible in `Application.onCreate()` |
| Custom action never appears in Dynatrace | `leaveAction()` not called, or action open > 9 minutes | Always call `leaveAction()` or `cancel()`; actions open longer than 9 minutes are discarded |
| Manual startup config ignored | Auto-instrumentation injects its own `startup` call first | Add `autoStart { enabled(false) }` in the DSL to prevent the injected call |
| WebSocket timing not reported | Connection open longer than ~9 minutes | Only WebSocket connections ≤ 9 minutes are reliably reported |
| WebView hybrid session not merging with native session | `instrumentWebView` called after `loadUrl` | Always call `Dynatrace.instrumentWebView(webView)` **before** `webView.loadUrl(...)` |
| Hybrid cookies lost after `removeAllCookies` | Dynatrace cookies deleted along with app cookies | Call `Dynatrace.restoreCookies()` immediately after clearing cookies |
| OneAgent stops sending data after 403 | Server rejects stale auth token, no `CommunicationProblemListener` | Add a `CommunicationProblemListener` and call `setBeaconHeaders` with refreshed token |
| Business events not appearing | OneAgent disabled (cost control, privacy off) | Business events require an active monitored session |
| `enterAction` returns an action but no data appears | `reportValue`/`reportError` called on a finished action | Check `action.isFinished()` before reporting; don't interact with finished actions |

---

## Troubleshooting

### General checklist

Before investigating a specific symptom, verify the following:

1. **Technology is supported** — check the Supported Versions table above. ANR and native-crash reporting require Android 11+.
2. **Plugin version is current** — ensure you are on a supported `8.x` release (check Maven Central for the latest patch).
3. **Debug logging is enabled** — add `debug { agentLogging(true) }` to the `dynatrace {}` block, reproduce the issue, then review Logcat filtered by tag `dtx|caa`. Remove the flag before any production build.
4. **Credentials are correct** — verify `applicationId` and `beaconUrl` match the values in Dynatrace → Mobile → (your app) → Settings → Instrumentation. The Beacon URL must use `https://`.
5. **Network Security Configuration includes system CA certs** — ensure `networkSecurityConfig` does not pin certificates in a way that blocks the Dynatrace endpoint.
6. **Dynatrace endpoint is reachable** — test connectivity from inside your corporate network/VPN.
7. **No other monitoring plugin is interfering** — see Compatibility with other monitoring tools above.

---

### Why is OneAgent not sending monitoring data?

**Check:**
- `applicationId` and `beaconUrl` are correct (copy from Dynatrace → Mobile → Settings → Instrumentation).
- The Beacon URL uses `https://`.
- Network Security Configuration allows system CA certificates and does not block the beacon endpoint.
- The Dynatrace server is reachable from the test device's network.
- `userOptIn` is `false`, or — if it is `true` — `Dynatrace.applyUserPrivacyOptions()` has been called with `DataCollectionLevel.USER_BEHAVIOR` after the user consented.
- `pluginEnabled` is not set to `false` (global kill-switch).
- `autoStart { enabled(false) }` is not set unless you are calling `Dynatrace.startup()` manually.

**For hybrid apps:**
- `Dynatrace.instrumentWebView(webView)` is called **before** `webView.loadUrl(...)`.
- `withHybridMonitoring(true)` / `hybridMonitoring(true)` is set in the config.
- `withMonitoredDomains(...)` includes the domains loaded in the WebView.

---

### Why are some web requests missing?

- **Unsupported HTTP library** — only `HttpURLConnection` and `OkHttp` (v3/4/5) are auto-instrumented. For all other libraries, use manual `HttpRequestEventData` reporting or `WebRequestTiming`.
- **Firebase plugin conflict** — the Firebase Gradle plugin can interfere with OkHttp instrumentation. Verify by removing Firebase temporarily and testing again.
- **Request made outside an active session** — requests are only captured when OneAgent is running and a session is active.

---

### Why are web requests not associated with a user action?

OneAgent associates web requests with a user action only when the request is initiated within a specific time window: from when the action opens until **500 ms after** the action closes. Requests outside this window are captured as standalone events.

To extend the window, configure `userActions.timeout` in the `dynatrace {}` block or via `DynatraceConfigurationBuilder`.

---

### Why does my UI component not generate a user action?

Possible causes:
- **WebView component** — UI components inside a WebView are not auto-captured; use hybrid monitoring.
- **Unsupported listener type** — OneAgent instruments standard Android listeners. Custom or library-specific event handling may not be captured.
- **Unsupported Jetpack Compose component** — not all Compose composables are instrumented; check the supported component list in Dynatrace documentation.

---

### Why does Dynatrace not monitor Jetpack Compose in Android Studio's interactive preview?

The **interactive preview mode** runs directly inside Android Studio in a sandbox with no network access. In this mode, the bytecode instrumentation step is skipped and OneAgent is never started. This is expected behaviour — interactive preview is not a supported monitoring environment. Run on a real device or emulator to verify instrumentation.

---

### Build error reference

#### `OneAgent SDK version does not match Dynatrace Android Gradle plugin version`

The SDK JAR version and the Gradle plugin version must match exactly. This usually happens when you added the `agentDependency()` or `com.dynatrace.agent:agent-android` dependency with a different version.

**Fix:** Remove the explicit SDK dependency version and let the plugin inject it automatically via `agentDependency()`, or align both versions.

---

#### `Plugin with id 'com.dynatrace.instrumentation' not found`

The plugin classpath entry is missing.

**Fix:** Add the plugin to the root build file (Plugin DSL or `buildscript { classpath … }`) and ensure `mavenCentral()` is in the plugin repository list. See Step 1 and Step 2 above.

---

#### `Could not find com.dynatrace.tools.android:gradle-plugin:<version>`

Gradle cannot resolve the specified version.

**Fix:**
1. Verify the version exists on [Maven Central](https://central.sonatype.com/artifact/com.dynatrace.tools.android/gradle-plugin).
2. Ensure `mavenCentral()` is declared in `pluginManagement.repositories` (or `buildscript.repositories`).

---

#### `Could not find any version that matches com.dynatrace.tools.android:gradle-plugin:<version>.+`

Same root cause as above — Gradle cannot find a matching version in any configured repository.

**Fix:** Same as above. Also check whether a corporate proxy or Gradle cache is blocking Maven Central.

---

#### `Could not get unknown property 'dynatrace' for object of type DefaultDependencyHandler`

The `dynatrace {}` block appears **before** `apply plugin: 'com.dynatrace.instrumentation'` / `apply(plugin = "com.dynatrace.instrumentation")`.

**Fix:** The plugin must be applied **before** the `dynatrace {}` configuration block:

```groovy
// Groovy
apply plugin: 'com.dynatrace.instrumentation'

dynatrace { … }
```

```kotlin
// Kotlin DSL
apply(plugin = "com.dynatrace.instrumentation")

configure<com.dynatrace.tools.android.dsl.DynatraceExtension> { … }
```

---

#### `No configuration for the Dynatrace Android Gradle plugin found!`

The plugin was applied but no `dynatrace {}` block (or `configure<DynatraceExtension> {}` for Kotlin DSL) was provided in the same or root build file.

**Fix:** Add the `dynatrace {}` configuration block with at least one `configurations { create("…") { autoStart { … } } }` entry.

---

#### `Task 'printVariantAffiliation' not found in project :<module>`

The plugin generates tasks only for Android application submodules, not for the root project or Android library modules.

**Fix:** Ensure the plugin is applied to the correct build file. Run `printVariantAffiliation` on an app module, not on a library module.

---

#### `The Dynatrace Android Gradle Plugin can only be applied to Android projects`

The plugin was applied to a module that is not built with the Android Gradle plugin (e.g. a pure Java/Kotlin module, a Gradle root project, or a Compose multiplatform module).

**Fix:** Only apply `com.dynatrace.instrumentation` (or `com.dynatrace.instrumentation.module`) to modules that use `com.android.application` or `com.android.library`.

---

#### `The Dynatrace Android Gradle plugin must be applied in the top-level build.gradle (or build.gradle.kts) file`

The coordinator plugin (`com.dynatrace.instrumentation`) was applied inside an app module build file instead of the root build file.

**Fix:** Move the plugin declaration to the root `build.gradle(.kts)`. For per-module configuration, use `com.dynatrace.instrumentation.module` in the app module build file.

---

#### `The Dynatrace Android Gradle plugin can't be directly applied to a Java- or Android-related module`

Uncommon project architecture — the coordinator plugin is being applied inside a module that has Android or Java plugins.

**Fix:**
- If the project has a single build file, see the "Projects with one build file" pattern in Dynatrace documentation.
- Otherwise, use the module plugin (`com.dynatrace.instrumentation.module`) in that module and the coordinator at root.

---

#### `It is not possible to use both 'com.dynatrace.instrumentation' and 'com.dynatrace.instrumentation.module' for the same module`

The coordinator plugin and the module plugin were both applied to the same module.

**Fix:** The coordinator plugin automatically configures all submodules — you do not need `com.dynatrace.instrumentation.module` in any module when using the coordinator. Remove the coordinator from the root if you need per-module control, and use only `com.dynatrace.instrumentation.module` per app module.

---

## Plugin Version Policy

The wizard uses `8.+` as the version constraint, which allows automatic minor and patch updates within the `8.x` major line. Major version upgrades must be done manually — check Dynatrace release notes before bumping the major version.
