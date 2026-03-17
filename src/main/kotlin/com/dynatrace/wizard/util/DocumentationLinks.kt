package com.dynatrace.wizard.util

import com.intellij.ide.BrowserUtil
import com.intellij.ui.components.JBLabel
import java.awt.Cursor
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent

/**
 * Utility object providing Dynatrace documentation URL constants and helper methods
 * for creating clickable documentation link labels in the wizard UI.
 */
object DocumentationLinks {
    // Root page for the instrumentation-via-plugin section
    private const val BASE = "https://docs.dynatrace.com/docs/observe/digital-experience/mobile-applications/instrument-android-app/instrumentation-via-plugin"
    private const val MOBILE_BASE = "https://docs.dynatrace.com/docs/observe/digital-experience/mobile-applications"

    // BASE itself is the getting-started / overview page — do NOT append the slug again
    const val GETTING_STARTED         = BASE
    const val GRADLE_PLUGIN           = BASE
    const val BEACON_URL              = BASE

    const val CONFIGURE_PLUGIN        = "$BASE/configure-plugin-for-instrumentation"
    const val AUTO_INSTRUMENTATION    = "$BASE/configure-plugin-for-instrumentation"
    const val MONITORING_CAPABILITIES = "$BASE/monitoring-capabilities"
    const val CRASH_REPORTING         = "$BASE/monitoring-capabilities"
    const val ADJUST_ONEAGENT         = "$BASE/adjust-oneagent-configuration"
    const val USER_OPT_IN             = "$BASE/adjust-oneagent-configuration"
    const val HYBRID_MONITORING       = "$BASE/adjust-oneagent-configuration"
    const val MULTI_MODULE            = "$BASE/configure-multi-module-projects"

    // Privacy & data collection
    const val PRIVACY_DATA_COLLECTION = "$MOBILE_BASE/configure-privacy-and-data-collection"

    // App performance
    const val APP_PERFORMANCE         = "$MOBILE_BASE/app-performance-for-android"

    // Web request monitoring
    const val WEB_REQUEST_MONITORING  = "$MOBILE_BASE/web-request-performance-monitoring"

    // Error & crash reporting
    const val ERROR_CRASH_REPORTING   = "$MOBILE_BASE/error-and-crash-reporting"

    // Custom events
    const val CUSTOM_EVENTS           = "$MOBILE_BASE/custom-events"

    // User & session management
    const val USER_SESSION_MANAGEMENT = "$MOBILE_BASE/user-and-session-management"

    // Support & limitations
    const val SUPPORT_LIMITATIONS     = "$MOBILE_BASE/support-and-limitations"

    // Build-specific limitations (anchor on support page)
    const val BUILD_SPECIFIC_LIMITATIONS =
        "https://docs.dynatrace.com/docs/shortlink/support-and-limitations-android#build-specific-limitations"

    // Compatibility with other monitoring tools
    const val COMPATIBILITY_MONITORING_TOOLS =
        "https://docs.dynatrace.com/docs/shortlink/dynatrace-android-gradle-plugin-overview#compatibility-monitoring-tools"

    const val RELEASE_NOTES           = "https://docs.dynatrace.com/docs/whats-new/oneagent-mobile"
    const val SUPPORTED_TECHNOLOGIES  = "https://docs.dynatrace.com/docs/ingest-from/technology-support"

    /**
     * Creates a clickable HTML link label that opens the given URL in the default browser.
     */
    fun createLinkLabel(text: String, url: String): JComponent {
        val label = JBLabel("<html><a href='$url'>$text</a></html>")
        label.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        label.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
                BrowserUtil.browse(url)
            }
        })
        return label
    }
}
