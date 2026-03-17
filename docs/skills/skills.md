---
name: dynatrace-android-sdk
description: >
  Complete reference for configuring the Dynatrace Mobile SDK in Android projects via the
  Dynatrace Gradle plugin. Covers Plugin DSL and Buildscript Classpath approaches, single-app,
  feature-module, and multi-app project layouts, Kotlin DSL and Groovy DSL, all supported
  monitoring features, privacy and data collection, app performance monitoring, web request
  monitoring, error and crash reporting, custom events, and user/session management.
  Use whenever a user asks to add Dynatrace to Android, configure mobile instrumentation,
  enable crash reporting, ANR reporting, Session Replay, or privacy-aware monitoring.
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

**Specific library modules only:**
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

## Privacy & Data Collection

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
import com.dynatrace.android.agent.Dynatrace
import com.dynatrace.android.agent.conf.UserPrivacyOptions
import com.dynatrace.android.agent.conf.DataCollectionLevel

val privacyOptions = Dynatrace.getUserPrivacyOptions()
val updatedOptions = privacyOptions.newBuilder()
    .withDataCollectionLevel(DataCollectionLevel.PERFORMANCE)
    .build()
Dynatrace.applyUserPrivacyOptions(updatedOptions)
```

### Enable or Disable Crash Reporting at Runtime

```kotlin
val privacyOptions = Dynatrace.getUserPrivacyOptions()
val updatedOptions = privacyOptions.newBuilder()
    .withCrashReportingOptedIn(true) // or false to disable
    .build()
Dynatrace.applyUserPrivacyOptions(updatedOptions)
```

### Apply Combined Privacy Options

```kotlin
val updatedOptions = Dynatrace.getUserPrivacyOptions().newBuilder()
    .withDataCollectionLevel(DataCollectionLevel.USER_BEHAVIOR)
    .withCrashReportingOptedIn(false)
    .build()
Dynatrace.applyUserPrivacyOptions(updatedOptions)
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
DynatraceConfigurationBuilder("<YourApplicationID>", "<ProvidedBeaconURL>")
    .withUserOptIn(true)
    .buildConfiguration()
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

// After executing the request:
val requestData = HttpRequestEventData(request.url.toString(), request.method)
    .withDuration(duration)
    .withStatusCode(statusCode)

if (traceparentForReporting != null) {
    requestData.withTraceparentHeader(traceparentForReporting)
}

Dynatrace.sendHttpRequestEvent(requestData)
```

### Manual Web Request Reporting

For networking libraries not supported by automatic instrumentation:

```kotlin
import com.dynatrace.android.agent.Dynatrace
import com.dynatrace.android.agent.HttpRequestEventData

// Successful request
val requestData = HttpRequestEventData("https://api.example.com/data", "GET")
    .withDuration(250)          // Duration in milliseconds
    .withStatusCode(200)
    .withBytesSent(128)
    .withBytesReceived(4096)

Dynatrace.sendHttpRequestEvent(requestData)

// Failed request — include the exception
val failedRequest = HttpRequestEventData("https://api.example.com/data", "POST")
    .withDuration(1500)
    .withThrowable(exception)

Dynatrace.sendHttpRequestEvent(failedRequest)
```

### Add Custom Properties to Web Requests

```kotlin
val requestData = HttpRequestEventData(url, "POST")
    .withDuration(300)
    .withStatusCode(201)
    .addEventProperty("event_properties.api_version", "v2")
    .addEventProperty("event_properties.endpoint", "users")

Dynatrace.sendHttpRequestEvent(requestData)
```

> Custom property keys **must** be prefixed with `event_properties.` — properties without this prefix are dropped.

### OkHttp Event Modifier

Enrich, redact, or filter OkHttp web request events before they are sent:

```kotlin
val modifier: OkHttpEventModifier = object : OkHttpEventModifier {
    override fun modifyEvent(request: Request, response: Response): JSONObject {
        val event = JSONObject()
        val serverTiming = response.header("Server-Timing")
        if (serverTiming != null) {
            event.put("event_properties.server_timing", serverTiming)
        }
        // Always use peekBody() — never body() — to avoid consuming the response stream
        val body = response.peekBody(1000)
        event.put("event_properties.body_preview", body.toString())
        return event
    }

    override fun modifyEvent(request: Request, throwable: Throwable): JSONObject {
        val event = JSONObject()
        val customHeader = request.header("X-Custom-Header")
        if (customHeader != null) {
            event.put("event_properties.custom_header", customHeader)
        }
        return event
    }
}

Dynatrace.addHttpEventModifier(modifier)
```

**Filter a request** (return `null` to drop the event):

```kotlin
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
Dynatrace.startup(this, DynatraceConfigurationBuilder("<YourApplicationID>", "<ProvidedBeaconUrl>")
    .withAnrReporting(false)
    .buildConfiguration()
)
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
Dynatrace.startup(this, DynatraceConfigurationBuilder("<YourApplicationID>", "<ProvidedBeaconUrl>")
    .withNativeCrashReporting(false)
    .buildConfiguration()
)
```

**Native crash limitations:**
- Android 11+ only
- Empty stack traces may occur due to OS limitations
- Multiple native crashes in one session generate separate events

### Manual Error Reporting

Report handled exceptions with optional custom properties:

```kotlin
try {
    // ...
} catch (exception: Exception) {
    Dynatrace.sendExceptionEvent(
        ExceptionEventData(exception)
            .addEventProperty("event_properties.<YourPropertyName>", "<YourPropertyValue>")
    )
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
// Simple event
Dynatrace.sendEvent(EventData())

// Event with duration (milliseconds)
Dynatrace.sendEvent(EventData().withDuration(150))

// Event with custom properties
Dynatrace.sendEvent(
    EventData()
        .withDuration(250)
        .addEventProperty("event_properties.checkout_step", "payment_confirmed")
        .addEventProperty("event_properties.cart_value", 149.99)
        .addEventProperty("event_properties.item_count", 3)
)
```

### Event Modifiers

Intercept all events before they are sent to add context, redact PII, or filter events.

**Add a modifier:**

```kotlin
val modifier = EventModifier { event ->
    event.put("event_properties.build_type", BuildConfig.BUILD_TYPE)
    event.put("event_properties.flavor", BuildConfig.FLAVOR)
    event
}
Dynatrace.addEventModifier(modifier)
```

**Filter events** (return `null` to discard):

```kotlin
val modifier = EventModifier { event ->
    if (event.optString("view.detected_name") == "com.example.MainActivity") {
        return@EventModifier null
    }
    event
}
Dynatrace.addEventModifier(modifier)
```

**Redact sensitive data:**

```kotlin
val modifier = EventModifier { event ->
    val url = event.optString("url.full", null)
    if (url != null) {
        val redactedUrl = url.replace(Regex("/users/\\w+/"), "/users/{id}/")
        event.put("url.full", redactedUrl)
    }
    event
}
Dynatrace.addEventModifier(modifier)
```

**Conditional enrichment (HTTP and error events only):**

```kotlin
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
// After user logs in
Dynatrace.identifyUser("user@example.com")

// Or use an internal user ID
Dynatrace.identifyUser("user-12345")
```

**Important:**
- The user tag is **not persisted** — call `identifyUser()` for every new session
- When a session splits (idle/duration timeout), the next session is automatically re-tagged with the same identifier
- After logout or privacy changes, sessions are **not** re-tagged automatically

**Remove user identification (on logout):**

```kotlin
fun logout() {
    Dynatrace.identifyUser(null)
}
```

### Session Properties

Attach key-value pairs that apply to all events in the current session. Properties must be configured in the Dynatrace UI before use — unconfigured properties are dropped.

```kotlin
Dynatrace.sendSessionPropertyEvent(
    SessionPropertyEventData()
        .addSessionProperty("session_properties.product_tier", "premium")
        .addSessionProperty("session_properties.loyalty_status", "gold")
        .addSessionProperty("session_properties.onboarding_complete", true)
)
```

**Update session properties during a session:**

```kotlin
// Initial value
Dynatrace.sendSessionPropertyEvent(
    SessionPropertyEventData()
        .addSessionProperty("session_properties.cart_value", 0)
)

// Update after user adds items
Dynatrace.sendSessionPropertyEvent(
    SessionPropertyEventData()
        .addSessionProperty("session_properties.cart_value", 149.99)
)
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

- **HTTP** — Only `HttpURLConnection` and `OkHttp` (v3/4/5) are automatically instrumented; all other HTTP frameworks require manual instrumentation
- **WebSocket and non-HTTP protocols** — require manual instrumentation
- **Native code (NDK)** — not instrumented by bytecode transformation
- **Web components** — `.html`, `.js` files are not instrumented
- **Android library projects** — auto-instrumentation applies only to application projects; library projects are instrumented when added as a dependency to an app project
- **ANR and native crash reporting** — available only on Android 11+; app must be restarted within 10 minutes
- **Events per minute** — default limit is 1,000 events per minute; exceeding this may result in dropped events
- **Multiple monitoring plugins** — using several monitoring plugins simultaneously can cause compatibility issues; test thoroughly or use only one

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

---

## Plugin Version Policy

The wizard uses `8.+` as the version constraint, which allows automatic minor and patch updates within the `8.x` major line. Major version upgrades must be done manually — check Dynatrace release notes before bumping the major version.
