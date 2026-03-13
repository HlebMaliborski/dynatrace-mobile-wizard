package com.dynatrace.wizard.util

import com.intellij.ui.JBColor
import java.awt.Color

/**
 * Shared color palette for the Dynatrace Wizard UI.
 * Every color is a [JBColor] so it adapts automatically to light and dark themes.
 */
object WizardColors {

    /** Dynatrace-blue accent — step title headers, primary highlights. */
    val accent = JBColor(Color(0x0A6BD6), Color(0x58A6FF))

    /** Success green — "configured", validation passed, single-app flow. */
    val success = JBColor(Color(0x2E7D32), Color(0x81C784))

    /** Warning amber — missing mavenCentral, complex multi-module flows. */
    val warning = JBColor(Color(0xC76B00), Color(0xFFB74D))

    /** Error red — unsupported detected version. */
    val error = JBColor(Color(0xC62828), Color(0xEF9A9A))

    /** App module label color (blue). */
    val moduleApp = JBColor(Color(0x0078D7), Color(0x4DA8DA))

    /** Feature / dynamic-feature module label color (purple). */
    val moduleFeature = JBColor(Color(0x7B1FA2), Color(0xCE93D8))

    /** Library module label color (teal). */
    val moduleLib = JBColor(Color(0x00897B), Color(0x4DB6AC))
}

