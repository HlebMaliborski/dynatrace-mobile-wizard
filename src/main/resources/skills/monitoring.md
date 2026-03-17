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
- User asks about crash reporting, ANR reporting, or native crash reporting
- User asks about `ExceptionEventData`, `sendExceptionEvent`, or manual error reporting
- User asks about `EventData`, `EventModifier`, custom events, or event/session properties
- User asks about `identifyUser`, `SessionPropertyEventData`, or user tagging
- User asks about `sendEvent`, `addEventModifier`, or enriching events with custom properties
- User asks about Session Replay, rage tap detection, or lifecycle monitoring
- User asks about the New RUM Experience or `startupWithGrailEnabled`
- User asks about `OkHttpEventModifier`, `HttpRequestEventData`, or `sendHttpRequestEvent`

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
    .withStatusCode(200);
Dynatrace.sendHttpRequestEvent(requestData);
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

Report handled exceptions:

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

---

## Custom Events

### Configure Event and Session Properties

Before sending custom properties, define them in the Dynatrace UI:

1. Go to **Experience Vitals** → select your frontend → **Settings** → **Event and session properties**
2. Select **Add** under **Defined event properties** or **Defined session properties**
3. Enter a field name — Dynatrace prefixes it with `event_properties.` or `session_properties.`

> Properties **not configured** in the UI are dropped during ingest.

### Send Custom Events

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

