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
| [`setup.md`](setup.md) | **Plugin Setup & Configuration** | Adding or configuring the Dynatrace Gradle plugin, `dynatrace {}` DSL block reference, multi-module patterns, manual startup, standalone instrumentation |
| [`sdk-apis.md`](sdk-apis.md) | **OneAgent SDK APIs** | `enterAction`, `leaveAction`, `reportValue`, `sendBizEvent`, `WebRequestTiming`, hybrid WebView monitoring, `setBeaconHeaders`, `DataCollectionLevel`, `endVisit` |
| [`monitoring.md`](monitoring.md) | **Monitoring Features** | App start measurement, web request monitoring, W3C Trace Context, OkHttp modifier, crash/ANR/native crash reporting, custom events, event modifiers, user tagging, session properties |
| [`troubleshooting.md`](troubleshooting.md) | **Troubleshooting & Limitations** | Build errors, missing data, missing user actions or web requests, plugin compatibility issues, instrumentation and build-specific limitations |

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
