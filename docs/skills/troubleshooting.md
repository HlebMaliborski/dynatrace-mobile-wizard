---
name: dynatrace-android-troubleshooting
description: >
  Troubleshooting guide and limitations reference for the Dynatrace Android SDK and Gradle
  plugin. Covers instrumentation-specific and build-specific limitations, a general
  troubleshooting checklist, runtime data Q&A (missing data, missing web requests, user
  action association, UI component capture, Jetpack Compose preview), and a complete build
  error reference for all known Dynatrace Gradle plugin error messages with causes and fixes.
license: Apache-2.0
category: sdk-setup
parent: dynatrace-android-sdk
generated-by: dynatrace-wizard
disable-model-invocation: true
---

# Dynatrace Android — Troubleshooting & Limitations

---

## Invoke This Skill When

- User asks why Dynatrace is not capturing data or monitoring data is missing after setup
- User asks why a Dynatrace-related build fails or sees a Dynatrace Gradle plugin error message
- User asks about compatibility issues with other performance monitoring plugins
- User asks why a user action or specific UI component is not captured
- User asks why Jetpack Compose is not monitored in Android Studio interactive preview
- User asks about `userActions.timeout` or web requests not associating with user actions
- User wants to troubleshoot missing web requests, user actions, crashes, or ANR events
- User asks about instrumentation limitations (NDK, WebView, resource files, library projects)
- User asks about `Could not find gradle-plugin`, `Plugin not found`, or any Dynatrace build error
- User says **"it's not working"** or **"I don't see any data"** — start with the Decision Tree below
- User asks why **Compose**, **OkHttp**, **Retrofit**, or any specific library is not instrumented
- User asks about **events limit**, **data being dropped**, or **offline behaviour**
- User asks why **Session Replay** is not recording

---

## When to Ask for Clarification

Ask only when the decision tree stalls and the answer changes the fix:

| Unknown | Question to ask |
| --- | --- |
| Cannot tell if build failed or runtime issue | "Does the problem occur during Gradle build, or does the app build fine but data is missing in Dynatrace?" |
| Cannot tell Android version | "Which Android version is the test device running? (ANR and native crash reporting require Android 11+)" |
| Cannot tell opt-in state | "Is `userOptIn(true)` set in the `dynatrace {}` block? If so, has the user consented in the app?" |
| Cannot tell if data ever appeared | "Has data ever appeared in Dynatrace for this app, or is this a fresh setup?" |

---

## Decision Tree — Start Here

Use this tree before diving into specific sections. Follow branches until you reach a fix or a pointer to a deeper section.

```
START
│
├─ Is the problem a BUILD ERROR (Gradle sync or compile fails)?
│   └─ YES → go to "Build Error Reference" section
│
├─ Does the app BUILD but NO DATA appears in Dynatrace?
│   │
│   ├─ 1. Is debug logging enabled? (add `debug { agentLogging(true) }` temporarily)
│   │       → Filter Logcat by tags `dtx` and `caa`
│   │       → See "Verify Monitoring is Working" in monitoring.md for expected log lines
│   │
│   ├─ 2. Log says "OneAgent disabled" or "pluginEnabled is false"?
│   │       → Remove `pluginEnabled(false)` from the DSL
│   │
│   ├─ 3. Log says "Data collection level: OFF"?
│   │       → userOptIn is enabled but user hasn't consented
│   │       → Call applyUserPrivacyOptions with DataCollectionLevel.USER_BEHAVIOR after consent
│   │
│   ├─ 4. Log shows HTTP 403 or 404 on beacon URL?
│   │       → 403: credentials wrong or auth header rejected — verify applicationId + beaconUrl
│   │       → 404: wrong Beacon URL — copy from Dynatrace → Mobile → Settings → Instrumentation
│   │
│   ├─ 5. Log shows certificate error?
│   │       → Cluster uses a private CA → add CA cert to network_security_config.xml
│   │
│   ├─ 6. No log output at all?
│   │       → Plugin was not applied, or auto-instrumentation is disabled
│   │       → Verify plugin declaration in root build file and dynatrace {} block exists
│   │       → See setup.md Phase 1 for detection commands
│   │
│   └─ 7. Logs look healthy but portal shows nothing?
│           → Wait 1–2 minutes; portal has ingestion delay
│           → Confirm device has network access and can reach the beacon URL
│           → Check "Events per minute" limit (default 1,000/min)
│
├─ SPECIFIC DATA IS MISSING (some data appears, but not all)?
│   │
│   ├─ Web requests missing → see "Why are some web requests missing?" below
│   ├─ User actions missing → see "Why are user actions missing?" below
│   ├─ Crash events missing → see "Why are crash/ANR events missing?" below
│   ├─ Custom events / properties missing → see "Why are custom events or properties missing?" below
│   └─ Session Replay not recording → see "Session Replay" below
│
└─ BUILD ERROR → see "Build Error Reference" section
```

---

## Troubleshooting

### General checklist

Before investigating a specific symptom, verify:

1. **Technology is supported** — check the Supported Versions table in `setup.md`. ANR and native-crash reporting require Android 11+.
2. **Plugin version is current** — ensure you are on a supported `8.x` release (check Maven Central for the latest patch).
3. **Debug logging is enabled** — add `debug { agentLogging(true) }` to the `dynatrace {}` block, reproduce the issue, then review Logcat filtered by tag `dtx|caa`. Remove the flag before any production build.
4. **Credentials are correct** — verify `applicationId` and `beaconUrl` match the values in Dynatrace → Mobile → (your app) → Settings → Instrumentation. The Beacon URL must use `https://`.
5. **Network Security Configuration** includes system CA certificates and does not block the Dynatrace endpoint.
6. **Dynatrace endpoint is reachable** from the test device's network.
7. **No other monitoring plugin is interfering** — see Compatibility with other monitoring tools above.

---

### Why are some web requests missing?

1. **Unsupported HTTP library** — only `HttpURLConnection` and `OkHttp` (v3/4/5) are auto-instrumented.
   → For other libraries use `HttpRequestEventData` (modern) or `WebRequestTiming` (legacy) — see `sdk-apis.md`.
2. **Firebase plugin conflict** — the Firebase Performance Gradle plugin can interfere with OkHttp instrumentation.
   → Verify by temporarily removing Firebase Performance, then retest.
3. **Request made outside an active session** — requests are only captured when OneAgent is running and data collection level is `USER_BEHAVIOR`.
4. **Auto web request monitoring disabled** — check for `webRequests { enabled(false) }` in the DSL.

---

### Why are user actions missing?

1. **Auto-instrumentation disabled** — check for `enabled(false)` at the configuration level or `userActions { enabled(false) }`.
2. **Unsupported UI component** — WebView content, custom listeners, and some third-party components are not auto-captured.
3. **Jetpack Compose interactive preview** — the sandbox has no network; instrumentation is skipped by design. Test on a device or emulator.
4. **Compose version out of range** — Compose auto-instrumentation requires plugin 8.271+ and Compose 1.4–1.10. Check the Technologies tab in the wizard.
5. **namePrivacy is on** — `namePrivacy(true)` masks action names; actions still appear but with generic names.

---

### Why are web requests not associated with a user action?

OneAgent attaches web requests to a user action within a window from when the action opens until **500 ms after** it closes. Requests outside this window appear as standalone events.

→ To extend the window, configure `userActions.timeout` in the `dynatrace {}` block.
→ For manually-created actions, ensure the HTTP call is made before `leaveAction()`.

---

### Why are crash / ANR / native crash events missing?

| Symptom | Check |
| --- | --- |
| No crash events at all | Is `crashReporting(false)` set? Is `pluginEnabled(false)` set? |
| Crash event missing from portal | Was app restarted within 10 minutes of the crash? Dynatrace discards reports older than 10 min |
| ANR events missing | Android version < 11? ANR reporting requires Android 11+ |
| Native crash missing | Android version < 11? NDK crash reporting requires Android 11+ |
| Crash appeared once, not again | Check for duplicate crash reporters (Firebase Crashlytics etc.) intercepting uncaught exceptions |

---

### Why are custom events or properties missing?

1. **Property not defined in Dynatrace UI** — `event_properties.*` and `session_properties.*` keys must be configured in Dynatrace → Experience Vitals → Settings → Event and session properties **before** data is sent. Properties without UI definition are silently dropped.
2. **Missing prefix** — Keys without `event_properties.` or `session_properties.` prefix are dropped.
3. **Business event not in active session** — `sendBizEvent` requires OneAgent to be running with `USER_BEHAVIOR` data collection.
4. **Action already finished** — `reportValue` / `reportError` on a finished `DTXAction` is silently ignored. Check `action.isFinished()` first.

---

### Session Replay not recording

1. `sessionReplay.enabled(true)` must be set in the DSL.
2. A Session Replay license is required in the Dynatrace environment.
3. Privacy consent: if `userOptIn(true)` is set, the user must have consented with `USER_BEHAVIOR` level.
4. Session Replay requires plugin 8.281+.
5. Session Replay does not work in Jetpack Compose interactive preview.

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

- **Native code (NDK)** — code written with the Android NDK
- **Web components** — `.html` and `.js` files
- **Resource files** — layout `.xml` files and other Android resources
- **WebView content** — JavaScript inside a WebView; use hybrid monitoring to correlate WebView sessions

### Compatibility with other monitoring tools

Using multiple performance monitoring plugins simultaneously may cause compatibility issues, especially when other plugins also instrument the Android runtime. Either use only one monitoring plugin at a time, or verify compatibility through manual testing before releasing to production.

### Build-specific limitations

- **Android library projects** — the plugin auto-instruments only `com.android.application` projects; library code is instrumented when the library is a dependency of an app module
- **Android Gradle plugin `excludes` property** — disables instrumentation for ALL specified classes including Dynatrace-critical ones; use Dynatrace's own `exclude { }` block instead
- **`com.dynatrace.instrumentation` must be at root** — applying the coordinator plugin inside an app module build file causes a build error
- **`com.dynatrace.instrumentation` and `com.dynatrace.instrumentation.module` are mutually exclusive** — using both in the same module causes a build error

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

Before investigating a specific symptom, verify:

1. **Technology is supported** — check the Supported Versions table in `setup.md`. ANR and native-crash reporting require Android 11+.
2. **Plugin version is current** — ensure you are on a supported `8.x` release (check Maven Central for the latest patch).
3. **Debug logging is enabled** — add `debug { agentLogging(true) }` to the `dynatrace {}` block, reproduce the issue, then review Logcat filtered by tag `dtx|caa`. Remove the flag before any production build.
4. **Credentials are correct** — verify `applicationId` and `beaconUrl` match the values in Dynatrace → Mobile → (your app) → Settings → Instrumentation. The Beacon URL must use `https://`.
5. **Network Security Configuration** includes system CA certificates and does not block the Dynatrace endpoint.
6. **Dynatrace endpoint is reachable** from the test device's network.
7. **No other monitoring plugin is interfering** — see Compatibility with other monitoring tools above.

---


## Build Error Reference

### `OneAgent SDK version does not match Dynatrace Android Gradle plugin version`

The SDK JAR version and the Gradle plugin version must match exactly. This usually happens when you added `com.dynatrace.agent:agent-android` with a different version than the plugin.

**Fix:** Remove the explicit SDK dependency version and let the plugin inject it automatically via `agentDependency()`, or align both versions.

---

### `Plugin with id 'com.dynatrace.instrumentation' not found`

The plugin classpath entry is missing.

**Fix:** Add the plugin to the root build file (Plugin DSL or `buildscript { classpath … }`) and ensure `mavenCentral()` is in the plugin repository list. See `setup.md` Steps 1 and 2.

---

### `Could not find com.dynatrace.tools.android:gradle-plugin:<version>`

Gradle cannot resolve the specified version.

**Fix:**
1. Verify the version exists on [Maven Central](https://central.sonatype.com/artifact/com.dynatrace.tools.android/gradle-plugin).
2. Ensure `mavenCentral()` is declared in `pluginManagement.repositories` (or `buildscript.repositories`).

---

### `Could not find any version that matches com.dynatrace.tools.android:gradle-plugin:<version>.+`

Gradle cannot find a matching version in any configured repository.

**Fix:** Same as above. Also check whether a corporate proxy or Gradle cache is blocking Maven Central.

---

### `Could not get unknown property 'dynatrace' for object of type DefaultDependencyHandler`

The `dynatrace {}` block appears **before** `apply plugin: 'com.dynatrace.instrumentation'`.

**Fix:** Always apply the plugin **before** the `dynatrace {}` block:

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

### `No configuration for the Dynatrace Android Gradle plugin found!`

The plugin was applied but no `dynatrace {}` block was provided.

**Fix:** Add the `dynatrace {}` configuration block with at least one `configurations { create("…") { autoStart { … } } }` entry.

---

### `Task 'printVariantAffiliation' not found in project :<module>`

The plugin generates tasks only for Android application submodules, not for the root project or library modules.

**Fix:** Run `printVariantAffiliation` on an app module, not on a library or root module.

---

### `The Dynatrace Android Gradle Plugin can only be applied to Android projects`

The plugin was applied to a module that is not built with the Android Gradle plugin.

**Fix:** Only apply `com.dynatrace.instrumentation` (or `.module`) to modules that use `com.android.application` or `com.android.library`.

---

### `The Dynatrace Android Gradle plugin must be applied in the top-level build.gradle (or build.gradle.kts) file`

The coordinator plugin was applied inside an app module build file instead of the root build file.

**Fix:** Move the plugin declaration to the root `build.gradle(.kts)`. For per-module configuration, use `com.dynatrace.instrumentation.module` in the app module and the coordinator at root.

---

### `The Dynatrace Android Gradle plugin can't be directly applied to a Java- or Android-related module`

Uncommon project architecture — the coordinator plugin is being applied inside a module that also has Android or Java plugins.

**Fix:**
- If the project has a single build file, see "Projects with one build file" in Dynatrace documentation.
- Otherwise, use `com.dynatrace.instrumentation.module` in that module and the coordinator at root.

---

### `It is not possible to use both 'com.dynatrace.instrumentation' and 'com.dynatrace.instrumentation.module' for the same module`

Both plugins were applied to the same module.

**Fix:** The coordinator automatically configures all submodules — you do not need `.module` in any module when using the coordinator. If you need per-module control, remove the coordinator from root and use only `.module` per app module.

