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
