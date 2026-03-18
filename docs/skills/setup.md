---
name: dynatrace-android-setup
description: >
  Step-by-step guide for adding the Dynatrace Android Gradle plugin to any Android project.
  Covers Plugin DSL and Buildscript Classpath approaches, single-app, feature-module, and
  multi-app project layouts, Kotlin DSL and Groovy DSL, the complete dynatrace {} block
  reference (all options, debug logging, exclusions), multi-module patterns, manual startup,
  and standalone instrumentation without the Gradle plugin.
license: Apache-2.0
category: sdk-setup
parent: dynatrace-android-sdk
generated-by: dynatrace-wizard
disable-model-invocation: true
---

# Dynatrace Android — Plugin Setup & Configuration

---

## Invoke This Skill When

- User asks to add Dynatrace monitoring to an Android app
- User asks about `com.dynatrace.instrumentation`, the Dynatrace Gradle plugin, or how to apply it
- User wants to configure the `dynatrace {}` block, set `applicationId` or `beaconUrl`
- User wants to **update, reconfigure, or verify** an existing Dynatrace Gradle configuration
- User wants to **migrate** from Buildscript Classpath to Plugin DSL, or vice versa
- User wants to **remove** Dynatrace from their project
- User has a multi-module, feature-module, or multi-app Android project and needs per-module setup
- User asks about Plugin DSL vs Buildscript Classpath approach
- User asks about `variantFilter`, `autoStart`, `enabled`, `strictMode`, or `pluginEnabled`
- User asks about any `dynatrace {}` block option: `crashReporting`, `anrReporting`, `sessionReplay`, `agentLogging`, `exclude`, etc.
- User wants to use OneAgent SDK in a library module (`agentDependency`)
- User wants standalone instrumentation without the Gradle plugin
- User asks about `autoStart { enabled(false) }` or calling `Dynatrace.startup()` manually
- User has a **Version Catalog** (`libs.versions.toml`) and asks how to add the Dynatrace plugin

---

## When to Ask for Clarification

Before generating code, resolve these unknowns if the output from Phase 1 is ambiguous:

| Unknown | Question to ask |
| --- | --- |
| DSL type not clear | "Does your project use `build.gradle.kts` (Kotlin DSL) or `build.gradle` (Groovy)?" |
| Plugin approach not clear | "Does your root build file have a `plugins { }` block or a `buildscript { dependencies { classpath … } }` block?" |
| Single-app vs multi-module not clear | "How many modules in your project use `com.android.application`?" |
| Existing config present | "I see Dynatrace is already configured. Should I update the existing config or start from scratch?" |
| Version Catalog in use | "Does your project use `gradle/libs.versions.toml`? I'll tailor the snippets accordingly." |

---

## Supported Versions

| Component | Minimum Version |
| --- | --- |
| Android API level | 21 |
| Gradle | 7.0.2 |
| Android Gradle Plugin | 7.0 |
| Java | 11 |
| Kotlin | 1.8 – 2.3 |
| Jetpack Compose | 1.4 – 1.10 |

> ANR reporting and Native crash reporting require Android 11 or higher.

---

## Phase 1: Detect

Run these commands in the project root before making any recommendations or changes.

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

| Question | How to check |
| --- | --- |
| **Single-app or multi-module?** | Count `com.android.application` plugin declarations across all `build.gradle(.kts)` files |
| **Feature modules present?** | Look for `com.android.dynamic-feature` plugin in any module |
| **Multiple app modules?** | More than one `com.android.application` module |
| **Kotlin DSL or Groovy?** | File extension: `.kts` = Kotlin DSL; no extension = Groovy |
| **Plugin DSL or Buildscript?** | Root build file has `plugins { }` block → Plugin DSL; has `buildscript { dependencies { classpath … } }` → Buildscript Classpath |

---

## Step 1 — Add mavenCentral() Repository

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

### 2a. Plugin DSL (recommended)

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

> The Dynatrace coordinator plugin **must** be declared at the project root. Do not apply it inside an app module's `build.gradle`.

---

### 2b. Buildscript Classpath (legacy)

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
- `beaconUrl` — found in the same place (must be HTTPS)
- `variantFilter` — regex matching build variant names; `".*"` instruments all variants

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
| `agentLogging(true)` | `false` | Write verbose OneAgent output to Logcat (tag: `dtx\|caa`). **Remove before Play Store builds.** |

> Do not enable debug logging in production. Extra logging may slow down the app and expose sensitive data in device logs.

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

**Via OneAgent SDK** (no Gradle plugin required):
```kotlin
Dynatrace.startup(this, DynatraceConfigurationBuilder(
    "<YourApplicationID>", "<ProvidedBeaconURL>"
).withDebugLogging(true).buildConfiguration())
```

Retrieve logs via Logcat and filter by tag `dtx|caa`.

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
            behavioralEvents { detectRageTaps(true) }
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
            behavioralEvents { detectRageTaps true }
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

```
root/
  build.gradle.kts          ← plugin declaration (Plugin DSL)
  app/build.gradle.kts      ← dynatrace { } block + autoStart credentials
  feature_login/            ← no changes needed
  feature_checkout/         ← no changes needed
```

### Multi-App Modules (multiple `com.android.application`)

#### Option A — Plugin DSL Coordinator (recommended)

**Root `build.gradle.kts`:**
```kotlin
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

Use when different app modules need different Application IDs or Beacon URLs.

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

> `com.dynatrace.instrumentation.module` must be declared **without** a version — the classpath entry supplies it.

---

### 2c. Version Catalog (`libs.versions.toml`) — modern projects

If the project uses `gradle/libs.versions.toml`, declare the plugin there and reference it via alias.

**`gradle/libs.versions.toml`:**
```toml
[versions]
dynatrace = "8.+"

[plugins]
dynatrace = { id = "com.dynatrace.instrumentation", version.ref = "dynatrace" }
```

**Root `build.gradle.kts`:**
```kotlin
plugins {
    alias(libs.plugins.dynatrace)
}
```

**Root `build.gradle` (Groovy):**
```groovy
plugins {
    alias(libs.plugins.dynatrace)
}
```

> The `dynatrace {}` configuration block still goes in the same root build file as the plugin declaration.

---

## Updating Existing Configuration

When `com.dynatrace.instrumentation` is already present, **always read before overwriting**.

### Step 1 — Find the existing config

```bash
# Locate the dynatrace {} block
grep -rn 'dynatrace {' . --include="*.gradle" --include="*.kts" 2>/dev/null
# Show the current applicationId and beaconUrl
grep -A3 'applicationId\|beaconUrl' $(grep -rl 'dynatrace {' . 2>/dev/null) 2>/dev/null
```

### Step 2 — Identify what changed

| Change type | Action |
| --- | --- |
| New Application ID or Beacon URL | Update `applicationId` / `beaconUrl` in `autoStart {}` |
| New feature toggle | Add/remove the relevant DSL option from Step 4 reference |
| Migrating from Classpath → Plugin DSL | See Migration section below |
| Removing Dynatrace | See "Removing Dynatrace Configuration" section |

### Step 3 — Apply the minimal diff

Only change what needs to change. Do **not** regenerate the whole `dynatrace {}` block from scratch — preserve any options the user may have customised beyond the wizard's output.

---

## Migration: Buildscript Classpath → Plugin DSL

1. **Root build file**: Remove `classpath("com.dynatrace.tools.android:gradle-plugin:8.+")` from `buildscript { dependencies { … } }`. If no other classpath entries remain, remove the whole `buildscript {}` block.
2. **Root build file**: Add `id("com.dynatrace.instrumentation") version "8.+"` to the `plugins {}` block.
3. **Each app module**: Remove `apply(plugin = "com.dynatrace.instrumentation")` / `apply plugin: '...'`.
4. **Each app module**: Move the `dynatrace {}` block up to the root build file (coordinator handles all modules).
5. Sync Gradle.

## Migration: Plugin DSL → Buildscript Classpath (per-module)

1. **Root build file**: Remove the `id("com.dynatrace.instrumentation")` line from `plugins {}`.
2. **Root build file**: Remove the root-level `dynatrace {}` block.
3. **Root build file**: Add `buildscript { dependencies { classpath("com.dynatrace.tools.android:gradle-plugin:8.+") } }`.
4. **Each app module**: Add `apply(plugin = "com.dynatrace.instrumentation")` and a `dynatrace {}` block with per-module credentials.
5. Sync Gradle.

If a library module's code needs to call Dynatrace APIs directly (e.g. `Dynatrace.enterAction()`):

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

**Specific modules only (Kotlin DSL):**
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

Call `Dynatrace.startup()` in `Application.onCreate()` as early as possible:

```kotlin
// Kotlin
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

Use when you cannot use the Dynatrace Android Gradle plugin.

### Step 1 — Add the OneAgent dependency

```kotlin
// Kotlin DSL
dependencies {
    implementation("com.dynatrace.agent:agent-android:8.+")
}
```

```groovy
// Groovy
dependencies {
    implementation 'com.dynatrace.agent:agent-android:8.+'
}
```

For multi-module projects, add it as `api` in the base app module so feature modules can access the SDK.

### Step 2 — Start OneAgent manually

```kotlin
// Kotlin
class YourApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Dynatrace.startup(this, DynatraceConfigurationBuilder(
            "<YourApplicationID>", "<ProvidedBeaconUrl>"
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
            "<YourApplicationID>", "<ProvidedBeaconUrl>"
        ).buildConfiguration());
    }
}
```

> When the Dynatrace Gradle plugin is **also** applied, disable the auto-injected startup call first — otherwise the plugin's injected call runs before yours and your configuration is ignored:
> ```kotlin
> dynatrace { configurations { create("s") { autoStart { enabled(false) } } } }
> ```

---

## Removing Dynatrace Configuration

1. Delete the `dynatrace { }` block from all Gradle files
2. Remove the plugin from `plugins { }` or `buildscript { dependencies { classpath … } }`
3. Remove `apply plugin: 'com.dynatrace.instrumentation'` / `apply(plugin = "…")` lines
4. Sync Gradle

---

## Plugin Version Policy

The wizard uses `8.+` as the version constraint, which allows automatic minor and patch updates within the `8.x` major line. Major version upgrades must be done manually — check Dynatrace release notes before bumping the major version.

