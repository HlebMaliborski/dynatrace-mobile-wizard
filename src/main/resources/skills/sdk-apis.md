---
name: dynatrace-android-sdk-apis
description: >
  OneAgent SDK API reference for Android. Covers DynatraceConfigurationBuilder options,
  custom user actions (DTXAction, enterAction, leaveAction, cancel, child actions),
  custom value and error reporting (reportValue, reportError, reportEvent), business events
  (sendBizEvent), manual web request instrumentation (WebRequestTiming, getRequestTag,
  WebSocket), hybrid WebView monitoring (instrumentWebView, withMonitoredDomains,
  restoreCookies), network and communication configuration (setBeaconHeaders,
  CommunicationProblemListener), and session/privacy management (endVisit,
  DataCollectionLevel, applyUserPrivacyOptions, userOptIn).
license: Apache-2.0
category: sdk-setup
parent: dynatrace-android-sdk
generated-by: dynatrace-wizard
disable-model-invocation: true
---

# Dynatrace Android — OneAgent SDK APIs

---

## Invoke This Skill When

- User asks about `Dynatrace.enterAction`, `leaveAction`, `DTXAction`, child actions, or custom user actions
- User asks about `reportValue`, `reportError`, `reportEvent`, or `sendBizEvent`
- User asks about manual web request instrumentation, `WebRequestTiming`, `getRequestTag`, or WebSocket monitoring
- User asks about hybrid app monitoring, `instrumentWebView`, `withMonitoredDomains`, or `restoreCookies`
- User asks about `setBeaconHeaders`, `CommunicationProblemListener`, certificate pinning, or custom auth headers
- User asks about `DynatraceConfigurationBuilder` options, `withCrashReporting`, `withUserOptIn`, `withActivityMonitoring`
- User asks about `DataCollectionLevel`, `applyUserPrivacyOptions`, or user opt-in/consent flows
- User asks about `Dynatrace.endVisit` or forcing a session to end
- User asks about `withHybridMonitoring`, `fileDomainCookies`, or `withMonitoredHttpsDomains`

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
| `.withDebugLogging(true)` | `false` | Enable verbose Logcat output (NOT for production) |

---

## Custom User Actions

Custom actions let you measure time spans around meaningful user interactions and attach data.

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
    action.leaveAction()
} catch (e: Exception) {
    action.cancel()   // all data discarded
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

All reporting methods work on an open `DTXAction`. Values appear in the waterfall analysis.

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
action.reportValue("query", searchText)      // String
action.reportValue("result_count", 42)       // Int
action.reportValue("latency_ms", 350L)       // Long
action.reportValue("score", 4.8)             // Double
```

```java
// Java
action.reportValue("query", searchText);
action.reportValue("result_count", 42);
action.reportValue("latency_ms", 350L);
action.reportValue("score", 4.8);
```

### Report Errors

```kotlin
// Kotlin
action.reportError("network_error", -1)          // error code attached to action
action.reportError("parse_failed", exception)    // exception attached to action

// Standalone (not tied to an action)
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

Business events are standalone events sent separately from user actions. They require an active monitored session — not sent when OneAgent is disabled.

```kotlin
// Kotlin
val attributes = JSONObject().apply {
    put("event.name", "Confirmed Booking")
    put("product", "Danube Anna Hotel")
    put("amount", 358.35)
    put("currency", "USD")
    put("journeyDuration", 10)
    put("adultTravelers", 2)
}
Dynatrace.sendBizEvent("com.easytravel.funnel.booking-finished", attributes)
```

```java
// Java
JSONObject attributes = new JSONObject();
attributes.put("event.name", "Confirmed Booking");
attributes.put("amount", 358.35);
attributes.put("currency", "USD");
Dynatrace.sendBizEvent("com.easytravel.funnel.booking-finished", attributes);
```

---

## Manual Web Request Instrumentation

Use when your HTTP library is not auto-instrumented, or to instrument non-HTTP protocols.

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
val tag = Dynatrace.getRequestTag()   // auto-associates with any open action
val timing = Dynatrace.getWebRequestTiming(tag)
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

// With Secure cookie flag (v8.237+):
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
```

Domain names must start with a period (`.`).

Also enable in the Gradle DSL: `hybridMonitoring(true)`.

### Instrument Every WebView

Call `instrumentWebView` **before** `loadUrl`:

```kotlin
// Kotlin
val webView = findViewById<WebView>(R.id.webview)
Dynatrace.instrumentWebView(webView)   // must come before loadUrl
webView.loadUrl("https://www.example.com")
```

```java
// Java
WebView webView = findViewById(R.id.webview);
Dynatrace.instrumentWebView(webView);
webView.loadUrl("https://www.example.com");
```

### Preserve Dynatrace Cookies

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
DynatraceConfigurationBuilder("<id>", "<url>")
    .withHybridMonitoring(true)
    .fileDomainCookies(false)
    .buildConfiguration()
```

---

## Network & Communication Configuration

### Custom Beacon Headers (Authorization, Cookies)

Set headers **before** `Dynatrace.startup`:

```kotlin
// Kotlin
Dynatrace.setBeaconHeaders(mapOf(
    "Authorization" to "Basic $encodedCredentials",
    "Cookie" to "session=abc123"
))
Dynatrace.startup(this, DynatraceConfigurationBuilder("<id>", "<url>").buildConfiguration())
```

```java
// Java
Map<String, String> headers = new HashMap<>();
headers.put("Authorization", "Basic " + encodedCredentials);
Dynatrace.setBeaconHeaders(headers);
Dynatrace.startup(this, new DynatraceConfigurationBuilder("<id>", "<url>").buildConfiguration());
```

To remove all custom headers: `Dynatrace.setBeaconHeaders(null)`

### Token Refresh with CommunicationProblemListener

```kotlin
// Kotlin
Dynatrace.startup(this, DynatraceConfigurationBuilder("<id>", "<url>")
    .withCommunicationProblemListener(object : CommunicationProblemListener {
        override fun onFailure(responseCode: Int, responseMessage: String, body: String) {
            val newToken = refreshToken()
            Dynatrace.setBeaconHeaders(mapOf("Authorization" to "Bearer $newToken"))
        }
        override fun onError(throwable: Throwable) {
            // Network-level errors — OneAgent retries automatically
        }
    })
    .buildConfiguration())
```

```java
// Java
Dynatrace.startup(this, new DynatraceConfigurationBuilder("<id>", "<url>")
    .withCommunicationProblemListener(new CommunicationProblemListener() {
        @Override
        public void onFailure(int code, String msg, String body) {
            Map<String, String> h = new HashMap<>();
            h.put("Authorization", "Bearer " + refreshToken());
            Dynatrace.setBeaconHeaders(h);
        }
        @Override
        public void onError(Throwable t) {}
    })
    .buildConfiguration());
```

When set, OneAgent waits for `setBeaconHeaders` instead of retrying automatically on 4xx.

### Network Security (HTTPS / Certificates)

For clusters with private CA certificates, add a `domain-config` to `network_security_config.xml`:

```xml
<domain-config>
  <domain includeSubdomains="true">your.cluster.domain.com</domain>
  <trust-anchors>
    <certificates src="@raw/your_server_certificate" />
  </trust-anchors>
</domain-config>
```

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

> After `endVisit`, sessions are **not** re-tagged automatically. Call `identifyUser()` again in the new session.

OneAgent also ends sessions automatically on: idle timeout, duration timeout, privacy changes, or app force-stop.

---

### Data Collection Levels

| Level | Description |
| --- | --- |
| `OFF` | No data is collected |
| `PERFORMANCE` | Performance data only (crashes, ANRs) |
| `USER_BEHAVIOR` | Performance data + user behavior (user actions, sessions) |

When `userOptIn(true)` is configured and the user has not yet consented, defaults are `OFF` with crash reporting disabled.

### Set Data Collection Level at Runtime

```kotlin
// Kotlin
val updatedOptions = Dynatrace.getUserPrivacyOptions().newBuilder()
    .withDataCollectionLevel(DataCollectionLevel.PERFORMANCE)
    .build()
Dynatrace.applyUserPrivacyOptions(updatedOptions)
```

```java
// Java
UserPrivacyOptions updatedOptions = Dynatrace.getUserPrivacyOptions().newBuilder()
    .withDataCollectionLevel(DataCollectionLevel.PERFORMANCE)
    .build();
Dynatrace.applyUserPrivacyOptions(updatedOptions);
```

### Enable or Disable Crash Reporting at Runtime

```kotlin
// Kotlin
val updatedOptions = Dynatrace.getUserPrivacyOptions().newBuilder()
    .withCrashReportingOptedIn(true)
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
            autoStart { userOptIn(true) }
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
- You must implement a consent dialog yourself
- After user consents, call `applyUserPrivacyOptions` with `DataCollectionLevel.USER_BEHAVIOR`

