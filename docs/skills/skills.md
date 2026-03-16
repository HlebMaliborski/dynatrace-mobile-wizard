---
name: dynatrace-android-sdk
description: >
  Complete reference for configuring the Dynatrace Mobile SDK in Android projects via the
  Dynatrace Gradle plugin. Covers Plugin DSL and Buildscript Classpath approaches, single-app,
  feature-module, and multi-app project layouts, Kotlin DSL and Groovy DSL, and all supported
  monitoring features. Use whenever a user asks to add Dynatrace to Android, configure mobile
  instrumentation, enable crash reporting, Session Replay, or privacy-aware monitoring.
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
- User wants crash reporting, Session Replay, user-action capture, web-request monitoring, or privacy mode in Android
- User asks why Dynatrace isn't capturing data after setup
- User wants to update, remove, or migrate their Dynatrace Gradle configuration
- User has a multi-module, feature-module, or multi-app Android project and needs per-module setup

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
# Detect AGP version (must be 8.0+ for Dynatrace plugin 8.x)
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
| `crashReporting(false)` | `true` | Disable crash reporting |
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

When auto-start is disabled, call `Dynatrace.startup()` in your `Application.onCreate()`:

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

## Removing Dynatrace Configuration

To cleanly remove Dynatrace:
1. Delete the `dynatrace { }` block from all Gradle files
2. Remove the plugin from `plugins { }` or `buildscript { dependencies { classpath … } }`
3. Remove `apply plugin: 'com.dynatrace.instrumentation'` / `apply(plugin = "…")` lines
4. Sync Gradle

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

---

## Plugin Version Policy

The wizard uses `8.+` as the version constraint, which allows automatic minor and patch updates within the `8.x` major line. Major version upgrades must be done manually — check Dynatrace release notes before bumping the major version.
