package com.dynatrace.wizard.model

/**
 * Per-module Application ID + Beacon URL override.
 * Used when the user opts for individual credentials in multi-app projects.
 */
data class ModuleCredentials(
    val appId: String,
    val beaconUrl: String
)

/**
 * Data model representing all Dynatrace configuration options collected by the wizard.
 */
data class DynatraceConfig(
    val applicationId: String = "",
    val beaconUrl: String = "",
    // --- auto-start ---
    val autoStartEnabled: Boolean = true,   // false → autoStart { enabled(false) } for Direct Boot apps
    val userOptIn: Boolean = false,         // inside autoStart block
    // --- instrumentation ---
    val autoInstrument: Boolean = true,     // config-level enabled flag
    val pluginEnabled: Boolean = true,      // dynatrace { pluginEnabled false } — global kill-switch
    val crashReporting: Boolean = true,
    val hybridMonitoring: Boolean = false,
    // --- monitoring sections ---
    val userActionsEnabled: Boolean = true,
    val webRequestsEnabled: Boolean = true,
    val lifecycleEnabled: Boolean = true,
    val locationMonitoring: Boolean = false,
    // --- user action details ---
    val namePrivacy: Boolean = false,       // userActions { namePrivacy true } — masks PII in action names
    val composeEnabled: Boolean = true,     // userActions { composeEnabled false }
    // --- behavioral events ---
    val rageTapDetection: Boolean = false,  // behavioralEvents { detectRageTaps true }
    // --- agent behavior (advanced) ---
    val agentBehaviorLoadBalancing: Boolean = false,  // agentBehavior { startupLoadBalancing true }
    val agentBehaviorGrail: Boolean = false,          // agentBehavior { startupWithGrailEnabled true }
    // --- session replay ---
    val sessionReplayEnabled: Boolean = false,        // sessionReplay.enabled(true)
    // --- exclusions ---
    val excludePackages: String = "",   // comma-separated package names
    val excludeClasses: String = "",    // comma-separated fully-qualified class names
    val excludeMethods: String = "",    // comma-separated fully-qualified method names
    // --- build ---
    val buildVariant: String = "all",
    val strictMode: Boolean = false,    // false = don't fail build when variant has no config
    // --- per-module credentials (MULTI_APP buildscript path only) ---
    /** When non-empty, each app module gets its own applicationId + beaconUrl.
     *  Key = module name (e.g. "app", "app2"). Falls back to [applicationId]/[beaconUrl] for absent keys. */
    val moduleCredentials: Map<String, ModuleCredentials> = emptyMap()
)
