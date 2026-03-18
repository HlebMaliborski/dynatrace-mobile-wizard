---
name: dynatrace-android-monitoring
description: >
  Monitoring features reference for the Dynatrace Android SDK. Covers app performance
  monitoring (app start, views, navigation), web request monitoring (automatic
  HttpURLConnection/OkHttp instrumentation, W3C Trace Context, manual HttpRequestEventData
  reporting, OkHttpEventModifier), error and crash reporting (automatic crash, ANR, native
  NDK crashes, manual exception reporting), custom events (EventData, event and session
  properties, EventModifier), user and session management (identifyUser, SessionPropertyEventData,
  user tagging), and enabling the New RUM Experience.
license: Apache-2.0
category: sdk-setup
parent: dynatrace-android-sdk
generated-by: dynatrace-wizard
disable-model-invocation: true
---

# Dynatrace Android — Monitoring Features

---

## Invoke This Skill When

- User asks about app start measurement, cold/warm/hot starts, or `Dynatrace.startView`
- User asks about web request monitoring, `HttpRequestEventData`, `OkHttpEventModifier`, or W3C Trace Context
- User asks about **legacy** web request APIs: `WebRequestTiming`, `getRequestTag`, or manual header injection
- User asks about crash reporting, ANR reporting, or native crash reporting
- User asks about `ExceptionEventData`, `sendExceptionEvent`, or manual error reporting
- User asks about **legacy** error reporting: `reportError` attached to an action or standalone
- User asks about `EventData`, `EventModifier`, custom events, or event/session properties
- User asks about **legacy** event/value APIs: `reportEvent`, `reportValue`, or action-attached values
- User asks about `identifyUser`, `SessionPropertyEventData`, or user tagging
- User asks about `sendEvent`, `addEventModifier`, or enriching events with custom properties
- User asks about Session Replay, rage tap detection, or lifecycle monitoring
- User asks about the New RUM Experience or `startupWithGrailEnabled`
- User asks about `OkHttpEventModifier`, `HttpRequestEventData`, or `sendHttpRequestEvent`
- User asks **"is Dynatrace working?"**, **"how do I verify monitoring?"**, or **"why don't I see data?"** — load this file and `troubleshooting.md`
- User asks which libraries or UI components are auto-instrumented

---

## When to Ask for Clarification

| Unknown | Question to ask |
| --- | --- |
| Old vs new API preference | "Are you writing new code (use Modern/Grail APIs) or working with existing code (may need Legacy APIs)?" |
| Monitoring data missing but setup looks correct | "Are you testing on a device with Android 11+ for ANR/crash events? Is the app connected to the internet?" |
| Properties not appearing | "Have you defined the property keys in Dynatrace UI under Experience Vitals → Settings → Event and session properties?" |

---

## What Is Auto-Instrumented (No Code Changes Needed)

When the Dynatrace Gradle plugin is applied and auto-instrumentation is enabled, the following are captured **automatically**:

| Category | What is captured | Notes |
| --- | --- | --- |
| **HTTP requests** | `HttpURLConnection`, `OkHttp` v3/4/5, `Retrofit 2` | Response code, URL, method, duration, errors |
| **App lifecycle** | Cold start, warm start, hot start, `onResume`, `onPause` | Duration from process creation to first rendered frame |
| **Activities** | Every `Activity.onResume` → view tracking | Activity class name used as view name |
| **Crashes** | Uncaught Java/Kotlin exceptions | Full stack trace; sent on next app launch |
| **ANR** | Application Not Responding events | Android 11+ only; app must restart within 10 min |
| **Native crashes** | NDK C/C++ crashes | Android 11+ only; app must restart within 10 min |
| **Jetpack Compose** | `clickable`, `combinedClickable`, `toggleable`, `selectable`, `swipeable`, `draggable`, `slider`, `pager`, `pull-refresh` | Plugin 8.271+; Compose 1.4–1.10 |
| **Coroutines** | Coroutine lifecycle and suspension points | Kotlin Coroutines on supported Kotlin versions |
| **Jetpack Navigation** | Fragment and composable destination changes | Navigation Component |
| **User gestures** | Taps, swipes on standard Android Views | Custom or third-party view handlers may not be captured |

**Not auto-instrumented** (require manual SDK calls or hybrid monitoring):
- Custom HTTP libraries (Ktor, Volley via non-OkHttp stack, etc.)
- WebView JavaScript — use `hybridMonitoring(true)` + `instrumentWebView()`
- React Native / Flutter — hybrid monitoring required
- Background services and WorkManager tasks
- NDK / native-layer code

---

## Verify Monitoring is Working

Use this checklist after setup to confirm OneAgent is active before checking the Dynatrace portal.

### Step 1 — Enable debug logging temporarily

```kotlin
// In your dynatrace {} block — REMOVE before production build
debug {
    agentLogging(true)
}
```

### Step 2 — Run the app and filter Logcat

```bash
# Android Studio Logcat filter, or adb:
adb logcat -s "dtx" "caa" "*:S"
```

**Expected log lines on successful startup:**

```
dtx  : OneAgent started successfully
dtx  : Application ID: <your-app-id>
dtx  : Beacon URL: https://<your-tenant>.live.dynatrace.com/mbeacon
dtx  : Data collection level: USER_BEHAVIOR
```

**Warning signs in logs:**

| Log message | Meaning | Fix |
| --- | --- | --- |
| `OneAgent disabled — pluginEnabled is false` | Kill-switch is on | Remove `pluginEnabled(false)` from DSL |
| `Data collection level: OFF` | opt-in mode, user hasn't consented | Call `applyUserPrivacyOptions` with `USER_BEHAVIOR` |
| `HTTP 403 sending beacon` | Invalid credentials or auth header rejected | Verify `applicationId` and `beaconUrl`; check `CommunicationProblemListener` |
| `HTTP 404 sending beacon` | Wrong Beacon URL | Copy URL from Dynatrace → Mobile → Settings → Instrumentation |
| `No network — data queued` | Device offline | Test on a network-connected device |
| `Certificate error` | Cluster uses private CA | Add CA cert to `network_security_config.xml` |

### Step 3 — Trigger a monitored event

```kotlin
// Create a simple test action and verify it in Dynatrace
val action = Dynatrace.enterAction("Test action — delete me")
action.reportValue("test_key", "test_value")
action.leaveAction()
```

Or with the modern API:

```kotlin
Dynatrace.sendEvent(
    EventData().addEventProperty("event_properties.test_key", "verify_setup")
)
```

### Step 4 — Check the Dynatrace portal

1. Dynatrace → **Mobile** → select your app
2. Under **Sessions**, look for the device/session from your test run (may take 1–2 minutes)
3. Under **User actions**, find your test action or event

If sessions appear but no data: check `Data collection level` in logs.
If nothing appears after 5 minutes: go to `troubleshooting.md`.

---

## App Performance Monitoring

OneAgent automatically captures and stores the following as events in Grail:

- **App Start events** — cold, warm, and hot starts; duration from process/Activity creation to first `onResume`
- **Views** — one active view per screen (Activities tracked automatically; others require manual tracking)
- **Navigation events** — transitions between views; app-start navigation has no source view; backgrounding has no current view
- **View summaries** — aggregated events on view end: start time, duration, error counts

### Ensure Accurate App Start Measurement

For auto-instrumented apps, measurement begins in `Application.onCreate`. When using manual startup, call `Dynatrace.startup()` as early as possible.

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
- `OkHttp` versions 3, 4, and 5 (includes Retrofit 2)

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

When automatic instrumentation is enabled, OneAgent automatically propagates W3C Trace Context headers (`traceparent`, `tracestate`) on outgoing requests.

**Automatic behavior:**
- No existing headers → OneAgent generates a new `traceparent` and `tracestate`
- Existing valid `traceparent` → OneAgent keeps it and adds Dynatrace vendor data to `tracestate`

### Manually Propagate Trace Context

For custom networking stacks:

```kotlin
// Kotlin
val existingTraceparent = request.header("traceparent")
val existingTracestate  = request.header("tracestate")

val traceContext = Dynatrace.generateTraceContext(existingTraceparent, existingTracestate)
// Returns null if invalid — do NOT modify headers in that case

if (traceContext != null) {
    request = request.newBuilder()
        .header("traceparent", traceContext.traceparent)
        .header("tracestate", traceContext.tracestate)
        .build()
}

val requestData = HttpRequestEventData(request.url.toString(), request.method)
    .withDuration(duration)
    .withStatusCode(statusCode)

if (traceContext != null) {
    requestData.withTraceparentHeader(traceContext.traceparent)
}
Dynatrace.sendHttpRequestEvent(requestData)
```

```java
// Java
TraceContext traceContext = Dynatrace.generateTraceContext(
    request.header("traceparent"), request.header("tracestate"));

if (traceContext != null) {
    request = request.newBuilder()
        .header("traceparent", traceContext.getTraceparent())
        .header("tracestate",  traceContext.getTracestate())
        .build();
}

HttpRequestEventData requestData = new HttpRequestEventData(
        request.url().toString(), request.method())
    .withDuration(duration)
    .withStatusCode(statusCode);

if (traceContext != null) {
    requestData.withTraceparentHeader(traceContext.getTraceparent());
}
Dynatrace.sendHttpRequestEvent(requestData);
```

### Manual Web Request Reporting

For networking libraries not supported by automatic instrumentation.

#### ✅ Modern approach (New RUM / Grail) — preferred for new code

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
    .withStatusCode(200);
Dynatrace.sendHttpRequestEvent(requestData);
```

#### ⚠️ Legacy approach (Classic RUM) — WebRequestTiming + getRequestTag

```kotlin
// Kotlin — attach a web request to a user action
val url = URL("https://api.example.com/data")
val action = Dynatrace.enterAction("Fetch data")
val tag = action.getRequestTag()
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
    action.leaveAction()
}
```

> **When to use each:** Use `sendHttpRequestEvent(HttpRequestEventData)` for new code — it requires no open action and works cleanly with W3C Trace Context. Use `WebRequestTiming` + `getRequestTag` only for existing Classic RUM instrumentation or non-HTTP protocols where you need the request to appear inside an action waterfall.

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
        return JSONObject()
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
        return new JSONObject();
    }
};
Dynatrace.addHttpEventModifier(modifier);
```

**Filter a request** (return `null` to drop the event):

```kotlin
val filterModifier: OkHttpEventModifier = object : OkHttpEventModifier {
    override fun modifyEvent(request: Request, response: Response?): JSONObject? {
        return if (request.url.toString().contains("analytics.example.com")) null else JSONObject()
    }
    override fun modifyEvent(request: Request?, throwable: Throwable?): JSONObject? = JSONObject()
}
```

**Remove a modifier:** `Dynatrace.removeHttpEventModifier(modifier)`

---

## Error and Crash Reporting

### Automatic Crash Reporting

OneAgent captures all uncaught Java/Kotlin exceptions with the full stack trace. Reports are sent after the crash, or on next relaunch within 10 minutes.

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

### ANR Reporting (Android 11+)

OneAgent automatically captures Application Not Responding events on Android 11+. App must be restarted within 10 minutes.

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
DynatraceConfigurationBuilder("<id>", "<url>").withAnrReporting(false).buildConfiguration()
```

```java
new DynatraceConfigurationBuilder("<id>", "<url>").withAnrReporting(false).buildConfiguration();
```

**ANR limitations:**
- Android 11+ only
- Multiple ANRs in one session generate separate events
- Some ANRs may lack stack traces due to OS limitations

### Native Crash Reporting (Android 11+)

Captures C/C++ NDK crashes on Android 11+. App must be restarted within 10 minutes.

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
DynatraceConfigurationBuilder("<id>", "<url>").withNativeCrashReporting(false).buildConfiguration()
```

```java
new DynatraceConfigurationBuilder("<id>", "<url>").withNativeCrashReporting(false).buildConfiguration();
```

### Manual Error Reporting

#### ✅ Modern approach (New RUM / Grail) — preferred for new code

```kotlin
// Kotlin
try {
    // ...
} catch (exception: Exception) {
    Dynatrace.sendExceptionEvent(
        ExceptionEventData(exception)
            .addEventProperty("event_properties.context", "checkout")
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
            .addEventProperty("event_properties.context", "checkout")
    );
}
```

#### ⚠️ Legacy approach (Classic RUM) — action-attached or standalone error codes

```kotlin
// Kotlin — attached to an open action
action.reportError("network_error", -1)          // error name + code
action.reportError("parse_failed", exception)    // error name + exception

// Standalone (not tied to an action)
Dynatrace.reportError("background_sync_failed", -2)
Dynatrace.reportError("unhandled_state", exception)
```

```java
// Java
action.reportError("network_error", -1);
action.reportError("parse_failed", exception);
Dynatrace.reportError("background_sync_failed", -2);
```

> **When to use each:** Prefer `sendExceptionEvent(ExceptionEventData)` for new code — it creates a standalone error event in Grail with full stack trace and custom properties. Use `reportError` only for existing Classic RUM instrumentation or when you need the error to appear in the action waterfall.

---

## Custom Events

### Configure Event and Session Properties

Before sending custom properties, define them in the Dynatrace UI:

1. Go to **Experience Vitals** → select your frontend → **Settings** → **Event and session properties**
2. Select **Add** under **Defined event properties** or **Defined session properties**
3. Enter a field name — Dynatrace prefixes it with `event_properties.` or `session_properties.`

> Properties **not configured** in the UI are dropped during ingest.

### Send Custom Events

#### ✅ Modern approach (New RUM / Grail) — preferred for new code

```kotlin
// Kotlin
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
Dynatrace.sendEvent(
    new EventData()
        .withDuration(250)
        .addEventProperty("event_properties.checkout_step", "payment_confirmed")
        .addEventProperty("event_properties.cart_value", 149.99)
);
```

#### ⚠️ Legacy approach (Classic RUM) — action-attached events

In the classic model, events and values are attached to an open `DTXAction`:

```kotlin
// Kotlin
val action = Dynatrace.enterAction("Checkout")
action.reportEvent("payment_confirmed")          // named event
action.reportValue("cart_value", 149.99)         // numeric value
action.reportValue("checkout_step", "confirmed") // string value
action.leaveAction()
```

```java
// Java
DTXAction action = Dynatrace.enterAction("Checkout");
action.reportEvent("payment_confirmed");
action.reportValue("cart_value", 149.99);
action.leaveAction();
```

> **When to use each:**
> - Use `sendEvent(EventData())` when you want a standalone event not tied to a user action, or when working with New RUM / Grail.
> - Use `reportEvent` / `reportValue` on a `DTXAction` when you want the value to appear in the Classic waterfall view, or when extending existing legacy instrumentation.

### Event Modifiers

Intercept all events before they are sent to add context, redact PII, or filter events.

```kotlin
// Kotlin — add context to every event
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
    return event;
};
Dynatrace.addEventModifier(modifier);
```

**Filter events** (return `null` to discard):

```kotlin
val modifier = EventModifier { event ->
    if (event.optString("view.detected_name") == "com.example.MainActivity") null
    else event
}
```

**Redact sensitive data:**

```kotlin
val modifier = EventModifier { event ->
    val url = event.optString("url.full", null)
    if (url != null) {
        event.put("url.full", url.replace(Regex("/users/\\w+/"), "/users/{id}/"))
    }
    event
}
```

**Conditional enrichment (HTTP and error events only):**

```kotlin
val modifier = EventModifier { event ->
    if (event.optBoolean("characteristics.has_request")) {
        event.put("event_properties.api_client", apiClientName)
    }
    if (event.optBoolean("characteristics.has_error")) {
        event.put("event_properties.triage_owner", "mobile")
    }
    event
}
```

**Remove a modifier:** `Dynatrace.removeEventModifier(modifier)`

**Modifiable fields:** `event_properties.*`, `session_properties.*`, `url.full`, `exception.stack_trace`

---

## User and Session Management

### Identify Users

```kotlin
// Kotlin — tag after login
Dynatrace.identifyUser("user@example.com")

// Remove on logout
Dynatrace.identifyUser(null)
```

```java
// Java
Dynatrace.identifyUser("user@example.com");
Dynatrace.identifyUser(null); // logout
```

**Important:**
- The user tag is **not persisted** — call `identifyUser()` on every new session
- Session splits (idle/duration timeout) re-tag automatically (v8.237+)
- Logout / privacy changes do **not** re-tag automatically

### Session Properties

Attach key-value pairs to all events in the current session. Properties must be configured in the Dynatrace UI first.

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
);
```

**Update during a session:**

```kotlin
// Update cart value after user adds items
Dynatrace.sendSessionPropertyEvent(
    SessionPropertyEventData().addSessionProperty("session_properties.cart_value", 149.99)
)
```

---

## Enable the New RUM Experience

To enable in the Dynatrace UI:

1. Go to **Experience Vitals** → select your mobile frontend → **Settings**
2. Under **Enablement and cost control**, turn on **New Real User Monitoring Experience**

Or enable at the environment level: **Settings** → **Collect and capture** → **Real User Monitoring** → **Mobile frontends** → **Traffic and cost control**.

To activate via agent at first app start:
```kotlin
dynatrace {
    configurations {
        create("sampleConfig") {
            agentBehavior { startupWithGrailEnabled(true) }
        }
    }
}
```

