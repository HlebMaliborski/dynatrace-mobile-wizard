---
name: dynatrace-android-sdk
description: >
  Index skill for the Dynatrace Android SDK. Routes AI agents to the correct topic-specific
  skill file: setup & plugin configuration, OneAgent SDK APIs, monitoring features, or
  troubleshooting. Load this skill first to determine which sub-skill applies, then load
  the relevant file for detailed guidance.
license: Apache-2.0
category: sdk-setup
generated-by: dynatrace-wizard
disable-model-invocation: true
---

# Dynatrace Android SDK — Skill Index

This index covers the **Dynatrace Mobile SDK for Android**. Load the topic-specific skill
that matches the user's question for detailed snippets and guidance.

---

## Invoke This Skill When

- User asks anything about Dynatrace Mobile SDK, the Dynatrace Android Gradle plugin, or OneAgent for Android
- Use this file to route to the correct sub-skill below

---

## Sub-Skills

| File | Name | Use when… |
| --- | --- | --- |
| [`setup.md`](setup.md) | **Plugin Setup & Configuration** | Adding the Gradle plugin, `dynatrace {}` DSL reference, multi-module patterns, manual startup, standalone instrumentation |
| [`sdk-apis.md`](sdk-apis.md) | **OneAgent SDK APIs** | Any question about sending events, reporting values, tracking errors, or manual web requests — covers **both Modern (New RUM/Grail) and Legacy (Classic) APIs** side-by-side: `sendEvent`/`EventData`, `sendExceptionEvent`, `sendBizEvent`, `enterAction`/`leaveAction`/`DTXAction`, `reportEvent`/`reportValue`/`reportError`, `HttpRequestEventData`, `WebRequestTiming`/`getRequestTag`, hybrid WebView monitoring, `setBeaconHeaders`, `DataCollectionLevel` |
| [`monitoring.md`](monitoring.md) | **Monitoring Features** | App start measurement, web requests (auto-instrumentation, W3C Trace Context, `OkHttpEventModifier`), crash/ANR/native crash, `sendExceptionEvent`, `sendEvent`/`EventData`, `EventModifier`, `identifyUser`, session properties — includes **legacy cross-references** for `reportError`, `reportEvent`, `reportValue`, and `WebRequestTiming` |
| [`troubleshooting.md`](troubleshooting.md) | **Troubleshooting & Limitations** | Build errors, missing data, missing user actions or web requests, plugin compatibility issues, instrumentation limitations |

---

## Supported Versions (quick reference)

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

## Quick-Start (minimal working config)

**Kotlin DSL — `build.gradle.kts` (root):**
```kotlin
plugins {
    id("com.dynatrace.instrumentation") version "8.+"
}

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

**Groovy — `build.gradle` (root):**
```groovy
plugins {
    id 'com.dynatrace.instrumentation' version '8.+'
}

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

Find `applicationId` and `beaconUrl` in Dynatrace → Mobile → (your app) → Settings → Instrumentation.

---

## Plugin Version Policy

The wizard uses `8.+` as the version constraint, which allows automatic minor and patch updates within the `8.x` major line. Major version upgrades must be done manually — check Dynatrace release notes before bumping the major version.
